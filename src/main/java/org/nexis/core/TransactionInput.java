/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Objects;
import org.nexis.base.Coin;
import org.nexis.script.Script;
import static org.nexis.utilities.Preconditions.checkArgument;
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
     */
    TransactionInput(Transaction parentTransaction, TransactionOutput output) {
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
    public byte[] serialize() {
        throw new UnsupportedOperationException("no supported yet");
    }

    /**
     * Get the transaction witness of this input.
     *
     * @return the witness of the input
     */
    public TransactionWitness getWitness() {
        return witness != null ? witness : TransactionWitness.EMPTY;
    }

    protected final void setParent(
            //            @Nullable
            Transaction parent) {
        this.parent = parent;
    }

    NexusProtocol.TransactionInput toProto() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }
}
