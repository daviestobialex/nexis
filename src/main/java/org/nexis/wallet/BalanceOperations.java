/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.wallet;

import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.math.BigInteger;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import org.nexis.base.Coin;
import org.nexis.base.NexusNetwork;
import org.nexis.base.Sha256Hash;
import org.nexis.core.Transaction;
import org.nexis.core.TransactionInput;
import org.nexis.core.TransactionOutput;
import org.nexis.internal.ListenableCompletableFuture;
import org.nexis.internal.Threading;
import static org.nexis.utilities.Preconditions.checkState;

/**
 *
 * @author daviestobialex
 */
public abstract class BalanceOperations extends WalletDefaultOperations {

    /**
     * abstract method to get all transactions
     *
     * @return
     */
    protected abstract Map<Sha256Hash, Transaction> getTransactions();

    //region Balance and balance futures
    /**
     * <p>
     * It's possible to calculate a wallets balance from multiple points of
     * view. This enum selects which {@link #getBalance(BalanceType)} should
     * use.</p>
     *
     * <p>
     * Consider a real-world example: you buy a snack costing $5 but you only
     * have a $10 bill. At the start you have $10 viewed from every possible
     * angle. After you order the snack you hand over your $10 bill. From the
     * perspective of your wallet you have zero dollars (AVAILABLE). But you
     * know in a few seconds the shopkeeper will give you back $5 change so most
     * people in practice would say they have $5 (ESTIMATED).</p>
     *
     * <p>
     * The fact that the wallet can track transactions which are not spendable
     * by itself ("watching wallets") adds another type of balance to the mix.
     * Although the wallet won't do this by default, advanced use cases that
     * override the relevancy checks can end up with a mix of spendable and
     * unspendable transactions.</p>
     */
    public enum BalanceType {
        /**
         * Balance calculated assuming all pending transactions are in fact
         * included into the best chain by miners. This includes the value of
         * immature coinbase transactions.
         */
        ESTIMATED,
        /**
         * Balance that could be safely used to create new spends, if we had all
         * the needed private keys. This is whatever the default coin selector
         * would make available, which by default means transaction outputs with
         * at least 1 confirmation and pending transactions created by our own
         * wallet which have been propagated across the network. Whether we
         * <i>actually</i> have the private keys or not is irrelevant for this
         * balance type.
         */
        AVAILABLE,
        /**
         * Same as ESTIMATED but only for outputs we have the private keys for
         * and can sign ourselves.
         */
        ESTIMATED_SPENDABLE,
        /**
         * Same as AVAILABLE but only for outputs we have the private keys for
         * and can sign ourselves.
         */
        AVAILABLE_SPENDABLE
    }

    /**
     * Returns the AVAILABLE balance of this wallet.See
     * {@link BalanceType#AVAILABLE} for details on what this means.
     *
     * @return
     */
    public Coin getBalance() {
        return getBalance(BalanceType.AVAILABLE);
    }

    /**
     * Returns the balance of this wallet as calculated by the provided
     * balanceType.
     *
     * @param balanceType
     * @return
     */
    public Coin getBalance(BalanceType balanceType) {
        getLock().lock();
        try {
            if (null == balanceType) {
                throw new AssertionError("Unknown balance type");  // Unreachable.
            } else {
                switch (balanceType) {
                    case AVAILABLE, AVAILABLE_SPENDABLE -> {
                        List<TransactionOutput> candidates = calculateAllSpendCandidates(true, balanceType == BalanceType.AVAILABLE_SPENDABLE);
                        CoinSelection selection = getCoinSelector().select(NexusNetwork.MAX_MONEY, candidates);
                        return selection.totalValue();
                    }
                    case ESTIMATED, ESTIMATED_SPENDABLE -> {
                        List<TransactionOutput> all = calculateAllSpendCandidates(false, balanceType == BalanceType.ESTIMATED_SPENDABLE);
                        Coin value = Coin.ZERO;
                        for (TransactionOutput out : all) {
                            value = value.add(out.getValue());
                        }
                        return value;
                    }
                    default ->
                        throw new AssertionError("Unknown balance type");  // Unreachable.
                }
            }
        } finally {
            getLock().unlock();
        }
    }

    /**
     * Returns the balance that would be considered spendable by the given coin
     * selector, including watched outputs (i.e.balance includes outputs we
     * don't have the private keys for).Just asks it to select as many coins as
     * possible and returns the total.
     *
     * @param selector
     * @return
     */
    public Coin getBalance(CoinSelector selector) {
        getLock().lock();
        try {
            Objects.requireNonNull(selector);
            List<TransactionOutput> candidates = calculateAllSpendCandidates(true, false);
            CoinSelection selection = selector.select((Coin) getNetwork().maxMoney(), candidates);
            return selection.totalValue();
        } finally {
            getLock().unlock();
        }
    }

    private static class BalanceFutureRequest {

        public final CompletableFuture<Coin> future;
        public final Coin value;
        public final BalanceType type;

        private BalanceFutureRequest(CompletableFuture<Coin> future, Coin value, BalanceType type) {
            this.future = future;
            this.value = value;
            this.type = type;
        }
    }
    @GuardedBy("lock")
    private final List<BalanceFutureRequest> balanceFutureRequests = new LinkedList<>();

