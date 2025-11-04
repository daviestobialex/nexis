/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import static org.nexis.base.Coin.FIFTY_COINS;
import org.nexis.base.Sha256Hash;
import org.nexis.base.utils.ByteUtils;
import org.nexis.internal.TimeUtils;
import org.nexis.script.ScriptBuilder;
import org.nexis.script.ScriptOpCodes;
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

    // Fields defined as part of the protocol format.
    private final long version;
    private Sha256Hash prevBlockHash;
    private Sha256Hash merkleRoot, witnessRoot;
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
     * height is in the coinbase input.
     * @param prevBlockHash Reference to previous block in the chain or
     * {@link Sha256Hash#ZERO_HASH} if genesis.
     * @param merkleRoot The root of the merkle tree formed by the transactions.
     * @param time time when the block was mined.
     * @param nonce Arbitrary number to make the block hash lower than the
     * target.
     * @param transactions List of transactions including the coinbase, or
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
        tx.addOutput(new TransactionOutput(tx, FIFTY_COINS, genesisTxScriptPubKeyBytes));
        return Collections.singletonList(tx);
    }

    // A script containing the difficulty bits and the following message:
    //
    //   "The Times 03/Jan/2009 Chancellor on brink of second bailout for banks"
    private static final byte[] genesisTxInputScriptBytes = ByteUtils.parseHex("04ffff001d01044554696d6573206f6e2030342f4465632f323032352c206275696c64696e6720736f6d657468696e67204920636f756c64206e6f742072657369737420627574206e656564656420746f20626520637265617465642e");

    private static final byte[] genesisTxScriptPubKeyBytes = new ScriptBuilder()
            .data(ByteUtils.parseHex("fe7afe209b36127700166af92015cb1fd523885401987e99d61a831d4708cf71"))
            .op(ScriptOpCodes.OP_CHECKSIG)
            .build()
            .program();

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
            List<Transaction> transactions) {
        this.version = version;
        this.prevBlockHash = prevBlockHash;
        this.merkleRoot = merkleRoot;
        this.hash = hash;
        this.time = time;
        this.nonce = nonce;
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
        long timestamp = payload.getTimestamp();
        List<Transaction> transactions = payload.getTransactionsList()
                .stream().map(proto -> Transaction.read(proto))
                .collect(Collectors.toList());

        return new Block(version,
                prevHash,
                merkelRoot, hash,
                Instant.ofEpochSecond(timestamp),
                nonce,
                transactions);
    }
}
