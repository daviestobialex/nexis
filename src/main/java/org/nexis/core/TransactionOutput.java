/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.Address;
import org.nexis.base.Coin;
import org.nexis.base.Identity;
import org.nexis.base.VarInt;
import org.nexis.script.Script;
import org.nexis.script.ScriptBuilder;
import org.nexis.script.ScriptException;
import org.nexis.script.ScriptPattern;
import static org.nexis.utilities.Preconditions.checkArgument;
import static org.nexis.utilities.Preconditions.checkState;
import org.nexis.base.Sha256Hash;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class TransactionOutput {

    private static final Logger log = Logger.getLogger(TransactionOutput.class.getName());

    public TransactionOutput(Transaction parent, Coin value, byte[] scriptBytes) {
        Objects.requireNonNull(value, "Value cannot be null");
        Objects.requireNonNull(scriptBytes, "Script bytes cannot be null");
        this.value = value.value;
        this.scriptBytes = scriptBytes;
        this.parent = parent;
    }

    public TransactionOutput(Transaction parent, Coin value, Script scriptPubKey) {
        this(parent, value, scriptPubKey.program());
        this.scriptPubKey = scriptPubKey;
    }

    /**
     * Creates an output that sends 'value' to the given address (public key
     * hash).The amount should be created with something like
     * {@link Coin#valueOf(int, int)}.Typically you would use
     * {@link Transaction#addOutput(Coin, Address)} instead of creating a
     * TransactionOutput directly.
     *
     * @param parent
     * @param value
     * @param to
     */
    public TransactionOutput(Transaction parent, Coin value, Address to) {
        this(parent, value, ScriptBuilder.createOutputScript(to).program());
    }


    public static TransactionOutput read(NexusProtocol.TransactionOutput proto, Transaction parentTransaction) {
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

    // indicates that this is a governance transaction output and coin value 
    // is to be allocated to receiver even if the system does not have, essentially minitng 
    // new coin on demand based on real life exchanges baked into the system from root/genesis 
    private boolean system = false;// default is false
    // These fields are not Bitcoin serialized. They are used for tracking purposes in our wallet
    // only. If set to true, this output is counted towards our balance. If false and spentBy is null the tx output
    // was owned by us and was sent to somebody else. If false and spentBy is set it means this output was owned by
    // us and used in one of our own transactions (eg, because it is a change output).
    private boolean availableForSpending;
//    @Nullable
    private TransactionInput spentBy;

    public Script getScriptPubKey() throws ScriptException {
        if (scriptPubKey == null) {
            scriptPubKey = Script.parse(scriptBytes);
        }
        return scriptPubKey;
    }

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

    /**
     * The backing script bytes which can be turned into a Script object.
     *
     * @return the scriptBytes
     */
    public byte[] getScriptBytes() {
        return scriptBytes;
    }

    protected final void setParent(Transaction parent) {
        this.parent = parent;
    }

    /**
     * Returns the depth in blocks of the parent tx.
     *
     * <p>
     * If the transaction appears in the top block, the depth is one. If it's
     * anything else (pending, dead, unknown) then -1.</p>
     *
     * @return The tx depth or -1.
     */
    public int getParentTransactionDepthInBlocks() {
        if (getParentTransaction() != null) {
            TransactionConfidence confidence = getParentTransaction().getConfidence();
            if (confidence.getConfidenceType() == TransactionConfidence.ConfidenceType.BUILDING) {
                return confidence.getDepthInBlocks();
            }
        }
        return -1;
    }

    /**
     * Returns a new {@link TransactionOutPoint}, which is essentially a
     * structure pointing to this output.Requires that this output is not
     * detached.
     *
     * @return
     */
    public TransactionOutPoint getOutPointFor() {
        return new TransactionOutPoint(getIndex(), getParentTransaction());
    }

    /**
     * Returns a copy of the output detached from its containing transaction, if
     * need be.
     *
     * @return
     */
    public TransactionOutput duplicateDetached() {
        return new TransactionOutput(null, Coin.valueOf(value), Arrays.copyOf(scriptBytes, scriptBytes.length));
    }

    /**
     * Will this transaction be considered dust and not be relayable and mined
     * by default miners?
     *
     * @return true if this output is dust
     */
    public boolean isDust() {
        // Transactions that are OP_RETURN can't be dust regardless of their value.
        // If output is not OP_RETURN and value is below getMinNonDustValue() it is dust.
        return !ScriptPattern.isOpReturn(getScriptPubKey()) && getValue().isLessThan(getMinNonDustValue());
    }

    /**
     * <p>
     * Gets the minimum value for a txout of this size to be considered non-dust
     * by Bitcoin Core (and thus relayed). See: CTxOut::IsDust() in Bitcoin
     * Core.</p>
     *
     * <p>
     * You probably should use {@link TransactionOutput#getMinNonDustValue()}
     * which uses a safe fee-per-kb by default.</p>
     *
     * @param feePerKb The fee required per kilobyte. Note that this is the same
     * as Bitcoin Core's -minrelaytxfee * 3
     */
    public Coin getMinNonDustValue(Coin feePerKb) {
        // "Dust" is defined in terms of dustRelayFee,
        // which has units satoshis-per-kilobyte.
        // If you'd pay more in fees than the value of the output
        // to spend something, then we consider it dust.
        // A typical spendable non-segwit txout is 34 bytes big, and will
        // need a CTxIn of at least 148 bytes to spend:
        // so dust is a spendable txout less than
        // 182*dustRelayFee/1000 (in satoshis).
        // 546 satoshis at the default rate of 3000 sat/kB.
        // A typical spendable segwit txout is 31 bytes big, and will
        // need a CTxIn of at least 67 bytes to spend:
        // so dust is a spendable txout less than
        // 98*dustRelayFee/1000 (in satoshis).
        // 294 satoshis at the default rate of 3000 sat/kB.
        long size = this.messageSize();
        final Script script = getScriptPubKey();
        if (ScriptPattern.isP2PKH(script) || ScriptPattern.isP2PK(script) || ScriptPattern.isP2SH(script) || ScriptPattern.isSentToMultisig(script)) {
            size += 32 + 4 + 1 + 107 + 4; // 148
        } else if (ScriptPattern.isP2WH(script)) {
            size += 32 + 4 + 1 + (107 / 4) + 4; // 68
        } else {
            return Coin.ZERO;
        }
        return feePerKb.multiply(size).divide(1000);
    }

    /**
     * Returns the minimum value for this output to be considered "not dust",
     * i.e. the transaction will be relayable and mined by default miners.
     */
    public Coin getMinNonDustValue() {
        return getMinNonDustValue(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE.multiply(3));
    }

    /**
     * Sets this objects availableForSpending flag to false and the spentBy
     * pointer to the given input.If the input is null, it means this output was
     * signed over to somebody else rather than one of our own keys.
     *
     * @param input
     * @throws IllegalStateException if the transaction was already marked as
     * spent.
     */
    public void markAsSpent(TransactionInput input) {
        checkState(availableForSpending);
        availableForSpending = false;
        spentBy = input;
        if (parent != null) {
            log.log(Level.INFO, "Marked {0}:{1} as spent by {2}", new Object[]{getParentTransactionHash(), getIndex(), input});
        } else {
            log.log(Level.INFO, "Marked floating output as spent by {0}", input);
        }
    }

    /**
     * Resets the spent pointer / availableForSpending flag to null.
     */
    public void markAsUnspent() {
        if (parent != null) {
            log.log(Level.INFO, "Un-marked {0}:{1} as spent by {2}", new Object[]{getParentTransactionHash(),
                getIndex(), spentBy
            });
        } else {
            log.log(Level.INFO, "Un-marked floating output as spent by {0}", spentBy);
        }
        availableForSpending = true;
        spentBy = null;
    }

    /**
     * Returns whether {@link TransactionOutput#markAsSpent(TransactionInput)}
     * has been called on this class. A {@link Wallet} will mark a transaction
     * output as spent once it sees a transaction input that is connected to it.
     * Note that this flag can be false when an output has in fact been spent
     * according to the rest of the network if the spending transaction wasn't
     * downloaded yet, and it can be marked as spent when in reality the rest of
     * the network believes it to be unspent if the signature or script
     * connecting to it was not actually valid.
     */
    public boolean isAvailableForSpending() {
        return availableForSpending;
    }

    /**
     * Return the size of the serialized message. Note that if the message was
     * deserialized from a payload, this size can differ from the size of the
     * original payload.
     *
     * @return size of the serialized message in bytes
     */
    public int messageSize() {
        int size = Coin.BYTES; // value
        size += VarInt.sizeOf(scriptBytes.length) + scriptBytes.length;
        return size;
    }

    /**
     * Sets the value of this output.
     *
     * @param value
     */
    public void setValue(Coin value) {
        Objects.requireNonNull(value);
        // Negative values obviously make no sense, except for -1 which is used as a sentinel value when calculating
        // SIGHASH_SINGLE signatures, so unfortunately we have to allow that here.
        checkArgument(value.signum() >= 0 || value.equals(Coin.NEGATIVE_SATOSHI), () -> "value out of range: " + value);
        this.value = value.value;
    }

    /**
     * Returns true if this output is to a key, or an address we have the keys
     * for, in the wallet.
     *
     * @param identity
     * @return
     */
    public boolean isMine(Identity identity) {
        try {
            Script script = getScriptPubKey();
            if (ScriptPattern.isP2WPKH(script)) {
                return identity.isPubKeyHashMine(ScriptPattern.extractHashFromP2WH(script));// ensure se
            } else {
                return false;
            }
        } catch (ScriptException e) {
            // Just means we didn't understand the output of this transaction: ignore it.
            System.err.println("Could not parse tx");
//            log.debug("Could not parse tx {} output script: {}",
//                    parent != null ? ((Transaction) parent).getTxId() : "(no parent)", e.toString());
            return false;
        }
    }

    public boolean isSystem() {
        return system;
    }

    public void setSystem(boolean system){
        this.system = system;
    }
}