    /**
     * <p>
     * Returns a future that will complete when the balance of the given type
     * has becom equal or larger to the given value.If the wallet already has a
     * large enough balance the future is returned in a pre-completed state.Note
     * that this method is not blocking, if you want to actually wait
     * immediately, you have to call .get() on the result.</p>
     *
     * <p>
     * Also note that by the time the future completes, the wallet may have
     * changed yet again if something else is going on in parallel, so you
     * should treat the returned balance as advisory and be prepared for sending
     * money to fail! Finally please be aware that any listeners on the future
     * will run either on the calling thread if it completes immediately, or
     * eventually on a background thread if the balance is not yet at the right
     * level. If you do something that means you know the balance should be
     * sufficient to trigger the future, you can use
     * {@link Threading#waitForUserCode()} to block until the future had a
     * chance to be updated.</p>
     *
     * @param value
     * @param type
     * @return
     */
    public ListenableCompletableFuture<Coin> getBalanceFuture(final Coin value, final BalanceType type) {
        getLock().lock();
        try {
            final CompletableFuture<Coin> future = new CompletableFuture<>();
            final Coin current = getBalance(type);
            if (current.compareTo(value) >= 0) {
                // Already have enough.
                future.complete(current);
            } else {
                // Will be checked later in checkBalanceFutures. We don't just add an event listener for ourselves
                // here so that running getBalanceFuture().get() in the user code thread works - generally we must
                // avoid giving the user back futures that require the user code thread to be free.
                balanceFutureRequests.add(new BalanceFutureRequest(future, value, type));
            }
            return ListenableCompletableFuture.of(future);
        } finally {
            getLock().unlock();
        }
    }

    // Runs any balance futures in the user code thread.
    @SuppressWarnings("FieldAccessNotGuarded")
    protected void checkBalanceFuturesLocked() {
        checkState(getLock().isHeldByCurrentThread());
        balanceFutureRequests.forEach(req -> {
            Coin current = getBalance(req.type);   // This could be slow for lots of futures.
            if (current.compareTo(req.value) >= 0) {
                // Found one that's finished.
                // Don't run any user-provided future listeners with our lock held.
                Threading.USER_THREAD.execute(() -> req.future.complete(current));
            }
        });
        balanceFutureRequests.removeIf(req -> req.future.isDone());
    }

    /**
     * Returns the amount of bitcoin ever received via output. <b>This is not
     * the balance!</b> If an output spends from a transaction whose inputs are
     * also to our wallet, the input amounts are deducted from the outputs
     * contribution, with a minimum of zero contribution. The idea behind this
     * is we avoid double counting money sent to us.
     *
     * @return the total amount of satoshis received, regardless of whether it
     * was spent or not.
     */
    public Coin getTotalReceived() {
        Coin total = Coin.ZERO;

        // Include outputs to us if they were not just change outputs, ie the inputs to us summed to less
        // than the outputs to us.
        for (Transaction tx : getTransactions().values()) {
            Coin txTotal = Coin.ZERO;
            for (TransactionOutput output : tx.getOutputs()) {
                if (output.isMine(getIdentity())) {
                    txTotal = txTotal.add(output.getValue());
                }
            }
            for (TransactionInput in : tx.getInputs()) {
                TransactionOutput prevOut = in.getConnectedOutput();
                if (prevOut != null && prevOut.isMine(getIdentity())) {
                    txTotal = txTotal.subtract(prevOut.getValue());
                }
            }
            if (txTotal.isPositive()) {
                total = total.add(txTotal);
            }
        }
        return total;
    }

    /**
     * Returns the amount of bitcoin ever sent via output. If an output is sent
     * to our own wallet, because of change or rotating keys or whatever, we do
     * not count it. If the wallet was involved in a shared transaction, i.e.
     * there is some input to the transaction that we don't have the key for,
     * then we multiply the sum of the output values by the proportion of
     * satoshi coming in to our inputs. Essentially we treat inputs as pooling
     * into the transaction, becoming fungible and being equally distributed to
     * all outputs.
     *
     * @return the total amount of satoshis sent by us
     */
    public Coin getTotalSent() {
        Coin total = Coin.ZERO;

        for (Transaction tx : getTransactions().values()) {
            // Count spent outputs to only if they were not to us. This means we don't count change outputs.
            Coin txOutputTotal = Coin.ZERO;
            for (TransactionOutput out : tx.getOutputs()) {
                if (out.isMine(getIdentity()) == false) {
                    txOutputTotal = txOutputTotal.add(out.getValue());
                }
            }

            // Count the input values to us
            Coin txOwnedInputsTotal = Coin.ZERO;
            for (TransactionInput in : tx.getInputs()) {
                TransactionOutput prevOut = in.getConnectedOutput();
                if (prevOut != null && prevOut.isMine(getIdentity())) {
                    txOwnedInputsTotal = txOwnedInputsTotal.add(prevOut.getValue());
                }
            }

            // If there is an input that isn't from us, i.e. this is a shared transaction
            Coin txInputsTotal = tx.getInputSum();
            if (!txOwnedInputsTotal.equals(txInputsTotal)) {

                // multiply our output total by the appropriate proportion to account for the inputs that we don't own
                BigInteger txOutputTotalNum = new BigInteger(txOutputTotal.toString());
                txOutputTotalNum = txOutputTotalNum.multiply(new BigInteger(txOwnedInputsTotal.toString()));
                txOutputTotalNum = txOutputTotalNum.divide(new BigInteger(txInputsTotal.toString()));
                txOutputTotal = Coin.valueOf(txOutputTotalNum.longValue());
            }
            total = total.add(txOutputTotal);

        }
        return total;
    }

    //endregion
    // ***************************************************************************************************************
}
