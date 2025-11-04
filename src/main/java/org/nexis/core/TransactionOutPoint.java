/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import org.nexis.base.Identity;
import org.nexis.base.ScriptType;
import org.nexis.script.Script;
import org.nexis.script.ScriptError;
import org.nexis.script.ScriptException;
import org.nexis.script.ScriptPattern;
import org.nexis.utilities.ByteUtils;
import static org.nexis.utilities.Preconditions.checkArgument;
import org.nexis.utilities.Sha256Hash;
import org.nexis.wallet.RedeemData;
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

    /**
     * Returns the hash of the transaction this outpoint references/spends/is
     * connected to.
     */
    public Sha256Hash hash() {
        return hash;
    }

    /**
     * @return the index of this outpoint
     */
    public long index() {
        return index;
    }

    /**
     * An outpoint is a part of a transaction input that points to the output of
     * another transaction.If we have both sides in memory, and they have been
     * linked together, this returns a pointer to the connected output, or null
     * if there is no such connection.
     *
     * @return
     */
//    @Nullable
    public TransactionOutput getConnectedOutput() {
        if (fromTx != null) {
            return fromTx.getOutput(index);
        } else if (connectedOutput != null) {
            return connectedOutput;
        }
        return null;
    }

    /**
     * Returns a copy of this outpoint, but with fromTx removed.
     *
     * @return outpoint with removed fromTx
     */
    public TransactionOutPoint disconnectTransaction() {
        return new TransactionOutPoint(hash, index, null, connectedOutput);
    }

    /**
     * Returns a copy of this outpoint, but with the connectedOutput removed.
     *
     * @return outpoint with removed connectedOutput
     */
    public TransactionOutPoint disconnectOutput() {
        return new TransactionOutPoint(hash, index, fromTx, null);
    }

    /**
     * Returns a copy of this outpoint, but with the provided transaction as
     * fromTx.
     *
     * @param transaction transaction to set as fromTx
     * @return outpoint with fromTx set
     */
    public TransactionOutPoint connectTransaction(Transaction transaction) {
        return new TransactionOutPoint(hash, index, Objects.requireNonNull(transaction), connectedOutput);
    }

    /**
     * Returns the RedeemData identified in the connected output, for either
     * P2PKH, P2WPKH, P2PK or P2SH scripts. If the script forms cannot be
     * understood, throws ScriptException.
     *
     * @return a RedeemData or null if the connected data cannot be found in the
     * wallet.
     */
//    @Nullable
    public RedeemData getConnectedRedeemData(Identity identity) throws ScriptException {
        TransactionOutput connectedOutput = getConnectedOutput();
        Objects.requireNonNull(connectedOutput, "Input is not connected so cannot retrieve key");
        Script connectedScript = connectedOutput.getScriptPubKey();
        if (ScriptPattern.isP2WPKH(connectedScript)) {
            byte[] addressBytes = ScriptPattern.extractHashFromP2WH(connectedScript);
            Arrays.equals(addressBytes, identity.getNodeId().getId());
            return RedeemData.of(identity.getKeyPair().getPublic(), connectedScript);
        } else if (ScriptPattern.isP2WSH(connectedScript)) {
            byte[] pubkeyBytes = ScriptPattern.extractHashFromP2SH(connectedScript);
            Arrays.equals(pubkeyBytes, identity.getNodeId().getId());
            return RedeemData.of(identity.getKeyPair().getPublic(), connectedScript);
        } else {
            throw new ScriptException(ScriptError.SCRIPT_ERR_UNKNOWN_ERROR, "Could not understand form of connected output script: " + connectedScript);
        }
    }

}
