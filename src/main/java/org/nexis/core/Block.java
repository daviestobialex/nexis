/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.nexis.base.Sha256Hash;
import static org.nexis.base.Sha256Hash.hashTwice;
import org.nexis.base.utils.ByteUtils;
import org.nexis.exceptions.VerificationException;
import org.nexis.internal.InternalUtils;
import org.nexis.internal.TimeUtils;
import org.nexis.script.ScriptBuilder;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class Block {

    /**
     * How many bytes are required to represent a block header
     */
    public static final int HEADER_SIZE = 80;

    static final Duration ALLOWED_TIME_DRIFT = Duration.ofHours(2); // Same value as Bitcoin Core.

    /**
     * A constant shared by the entire network: how large in bytes a block is
     * allowed to be. One day we may have to upgrade everyone to change this, so
     * Bitcoin can continue to grow. For now it exists as an anti-DoS measure to
     * avoid somebody creating a titanically huge but valid block and forcing
     * everyone to download/store it forever.
     */
    public static final int MAX_BLOCK_SIZE = 1_000_000;

    /**
     * Value to use if the block height is unknown
     */
    public static final int BLOCK_HEIGHT_UNKNOWN = -1;
    /**
     * Height of the first block
     */
    public static final int BLOCK_HEIGHT_GENESIS = 0;

    public static final long BLOCK_VERSION_GENESIS = 1;

    /**
     * Block version introduced in BIP 34: Height in coinbase
     */
    public static final long BLOCK_VERSION_BIP34 = 2;
    /**
     * Block version introduced in BIP 66: Strict DER signatures
     */
    public static final long BLOCK_VERSION_BIP66 = 3;
    /**
     * Block version introduced in BIP 65: OP_CHECKLOCKTIMEVERIFY
     */
    public static final long BLOCK_VERSION_BIP65 = 4;

    // Fields defined as part of the protocol format.
    private final long version;
    private Sha256Hash prevBlockHash;
    private Sha256Hash merkleRoot, witnessRoot;
    // compact difficulty target (nBits format). Stored as unsigned 32-bit value.
    private int difficultyTarget = 0x1d07fff8; // default testnet/mainnet-like placeholder
    private Instant time;
    private long nonce;// used in minning but this may not be required

    /**
     * Stores the hash of the block. If null, getHash() will recalculate it.
     */
    private Sha256Hash hash;

    List<Transaction> transactions;

    /**
     * Special case constructor, used for unit tests.
     */
    // For testing only
    Block(long setVersion) {
        // Set up a few basic things. We are not complete after this though.
        this(setVersion,
                TimeUtils.currentTime().truncatedTo(ChronoUnit.SECONDS), // convert to Bitcoin time)
                0x1d07fff8L,
                Collections.emptyList());
    }

    // For unit-test genesis blocks
    // For testing only
    Block(long setVersion, Instant time, List<Transaction> transactions) {
        this(setVersion, time, 0, transactions);
        // Solve for nonce?
    }

    // For genesis blocks (and also unit tests)
    Block(long setVersion, Instant time, long nonce, List<Transaction> transactions) {
        this.version = setVersion;
        this.time = time;
        this.nonce = nonce;
        this.prevBlockHash = Sha256Hash.ZERO_HASH;
        this.transactions = new ArrayList<>(Objects.requireNonNull(transactions));
    }

    /**
     * Construct a block initialized with all the given fields.
     *
     * @param version This should usually be set to 1 or 2, depending on if the
     * height is in the genesis input.
     * @param prevBlockHash Reference to previous block in the chain or
     * {@link Sha256Hash#ZERO_HASH} if genesis.
     * @param merkleRoot The root of the merkle tree formed by the transactions.
     * @param time time when the block was mined.
     * @param nonce Arbitrary number to make the block hash lower than the
     * target.
     * @param transactions List of transactions including the genesis, or
     * {@code null} for header-only blocks
     */
    public Block(long version, Sha256Hash prevBlockHash, Sha256Hash merkleRoot, Instant time,
            long nonce, List<Transaction> transactions) {
        super();
        this.version = version;
        this.prevBlockHash = prevBlockHash;
        this.merkleRoot = merkleRoot;
        this.time = time;
        this.nonce = nonce;
        this.transactions = transactions != null
                ? new ArrayList<>(transactions)
                : null;
    }

    public static Block createGenesis(Instant time) {
        return new Block(BLOCK_VERSION_GENESIS, time, genesisTransactions());
    }

    public static Block createGenesis(Instant time, long nonce) {
        return new Block(BLOCK_VERSION_GENESIS, time, nonce, genesisTransactions());
    }
    
    private static List<Transaction> genesisTransactions() {
        Transaction tx = Transaction.genesis(genesisTxInputScriptBytes);
        tx.addOutput(new TransactionOutput(tx, MonetaryPolicy.getStartCoinsAsCoin(), genesisTxScriptPubKeyBytes));
        return Collections.singletonList(tx);
    }

    // A script containing the difficulty bits and the following message:
    //
    //   "Times on 04/Dec/2025, building something I could not resist but needed to be created."
    public static final byte[] genesisTxInputScriptBytes = ByteUtils.parseHex("04ffff001d01044554696d6573206f6e2030342f4465632f323032352c206275696c64696e6720736f6d657468696e67204920636f756c64206e6f742072657369737420627574206e656564656420746f20626520637265617465642e");

    public static final byte[] genesisTxScriptPubKeyBytes
            = new ScriptBuilder()
                    .smallNum(0)
                    .data(
                            ByteUtils.
                            parseHex("43332f52fa5163eee052675664db00e385b4388c"))
                    .build()
                    .program();//p2wpkh

//    private static final byte[] genesisTxScriptPubKeyBytes = new ScriptBuilder()
//            .data(ByteUtils.parseHex("fe7afe209b36127700166af92015cb1fd523885401987e99d61a831d4708cf71"))
//            .build()
//            .program();// p2pk

    /**
     * Returns whether this block conforms to
     * <a href="https://github.com/bitcoin/bips/blob/master/bip-0034.mediawiki">BIP34:
     * Height in Coinbase</a>.
     */
    public boolean isBIP34() {
        return version >= BLOCK_VERSION_BIP34;
    }

    /**
     * Returns whether this block conforms to
     * <a href="https://github.com/bitcoin/bips/blob/master/bip-0066.mediawiki">BIP66:
     * Strict DER signatures</a>.
     */
    public boolean isBIP66() {
        return version >= BLOCK_VERSION_BIP66;
    }

    /**
     * Returns whether this block conforms to
     * <a href="https://github.com/bitcoin/bips/blob/master/bip-0065.mediawiki">BIP65:
     * OP_CHECKLOCKTIMEVERIFY</a>.
     */
    public boolean isBIP65() {
        return version >= BLOCK_VERSION_BIP65;
    }

    /**
     * Construct a block initialized with all the given fields.
     *
     * @param version This should usually be set to 1 or 2, depending on if the
     * height is in the base input.
     * @param prevBlockHash Reference to previous block in the chain or
     * {@link Sha256Hash#ZERO_HASH} if genesis.
     * @param merkleRoot The root of the merkle tree formed by the transactions.
     * @param hash
     * @param time time when the block was mined.
     * @param nonce Arbitrary number to make the block hash lower than the
     * target.
     * @param difficultyTarget
     * @param transactions List of transactions including the coinbase, or
     * {@code null} for header-only blocks
     */
    protected Block(
            long version,
            Sha256Hash prevBlockHash,
            Sha256Hash merkleRoot,
            Sha256Hash hash,
            Instant time,
            long nonce,
            int difficultyTarget,
            List<Transaction> transactions) {
        this.version = version;
        this.prevBlockHash = prevBlockHash;
        this.merkleRoot = merkleRoot;
        this.hash = hash;
        this.time = time;
        this.nonce = nonce;
        this.difficultyTarget = difficultyTarget;
        this.transactions = transactions != null ? new ArrayList<>(transactions) : null;
    }

    /**
     * Deserialize this message from a given payload.
     *
     * @param payload payload to deserialize from
     * @return read message
     */
    public static Block read(NexusProtocol.Block payload) {

        long nonce = payload.getNonce();
        long version = payload.getVersion();
        Sha256Hash prevHash = Sha256Hash.of(payload.getHeader().getPrevHash().toByteArray());
        Sha256Hash merkelRoot = Sha256Hash.of(payload.getHeader().getMerkleRoot().toByteArray());
        Sha256Hash hash = Sha256Hash.of(payload.getHeader().getBlockHash().toByteArray());
        int difficultyTarget = 0;
        if (payload.hasDifficultyTarget()) {
            difficultyTarget = payload.getDifficultyTarget();
        }
        long timestamp = payload.getTimestamp();
        List<Transaction> transactions = payload.getTransactionsList()
                .stream().map(proto -> Transaction.read(proto))
                .collect(Collectors.toList());

        return new Block(version,
                prevHash,
                merkelRoot, hash,
                Instant.ofEpochSecond(timestamp),
                nonce,
                difficultyTarget,
                transactions);
    }

    /**
     * Returns the hash of the block (which for a valid, solved block should be
     * below the target). Big endian.
     */
    public Sha256Hash getHash() {
        if (hash == null) {
            hash = calculateHash();
        }
        return hash;
    }

    /**
     * Calculates the block hash by serializing the block and hashing the
     * resulting bytes.
     */
    private Sha256Hash calculateHash() {
        try {
            ByteArrayOutputStream bos = new ByteArrayOutputStream(HEADER_SIZE);
            writeHeader(bos);
            return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(bos.toByteArray()));
        } catch (IOException e) {
            throw new RuntimeException(e); // Cannot happen.
        }
    }

    // default for testing
    void writeHeader(OutputStream stream) throws IOException {
        ByteUtils.writeInt32LE(version, stream);
        stream.write(prevBlockHash.serialize());
        stream.write(getMerkleRoot().serialize());
        ByteUtils.writeInt32LE(time.getEpochSecond(), stream);
        // write compact difficulty (nBits) then the nonce
        ByteUtils.writeInt32LE(difficultyTarget, stream);
        ByteUtils.writeInt32LE(nonce, stream);
    }

    /**
     * The number that is one greater than the largest representable SHA-256
     * hash.
     */
    /**
     * Returns a copy of the block, but without any transactions.
     *
     * @return new, header-only {@code Block}
     */
    public Block cloneAsHeader() {
        Block block = new Block(version, prevBlockHash, getMerkleRoot(), time, nonce, null);
        block.hash = getHash();
        return block;
    }

    /**
     * Returns the merkle root in bi
            int difficultyTarget,g endian form, calculating it from
     * transactions if necessary.
     *
     * @return
     */
    public Sha256Hash getMerkleRoot() {
        if (merkleRoot == null) {
            //TODO check if this is really necessary.
            unCacheHeader();
            merkleRoot = calculateMerkleRoot();
        }
        return merkleRoot;
    }

    private void unCacheHeader() {
        hash = null;
    }

    /**
     * Exists only for unit testing.
     */
    // For testing only
    void setMerkleRoot(Sha256Hash value) {
        unCacheHeader();
        merkleRoot = value;
        hash = null;
    }

    /**
     * Returns the witness root in big endian form, calculating it from
     * transactions if necessary.
     * @return 
     */
    public Sha256Hash getWitnessRoot() {
        if (witnessRoot == null) {
            witnessRoot = calculateWitnessRoot();
        }
        return witnessRoot;
    }

    private Sha256Hash calculateMerkleRoot() {
        List<Sha256Hash> tree = buildMerkleTree(false);
        return tree.get(tree.size() - 1);
    }

    private Sha256Hash calculateWitnessRoot() {
        List<Sha256Hash> tree = buildMerkleTree(true);
        return tree.get(tree.size() - 1);
    }

    private List<Sha256Hash> buildMerkleTree(boolean useWTxId) {
        // The Merkle root is based on a tree of hashes calculated from the transactions:
        //
        //     root
        //      / \
        //   A      B
        //  / \    / \
        // t1 t2 t3 t4
        //
        // The tree is represented as a list: t1,t2,t3,t4,A,B,root where each
        // entry is a hash.
        //
        // The hashing algorithm is double SHA-256. The leaves are a hash of the serialized contents of the transaction.
        // The interior nodes are hashes of the concatenation of the two child hashes.
        //
        // This structure allows the creation of proof that a transaction was included into a block without having to
        // provide the full block contents. Instead, you can provide only a Merkle branch. For example to prove tx2 was
        // in a block you can just provide tx2, the hash(tx1) and B. Now the other party has everything they need to
        // derive the root, which can be checked against the block header. These proofs aren't used right now but
        // will be helpful later when we want to download partial block contents.
        //
        // Note that if the number of transactions is not even the last tx is repeated to make it so (see
        // tx3 above). A tree with 5 transactions would look like this:
        //
        //         root
        //        /     \
        //       1        5
        //     /   \     / \
        //    2     3    4  4
        //  / \   / \   / \
        // t1 t2 t3 t4 t5 t5
        ArrayList<Sha256Hash> tree = new ArrayList<>(transactions.size());
        // Start by adding all the hashes of the transactions as leaves of the tree.
        for (Transaction tx : transactions) {
            final Sha256Hash hash;
            if (useWTxId && tx.isGenesis()) {
                hash = Sha256Hash.ZERO_HASH;
            } else {
                hash = useWTxId ? tx.getWTxId() : tx.getTxId();
            }
            tree.add(hash);
        }
        int levelOffset = 0; // Offset in the list where the currently processed level starts.
        // Step through each level, stopping when we reach the root (levelSize == 1).
        for (int levelSize = transactions.size(); levelSize > 1; levelSize = (levelSize + 1) / 2) {
            // For each pair of nodes on that level:
            for (int left = 0; left < levelSize; left += 2) {
                // The right hand node can be the same as the left hand, in the case where we don't have enough
                // transactions.
                int right = Math.min(left + 1, levelSize - 1);
                Sha256Hash leftHash = tree.get(levelOffset + left);
                Sha256Hash rightHash = tree.get(levelOffset + right);
                tree.add(Sha256Hash.wrapReversed(hashTwice(
                        leftHash.serialize(),
                        rightHash.serialize())));
            }
            // Move to the next level.
            levelOffset += levelSize;
        }
        return tree;
    }

    /**
     * Returns the hash of the previous block in the chain, as defined by the
     * block header.
     *
     * @return
     */
    public Sha256Hash getPrevBlockHash() {
        return prevBlockHash;
    }

    /**
     * Returns a multi-line string containing a description of the contents of
     * the block.Use for debugging purposes only.
     *
     * @return
     */
    @Override
    public String toString() {
        StringBuilder s = new StringBuilder();
        s.append(" block: \n");
        s.append("   hash: ").append(getHashAsString()).append('\n');
        s.append("   version: ").append(version);
        String bips = InternalUtils.commaJoin(isBIP34() ? "BIP34" : null, isBIP66() ? "BIP66" : null, isBIP65() ? "BIP65" : null);
        if (!bips.isEmpty()) {
            s.append(" (").append(bips).append(')');
        }
        s.append('\n');
        s.append("   previous block: ").append(getPrevBlockHash()).append("\n");
        s.append("   time: ").append(time).append(" (").append(TimeUtils.dateTimeFormat(time)).append(")\n");
//        s.append("   difficulty target (nBits): ").append(difficultyTarget).append("\n");
        s.append("   nonce: ").append(nonce).append("\n");
        if (transactions != null && transactions.size() > 0) {
            s.append("   merkle root: ").append(getMerkleRoot()).append("\n");
            s.append("   witness root: ").append(getWitnessRoot()).append("\n");
            s.append("   with ").append(transactions.size()).append(" transaction(s):\n");
            for (Transaction tx : transactions) {
                s.append(tx).append('\n');
            }
        }
        return s.toString();
    }

    /**
     * Returns the hash of the block (which for a valid, solved block should be
     * below the target) in the form seen on the block explorer.If you call this
     * on block 1 in the mainnet chain you will get
     * "00000000839a8e6886ab5951d76f411475428afc90947ee320161bbf18eb6048".
     *
     * @return
     */
    public String getHashAsString() {
        return getHash().toString();
    }

    /**
     * Returns the work represented by this block.<p>
     *
     * Work is defined as the number of tries needed to solve a block in the
     * average case. Consider a difficulty target that covers 5% of all possible
     * hash values. Then the work of the block will be 20. As the target gets
     * lower, the amount of work goes up.
     */
    public BigInteger getWork() throws VerificationException {
        throw new UnsupportedOperationException("");
    }

    /**
     * Returns the compact difficulty target (nBits) for this block.
     */
    public int getDifficultyTarget() {
        return difficultyTarget;
    }
}
