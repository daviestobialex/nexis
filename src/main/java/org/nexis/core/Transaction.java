/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.nexis.base.Coin;
import org.nexis.core.TransactionConfidence.ConfidenceType;
import org.nexis.utilities.ByteUtils;
import static org.nexis.utilities.ByteUtils.writeInt32LE;
import org.nexis.utilities.ExchangeRate;
import org.nexis.utilities.Sha256Hash;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class Transaction {

    private static final Comparator<Transaction> SORT_TX_BY_ID = Comparator.comparing(Transaction::getTxId);

    /**
     * A comparator that can be used to sort transactions by their updateTime
     * field. The ordering goes from most recent into the past. Transactions
     * with an unknown update time will go to the end.
     */
    public static final Comparator<Transaction> SORT_TX_BY_UPDATE_TIME = Comparator.comparing(
            Transaction::sortableUpdateTime,
            Comparator.reverseOrder())
            .thenComparing(SORT_TX_BY_ID);

    // helps the above comparator by handling transactions with unknown update time
    private static Instant sortableUpdateTime(Transaction tx) {
        return tx.updateTime().orElse(Instant.EPOCH);
    }

    /**
     * A comparator that can be used to sort transactions by their chain height.
     * Unconfirmed transactions will go to the end.
     */
    public static final Comparator<Transaction> SORT_TX_BY_HEIGHT = Comparator.comparing(
            Transaction::sortableBlockHeight,
            Comparator.reverseOrder())
            .thenComparing(SORT_TX_BY_ID);

    // helps the above comparator by handling unconfirmed transactions
    private static int sortableBlockHeight(Transaction tx) {
        TransactionConfidence confidence = tx.getConfidence();
        return confidence.getConfidenceType() == ConfidenceType.BUILDING
                ? confidence.getAppearedAtChainHeight()
                : Block.BLOCK_HEIGHT_UNKNOWN;
    }

    /**
     * When this bit is set in protocolVersion, do not include witness. The
     * actual value is the same as in Bitcoin Core for consistency.
     */
    public static final int SERIALIZE_TRANSACTION_NO_WITNESS = 0x40000000;

    /**
     * @deprecated use {@link LockTime#THRESHOLD} or
     * {@code lockTime instanceof HeightLock} or
     * {@code lockTime instanceof TimeLock}
     *
     */
    @Deprecated
    public static final int LOCKTIME_THRESHOLD = (int) LockTime.THRESHOLD;

    /**
     * How many bytes a transaction can be before it won't be relayed anymore.
     * Currently 100kb.
     */
    public static final int MAX_STANDARD_TX_SIZE = 100_000;

    /**
     * If feePerKb is lower than this, Bitcoin Core will treat it as if there
     * were no fee.
     */
    public static final Coin REFERENCE_DEFAULT_MIN_TX_FEE = Coin.valueOf(1_000); // 0.01 mBTC

    /**
     * If using this feePerKb, transactions will get confirmed within the next
     * couple of blocks. This should be adjusted from time to time. Last
     * adjustment: February 2017.
     */
    public static final Coin DEFAULT_TX_FEE = Coin.valueOf(100_000); // 1 mBTC

    private final int protocolVersion;

    // These are bitcoin serialized.
    private long version;
    private List<TransactionInput> inputs;
    private List<TransactionOutput> outputs;

    private volatile LockTime vLockTime;

    // This is either the time the transaction was broadcast as measured from the local clock, or the time from the
    // block in which it was included. Note that this can be changed by re-orgs so the wallet may update this field.
    // Old serialized transactions don't have this field, thus null is valid. It is used for returning an ordered
    // list of transactions from a wallet, which is helpful for presenting to users.
//    @Nullable
    private Instant updateTime = null;

    // Data about how confirmed this tx is. Serialized, may be null.
//    @Nullable
    private TransactionConfidence confidence;

    // Records a map of which blocks the transaction has appeared in (keys) to an index within that block (values).
    // The "index" is not a real index, instead the values are only meaningful relative to each other. For example,
    // consider two transactions that appear in the same block, t1 and t2, where t2 spends an output of t1. Both
    // will have the same block hash as a key in their appearsInHashes, but the counter would be 1 and 2 respectively
    // regardless of where they actually appeared in the block.
    //
    // If this transaction is not stored in the wallet, appearsInHashes is null.
    private Map<Sha256Hash, Integer> appearsInHashes;

    /**
     * Returns the confidence object for this transaction from the
     * {@link TxConfidenceTable} referenced by the implicit {@link Context}.
     *
     * @return
     */
    public TransactionConfidence getConfidence() {
//        return getConfidence(Context.get());
        throw new UnsupportedOperationException("operation not implemented yet");
    }

    /**
     * Returns the confidence object for this transaction from the
     * {@link TxConfidenceTable} referenced by the given {@link Context}.
     */
    TransactionConfidence getConfidence(Context context) {
//        return getConfidence(context.getConfidenceTable());
        throw new UnsupportedOperationException("operation not implemented yet");
    }

    /**
     * Returns the confidence object for this transaction from the
     * {@link TxConfidenceTable}
     */
    TransactionConfidence getConfidence(TxConfidenceTable table) {
        if (confidence == null) {
            confidence = table.getOrCreate(getTxId());
        }
        return confidence;
    }

    /**
     * Check if the transaction has a known confidence
     *
     * @return
     */
    public boolean hasConfidence() {
        return getConfidence().getConfidenceType() != TransactionConfidence.ConfidenceType.UNKNOWN;
    }

    /**
     * This enum describes the underlying reason the transaction was created.
     * It's useful for rendering wallet GUIs more appropriately.
     */
    public enum Purpose {
        /**
         * Used when the purpose of a transaction is genuinely unknown.
         */
        UNKNOWN,
        /**
         * Transaction created to satisfy a user payment request.
         */
        USER_PAYMENT,
        /**
         * Transaction automatically created and broadcast in order to
         * reallocate money from old to new keys.
         */
        KEY_ROTATION,
        /**
         * Transaction that uses up pledges to an assurance contract and be used
         * in approvals on governance
         */
        ASSURANCE_CONTRACT_CLAIM,// used in approvals on goverance
        /**
         * Transaction that makes a pledge to an assurance contract and be used
         * in approvals on governance
         */
        ASSURANCE_CONTRACT_PLEDGE,
        /**
         * Send-to-self transaction that exists just to create an output of the
         * right size we can pledge.
         */
        ASSURANCE_CONTRACT_STUB,
        /**
         * Raise fee, e.g. child-pays-for-parent.
         */
        RAISE_FEE,
        // In future: de/refragmentation, privacy boosting/mixing, etc.
        // When adding a value, it also needs to be added to wallet.proto, WalletProtobufSerialize.makeTxProto()
        // and WalletProtobufSerializer.readTransaction()!
    }

    private Purpose purpose = Purpose.UNKNOWN;

    /**
     * This field can be used by applications to record the exchange rate that
     * was valid when the transaction happened. It's optional.
     */
//    @Nullable
    private ExchangeRate exchangeRate;

    /**
     * This field can be used to record the memo of the payment request that
     * initiated the transaction. It's optional.
     */
//    @Nullable
    private String memo;

    /**
     * Constructs an incomplete coinbase transaction with a minimal input script
     * and no outputs.
     *
     * @return coinbase transaction
     */
    public static Transaction genesis() {
        Transaction tx = new Transaction();
        tx.addInput(TransactionInput.genesisInput(tx, new byte[2])); // 2 is minimum
        return tx;
    }

    /**
     * Constructs an incomplete coinbase transaction with given bytes for the
     * input script and no outputs.
     *
     * @param inputScriptBytes arbitrary bytes for the coinbase input
     * @return coinbase transaction
     */
    public static Transaction genesis(byte[] inputScriptBytes) {
        Transaction tx = new Transaction();
        tx.addInput(TransactionInput.genesisInput(tx, inputScriptBytes));
        return tx;
    }

    /**
     * Deserialize from Nexus Protobuf message broadcast over network
     *
     * @param proto
     * @return
     */
    public static Transaction read(NexusProtocol.Transaction proto) {
        Transaction tx = new Transaction();
        tx.version = proto.getVersion();

        // Read inputs from protobuf
        tx.inputs = new ArrayList<>();
        for (NexusProtocol.TransactionInput inputProto : proto.getInputsList()) {
            tx.inputs.add(TransactionInput.read(inputProto, tx));
        }

        // Read outputs from protobuf
        tx.outputs = new ArrayList<>();
        for (NexusProtocol.TransactionOutput outputProto : proto.getOutputsList()) {
            tx.outputs.add(TransactionOutput.read(outputProto, tx));
        }

        tx.vLockTime = LockTime.of(proto.getLockTime());

        return tx;
    }

    /**
     * Serialize to Nexus Protobuf for broadcasting
     *
     * @return
     */
    public NexusProtocol.Transaction toProto() {
        NexusProtocol.Transaction.Builder builder = NexusProtocol.Transaction.newBuilder()
                .setVersion(version)
                .setLockTime(vLockTime.rawValue());

        // Add inputs
        for (TransactionInput input : inputs) {
            builder.addInputs(input.toProto());
        }

        // Add outputs
        for (TransactionOutput output : outputs) {
            builder.addOutputs(output.toProto());
        }

        return builder.build();
    }

    /**
     * Returns the transaction id as you see them in block explorers.It is used
     * as a reference by transaction inputs via outpoints.
     *
     * @return
     */
    public Sha256Hash getTxId() {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            serializeToStream(baos, false);
        } catch (IOException e) {
            throw new RuntimeException(e); // cannot happen
        }
        return Sha256Hash.wrapReversed(Sha256Hash.hashTwice(baos.toByteArray()));
    }

    /**
     * Serialize according to
     * <a href="https://github.com/bitcoin/bips/blob/master/bip-0144.mediawiki">BIP144</a>
     * or the
     * <a href="https://en.bitcoin.it/wiki/Protocol_documentation#tx">classic
     * format</a>, depending on if segwit is desired.
     *
     * @param stream
     * @param useSegwit
     * @throws java.io.IOException
     */
    protected void serializeToStream(OutputStream stream, boolean useSegwit) throws IOException {
        // version
        writeInt32LE(version, stream);
        // marker, flag
        if (useSegwit) {
            stream.write(0);
            stream.write(1);
        }
        // txin_count, txins
        stream.write(ByteUtils.writInt32BE(inputs.size()));
        for (TransactionInput in : inputs) {
            stream.write(in.serialize());
        }
        // txout_count, txouts
        stream.write(ByteUtils.writInt32BE(outputs.size()));
        for (TransactionOutput out : outputs) {
            stream.write(out.serialize());
        }
        // script_witnisses
        if (useSegwit) {
            for (TransactionInput in : inputs) {
                stream.write(in.getWitness().serialize());
            }
        }
        // lock_time
        writeInt32LE(vLockTime.rawValue(), stream);
    }

    private Transaction(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Transaction() {
        this.protocolVersion = 1; //ProtocolVersion.CURRENT.intValue(); hard coded for now
        version = 1;
        inputs = new ArrayList<>();
        outputs = new ArrayList<>();
        // We don't initialize appearsIn deliberately as it's only useful for transactions stored in the wallet.
        vLockTime = LockTime.unset();
    }

    /**
     * Returns the earliest time at which the transaction was seen (broadcast or
     * included into the chain), or empty if that information isn't available.
     *
     * @return
     */
    public Optional<Instant> updateTime() {
        return Optional.ofNullable(updateTime);
    }

    /**
     * Returns an unmodifiable view of all inputs.
     * @return 
     */
    public List<TransactionInput> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

    /**
     * Returns an unmodifiable view of all outputs.
     * @return 
     */
    public List<TransactionOutput> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }

    /**
     * Adds an input directly, with no checking that it's valid.
     *
     * @param input
     * @return the new input.
     */
    public TransactionInput addInput(TransactionInput input) {
        input.setParent(this);
        inputs.add(input);
        return input;
    }

}
