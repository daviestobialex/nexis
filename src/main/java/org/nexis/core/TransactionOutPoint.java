/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Objects;
import org.nexis.utilities.ByteUtils;
import static org.nexis.utilities.Preconditions.checkArgument;
import org.nexis.utilities.Sha256Hash;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class TransactionOutPoint {

    public static final int BYTES = 36;

    /**
     * Special outpoint that normally marks a coinbase input. It's also used as
     * a test dummy.
     */
    public static final TransactionOutPoint UNCONNECTED
            = new TransactionOutPoint(ByteUtils.MAX_UNSIGNED_INTEGER, Sha256Hash.ZERO_HASH);

    public static TransactionOutPoint read(NexusProtocol.TransactionOutPoint outpoint) {
        Sha256Hash hash = Sha256Hash.of(outpoint.getHash().toByteArray());
        long index = outpoint.getIndex();
        return new TransactionOutPoint(index, hash);
    }

    /**
     * Hash of the transaction to which we refer.
     */
    private final Sha256Hash hash;
    /**
     * Which output of that transaction we are talking about.
     */
    private final long index;

    // This is not part of bitcoin serialization. It points to the connected transaction.
    final Transaction fromTx;

    // The connected output.
    final TransactionOutput connectedOutput;

    public TransactionOutPoint(long index, Transaction fromTx) {
        this(fromTx.getTxId(), index, fromTx, null);
    }

    public TransactionOutPoint(long index, Sha256Hash hash) {
        this(hash, index, null, null);
    }

    public TransactionOutPoint(TransactionOutput connectedOutput) {
        this(connectedOutput.getParentTransactionHash(), connectedOutput.getIndex(), null, connectedOutput);
    }

    private TransactionOutPoint(Sha256Hash hash, long index,
            //            @Nullable 
            Transaction fromTx,
            //            @Nullable 
            TransactionOutput connectedOutput) {
        this.hash = Objects.requireNonNull(hash);
        checkArgument(index >= 0 && index <= ByteUtils.MAX_UNSIGNED_INTEGER, ()
                -> "index out of range: " + index);
        this.index = index;
        this.fromTx = fromTx;
        this.connectedOutput = connectedOutput;
    }

    /**
     * Write this transaction outpoint into the given buffer.
     *
     * @param buf buffer to write into
     * @return the buffer
     * @throws BufferOverflowException if the outpoint doesn't fit the remaining
     * buffer
     */
    public ByteBuffer write(ByteBuffer buf) throws BufferOverflowException {
        buf.put(hash.serialize());
        ByteUtils.writeInt32LE(index, buf);
        return buf;
    }
}
