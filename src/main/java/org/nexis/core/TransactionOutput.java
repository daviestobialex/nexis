/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.util.List;
import java.util.Objects;
import org.nexis.base.Coin;
import org.nexis.script.Script;
import org.nexis.utilities.Sha256Hash;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class TransactionOutput {

    public TransactionOutput(Transaction parent, Coin value, byte[] scriptBytes) {
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(scriptBytes, "Script bytes cannot be null");
        this.value = value.value;
        this.scriptBytes = scriptBytes;
        this.parent = parent;
    }

    public TransactionOutput(Transaction parent, Coin value, Script scriptPubKey) {
        this(parent, value, scriptPubKey.getProgram());
        this.scriptPubKey = scriptPubKey;
    }

    static TransactionOutput read(NexusProtocol.TransactionOutput proto, Transaction parentTransaction) {
        Objects.requireNonNull(proto, "TransactionOutput proto cannot be null");
        long value = proto.getValue();
        byte[] script = proto.getScriptBytes().toByteArray();
        return new TransactionOutput(parentTransaction, Coin.valueOf(value), script);
    }

//    @Nullable
    protected Transaction parent;

    // The output's value is kept as a native type in order to save class instances.
    private long value;

    // A transaction output has a script used for authenticating that the redeemer is allowed to spend
    // this output.
    private byte[] scriptBytes;

    // The script bytes are parsed and turned into a Script on demand.
    private Script scriptPubKey;

    // These fields are not Bitcoin serialized. They are used for tracking purposes in our wallet
    // only. If set to true, this output is counted towards our balance. If false and spentBy is null the tx output
    // was owned by us and was sent to somebody else. If false and spentBy is set it means this output was owned by
    // us and used in one of our own transactions (eg, because it is a change output).
    private boolean availableForSpending;
//    @Nullable
    private TransactionInput spentBy;

    /**
     * Returns the connected input.
     */
//    @Nullable
    public TransactionInput getSpentBy() {
        return spentBy;
    }

    /**
     * Returns the transaction that owns this output.
     *
     * @return
     */
//    @Nullable
    public Transaction getParentTransaction() {
        return parent;
    }

    /**
     * Returns the transaction hash that owns this output.
     *
     * @return
     */
//    @Nullable
    public Sha256Hash getParentTransactionHash() {
        return parent == null ? null : ((Transaction) parent).getTxId();
    }

    /**
     * Gets the index of this output in the parent transaction, or throws if
     * this output is freestanding. Iterates over the parents list to discover
     * this.
     */
    public int getIndex() {
        List<TransactionOutput> outputs = getParentTransaction().getOutputs();
        for (int i = 0; i < outputs.size(); i++) {
            if (outputs.get(i) == this) {
                return i;
            }
        }
        throw new IllegalStateException("Output linked to wrong parent transaction?");
    }

    /**
     * Returns the value of this output.This is the amount of currency that the
     * destination address receives.
     *
     * @return
     */
    public Coin getValue() {
        return Coin.valueOf(value);
    }

    /**
     * Allocates a byte array and writes into it.
     *
     * @return byte array containing the transaction input
     */
    public byte[] serialize() {
        return toProto().toByteArray();
    }

    public NexusProtocol.TransactionOutput toProto() {
        NexusProtocol.TransactionOutput.Builder builder = NexusProtocol.TransactionOutput.newBuilder();
        builder.setValue(value);
        if (scriptBytes != null) {
            builder.setScriptBytes(com.google.protobuf.ByteString.copyFrom(scriptBytes));
        }
        return builder.build();
    }

}
