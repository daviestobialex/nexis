/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.wallet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import org.nexis.base.Identity;
import org.nexis.base.Network;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.Sha256Hash;
import org.nexis.core.Context;
import org.nexis.core.Transaction;
import org.nexis.core.TransactionConfidence;
import org.nexis.core.TransactionInput;
import org.nexis.core.TransactionOutput;
import org.nexis.core.UTXO;
import org.nexis.core.UTXOProvider;
import org.nexis.exceptions.UTXOProviderException;
import static org.nexis.internal.Preconditions.checkState;

/**
 *
 * @author daviestobialex
 */
public abstract class WalletDefaultOperations {

    /**
     * Abstract method to get the lock used for synchronization.
     *
     * @return The lock object
     */
    protected abstract ReentrantLock getLock();

    /**
     * Abstract method to get the wallet getIdentity().
     *
     * @return The identity
     */
    protected abstract Identity getIdentity();

    /**
     * Abstract method to get the network.
     *
     * @return The network
     */
    protected abstract Network getNetwork();

    /**
     * Abstract method to get the coin selector.
     *
     * @return The coin selector
     */
    protected abstract CoinSelector getCoinSelector();

    /**
     * abstract method to get pending transactions
     *
     * @return
     */
    protected abstract Map<Sha256Hash, Transaction> getPending();

    /**
     * abstract method to get unspent outputs
     *
     * @return
     */
    protected abstract Set<TransactionOutput> getMyUnspents();

    /**
     * abstract method to get network configuration settings
     *
     * @return
     */
    protected abstract NetworkConfiguration getParams();

    /**
     * abstract method to get UTXO ledger provider
     *
     * @return
     */
    protected abstract UTXOProvider getVUTXOProvider();

    /**
     * Returns a list of all outputs that are being tracked by this wallet
     * either from the {@link UTXOProvider} (in this case the existence or not
     * of private keys is ignored), or the wallets internal storage (the
     * default) taking into account the flags.
     *
     * @param excludeImmatureCoinbases Whether to ignore coinbase outputs that
     * we will be able to spend in future once they mature.
     * @param excludeUnsignable Whether to ignore outputs that we are tracking
     * but don't have the keys to sign for.
     * @return
     */
    public List<TransactionOutput> calculateAllSpendCandidates(boolean excludeImmatureCoinbases, boolean excludeUnsignable) {
        getLock().lock();
        try {
            return calculateAllSpendCandidatesFromUTXOProvider(excludeImmatureCoinbases);

        } finally {
            getLock().unlock();
        }
    }

    /**
     * Returns the spendable candidates from the {@link UTXOProvider} based on
     * keys that the wallet contains.
     *
     * @param excludeImmatureCoinbases
     * @return The list of candidates.
     */
    protected List<TransactionOutput> calculateAllSpendCandidatesFromUTXOProvider(boolean excludeImmatureCoinbases) {
        checkState(getLock().isHeldByCurrentThread());
        UTXOProvider utxoProvider = Objects.requireNonNull(getVUTXOProvider(), "No UTXO provider has been set");
        List<TransactionOutput> candidates = new LinkedList<>();
        try {
            int chainHeight = utxoProvider.getChainHeadHeight();
            for (UTXO output : getStoredOutputsFromUTXOProvider()) {
                boolean coinbase = output.isCoinbase();
                int depth = chainHeight - output.getHeight() + 1; // the current depth of the output (1 = same as head).
                // Do not try and spend coinbases that were mined too recently, the protocol forbids it.
                if (!excludeImmatureCoinbases || !coinbase || depth >= getParams().getSpendableCoinbaseDepth()) {
                    candidates.add(new FreeStandingTransactionOutput(output, chainHeight));
                }
            }
        } catch (UTXOProviderException e) {
            throw new RuntimeException("UTXO provider error", e);
        }

        // We need to handle the pending transactions that we know about.
        for (Transaction tx : getPending().values()) {
            // Remove the spent outputs.
            for (TransactionInput input : tx.getInputs()) {
                if (input.getConnectedOutput().isMine(getIdentity())) {
                    candidates.remove(input.getConnectedOutput());
                }
            }
            // Add change outputs. Do not try and spend coinbases that were mined too recently, the protocol forbids it.
            if (!excludeImmatureCoinbases || isTransactionMature(tx)) {
                for (TransactionOutput output : tx.getOutputs()) {
                    if (output.isAvailableForSpending() && output.isMine(getIdentity())) {
                        candidates.add(output);
                    }
                }
            }
        }
        return candidates;
    }

    /**
     * Determine if a transaction is <i>mature</i>. A coinbase transaction is
     * <i>mature</i> if it has been confirmed at least
     * {@link NetworkParameters#getSpendableCoinbaseDepth()} times. On
     * {@link BitcoinNetwork#MAINNET} this value is {@code 100}. For purposes of
     * this method, non-coinbase transactions are also considered <i>mature</i>.
     *
     * @param tx the transaction to evaluate
     * @return {@code true} if it is a mature coinbase transaction or if it is
     * not a coinbase transaction
     */
    public boolean isTransactionMature(Transaction tx) {
        return getConfidence(tx).getDepthInBlocks() >= getParams().getSpendableCoinbaseDepth();
    }

    TransactionConfidence getConfidence(Transaction tx) {
        return Context.get().getConfidenceTable().getConfidence(tx);
    }

    /**
     * Get all the {@link UTXO}'s from the {@link UTXOProvider} based on keys
     * that the wallet contains.
     *
     * @return The list of stored outputs.
     * @throws org.nexis.exceptions.UTXOProviderException
     */
    protected List<UTXO> getStoredOutputsFromUTXOProvider() throws UTXOProviderException {
        UTXOProvider utxoProvider = Objects.requireNonNull(getVUTXOProvider(), "No UTXO provider has been set");
        List<UTXO> candidates = new ArrayList<>();
//        List<PublicKey> keys = getImportedKeys();
//        keys.addAll(getActiveKeyChain().getLeafKeys());
        candidates.addAll(utxoProvider.getOpenTransactionOutputs(Arrays
                .asList(getIdentity().getKeyPair().getPublic())));
        return candidates;
    }

    // ***************************************************************************************************************
    /**
     * A custom {@link TransactionOutput} that is freestanding. This contains
     * all the information required for spending without actually having all the
     * linked data (i.e parent tx).
     *
     */
    private static class FreeStandingTransactionOutput extends TransactionOutput {

        private final UTXO output;
        private final int chainHeight;

        /**
         * Construct a freestanding Transaction Output.
         *
         * @param output The stored output (freestanding).
         */
        public FreeStandingTransactionOutput(UTXO output, int chainHeight) {
            super(null, output.getValue(), output.getScript().program());
            this.output = output;
            this.chainHeight = chainHeight;
        }

        /**
         * Get the {@link UTXO}.
         *
         * @return The stored output.
         */
        public UTXO getUTXO() {
            return output;
        }

        /**
         * Get the depth withing the chain of the parent tx, depth is 1 if it
         * the output height is the height of the latest block.
         *
         * @return The depth.
         */
        @Override
        public int getParentTransactionDepthInBlocks() {
            return chainHeight - output.getHeight() + 1;
        }

        @Override
        public int getIndex() {
            return (int) output.getIndex();
        }

        @Override
        public Sha256Hash getParentTransactionHash() {
            return output.getHash();
        }
    }

}
