/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.core;

import com.google.common.math.IntMath;
import com.google.protobuf.ByteString;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SignatureException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.Address;
import org.nexis.base.Coin;
import org.nexis.base.Identity;
import org.nexis.base.Network;
import org.nexis.base.VarInt;
import org.nexis.core.TransactionConfidence.ConfidenceType;
import org.nexis.exceptions.VerificationException;
import static org.nexis.internal.Preconditions.checkArgument;
import org.nexis.script.Script;
import org.nexis.script.ScriptException;
import org.nexis.script.ScriptOpCodes;
import org.nexis.base.utils.ByteUtils;
import static org.nexis.base.utils.ByteUtils.writeInt32LE;
import static org.nexis.base.utils.ByteUtils.writeInt64LE;
import org.nexis.utilities.CryptographyUtils;
import org.nexis.utilities.ExchangeRate;
import org.nexis.base.Sha256Hash;
import static org.nexis.core.ProtocolVersion.WITNESS_VERSION;
import org.nexis.exceptions.ProtocolException;
import static org.nexis.internal.Preconditions.check;
import static org.nexis.internal.StreamUtils.MAX_INITIAL_ARRAY_LENGTH;
import org.nexis.wallet.WalletTransaction.Pool;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class Transaction {

    private final static Logger log = Logger.getLogger(Transaction.class.getName());
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

    // These are bitcoin serialized.
    private long version;
    private List<TransactionInput> inputs;
    private List<TransactionOutput> outputs;

    private volatile LockTime vLockTime;

    private final int protocolVersion;

    // These are in memory helpers only. They contain the transaction hashes without and with witness.
    private Sha256Hash cachedTxId;
    private Sha256Hash cachedWTxId;

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
        return getConfidence(Context.get());
    }

    /**
     * Returns the confidence object for this transaction from the
     * {@link TxConfidenceTable} referenced by the given {@link Context}.
     */
    TransactionConfidence getConfidence(Context context) {
        return getConfidence(context.getConfidenceTable());
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
     * Constructs an incomplete genesis transaction with a minimal input script
     * and no outputs.
     *
     * @return genesis transaction
     */
    public static Transaction genesis() {
        Transaction tx = new Transaction();
        tx.addInput(TransactionInput.genesisInput(tx, new byte[2])); // 2 is minimum
        return tx;
    }

    /**
     * Constructs an incomplete genesis transaction with given bytes for the
     * input script and no outputs.
     *
     * @param inputScriptBytes arbitrary bytes for the genesis input
     * @return genesis transaction
     */
    public static Transaction genesis(byte[] inputScriptBytes) {
        Transaction tx = new Transaction();
        tx.addInput(TransactionInput.genesisInput(tx, inputScriptBytes));
        return tx;
    }

    /**
     * Deserialize this message from a given payload.
     *
     * @param payload payload to deserialize from
     * @return read message
     * @throws BufferUnderflowException if the read message extends beyond the
     * remaining bytes of the payload
     */
    public static Transaction read(ByteBuffer payload) throws BufferUnderflowException, ProtocolException {
        return Transaction.read(payload, ProtocolVersion.CURRENT.intValue());
    }

    /**
     * Deserialize this message from a given payload, according to
     * <a href="https://github.com/bitcoin/bips/blob/master/bip-0144.mediawiki">BIP144</a>
     * or the
     * <a href="https://en.bitcoin.it/wiki/Protocol_documentation#tx">classic
     * format</a>, depending on if the transaction is segwit or not.
     *
     * @param payload payload to deserialize from
     * @param protocolVersion protocol version to use for deserialization
     * @return read message
     * @throws BufferUnderflowException if the read message extends beyond the
     * remaining bytes of the payload
     */
    public static Transaction read(ByteBuffer payload, int protocolVersion) throws BufferUnderflowException, ProtocolException {
        Transaction tx = new Transaction(protocolVersion);
        boolean allowWitness = allowWitness(protocolVersion);

        // version
        tx.version = ByteUtils.readUint32(payload);
        byte flags = 0;
        // Try to parse the inputs. In case the dummy is there, this will be read as an empty array list.
        tx.readInputs(payload);
        if (tx.inputs.size() == 0 && allowWitness) {
            // We read a dummy or an empty input
            flags = payload.get();

            if (flags != 0) {
                tx.readInputs(payload);
                tx.readOutputs(payload);
            } else {
                tx.outputs = new ArrayList<>(0);
            }
        } else {
            // We read non-empty inputs. Assume normal outputs follows.
            tx.readOutputs(payload);
        }

        if (((flags & 1) != 0) && allowWitness) {
            // The witness flag is present, and we support witnesses.
            flags ^= 1;
            // script_witnesses
            tx.readWitnesses(payload);
            if (!tx.hasWitnesses()) {
                // It's illegal to encode witnesses when all witness stacks are empty.
                throw new ProtocolException("Superfluous witness record");
            }
        }
        if (flags != 0) {
            // Unknown flag in the serialization
            throw new ProtocolException("Unknown transaction optional data");
        }
        // lock_time
        tx.vLockTime = LockTime.of(ByteUtils.readUint32(payload));
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
        tx.version = proto.getVersion();// manifest, wallet, and proto must have the same version

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
     * Gets the transaction weight as defined in BIP141.
     */
    public int getWeight() {
        if (!hasWitnesses()) {
            return this.messageSize() * 4;
        }
        int baseSize = messageSize(false);
        int totalSize = messageSize(true);
        return baseSize * 3 + totalSize;
    }

    /**
     * Gets the virtual transaction size as defined in BIP141.
     */
    public int getVsize() {
        if (!hasWitnesses()) {
            return this.messageSize();
        }
        return IntMath.divide(getWeight(), 4, RoundingMode.CEILING); // round up
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

//        builder.setSignature(ByteString.copyFrom(bytes));// TODO: need to understand this
        return builder.build();
    }

    private void readInputs(ByteBuffer payload) throws BufferUnderflowException, ProtocolException {
        VarInt numInputsVarInt = VarInt.read(payload);
        check(numInputsVarInt.fitsInt(), BufferUnderflowException::new);
        int numInputs = numInputsVarInt.intValue();
        inputs = new ArrayList<>(Math.min((int) numInputs, MAX_INITIAL_ARRAY_LENGTH));
        for (long i = 0; i < numInputs; i++) {
            inputs.add(TransactionInput.read(payload, this));
        }
        invalidateCachedTxIds();
    }

    private void readOutputs(ByteBuffer payload) throws BufferUnderflowException, ProtocolException {
        VarInt numOutputsVarInt = VarInt.read(payload);
        check(numOutputsVarInt.fitsInt(), BufferUnderflowException::new);
        int numOutputs = numOutputsVarInt.intValue();
        outputs = new ArrayList<>(Math.min((int) numOutputs, MAX_INITIAL_ARRAY_LENGTH));
        for (long i = 0; i < numOutputs; i++) {
            outputs.add(TransactionOutput.read(payload, this));
        }
        invalidateCachedTxIds();
    }

    private void readWitnesses(ByteBuffer payload) throws BufferUnderflowException, ProtocolException {
        int numInputs = inputs.size();
        for (int i = 0; i < numInputs; i++) {
            TransactionWitness witness = TransactionWitness.read(payload);
            replaceInput(i, getInput(i).withWitness(witness));
        }
    }

    /**
     * @return true of the transaction has any witnesses in any of its inputs
     */
    public boolean hasWitnesses() {
        return inputs.stream().anyMatch(TransactionInput::hasWitness);
    }

    /**
     * Replaces an already added input. This is meant to amend a transaction
     * before it's committed to a wallet.
     *
     * @param index index of input to replace
     * @param input input to replace with
     */
    public void replaceInput(int index, TransactionInput input) {
        TransactionInput oldInput = inputs.remove(index);
        oldInput.setParent(null);
        input.setParent(this);
        inputs.add(index, input);
        invalidateCachedTxIds();
    }

    /**
     * invalidates cache for both transaction IDs
     */
    private void invalidateCachedTxIds() {
        cachedTxId = null;
        cachedWTxId = null;
    }

    /**
     * Returns the transaction ID as you see them in block explorers. It is used
     * as a reference by transaction inputs via outpoints.
     *
     * @return transaction ID
     */
    public Sha256Hash getTxId() {
        if (cachedTxId == null) {
            if (!hasWitnesses() && cachedWTxId != null) {
                cachedTxId = cachedWTxId;
            } else {
                ByteBuffer buf = ByteBuffer.allocate(messageSize(false));
                write(buf, false);
                cachedTxId = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(buf.array()));
            }
        }
        return cachedTxId;
    }

    /**
     * Determines if segwit serialization should be used for this transaction,
     * based on whether it has any witnesses and on protocol version.
     *
     * @return {@code true} if segwit serialization should be used,
     * {@code false} otherwise
     */
    private boolean useSegwitSerialization() {
        return hasWitnesses() && allowWitness(protocolVersion);
    }

    public int messageSize() {
        return messageSize(useSegwitSerialization());
    }

    private int messageSize(boolean useSegwitSerialization) {
        int size = 4; // version
        if (useSegwitSerialization) {
            size += 2; // marker, flag
        }
        size += VarInt.sizeOf(inputs.size())
                + inputs.stream()
                        .mapToInt(TransactionInput::messageSize)
                        .sum()
                + VarInt.sizeOf(outputs.size())
                + outputs.stream()
                        .mapToInt(TransactionOutput::messageSize)
                        .sum();
        if (useSegwitSerialization) {
            size += inputs.stream()
                    .mapToInt(in -> in.getWitness().messageSize())
                    .sum();
        }
        size += 4; // locktime
        return size;
    }

    public ByteBuffer write(ByteBuffer buf) throws BufferOverflowException {
        write(buf, useSegwitSerialization());
        return buf;
    }

    /**
     * Serialize according to
     * <a href="https://github.com/bitcoin/bips/blob/master/bip-0144.mediawiki">BIP144</a>
     * or the
     * <a href="https://en.bitcoin.it/wiki/Protocol_documentation#tx">classic
     * format</a>, depending on if segwit is desired.
     *
     * @param buf
     * @param useSegwitSerialization
     */
    protected void write(ByteBuffer buf, boolean useSegwitSerialization) throws BufferOverflowException {
        // version
        writeInt32LE(version, buf);
        // marker, flag
        if (useSegwitSerialization) {
            buf.put((byte) 0);
            buf.put((byte) 1);
        }
        // txin_count, txins
        VarInt.of(inputs.size()).write(buf);
        for (TransactionInput in : inputs) {
            in.write(buf);
        }
        // txout_count, txouts
        VarInt.of(outputs.size()).write(buf);
        for (TransactionOutput out : outputs) {
            out.write(buf);
        }
        // script_witnesses
        if (useSegwitSerialization) {
            for (TransactionInput in : inputs) {
                in.getWitness().write(buf);
            }
        }
        // lock_time
        writeInt32LE(vLockTime.rawValue(), buf);
    }

    private Transaction(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public Transaction() {
        this.protocolVersion = ProtocolVersion.CURRENT.intValue();
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
     *
     * @return
     */
    public List<TransactionInput> getInputs() {
        return Collections.unmodifiableList(inputs);
    }

    /**
     * Returns an unmodifiable view of all outputs.
     *
     * @return
     */
    public List<TransactionOutput> getOutputs() {
        return Collections.unmodifiableList(outputs);
    }

    /**
     * Adds the given output to this transaction.The output must be completely
     * initialized.Returns the given output.
     *
     * @param to
     * @return
     */
    public TransactionOutput addOutput(TransactionOutput to) {
        to.setParent(this);
        outputs.add(to);
        return to;
    }

    /**
     * Creates an output based on the given address and value, adds it to this
     * transaction, and returns the new output.
     *
     * @param value
     * @param address
     * @return
     */
    public TransactionOutput addOutput(Coin value, Address address) {
        return addOutput(new TransactionOutput(this, value, address));
    }

    /**
     * Creates an output that pays to the given pubkey directly (no address)
     * with the given value, adds it to this transaction, and returns the new
     * output.
     *
     * @param value
     * @param pubkey
     * @return
     */
    public TransactionOutput addOutput(Coin value, PublicKey pubkey) {
        return addOutput(new TransactionOutput(this, value, pubkey.getEncoded()));
    }

    /**
     * Creates an output that pays to the given script.The address and key forms
     * are specialisations of this method, you won't normally need to use it
     * unless you're doing unusual things.
     *
     * @param value
     * @param script
     * @return
     */
    public TransactionOutput addOutput(Coin value, Script script) {
        return addOutput(new TransactionOutput(this, value, script.program()));
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

    /**
     * Adds an input to this transaction that imports value from the given
     * output.Note that this input is <i>not</i>
     * complete and after every input is added with
     * {@link #addInput(TransactionInput)} and every output is added with
     * {@link #addOutput(TransactionOutput)}, a {@link TransactionSigner} must
     * be used to finalize the transaction and finish the inputs off. Otherwise
     * it won't be accepted by the network.
     *
     * @param from
     * @return the newly created input.
     */
    public TransactionInput addInput(TransactionOutput from) {
        return addInput(new TransactionInput(this, from));
    }

    /**
     * Creates and adds an input to this transaction, with no checking that it's
     * valid.
     *
     * @param spendTxHash
     * @param outputIndex
     * @param script
     * @return the newly created input.
     */
    public TransactionInput addInput(Sha256Hash spendTxHash, long outputIndex, Script script) {
        return addInput(new TransactionInput(this, script.program(), new TransactionOutPoint(outputIndex, spendTxHash)));
    }

    /**
     * Transactions can have an associated lock time, specified either as a
     * block height or as a timestamp (in seconds since epoch). A transaction is
     * not allowed to be confirmed by miners until the lock time is reached, and
     * since Bitcoin 0.8+ a transaction that did not end its lock period (non
     * final) is considered to be non standard and won't be relayed or included
     * in the memory pool either.
     *
     * @return lock time, wrapped in a {@link LockTime}
     */
    public LockTime lockTime() {
        return vLockTime;
    }

    /**
     * Transactions can have an associated lock time, specified either as a
     * block height or as a timestamp (in seconds since epoch).A transaction is
     * not allowed to be confirmed by miners until the lock time is reached, and
     * since Bitcoin 0.8+ a transaction that did not end its lock period (non
     * final) is considered to be non standard and won't be relayed or included
     * in the memory pool either.
     *
     * @param lockTime
     */
    public void setLockTime(long lockTime) {
        boolean seqNumSet = false;
        for (TransactionInput input : inputs) {
            if (input.getSequenceNumber() != TransactionInput.NO_SEQUENCE) {
                seqNumSet = true;
                break;
            }
        }
        if (lockTime != 0 && (!seqNumSet || inputs.isEmpty())) {
            // At least one input must have a non-default sequence number for lock times to have any effect.
            // For instance one of them can be set to zero to make this feature work.
            log.log(Level.WARNING, "You are setting the lock time on a transaction but none of the inputs have non-default sequence numbers. This will not do what you expect!");
        }
        this.vLockTime = LockTime.of(lockTime);
    }

    public long getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    /**
     * Same as getInputs().get(index).
     *
     * @param index
     * @return
     */
    public TransactionInput getInput(long index) {
        return inputs.get((int) index);
    }

    /**
     * Same as getOutputs().get(index)
     *
     * @param index
     * @return
     */
    public TransactionOutput getOutput(long index) {
        return outputs.get((int) index);
    }

    /**
     * These constants are a part of a scriptSig signature on the inputs. They
     * define the details of how a transaction can be redeemed, specifically,
     * they control how the hash of the transaction is calculated.
     */
    public enum SigHash {
        ALL(1),
        NONE(2),
        SINGLE(3),
        ANYONECANPAY(0x80), // Caution: Using this type in isolation is non-standard. Treated similar to ANYONECANPAY_ALL.
        ANYONECANPAY_ALL(0x81),
        ANYONECANPAY_NONE(0x82),
        ANYONECANPAY_SINGLE(0x83),
        UNSET(0); // Caution: Using this type in isolation is non-standard. Treated similar to ALL.

        public final int value;

        /**
         * @param value
         */
        SigHash(final int value) {
            this.value = value;
        }

        /**
         * @return the value as a byte
         */
        public byte byteValue() {
            return (byte) this.value;
        }
    }

    /**
     * Returns the purpose for which this transaction was created.See the
     * javadoc for {@link Purpose} for more information on the point of this
     * field and what it can be.
     *
     * @return
     */
    public Purpose getPurpose() {
        return purpose;
    }

    /**
     * Marks the transaction as being created for the given purpose.See the
     * javadoc for {@link Purpose} for more information on the point of this
     * field and what it can be.
     *
     * @param purpose
     */
    public void setPurpose(Purpose purpose) {
        this.purpose = purpose;
    }

    /**
     * Getter for {@link #exchangeRate}.
     *
     * @return
     */
//    @Nullable
    public ExchangeRate getExchangeRate() {
        return exchangeRate;
    }

    /**
     * Setter for {@link #exchangeRate}.
     *
     * @param exchangeRate
     */
    public void setExchangeRate(ExchangeRate exchangeRate) {
        this.exchangeRate = exchangeRate;
    }

    /**
     * Returns the transaction {@link #memo}.
     *
     * @return
     */
//    @Nullable
    public String getMemo() {
        return memo;
    }

    /**
     * Set the transaction {@link #memo}.It can be used to record the memo of
     * the payment request that initiated the transaction.
     *
     * @param memo
     */
    public void setMemo(String memo) {
        this.memo = memo;
    }

    /**
     * <p>
     * Calculates a signature hash, that is, a hash of a simplified form of the
     * transaction.How exactly the transaction is simplified is specified by the
     * type and anyoneCanPay parameters.</p>
     *
     * <p>
     * This is a low level API and when using the regular {@link Wallet} class
     * you don't have to call this yourself. When working with more complex
     * transaction types and contracts, it can be necessary. When signing a P2SH
     * output the redeemScript should be the script encoded into the scriptSig
     * field, for normal transactions, it's the scriptPubKey of the output
     * you're signing for.</p>
     *
     * @param inputIndex input the signature is being calculated for. Tx
     * signatures are always relative to an input.
     * @param redeemScript the script that should be in the given input during
     * signing.
     * @param type Should be SigHash.ALL
     * @param anyoneCanPay should be false.
     * @return
     */
    public Sha256Hash hashForSignature(int inputIndex, Script redeemScript,
            SigHash type, boolean anyoneCanPay) {
        int sigHash = calcSigHashValue(type, anyoneCanPay);
        return hashForSignature(inputIndex, redeemScript.program(), (byte) sigHash);
    }

    /**
     * Calculates the byte used in the protocol to represent the combination of
     * mode and anyoneCanPay.
     *
     * @param mode
     * @param anyoneCanPay
     * @return
     */
    public static int calcSigHashValue(Transaction.SigHash mode, boolean anyoneCanPay) {
        checkArgument(SigHash.ALL == mode || SigHash.NONE == mode || SigHash.SINGLE == mode); // enforce compatibility since this code was made before the SigHash enum was updated
        int sighashFlags = mode.value;
        if (anyoneCanPay) {
            sighashFlags |= Transaction.SigHash.ANYONECANPAY.value;
        }
        return sighashFlags;
    }

    /**
     * Returns if tx witnesses are allowed based on the protocol version
     */
    private static boolean allowWitness(int protocolVersion) {
        return (protocolVersion & SERIALIZE_TRANSACTION_NO_WITNESS) == 0
                && protocolVersion >= WITNESS_VERSION.intValue();
    }

    /**
     * Serialize this message to a byte array that conforms to the Bitcoin wire
     * protocol.
     *
     * @return serialized data in Bitcoin protocol format
     */
    public byte[] serialize() {
        return write(ByteBuffer.allocate(messageSize())).array();
    }

    /**
     * This is required for signatures which use a sigHashType which cannot be
     * represented using SigHash and anyoneCanPay See transaction
     * c99c49da4c38af669dea436d3e73780dfdb6c1ecf9958baa52960e8baee30e73, which
     * has sigHashType 0
     *
     * @param inputIndex
     * @param connectedScript
     * @param sigHashType
     * @return
     */
    public Sha256Hash hashForSignature(int inputIndex, byte[] connectedScript, byte sigHashType) {
        // The SIGHASH flags are used in the design of contracts, please see this page for a further understanding of
        // the purposes of the code in this method:
        //
        //   https://en.bitcoin.it/wiki/Contracts

        // Create a copy of this transaction to operate upon because we need make changes to the inputs and outputs.
        // It would not be thread-safe to change the attributes of the transaction object itself.
        Transaction tx = Transaction.read(ByteBuffer.wrap(serialize()));

        // Clear input scripts in preparation for signing. If we're signing a fresh
        // transaction that step isn't very helpful, but it doesn't add much cost relative to the actual
        // EC math so we'll do it anyway.
        for (int i = 0; i < tx.inputs.size(); i++) {
            TransactionInput input = tx.getInput(i);
            input = input.withoutScriptBytes();
            input = input.withoutWitness();
            tx.replaceInput(i, input);
        }

        // This step has no purpose beyond being synchronized with Bitcoin Core's bugs. OP_CODESEPARATOR
        // is a legacy holdover from a previous, broken design of executing scripts that shipped in Bitcoin 0.1.
        // It was seriously flawed and would have let anyone take anyone elses money. Later versions switched to
        // the design we use today where scripts are executed independently but share a stack. This left the
        // OP_CODESEPARATOR instruction having no purpose as it was only meant to be used internally, not actually
        // ever put into scripts. Deleting OP_CODESEPARATOR is a step that should never be required but if we don't
        // do it, we could split off the best chain.
        connectedScript = Script.removeAllInstancesOfOp(connectedScript, ScriptOpCodes.OP_CODESEPARATOR);

        // Set the input to the script of its output. Bitcoin Core does this but the step has no obvious purpose as
        // the signature covers the hash of the prevout transaction which obviously includes the output script
        // already. Perhaps it felt safer to him in some way, or is another leftover from how the code was written.
        TransactionInput input = tx.getInput(inputIndex);
        input = input.withScriptBytes(connectedScript);
        tx.replaceInput(inputIndex, input);

        if ((sigHashType & 0x1f) == SigHash.NONE.value) {
            // SIGHASH_NONE means no outputs are signed at all - the signature is effectively for a "blank cheque".
            tx.outputs = new ArrayList<>(0);
            // The signature isn't broken by new versions of the transaction issued by other parties.
            for (int i = 0; i < tx.inputs.size(); i++) {
                if (i != inputIndex) {
                    tx.replaceInput(i, tx.getInput(i).withSequence(0));
                }
            }
        } else if ((sigHashType & 0x1f) == SigHash.SINGLE.value) {
            // SIGHASH_SINGLE means only sign the output at the same index as the input (ie, my output).
            if (inputIndex >= tx.outputs.size()) {
                // The input index is beyond the number of outputs, it's a buggy signature made by a broken
                // Bitcoin implementation. Bitcoin Core also contains a bug in handling this case:
                // any transaction output that is signed in this case will result in both the signed output
                // and any future outputs to this public key being steal-able by anyone who has
                // the resulting signature and the public key (both of which are part of the signed tx input).

                // Bitcoin Core's bug is that SignatureHash was supposed to return a hash and on this codepath it
                // actually returns the constant "1" to indicate an error, which is never checked for. Oops.
                return Sha256Hash.wrap("0100000000000000000000000000000000000000000000000000000000000000");
            }
            // In SIGHASH_SINGLE the outputs after the matching input index are deleted, and the outputs before
            // that position are "nulled out". Unintuitively, the value in a "null" transaction is set to -1.
            tx.outputs = new ArrayList<>(tx.outputs.subList(0, inputIndex + 1));
            for (int i = 0; i < inputIndex; i++) {
                tx.outputs.set(i, new TransactionOutput(tx, Coin.NEGATIVE_SATOSHI, new byte[]{}));
            }
            // The signature isn't broken by new versions of the transaction issued by other parties.
            for (int i = 0; i < tx.inputs.size(); i++) {
                if (i != inputIndex) {
                    tx.replaceInput(i, tx.getInput(i).withSequence(0));
                }
            }
        }

        if ((sigHashType & SigHash.ANYONECANPAY.value) == SigHash.ANYONECANPAY.value) {
            // SIGHASH_ANYONECANPAY means the signature in the input is not broken by changes/additions/removals
            // of other inputs. For example, this is useful for building assurance contracts.
            tx.inputs = new ArrayList<>();
            tx.inputs.add(input);
        }

        ByteBuffer buf = ByteBuffer.allocate(tx.messageSize() + 4);
        tx.write(buf, false);
        // We also have to write a hash type (sigHashType is actually an unsigned char)
        writeInt32LE(0x000000ff & sigHashType, buf);
        // Note that this is NOT reversed to ensure it will be signed correctly. If it were to be printed out
        // however then we would expect that it IS reversed.
        Sha256Hash hash = Sha256Hash.twiceOf(buf.array());

        return hash;
    }

    public byte[] calculateWitnessSignature(
            int inputIndex,
            PrivateKey key,
            byte[] scriptCode,
            Coin value,
            SigHash hashType,
            boolean anyoneCanPay) {
        Sha256Hash hash = hashForWitnessSignature(inputIndex, scriptCode, value, hashType, anyoneCanPay);

        try {
            return CryptographyUtils.sign(hash.getBytes(), key);
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | SignatureException ex) {
            Logger.getLogger(Transaction.class.getName()).log(Level.SEVERE, null, ex);
        }
        throw new RuntimeException("error signing witness hash");
    }

    public byte[] calculateWitnessSignature(
            int inputIndex,
            PrivateKey key,
            Script scriptCode,
            Coin value,
            SigHash hashType,
            boolean anyoneCanPay) {
        return calculateWitnessSignature(inputIndex, key, scriptCode.program(), value, hashType, anyoneCanPay);
    }

    public synchronized Sha256Hash hashForWitnessSignature(
            int inputIndex,
            byte[] scriptCode,
            Coin prevValue,
            SigHash type,
            boolean anyoneCanPay) {
        int sigHash = calcSigHashValue(type, anyoneCanPay);
        return hashForWitnessSignature(inputIndex, scriptCode, prevValue, (byte) sigHash);
    }

    /**
     * <p>
     * Calculates a signature hash, that is, a hash of a simplified form of the
     * transaction.How exactly the transaction is simplified is specified by the
     * type and anyoneCanPay parameters.</p>
     *
     * <p>
     * This is a low level API and when using the regular {@link Wallet} class
     * you don't have to call this yourself. When working with more complex
     * transaction types and contracts, it can be necessary. When signing a
     * Witness output the scriptCode should be the script encoded into the
     * scriptSig field, for normal transactions, it's the scriptPubKey of the
     * output you're signing for. (See BIP143:
     * https://github.com/bitcoin/bips/blob/master/bip-0143.mediawiki)</p>
     *
     * @param inputIndex input the signature is being calculated for. Tx
     * signatures are always relative to an input.
     * @param scriptCode the script that should be in the given input during
     * signing.
     * @param prevValue the value of the coin being spent
     * @param type Should be SigHash.ALL
     * @param anyoneCanPay should be false.
     * @return
     */
    public synchronized Sha256Hash hashForWitnessSignature(
            int inputIndex,
            Script scriptCode,
            Coin prevValue,
            SigHash type,
            boolean anyoneCanPay) {
        return hashForWitnessSignature(inputIndex, scriptCode.program(), prevValue, type, anyoneCanPay);
    }

    public synchronized Sha256Hash hashForWitnessSignature(
            int inputIndex,
            byte[] scriptCode,
            Coin prevValue,
            byte sigHashType) {
        ByteArrayOutputStream bos = new ByteArrayOutputStream(255); // just a guess at an average tx length
        try {
            byte[] hashPrevouts = new byte[32];
            byte[] hashSequence = new byte[32];
            byte[] hashOutputs = new byte[32];
            int basicSigHashType = sigHashType & 0x1f;
            boolean anyoneCanPay = (sigHashType & SigHash.ANYONECANPAY.value) == SigHash.ANYONECANPAY.value;
            boolean signAll = (basicSigHashType != SigHash.SINGLE.value) && (basicSigHashType != SigHash.NONE.value);

            if (!anyoneCanPay) {
                ByteArrayOutputStream bosHashPrevouts = new ByteArrayOutputStream(256);
                for (TransactionInput input : this.inputs) {
                    bosHashPrevouts.write(input.getOutpoint().hash().serialize());
                    writeInt32LE(input.getOutpoint().index(), bosHashPrevouts);
                }
                hashPrevouts = Sha256Hash.hashTwice(bosHashPrevouts.toByteArray());
            }

            if (!anyoneCanPay && signAll) {
                ByteArrayOutputStream bosSequence = new ByteArrayOutputStream(256);
                for (TransactionInput input : this.inputs) {
                    writeInt32LE(input.getSequenceNumber(), bosSequence);
                }
                hashSequence = Sha256Hash.hashTwice(bosSequence.toByteArray());
            }

            if (signAll) {
                ByteArrayOutputStream bosHashOutputs = new ByteArrayOutputStream(256);
                for (TransactionOutput output : this.outputs) {
                    writeInt64LE(
                            BigInteger.valueOf(output.getValue().getValue()),
                            bosHashOutputs
                    );
                    bosHashOutputs.write(VarInt.of(output.getScriptBytes().length).serialize());
                    bosHashOutputs.write(output.getScriptBytes());
                }
                hashOutputs = Sha256Hash.hashTwice(bosHashOutputs.toByteArray());
            } else if (basicSigHashType == SigHash.SINGLE.value && inputIndex < outputs.size()) {
                ByteArrayOutputStream bosHashOutputs = new ByteArrayOutputStream(256);
                writeInt64LE(
                        BigInteger.valueOf(this.outputs.get(inputIndex).getValue().getValue()),
                        bosHashOutputs
                );
                bosHashOutputs.write(VarInt.of(this.outputs.get(inputIndex).getScriptBytes().length).serialize());
                bosHashOutputs.write(this.outputs.get(inputIndex).getScriptBytes());
                hashOutputs = Sha256Hash.hashTwice(bosHashOutputs.toByteArray());
            }
            writeInt32LE(version, bos);
            bos.write(hashPrevouts);
            bos.write(hashSequence);
            bos.write(inputs.get(inputIndex).getOutpoint().hash().serialize());
            writeInt32LE(inputs.get(inputIndex).getOutpoint().index(), bos);
            bos.write(VarInt.of(scriptCode.length).serialize());
            bos.write(scriptCode);
            writeInt64LE(BigInteger.valueOf(prevValue.getValue()), bos);
            writeInt32LE(inputs.get(inputIndex).getSequenceNumber(), bos);
            bos.write(hashOutputs);
            writeInt32LE(this.vLockTime.rawValue(), bos);
            writeInt32LE(0x000000ff & sigHashType, bos);
        } catch (IOException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }

        return Sha256Hash.twiceOf(bos.toByteArray());
    }

    /**
     * Gets the output the gihven outpoint is referring to. Note the output must
     * belong to this transaction, or else an {@link IllegalArgumentException}
     * will occur.
     *
     * @param outpoint outpoint referring to the output to get
     * @return output referred to by the given outpoint
     */
    public TransactionOutput getOutput(TransactionOutPoint outpoint) {
        checkArgument(outpoint.hash().equals(this.getTxId()), ()
                -> "outpoint poins to a different transaction");
        return getOutput(outpoint.index());
    }

    /**
     * Removes all the inputs from this transaction. Note that this also
     * invalidates the length attribute
     */
    public void clearInputs() {
        for (TransactionInput input : inputs) {
            input.setParent(null);
        }
        inputs.clear();
    }

    /**
     * Gets the sum of all transaction inputs, regardless of who owns them.
     * <p>
     * <b>Warning:</b> Inputs with {@code null}
     * {@link TransactionInput#getValue()} are silently skipped. Before
     * completing or signing a transaction you should verify that there are no
     * inputs with {@code null} values.
     *
     * @return The sum of all inputs with non-null values.
     */
    public Coin getInputSum() {
        return inputs.stream()
                .map(TransactionInput::getValue)
                .filter(Objects::nonNull)
                .reduce(Coin.ZERO, Coin::add);
    }

    /**
     * Gets the sum of the outputs of the transaction. If the outputs are less
     * than the inputs, it does not count the fee.
     *
     * @return the sum of the outputs regardless of who owns them.
     */
    public Coin getOutputSum() {
        return outputs.stream()
                .map(TransactionOutput::getValue)
                .reduce(Coin.ZERO, Coin::add);
    }

    /**
     * Randomly re-orders the transaction outputs: good for privacy
     */
    public void shuffleOutputs() {
        Collections.shuffle(outputs);
    }

    /**
     *
     * /**
     * Returns a map of block [hashes] which contain the transaction mapped to
     * relativity counters, or null if this transaction doesn't have that data
     * because it's not stored in the wallet or because it has never appeared in
     * a block.
     *
     * @return
     */
//    @Nullable
    public Map<Sha256Hash, Integer> getAppearsInHashes() {
        return appearsInHashes != null ? Collections.unmodifiableMap(new HashMap<>(appearsInHashes)) : null;
    }

    /**
     * Convenience wrapper around getConfidence().getConfidenceType()
     *
     * @return true if this transaction hasn't been seen in any block yet.
     */
    public boolean isPending() {
        return getConfidence().getConfidenceType() == TransactionConfidence.ConfidenceType.PENDING;
    }

    public void addBlockAppearance(final Sha256Hash blockHash, int relativityOffset) {
        if (appearsInHashes == null) {
            // TODO: This could be a lot more memory efficient as we'll typically only store one element.
            appearsInHashes = new TreeMap<>();
        }
        appearsInHashes.put(blockHash, relativityOffset);
    }

    /**
     * Returns false if this transaction has at least one output that is owned
     * by the given wallet and unspent, true otherwise.
     *
     * @param identity
     * @return
     */
    public boolean isEveryOwnedOutputSpent(Identity identity) {
        for (TransactionOutput output : outputs) {
            if (output.isAvailableForSpending() && output.isMine(identity)) {
                return false;
            }
        }
        return true;
    }

    /**
     * <p>
     * Checks the transaction contents for sanity, in ways that can be done in a
     * standalone manner. Does <b>not</b> perform all checks on a transaction
     * such as whether the inputs are already spent. Specifically this method
     * verifies:</p>
     *
     * <ul>
     * <li>That there is at least one input and output.</li>
     * <li>That the serialized size is not larger than the max block size.</li>
     * <li>That no outputs have negative value.</li>
     * <li>That the outputs do not sum to larger than the max allowed quantity
     * of coin in the system.</li>
     * <li>If the tx is a coinbase tx, the coinbase scriptSig size is within
     * range. Otherwise that there are no coinbase inputs in the tx.</li>
     * </ul>
     *
     * @param network network for the verification rules
     * @param tx transaction to verify
     * @throws VerificationException if at least one of the rules is violated
     */
    public static void verify(Network network, Transaction tx) throws VerificationException {
        if (tx.inputs.isEmpty() || tx.outputs.isEmpty()) {
            throw new VerificationException.EmptyInputsOrOutputs();
        }
        if (tx.messageSize() > Block.MAX_BLOCK_SIZE) {
            throw new VerificationException.LargerThanMaxBlockSize();
        }

        HashSet<TransactionOutPoint> outpoints = new HashSet<>();
        for (TransactionInput input : tx.inputs) {
            if (outpoints.contains(input.getOutpoint())) {
                throw new VerificationException.DuplicatedOutPoint();
            }
            outpoints.add(input.getOutpoint());
        }

        Coin valueOut = Coin.ZERO;
        for (TransactionOutput output : tx.outputs) {
            Coin value = output.getValue();
            if (value.signum() < 0) {
                throw new VerificationException.NegativeValueOutput();
            }
            try {
                valueOut = valueOut.add(value);
            } catch (ArithmeticException e) {
                throw new VerificationException.ExcessiveValue();
            }
            if (network.exceedsMaxMoney(valueOut)) {
                throw new VerificationException.ExcessiveValue();
            }
        }
    }

    /**
     * Sets the update time of this transaction.
     *
     * @param updateTime update time
     */
    public void setUpdateTime(Instant updateTime) {
        this.updateTime = Objects.requireNonNull(updateTime);
    }

    /**
     * Clears the update time of this transaction.
     */
    public void clearUpdateTime() {
        this.updateTime = null;
    }

    /**
     * Calculates the sum of the inputs that are spending coins with keys in the
     * wallet.This requires the transactions sending coins to those keys to be
     * in the wallet. This method will not attempt to download the blocks
     * containing the input transactions if the key is in the wallet but the
     * transactions are not.
     *
     * @param adapter
     * @return sum of the inputs that are spending coins with keys in the wallet
     */
    public Coin getValueSentFromMe(WalletTransactionAdapter adapter) throws ScriptException {
        // This is tested in WalletTest.
        Coin v = Coin.ZERO;
        for (TransactionInput input : inputs) {
            // This input is taking value from a transaction in our wallet. To discover the value,
            // we must find the connected transaction.
            TransactionOutput connected = input.getConnectedOutput(adapter.getTransactionPool(Pool.UNSPENT));
            if (connected == null) {
                connected = input.getConnectedOutput(adapter.getTransactionPool(Pool.SPENT));
            }
            if (connected == null) {
                connected = input.getConnectedOutput(adapter.getTransactionPool(Pool.PENDING));
            }
            if (connected == null) {
                continue;
            }
            // The connected output may be the change to the sender of a previous input sent to this wallet. In this
            // case we ignore it.
            if (!connected.isMine(adapter.getIdentity())) {
                continue;
            }
            v = v.add(connected.getValue());
        }
        return v;
    }

    /**
     * Calculates the sum of the outputs that are sending coins to a key in the
     * wallet.
     *
     * @param identity
     * @return
     */
    public Coin getValueSentToMe(Identity identity) {
        // This is tested in WalletTest.
        Coin v = Coin.ZERO;
        for (TransactionOutput o : outputs) {
            if (!o.isMine(identity)) {
                continue;
            }
            v = v.add(o.getValue());
        }
        return v;
    }

    /**
     * Returns the witness transaction ID (aka witness ID) as per BIP144. For
     * transactions without witness, this is the same as {@link #getTxId()}.
     *
     * @return witness transaction ID
     */
    public Sha256Hash getWTxId() {
        if (cachedWTxId == null) {
            if (!hasWitnesses() && cachedTxId != null) {
                cachedWTxId = cachedTxId;
            } else {
                ByteBuffer buf = ByteBuffer.allocate(messageSize(hasWitnesses()));
                write(buf, hasWitnesses());
                cachedWTxId = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(buf.array()));
            }
        }
        return cachedWTxId;
    }

    /**
     * A genesis transaction is one that creates a new coin. They are the first
     * transaction in each block and their value is determined by a formula that
     * all implementations of Bitcoin share. In 2011 the value of a genesis
     * transaction is 50 coins, but in future it will be less. A genesis
     * transaction is defined not only by its position in a block but by the
     * data in the inputs.
     */
    public boolean isGenesis() {
        return inputs.size() == 1 && inputs.get(0).isGenesis();
    }
}
