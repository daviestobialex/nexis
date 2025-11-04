/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.lang.ref.WeakReference;
import java.util.Map;
import java.util.Objects;
import org.nexis.base.Coin;
import org.nexis.base.Identity;
import org.nexis.base.VarInt;
import org.nexis.script.Script;
import org.nexis.script.ScriptException;
import org.nexis.base.utils.ByteUtils;
import static org.nexis.utilities.Preconditions.checkArgument;
import org.nexis.base.Sha256Hash;
import org.nexis.wallet.RedeemData;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class TransactionInput {

    /**
     * Magic sequence number that indicates there is no sequence number.
     */
    public static final long NO_SEQUENCE = 0xFFFFFFFFL;
    /**
     * BIP68: If this flag set, sequence is NOT interpreted as a relative
     * lock-time.
     */
    public static final long SEQUENCE_LOCKTIME_DISABLE_FLAG = 1L << 31;
    /**
     * BIP68: If sequence encodes a relative lock-time and this flag is set, the
     * relative lock-time has units of 512 seconds, otherwise it specifies
     * blocks with a granularity of 1.
     */
    public static final long SEQUENCE_LOCKTIME_TYPE_FLAG = 1L << 22;
    /**
     * BIP68: If sequence encodes a relative lock-time, this mask is applied to
     * extract that lock-time from the sequence field.
     */
    public static final long SEQUENCE_LOCKTIME_MASK = 0x0000ffff;

    private static final byte[] EMPTY_ARRAY = new byte[0];
    // Magic outpoint index that indicates the input is in fact unconnected.
    private static final long UNCONNECTED = 0xFFFFFFFFL;

    public static TransactionInput read(NexusProtocol.TransactionInput inputProto, Transaction parentTransaction) {
        Objects.requireNonNull(parentTransaction);
        TransactionOutPoint outpoint = TransactionOutPoint.read(inputProto.getOutpoint());
        byte[] scriptBytes = inputProto.getScriptBytes().toByteArray();
        long sequence = inputProto.getSequence();
        return new TransactionInput(parentTransaction, scriptBytes, outpoint, sequence, null);
    }

    private Transaction parent;

    // Allows for altering transactions after they were broadcast. Values below NO_SEQUENCE-1 mean it can be altered.
    private long sequence;
    // Data needed to connect to the output of the transaction we're gathering coins from.
    private TransactionOutPoint outpoint;
    // The "script bytes" might not actually be a script. In coinbase transactions where new coins are minted there
    // is no input transaction, so instead the scriptBytes contains some extra stuff (like a rollover nonce) that we
    // don't care about much. The bytes are turned into a Script object (cached below) on demand via a getter.
    private byte[] scriptBytes;
    // The Script object obtained from parsing scriptBytes. Only filled in on demand and if the transaction is not
    // coinbase.
    private WeakReference<Script> scriptSig;
    /**
     * Value of the output connected to the input, if known. This field does not
     * participate in equals()/hashCode().
     */
    private Coin value;

    private TransactionWitness witness;

    public enum ConnectionResult {
        NO_SUCH_TX,
        ALREADY_SPENT,
        SUCCESS
    }

    public enum ConnectMode {
        DISCONNECT_ON_CONFLICT,
        ABORT_ON_CONFLICT
    }

    /**
     * Creates an input that connects to nothing - used only in creation of
     * genesis transactions.
     *
     * @param parentTransaction parent transaction
     * @param scriptBytes arbitrary bytes in the script
     * @return
     */
    public static TransactionInput genesisInput(Transaction parentTransaction, byte[] scriptBytes) {
        Objects.requireNonNull(parentTransaction);
        checkArgument(scriptBytes.length >= 2 && scriptBytes.length <= 100, ()
                -> "script must be between 2 and 100 bytes: " + scriptBytes.length);
        return new TransactionInput(parentTransaction, scriptBytes, TransactionOutPoint.UNCONNECTED);
    }

    public TransactionInput(
            //            @Nullable 
            Transaction parentTransaction, byte[] scriptBytes,
            TransactionOutPoint outpoint) {
        this(parentTransaction, scriptBytes, outpoint, NO_SEQUENCE, null);
    }

    public TransactionInput(
            //            @Nullable 
            Transaction parentTransaction, byte[] scriptBytes,
            TransactionOutPoint outpoint,
            //                            @Nullable 
            Coin value) {
        this(parentTransaction, scriptBytes, outpoint, NO_SEQUENCE, value);
    }

    private TransactionInput(
            //            @Nullable 
            Transaction parentTransaction, byte[] scriptBytes,
            TransactionOutPoint outpoint, long sequence,
            //            @Nullable
            Coin value) {
        checkArgument(value == null || value.signum() >= 0, () -> "value out of range: " + value);
        parent = parentTransaction;
        this.scriptBytes = scriptBytes;
        this.outpoint = outpoint;
        this.sequence = sequence;
        this.value = value;
    }

    /**
     * Creates an UNSIGNED input that links to the given output
     *
     * @param parentTransaction
     * @param output
     */
    public TransactionInput(Transaction parentTransaction, TransactionOutput output) {
        this(parentTransaction,
                EMPTY_ARRAY,
                output.getParentTransaction() != null
                ? new TransactionOutPoint(output.getIndex(), output.getParentTransaction())
                : new TransactionOutPoint(output),
                NO_SEQUENCE,
                output.getValue());
    }

    /**
     * Allocates a byte array and writes this transaction input into it.
     *
     * @return byte array containing the transaction input
     */
    public NexusProtocol.TransactionInput toProto() {
        throw new UnsupportedOperationException("no supported yet");
    }

    /**
     * @return The Transaction that owns this input.
     */
    public Transaction getParentTransaction() {
        return parent;
    }

    /**
     * The "script bytes" might not actually be a script. In coinbase
     * transactions where new coins are minted there is no input transaction, so
     * instead the scriptBytes contains some extra stuff (like a rollover nonce)
     * that we don't care about much. The bytes are turned into a Script object
     * (cached below) on demand via a getter.
     *
     * @return the scriptBytes
     */
    public byte[] getScriptBytes() {
        return scriptBytes;
    }

    /**
     * Get the transaction witness of this input.
     *
     * @return the witness of the input
     */
    public TransactionWitness getWitness() {
        return witness != null ? witness : TransactionWitness.EMPTY;
    }

    /**
     * Determine if the transaction has witnesses.
     *
     * @return true if the transaction has witnesses
     */
    public boolean hasWitness() {
        return witness != null && witness.getPushCount() != 0;
    }

    protected final void setParent(
            //            @Nullable
            Transaction parent) {
        this.parent = parent;
    }

    /**
     * Sequence numbers allow participants in a multi-party transaction signing
     * protocol to create new versions of the transaction independently of each
     * other.Newer versions of a transaction can replace an existing version
     * that's in nodes memory pools if the existing version is time locked. See
     * the Contracts page on the Bitcoin wiki for examples of how you can use
     * this feature to build contract protocols.
     *
     * @return
     */
    public long getSequenceNumber() {
        return sequence;
    }

    /**
     * @return true if this transaction's sequence number is set (ie it may be a
     * part of a time-locked transaction)
     */
    public boolean hasSequence() {
        return sequence != NO_SEQUENCE;
    }

    /**
     * Clear input scripts, e.g. in preparation for signing.
     */
    public void clearScriptBytes() {
        setScriptBytes(TransactionInput.EMPTY_ARRAY);
    }

    /**
     * @param scriptBytes the scriptBytes to set
     */
    void setScriptBytes(byte[] scriptBytes) {
        this.scriptSig = null;
        this.scriptBytes = scriptBytes;
    }

    /**
     * Set the transaction witness of an input.
     *
     * @param witness
     */
    public void setWitness(TransactionWitness witness) {
        this.witness = witness;
    }

    /**
     * Sequence numbers allow participants in a multi-party transaction signing
     * protocol to create new versions of the transaction independently of each
     * other.Newer versions of a transaction can replace an existing version
     * that's in nodes memory pools if the existing version is time locked. See
     * the Contracts page on the Bitcoin wiki for examples of how you can use
     * this feature to build contract protocols.
     *
     * @param sequence
     */
    public void setSequenceNumber(long sequence) {
        checkArgument(sequence >= 0 && sequence <= ByteUtils.MAX_UNSIGNED_INTEGER, ()
                -> "sequence out of range: " + sequence);
        this.sequence = sequence;
    }

    /**
     * @return The previous output transaction reference, as an OutPoint
     * structure. This contains the data needed to connect to the output of the
     * transaction we're gathering coins from.
     */
    public TransactionOutPoint getOutpoint() {
        return outpoint;
    }

    /**
     * @return Value of the output connected to this input, if known. Null if
     * unknown.
     */
//    @Nullable
    public Coin getValue() {
        return value;
    }

    /**
     * Return the size of the serialized message. Note that if the message was
     * deserialized from a payload, this size can differ from the size of the
     * original payload.
     *
     * @return size of the serialized message in bytes
     */
    public int messageSize() {
        int size = TransactionOutPoint.BYTES;
        size += VarInt.sizeOf(scriptBytes.length) + scriptBytes.length;
        size += 4; // sequence
        return size;
    }

    /**
     * Returns the script that is fed to the referenced output (scriptPubKey)
     * script in order to satisfy it: usually contains signatures and maybe
     * keys, but can contain arbitrary data if the output script accepts it.
     */
    public Script getScriptSig() throws ScriptException {
        // Transactions that generate new coins don't actually have a script. Instead this
        // parameter is overloaded to be something totally different.
        Script script = scriptSig == null ? null : scriptSig.get();
        if (script == null) {
            script = Script.parse(scriptBytes);
            scriptSig = new WeakReference<>(script);
        }
        return script;
    }

    /**
     * Locates the referenced output from the given pool of transactions.
     *
     * @param transactions
     * @return The TransactionOutput or null if the transactions map doesn't
     * contain the referenced tx.
     */
//    @Nullable
    public TransactionOutput getConnectedOutput(Map<Sha256Hash, Transaction> transactions) {
        Transaction tx = transactions.get(outpoint.hash());
        if (tx == null) {
            return null;
        }
        return tx.getOutput(outpoint);
    }

    /**
     * Alias for getOutpoint().getConnectedRedeemData(keyBag)
     *
     * @param identity
     * @return
     * @see TransactionOutPoint#getConnectedRedeemData(KeyBag)
     */
//    @Nullable
    public RedeemData getConnectedRedeemData(Identity identity) throws ScriptException {
        return getOutpoint().getConnectedRedeemData(identity);
    }

    /**
     * Returns the connected output, assuming the input was connected with
     * {@link TransactionInput#connect(TransactionOutput)} or variants at some
     * point.If it wasn't connected, then this method returns null.
     *
     * @return
     */
//    @Nullable
    public TransactionOutput getConnectedOutput() {
        return getOutpoint().getConnectedOutput();
    }

    /**
     * Returns the connected transaction, assuming the input was connected with
     * {@link TransactionInput#connect(TransactionOutput)} or variants at some
     * point.If it wasn't connected, then this method returns null.
     *
     * @return
     */
//    @Nullable
    public Transaction getConnectedTransaction() {
        return getOutpoint().fromTx;
    }

    /**
     * If this input is connected, check the output is connected back to this
     * input and release it if so, making it spendable once again.
     *
     * @return true if the disconnection took place, false if it was not
     * connected.
     */
    public boolean disconnect() {
        TransactionOutput connectedOutput;
        if (outpoint.fromTx != null) {
            // The outpoint is connected using a "standard" wallet, disconnect it.
            connectedOutput = outpoint.fromTx.getOutput(outpoint);
            outpoint = outpoint.disconnectTransaction();
        } else if (outpoint.connectedOutput != null) {
            // The outpoint is connected using a UTXO based wallet, disconnect it.
            connectedOutput = outpoint.connectedOutput;
            outpoint = outpoint.disconnectOutput();
        } else {
            // The outpoint is not connected, do nothing.
            return false;
        }

        if (connectedOutput != null && connectedOutput.getSpentBy() == this) {
            // The outpoint was connected to an output, disconnect the output.
            connectedOutput.markAsUnspent();
            return true;
        } else {
            return false;
        }
    }

    /**
     * Connects this input to the relevant output of the referenced transaction
     * if it's in the given map. Connecting means updating the internal pointers
     * and spent flags. If the mode is to ABORT_ON_CONFLICT then the spent
     * output won't be changed, but the outpoint.fromTx pointer will still be
     * updated.
     *
     * @param transactions Map of txhash to transaction.
     * @param mode Whether to abort if there's a pre-existing connection or not.
     * @return NO_SUCH_TX if the prevtx wasn't found, ALREADY_SPENT if there was
     * a conflict, SUCCESS if not.
     */
    public ConnectionResult connect(Map<Sha256Hash, Transaction> transactions, ConnectMode mode) {
        Transaction tx = transactions.get(outpoint.hash());
        if (tx == null) {
            return TransactionInput.ConnectionResult.NO_SUCH_TX;
        }
        return connect(tx, mode);
    }

    /**
     * Connects this input to the relevant output of the referenced transaction.
     * Connecting means updating the internal pointers and spent flags. If the
     * mode is to ABORT_ON_CONFLICT then the spent output won't be changed, but
     * the outpoint.fromTx pointer will still be updated.
     *
     * @param transaction The transaction to try.
     * @param mode Whether to abort if there's a pre-existing connection or not.
     * @return NO_SUCH_TX if transaction is not the prevtx, ALREADY_SPENT if
     * there was a conflict, SUCCESS if not.
     */
    public ConnectionResult connect(Transaction transaction, ConnectMode mode) {
        if (!transaction.getTxId().equals(outpoint.hash())) {
            return ConnectionResult.NO_SUCH_TX;
        }
        TransactionOutput out = transaction.getOutput(outpoint);
        if (!out.isAvailableForSpending()) {
            if (getParentTransaction().equals(outpoint.fromTx)) {
                // Already connected.
                return ConnectionResult.SUCCESS;
            } else if (mode == ConnectMode.DISCONNECT_ON_CONFLICT) {
                out.markAsUnspent();
            } else if (mode == ConnectMode.ABORT_ON_CONFLICT) {
                outpoint = outpoint.connectTransaction(out.getParentTransaction());
                return TransactionInput.ConnectionResult.ALREADY_SPENT;
            }
        }
        connect(out);
        return TransactionInput.ConnectionResult.SUCCESS;
    }

    /**
     * Internal use only: connects this TransactionInput to the given output
     * (updates pointers and spent flags)
     *
     * @param out
     */
    public void connect(TransactionOutput out) {
        outpoint = outpoint.connectTransaction(out.getParentTransaction());
        out.markAsSpent(this);
        value = out.getValue();
    }

    /**
     * Set the given program as the scriptSig that is supposed to satisfy the
     * connected output script.
     * @param scriptSig
     */
    public void setScriptSig(Script scriptSig) {
        this.scriptSig = new WeakReference<>(Objects.requireNonNull(scriptSig));
        // TODO: This should all be cleaned up so we have a consistent internal representation.
        setScriptBytes(scriptSig.program());
    }
}
