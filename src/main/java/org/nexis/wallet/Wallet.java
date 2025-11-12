/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.wallet;

import com.google.common.math.IntMath;
import com.google.errorprone.annotations.concurrent.GuardedBy;
import java.math.RoundingMode;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.Address;
import org.nexis.base.Coin;
import org.nexis.base.Identity;
import org.nexis.base.Network;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.PeerConnection;
import org.nexis.base.SegwitAddress;
import org.nexis.core.Peer;
import org.nexis.core.SendRequest;
import org.nexis.core.Transaction;
import org.nexis.core.TransactionBroadcast;
import org.nexis.core.TransactionBroadcaster;
import org.nexis.core.TransactionConfidence;
import org.nexis.core.TransactionConfidence.ConfidenceType;
import org.nexis.core.TransactionInput;
import org.nexis.core.TransactionOutPoint;
import org.nexis.core.TransactionOutput;
import org.nexis.core.UTXOProvider;
import org.nexis.core.WalletTransactionAdapter;
import org.nexis.exceptions.InsufficientMoneyException;
import org.nexis.exceptions.VerificationException;
import org.nexis.internal.FutureUtils;
import org.nexis.internal.ListenerRegistration;
import org.nexis.internal.StreamUtils;
import org.nexis.internal.Threading;
import org.nexis.internal.TimeUtils;
import org.nexis.listeners.ScriptsChangeEventListener;
import org.nexis.listeners.TransactionConfidenceEventListener;
import org.nexis.listeners.WalletChangeEventListener;
import org.nexis.listeners.WalletCoinsReceivedEventListener;
import org.nexis.listeners.WalletCoinsSentEventListener;
import org.nexis.listeners.WalletReorganizeEventListener;
import org.nexis.script.Script;
import org.nexis.script.ScriptException;
import org.nexis.script.ScriptPattern;
import org.nexis.signers.LocalTransactionSigner;
import org.nexis.signers.MissingSigResolutionSigner;
import org.nexis.signers.TransactionSigner;
import org.nexis.base.utils.ByteUtils;
import static org.nexis.utilities.Preconditions.checkArgument;
import static org.nexis.utilities.Preconditions.checkState;
import org.nexis.base.Sha256Hash;
import org.nexis.wallet.WalletTransaction.Pool;
import org.slf4j.LoggerFactory;

/**
 *
 *
 * @author daviestobialex
 */
public class Wallet extends BalanceOperations implements WalletTransactionAdapter {

    private static final org.slf4j.Logger log2 = LoggerFactory.getLogger(Wallet.class);

    private static final Logger log = Logger.getLogger(Wallet.class.getName());
    private final Identity identity;
    private final NetworkConfiguration params;
    protected final ReentrantLock lock = Threading.lock(Wallet.class);

    protected final Network network;

    protected final CoinSelector coinSelector;
    // The wallet version. This is an int that can be used to track breaking changes in the wallet format.
    // You can also use it to detect wallets that come from the future (ie they contain features you
    // do not know how to deal with).
    private int version;

    // The various pools below give quick access to wallet-relevant transactions by the state they're in:
    //
    // Pending:  Transactions that didn't make it into the best chain yet. Pending transactions can be killed if a
    //           double spend against them appears in the best chain, in which case they move to the dead pool.
    //           If a double spend appears in the pending state as well, we update the confidence type
    //           of all txns in conflict to IN_CONFLICT and wait for the miners to resolve the race.
    // Unspent:  Transactions that appeared in the best chain and have outputs we can spend. Note that we store the
    //           entire transaction in memory even though for spending purposes we only really need the outputs, the
    //           reason being that this simplifies handling of re-orgs. It would be worth fixing this in future.
    // Spent:    Transactions that appeared in the best chain but don't have any spendable outputs. They're stored here
    //           for history browsing/auditing reasons only and in future will probably be flushed out to some other
    //           kind of cold storage or just removed.
    // Dead:     Transactions that we believe will never confirm get moved here, out of pending. Note that Bitcoin
    //           Core has no notion of dead-ness: the assumption is that double spends won't happen so there's no
    //           need to notify the user about them. We take a more pessimistic approach and try to track the fact that
    //           transactions have been double spent so applications can do something intelligent (cancel orders, show
    //           to the user in the UI, etc). A transaction can leave dead and move into spent/unspent if there is a
    //           re-org to a chain that doesn't include the double spend.
    private final Map<Sha256Hash, Transaction> pending;
    private final Map<Sha256Hash, Transaction> unspent;
    private final Map<Sha256Hash, Transaction> spent;
    private final Map<Sha256Hash, Transaction> dead;

    // All transactions together.
    protected final Map<Sha256Hash, Transaction> transactions;

    // Stuff for notifying transaction objects that we changed their confidences. The purpose of this is to avoid
    // spuriously sending lots of repeated notifications to listeners that API users aren't really interested in as a
    // side effect of how the code is written (e.g. during re-orgs confidence data gets adjusted multiple times).
    private int onWalletChangedSuppressions;
    private boolean insideReorg;

    // A listener that relays confidence changes from the transaction confidence object to the wallet event listener,
    // as a convenience to API users so they don't have to register on every transaction themselves.
    private TransactionConfidence.Listener txConfidenceListener;

    // All the TransactionOutput objects that we could spend (ignoring whether we have the private key or not).
    // Used to speed up various calculations.
    protected final Set<TransactionOutput> myUnspents = new HashSet<>();

    // Transactions that were dropped by the risk analysis system. These are not in any pools and not serialized
    // to disk. We have to keep them around because if we ignore a tx because we think it will never confirm, but
    // then it actually does confirm and does so within the same network session, remote peers will not resend us
    // the tx data along with the Bloom filtered block, as they know we already received it once before
    // (so it would be wasteful to repeat). Thus we keep them around here for a while. If we drop our network
    // connections then the remote peers will forget that we were sent the tx data previously and send it again
    // when relaying a filtered merkleblock.
    private final LinkedHashMap<Sha256Hash, Transaction> riskDropped = new LinkedHashMap<Sha256Hash, Transaction>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Sha256Hash, Transaction> eldest) {
            return size() > 1000;
        }
    };

    // Objects that perform transaction signing. Applied subsequently one after another
    @GuardedBy("lock")
    private final List<TransactionSigner> signers;

    private final List<ListenerRegistration<WalletChangeEventListener>> changeListeners
            = new CopyOnWriteArrayList<>();
    private final List<ListenerRegistration<WalletCoinsReceivedEventListener>> coinsReceivedListeners
            = new CopyOnWriteArrayList<>();
    private final List<ListenerRegistration<WalletCoinsSentEventListener>> coinsSentListeners
            = new CopyOnWriteArrayList<>();
    private final List<ListenerRegistration<WalletReorganizeEventListener>> reorganizeListeners
            = new CopyOnWriteArrayList<>();
    private final List<ListenerRegistration<ScriptsChangeEventListener>> scriptsChangeListeners
            = new CopyOnWriteArrayList<>();
    private final List<ListenerRegistration<TransactionConfidenceEventListener>> transactionConfidenceListeners
            = new CopyOnWriteArrayList<>();

    @Override
    public Identity getIdentity() {
        return identity;
    }

    @Override
    protected Map<Sha256Hash, Transaction> getTransactions() {
        return transactions;
    }

    @Override
    protected ReentrantLock getLock() {
        return lock;
    }

    @Override
    protected Network getNetwork() {
        return network;
    }

    @Override
    protected CoinSelector getCoinSelector() {
        return coinSelector;
    }

    @Override
    protected Map<Sha256Hash, Transaction> getPending() {
        return pending;
    }

    @Override
    protected Set<TransactionOutput> getMyUnspents() {
        return myUnspents;
    }

    @Override
    protected NetworkConfiguration getParams() {
        return params;
    }

    @Override
    protected UTXOProvider getVUTXOProvider() {
        return vUTXOProvider;
    }

    /**
     * Enumerates possible resolutions for missing signatures.
     */
    public enum MissingSigsMode {
        /**
         * Input script will have OP_0 instead of missing signatures
         */
        USE_OP_ZERO,
        /**
         * Missing signatures will be replaced by dummy sigs. This is useful
         * when you'd like to know the fee for a transaction without knowing the
         * user's password, as fee depends on size.
         */
        USE_DUMMY_SIG,
        /**
         * If signature is missing,
         * {@link TransactionSigner.MissingSignatureException} will be thrown
         * for P2SH and {@link ECKey.MissingPrivateKeyException} for other tx
         * types.
         */
        THROW
    }

    // If this is set then the wallet selects spendable candidate outputs from a UTXO provider.
//    @Nullable
    private volatile UTXOProvider vUTXOProvider;
    protected volatile TransactionBroadcaster vTransactionBroadcaster;

    private final Map<Transaction, TransactionConfidence.Listener.ChangeReason> confidenceChanged;

    public static Wallet of(Identity identity, NetworkConfiguration networkParams) {
        return new Wallet(identity, networkParams);
    }

    private Wallet(Identity identity, NetworkConfiguration networkParams) {

        this.identity = identity;
        this.params = networkParams;
        this.network = networkParams.getNetwork();
        this.coinSelector = DefaultCoinSelector.get(network);
        this.unspent = new HashMap<>();
        this.spent = new HashMap<>();
        this.pending = new HashMap<>();
        this.dead = new HashMap<>();
        transactions = new HashMap<>();
        // Use a linked hash map to ensure ordering of event listeners is correct.
        confidenceChanged = new LinkedHashMap<>();
        signers = new ArrayList<>();
        addTransactionSigner(new LocalTransactionSigner());
        // TODO: check locally for saved wallet file, else create a new one
    }

    /**
     * <p>
     * Adds given transaction signer to the list of signers.It will be added to
     * the end of the signers list, so if this wallet already has some signers
     * added, given signer will be executed after all of them.</p>
     * <p>
     * Transaction signer should be fully initialized before adding to the
     * wallet, otherwise {@link IllegalStateException} will be thrown</p>
     *
     * @param signer
     */
    public final void addTransactionSigner(TransactionSigner signer) {
        lock.lock();
        try {
            if (signer.isReady()) {
                signers.add(signer);
            } else {
                throw new IllegalStateException("Signer instance is not ready to be added into Wallet: " + signer.getClass());
            }
        } finally {
            lock.unlock();
        }
    }

    public List<TransactionSigner> getTransactionSigners() {
        lock.lock();
        try {
            return Collections.unmodifiableList(signers);
        } finally {
            lock.unlock();
        }
    }

    public Address currentAddress() {
        return SegwitAddress.fromHash(
                this.params.getNetwork(),
                this.identity.getNodeId().getId());
    }

    /**
     * A SendResult is returned to you as part of sending coins to a recipient.
     */
    public static class SendResult {

        /**
         * The broadcast object returned by the linked TransactionBroadcaster
         *
         * @deprecated Use {@link #getBroadcast()}
         */
        @Deprecated
        public final TransactionBroadcast broadcast;

        public SendResult(TransactionBroadcast broadcast) {
            this.broadcast = broadcast;
        }

    }

    /**
     * <p>
     * Sends coins to the given address, via the given {@link PeerGroup}. Change
     * is returned to {@link Wallet#currentChangeAddress()}. Note that a fee may
     * be automatically added if one may be required for the transaction to be
     * confirmed.</p>
     *
     * <p>
     * The returned object provides both the transaction, and a future that can
     * be used to learn when the broadcast is complete. Complete means, if the
     * PeerGroup is limited to only one connection, when it was written out to
     * the socket. Otherwise when the transaction is written out and we heard it
     * back from a different peer.</p>
     *
     * <p>
     * Note that the sending transaction is committed to the wallet immediately,
     * not when the transaction is successfully broadcast. This means that even
     * if the network hasn't heard about your transaction you won't be able to
     * spend those same coins again.</p>
     *
     * <p>
     * You MUST ensure that value is not smaller than
     * {@link TransactionOutput#getMinNonDustValue()} or the transaction will
     * almost certainly be rejected by the network as dust.</p>
     *
     * @param broadcaster a {@link TransactionBroadcaster} to use to send the
     * transactions out.
     * @param to Which address to send coins to.
     * @param value How much value to send.
     * @return An object containing the transaction that was created, and a
     * future for the broadcast of it.
     * @throws InsufficientMoneyException if the request could not be completed
     * due to not enough balance.
     * @throws DustySendRequested if the resultant transaction would violate the
     * dust rules.
     * @throws CouldNotAdjustDownwards if emptying the wallet was requested and
     * the output can't be shrunk for fees without violating a protocol rule.
     * @throws ExceededMaxTransactionSize if the resultant transaction is too
     * big for Bitcoin to process.
     * @throws MultipleOpReturnRequested if there is more than one OP_RETURN
     * output for the resultant transaction.
     * @throws BadWalletEncryptionKeyException if the supplied
     * {@link SendRequest#aesKey} is wrong.
     */
    public SendResult sendCoins(TransactionBroadcaster broadcaster, Address to, Coin value)
            throws InsufficientMoneyException, CompletionException {
        SendRequest request = SendRequest.to(to, value);
        return sendCoins(broadcaster, request);
    }

    /**
     * <p>
     * Sends coins according to the given request, via the given
     * {@link TransactionBroadcaster}.</p>
     *
     * <p>
     * The returned object provides both the transaction, and a future that can
     * be used to learn when the broadcast is complete. Complete means, if the
     * PeerGroup is limited to only one connection, when it was written out to
     * the socket. Otherwise when the transaction is written out and we heard it
     * back from a different peer.</p>
     *
     * <p>
     * Note that the sending transaction is committed to the wallet immediately,
     * not when the transaction is successfully broadcast. This means that even
     * if the network hasn't heard about your transaction you won't be able to
     * spend those same coins again.</p>
     *
     * @param broadcaster the target to use for broadcast.
     * @param request the SendRequest that describes what to do, get one using
     * static methods on SendRequest itself.
     * @return An object containing the transaction that was created, and a
     * future for the broadcast of it.
     * @throws InsufficientMoneyException if the request could not be completed
     * due to not enough balance.
     * @throws IllegalArgumentException if you try and complete the same
     * SendRequest twice
     * @throws DustySendRequested if the resultant transaction would violate the
     * dust rules.
     * @throws CouldNotAdjustDownwards if emptying the wallet was requested and
     * the output can't be shrunk for fees without violating a protocol rule.
     * @throws ExceededMaxTransactionSize if the resultant transaction is too
     * big for Bitcoin to process.
     * @throws MultipleOpReturnRequested if there is more than one OP_RETURN
     * output for the resultant transaction.
     * @throws BadWalletEncryptionKeyException if the supplied
     * {@link SendRequest#aesKey} is wrong.
     */
    public SendResult sendCoins(TransactionBroadcaster broadcaster, SendRequest request)
            throws InsufficientMoneyException, CompletionException {
        // Should not be locked here, as we're going to call into the broadcaster and that might want to hold its
        // own lock. sendCoinsOffline handles everything that needs to be locked.
        checkState(!lock.isHeldByCurrentThread());

        // Commit the TX to the wallet immediately so the spent coins won't be reused.
        // TODO: We should probably allow the request to specify tx commit only after the network has accepted it.
        Transaction tx = sendCoinsOffline(request);
        SendResult result = new SendResult(broadcaster.broadcastTransaction(tx));
        // The tx has been committed to the pending pool by this point (via sendCoinsOffline -> commitTx), so it has
        // a txConfidenceListener registered. Once the tx is broadcast the peers will update the memory pool with the
        // count of seen peers, the memory pool will update the transaction confidence object, that will invoke the
        // txConfidenceListener which will in turn invoke the wallets event listener onTransactionConfidenceChanged
        // method.
        return result;
    }

    /**
     * Satisfies the given {@link SendRequest} using the default transaction
     * broadcaster configured either via {@link PeerGroup#addWallet(Wallet)} or
     * directly with {@link #setTransactionBroadcaster(TransactionBroadcaster)}.
     *
     * @param request the SendRequest that describes what to do, get one using
     * static methods on SendRequest itself.
     * @return An object containing the transaction that was created, and a
     * future for the broadcast of it.
     * @throws IllegalStateException if no transaction broadcaster has been
     * configured.
     * @throws InsufficientMoneyException if the request could not be completed
     * due to not enough balance.
     * @throws IllegalArgumentException if you try and complete the same
     * SendRequest twice
     * @throws DustySendRequested if the resultant transaction would violate the
     * dust rules.
     * @throws CouldNotAdjustDownwards if emptying the wallet was requested and
     * the output can't be shrunk for fees without violating a protocol rule.
     * @throws ExceededMaxTransactionSize if the resultant transaction is too
     * big for Bitcoin to process.
     * @throws MultipleOpReturnRequested if there is more than one OP_RETURN
     * output for the resultant transaction.
     * @throws BadWalletEncryptionKeyException if the supplied
     * {@link SendRequest#aesKey} is wrong.
     */
    public SendResult sendCoins(SendRequest request)
            throws InsufficientMoneyException, CompletionException {
        TransactionBroadcaster broadcaster = vTransactionBroadcaster;
        checkState(broadcaster != null, ()
                -> "no transaction broadcaster is configured");
        return sendCoins(broadcaster, request);
    }

    /**
     * Sends coins to the given address, via the given {@link Peer}.Change is
     * returned to {@link Wallet#currentChangeAddress()}.If an exception is
     * thrown by {@link Peer#sendMessage(Message)} the transaction is still
     * committed, so the pending transaction must be broadcast <b>by you</b> at
     * some other time. Note that a fee may be automatically added if one may be
     * required for the transaction to be confirmed.
     *
     * @param peer
     * @param request
     * @return The {@link Transaction} that was created or null if there was
     * insufficient balance to send the coins.
     * @throws InsufficientMoneyException if the request could not be completed
     * due to not enough balance.
     * @throws IllegalArgumentException if you try and complete the same
     * SendRequest twice
     * @throws DustySendRequested if the resultant transaction would violate the
     * dust rules.
     * @throws CouldNotAdjustDownwards if emptying the wallet was requested and
     * the output can't be shrunk for fees without violating a protocol rule.
     * @throws ExceededMaxTransactionSize if the resultant transaction is too
     * big for Bitcoin to process.
     * @throws MultipleOpReturnRequested if there is more than one OP_RETURN
     * output for the resultant transaction.
     * @throws BadWalletEncryptionKeyException if the supplied
     * {@link SendRequest#aesKey} is wrong.
     */
    public Transaction sendCoins(PeerConnection peer, SendRequest request)
            throws InsufficientMoneyException, CompletionException {
        Transaction tx = sendCoinsOffline(request);
        peer.channel().writeAndFlush(tx.toProto());
        return tx;
    }

    /**
     * Initiate sending the transaction in a {@link SendRequest}. Calls
     * {@link Wallet#sendCoins(SendRequest)} which performs the following
     * significant operations internally:
     * <ol>
     * <li>{@link Wallet#completeTx(SendRequest)} -- calculate change and
     * sign</li>
     * <li>{@link Wallet#commitTx(Transaction)} -- puts the transaction in the
     * {@code Wallet}'s pending pool</li>
     * <li>{@link org.bitcoinj.core.TransactionBroadcaster#broadcastTransaction(Transaction)}
     * typically implemented by
     * {@link org.bitcoinj.core.PeerGroup#broadcastTransaction(Transaction)} --
     * queues requests to send the transaction to a single remote
     * {@code Peer}</li>
     * </ol>
     * This method will <i>complete</i> and return a
     * {@link TransactionBroadcast} when the send to the remote peer occurs (is
     * buffered.) The broadcast process includes the following steps:
     * <ol>
     * <li>Wait until enough {@link org.bitcoinj.core.Peer}s are connected.</li>
     * <li>Broadcast (buffer for send) the transaction to a single remote
     * {@link org.bitcoinj.core.Peer}</li>
     * <li>Mark {@link TransactionBroadcast#awaitSent()} as complete</li>
     * <li>Wait for a number of remote peers to confirm they have received the
     * broadcast</li>
     * <li>Mark {@link TransactionBroadcast#awaitRelayed()} as complete</li>
     * </ol>
     *
     * @param sendRequest transaction to send
     * @return A future for the transaction broadcast
     */
    public CompletableFuture<TransactionBroadcast> sendTransaction(SendRequest sendRequest) {
        try {
            // Complete successfully when the transaction has been sent (or buffered, at least) to peers.
            return sendCoins(sendRequest).broadcast.awaitSent();
        } catch (InsufficientMoneyException e) {
            // We should never try to send more coins than we have, if we do we get an InsufficientMoneyException
            return FutureUtils.failedFuture(e);
        }
    }

    /**
     * Wait for at least 1 confirmation on a transaction.
     *
     * @param tx the transaction we are waiting for
     * @return a future for an object that contains transaction confidence
     * information
     */
    public CompletableFuture<TransactionConfidence> waitForConfirmation(Transaction tx) {
        return waitForConfirmations(tx, 1);
    }

    /**
     * Wait for a required number of confirmations on a transaction.
     *
     * @param tx the transaction we are waiting for
     * @param requiredConfirmations the minimum required confirmations before
     * completing
     * @return a future for an object that contains transaction confidence
     * information
     */
    public CompletableFuture<TransactionConfidence> waitForConfirmations(Transaction tx, int requiredConfirmations) {

        return getConfidence(tx).getDepthFuture(requiredConfirmations);
    }

    /**
     * Class of exceptions thrown in {@link Wallet#completeTx(SendRequest)}.
     */
    public static class CompletionException extends RuntimeException {

        public CompletionException() {
            super();
        }

        public CompletionException(Throwable throwable) {
            super(throwable);
        }

        public CompletionException(String message) {
            super(message);
        }
    }

    /**
     * Thrown if the resultant transaction would violate the dust rules (an
     * output that's too small to be worthwhile).
     */
    public static class DustySendRequested extends CompletionException {
    }

    /**
     * Thrown if there is more than one OP_RETURN output for the resultant
     * transaction.
     */
    public static class MultipleOpReturnRequested extends CompletionException {
    }

    /**
     * Thrown when we were trying to empty the wallet, and the total amount of
     * money we were trying to empty after being reduced for the fee was smaller
     * than the min payment. Note that the missing field will be null in this
     * case.
     */
    public static class CouldNotAdjustDownwards extends CompletionException {

        CouldNotAdjustDownwards() {
            super();
        }

        CouldNotAdjustDownwards(Coin value, Coin nonDustAmout) {
            super(String.format("Value %s is below non-dust threshold of %s", value.toFriendlyString(), nonDustAmout.toFriendlyString()));
        }
    }

    /**
     * Thrown if the resultant transaction is too big for Bitcoin to process.
     * Try breaking up the amounts of value.
     */
    public static class ExceededMaxTransactionSize extends CompletionException {
    }

    /**
     * Thrown if the private keys and seed of this wallet cannot be decrypted
     * due to the supplied encryption key or password being wrong.
     */
    public static class BadWalletEncryptionKeyException extends CompletionException {

        public BadWalletEncryptionKeyException(Throwable throwable) {
            super(throwable);
        }
    }

    /**
     * Given a spend request containing an incomplete transaction, makes it
     * valid by adding outputs and signed inputs according to the instructions
     * in the request. The transaction in the request is modified by this
     * method.
     *
     * @param req a SendRequest that contains the incomplete transaction and
     * details for how to make it valid.
     * @throws InsufficientMoneyException if the request could not be completed
     * due to not enough balance.
     * @throws IllegalArgumentException if you try and complete the same
     * SendRequest twice
     * @throws DustySendRequested if the resultant transaction would violate the
     * dust rules.
     * @throws CouldNotAdjustDownwards if emptying the wallet was requested and
     * the output can't be shrunk for fees without violating a protocol rule.
     * @throws ExceededMaxTransactionSize if the resultant transaction is too
     * big for Bitcoin to process.
     * @throws MultipleOpReturnRequested if there is more than one OP_RETURN
     * output for the resultant transaction.
     * @throws BadWalletEncryptionKeyException if the supplied
     * {@link SendRequest#aesKey} is wrong.
     */
    public void completeTx(SendRequest req) throws InsufficientMoneyException, CompletionException {
        lock.lock();
        try {
            checkArgument(!req.completed, ()
                    -> "given SendRequest has already been completed");

            log.log(Level.INFO, "Completing send tx with {0} outputs totalling {1} and a fee of {2}/vkB",
                    new Object[]{req.tx.getOutputs().size(),
                        req.tx.getOutputSum().toFriendlyString(), req.feePerKb.toFriendlyString()});

            // Calculate a list of ALL potential candidates for spending and then ask a coin selector to provide us
            // with the actual outputs that'll be used to gather the required amount of value. In this way, users
            // can customize coin selection policies. The call below will ignore immature coinbases and outputs
            // we don't have the keys for.
            List<TransactionOutput> prelimCandidates = calculateAllSpendCandidates(true, req.missingSigsMode == MissingSigsMode.THROW);

            // Connect (add a value amount) unconnected inputs
            List<TransactionInput> inputs = connectInputs(prelimCandidates, req.tx.getInputs());
            req.tx.clearInputs();
            inputs.forEach(req.tx::addInput);

            // Warn if there are remaining unconnected inputs whose value we do not know
            // TODO: Consider throwing if there are inputs that we don't have a value for
            if (req.tx.getInputs().stream()
                    .map(TransactionInput::getValue)
                    .anyMatch(Objects::isNull)) {
                log.severe("SendRequest transaction already has inputs but we don't know how much they are worth - they will be added to fee.");
            }

            // If any inputs have already been added, we don't need to get their value from wallet
            Coin totalInput = req.tx.getInputSum();
            // Calculate the amount of value we need to import.
            Coin valueNeeded = req.tx.getOutputSum().subtract(totalInput);

            // Enforce the OP_RETURN limit
            if (req.tx.getOutputs().stream()
                    .filter(o -> ScriptPattern.isOpReturn(o.getScriptPubKey()))
                    .count() > 1) // Only 1 OP_RETURN per transaction allowed.
            {
                throw new MultipleOpReturnRequested();
            }

            // Check for dusty sends
            if (req.ensureMinRequiredFee && !req.emptyWallet) { // Min fee checking is handled later for emptyWallet.
                if (req.tx.getOutputs().stream().anyMatch(TransactionOutput::isDust)) {
                    throw new DustySendRequested();
                }
            }

            // Filter out candidates that are already included in the transaction inputs
            List<TransactionOutput> candidates = prelimCandidates.stream()
                    .filter(output -> alreadyIncluded(req.tx.getInputs(), output))
                    .collect(StreamUtils.toUnmodifiableList());

            CoinSelection bestCoinSelection;
            TransactionOutput bestChangeOutput = null;
            List<Coin> updatedOutputValues = null;
            if (!req.emptyWallet) {
                // This can throw InsufficientMoneyException.
                FeeCalculation feeCalculation = calculateFee(req, valueNeeded, req.ensureMinRequiredFee, candidates);
                bestCoinSelection = feeCalculation.bestCoinSelection;
                bestChangeOutput = feeCalculation.bestChangeOutput;
                updatedOutputValues = feeCalculation.updatedOutputValues;
            } else {
                // We're being asked to empty the wallet. What this means is ensuring "tx" has only a single output
                // of the total value we can currently spend as determined by the selector, and then subtracting the fee.
                checkState(req.tx.getOutputs().size() == 1, ()
                        -> "empty wallet TX must have a single output only");
                CoinSelector selector = req.coinSelector == null ? coinSelector : req.coinSelector;
                bestCoinSelection = selector.select((Coin) network.maxMoney(), candidates);
                candidates = null;  // Selector took ownership and might have changed candidates. Don't access again.
                req.tx.getOutput(0).setValue(bestCoinSelection.totalValue());
                log.log(Level.INFO, "  emptying {0}", bestCoinSelection.totalValue().toFriendlyString());
            }

            bestCoinSelection.outputs()
                    .forEach(req.tx::addInput);

            if (req.emptyWallet) {
                if (!adjustOutputDownwardsForFee(req.tx, bestCoinSelection, req.feePerKb, req.ensureMinRequiredFee)) {
                    throw new CouldNotAdjustDownwards();
                }
            }

            if (updatedOutputValues != null) {
                for (int i = 0; i < updatedOutputValues.size(); i++) {
                    req.tx.getOutput(i).setValue(updatedOutputValues.get(i));
                }
            }

            if (bestChangeOutput != null) {
                req.tx.addOutput(bestChangeOutput);
                log.log(Level.INFO, "  with {0} change", bestChangeOutput.getValue().toFriendlyString());
            }

            // Now shuffle the outputs to obfuscate which is the change.
            if (req.shuffleOutputs) {
                req.tx.shuffleOutputs();
            }

            // Now sign the inputs, thus proving that we are entitled to redeem the connected outputs.
            if (req.signInputs) {
                signTransaction(req);
            }

            // Check size.
            final int size = req.tx.messageSize();
            if (size > Transaction.MAX_STANDARD_TX_SIZE) {
                throw new ExceededMaxTransactionSize();
            }

            // Label the transaction as being self created. We can use this later to spend its change output even before
            // the transaction is confirmed. We deliberately won't bother notifying listeners here as there's not much
            // point - the user isn't interested in a confidence transition they made themselves.
            getConfidence(req.tx).setSource(TransactionConfidence.Source.SELF);
            // Label the transaction as being a user requested payment. This can be used to render GUI wallet
            // transaction lists more appropriately, especially when the wallet starts to generate transactions itself
            // for internal purposes.
            req.tx.setPurpose(Transaction.Purpose.USER_PAYMENT);
            // Record the exchange rate that was valid when the transaction was completed.
            req.tx.setExchangeRate(req.exchangeRate);
            req.tx.setMemo(req.memo);
            req.completed = true;
            log.log(Level.INFO, "  completed: {}", req.tx);
        } finally {
            lock.unlock();
        }
    }

    /**
     * <p>
     * Given a send request containing transaction, attempts to sign it's
     * inputs.This method expects transaction to have all necessary inputs
     * connected or they will be ignored.</p>
     * <p>
     * Actual signing is done by pluggable {@link #signers} and it's not
     * guaranteed that transaction will be complete in the end.</p>
     *
     * @param req
     * @throws BadWalletEncryptionKeyException if the supplied
     * {@link SendRequest#aesKey} is wrong.
     */
    public void signTransaction(SendRequest req) throws BadWalletEncryptionKeyException {
        lock.lock();
        try {
            Transaction tx = req.tx;
            List<TransactionInput> inputs = tx.getInputs();
            List<TransactionOutput> outputs = tx.getOutputs();
            checkState(inputs.size() > 0);
            checkState(outputs.size() > 0);

            int numInputs = tx.getInputs().size();
            for (int i = 0; i < numInputs; i++) {
                TransactionInput txIn = tx.getInput(i);
                TransactionOutput connectedOutput = txIn.getConnectedOutput();
                if (connectedOutput == null) {
                    // Missing connected output, assuming already signed.
                    continue;
                }
                Script scriptPubKey = connectedOutput.getScriptPubKey();

                try {
                    // We assume if its already signed, its hopefully got a SIGHASH type that will not invalidate when
                    // we sign missing pieces (to check this would require either assuming any signatures are signing
                    // standard output types or a way to get processed signatures out of script execution)
                    txIn.getScriptSig().correctlySpends(tx, i, txIn.getWitness(), connectedOutput.getValue(),
                            connectedOutput.getScriptPubKey(), Script.ALL_VERIFY_FLAGS);
                    log2.warn("Input {} already correctly spends output, assuming SIGHASH type used will be safe and skipping signing.", i);
                    continue;
                } catch (ScriptException e) {
                    log2.debug("Input contained an incorrect signature", e);
                    // Expected.
                } catch (NoSuchAlgorithmException | NoSuchProviderException | SignatureException | InvalidKeySpecException | InvalidKeyException ex) {
                    Logger.getLogger(Wallet.class.getName()).log(Level.SEVERE, null, ex);
                }

                RedeemData redeemData = txIn.getConnectedRedeemData(identity);
                Objects.requireNonNull(redeemData, ()
                        -> "Transaction exists in wallet that we cannot redeem: " + txIn.getOutpoint().hash());
                txIn.setScriptSig(scriptPubKey.createEmptyInputScript(redeemData.key, redeemData.redeemScript));
            }

            TransactionSigner.ProposedTransaction proposal = new TransactionSigner.ProposedTransaction(tx);
            for (TransactionSigner signer : signers) {
                if (!signer.signInputs(proposal, identity)) {
                    log2.info("{} returned false for the tx", signer.getClass().getName());
                }
            }

            // resolve missing sigs if any
            new MissingSigResolutionSigner(req.missingSigsMode).signInputs(proposal, identity);
        } catch (ScriptException e) {
            throw new BadWalletEncryptionKeyException(e);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns a list of the outputs that can potentially be spent, i.e.that we
     * have the keys for and are unspent according to our knowledge of the block
     * chain.
     *
     * @return
     */
    public List<TransactionOutput> calculateAllSpendCandidates() {
        return calculateAllSpendCandidates(true, true);
    }

    private static class FeeCalculation {

        // Selected UTXOs to spend
        public CoinSelection bestCoinSelection;
        // Change output (may be null if no change)
        public TransactionOutput bestChangeOutput;
        // List of output values adjusted downwards when recipients pay fees (may be null if no adjustment needed).
        public List<Coin> updatedOutputValues;
    }

    private FeeCalculation calculateFee(SendRequest req, Coin value, boolean needAtLeastReferenceFee, List<TransactionOutput> candidates) throws InsufficientMoneyException {
        checkState(lock.isHeldByCurrentThread());
        FeeCalculation result;
        Coin fee = Coin.ZERO;
        while (true) {
            result = new FeeCalculation();
            Transaction tx = new Transaction();
            addSuppliedInputs(tx, req.tx.getInputs());

            Coin valueNeeded = req.recipientsPayFees ? value : value.add(fee);
            if (req.recipientsPayFees) {
                result.updatedOutputValues = new ArrayList<>();
            }
            for (int i = 0; i < req.tx.getOutputs().size(); i++) {
                TransactionOutput output = TransactionOutput.read(req.tx.getOutput(i).toProto(), tx);
                if (req.recipientsPayFees) {
                    // Subtract fee equally from each selected recipient
                    output.setValue(output.getValue().subtract(fee.divide(req.tx.getOutputs().size())));
                    // first receiver pays the remainder not divisible by output count
                    if (i == 0) {
                        output.setValue(
                                output.getValue().subtract(fee.divideAndRemainder(req.tx.getOutputs().size())[1])); // Subtract fee equally from each selected recipient
                    }
                    result.updatedOutputValues.add(output.getValue());
                    Coin nonDustValue = output.getMinNonDustValue();
                    if (output.getValue().isLessThan(nonDustValue)) {
                        throw new CouldNotAdjustDownwards(output.getValue(), nonDustValue);
                    }
                }
                tx.addOutput(output);
            }
            CoinSelector selector = req.coinSelector == null ? coinSelector : req.coinSelector;
            // selector is allowed to modify candidates list.
            CoinSelection selection = selector.select(valueNeeded, new LinkedList<>(candidates));
            result.bestCoinSelection = selection;
            // Can we afford this?
            if (selection.totalValue().compareTo(valueNeeded) < 0) {
                Coin valueMissing = valueNeeded.subtract(selection.totalValue());
                throw new InsufficientMoneyException(valueMissing, selection.totalValue(), value, fee);
            }
            Coin change = selection.totalValue().subtract(valueNeeded);
            if (change.isGreaterThan(Coin.ZERO)) {
                // The value of the inputs is greater than what we want to send. Just like in real life then,
                // we need to take back some coins ... this is called "change". Add another output that sends the change
                // back to us. The address comes either from the request or currentChangeAddress() as a default.
                Address changeAddress = (req.changeAddress != null) ? req.changeAddress : currentAddress();
                TransactionOutput changeOutput = new TransactionOutput(tx, change, changeAddress);
                if (req.recipientsPayFees && changeOutput.isDust()) {
                    // We do not move dust-change to fees, because the sender would end up paying more than requested.
                    // This would be against the purpose of the all-inclusive feature.
                    // So instead we raise the change and deduct from the first recipient.
                    Coin missingToNotBeDust = changeOutput.getMinNonDustValue().subtract(changeOutput.getValue());
                    changeOutput.setValue(changeOutput.getValue().add(missingToNotBeDust));
                    TransactionOutput firstOutput = tx.getOutput(0);
                    firstOutput.setValue(firstOutput.getValue().subtract(missingToNotBeDust));
                    result.updatedOutputValues.set(0, firstOutput.getValue());
                    if (firstOutput.isDust()) {
                        throw new CouldNotAdjustDownwards();
                    }
                }
                if (changeOutput.isDust()) {
                    // Never create dust outputs; if we would, just
                    // add the dust to the fee.
                    // Oscar comment: This seems like a way to make the condition below "if
                    // (!fee.isLessThan(feeNeeded))" to become true.
                    // This is a non-easy to understand way to do that.
                    // Maybe there are other effects I am missing
                    fee = fee.add(changeOutput.getValue());
                } else {
                    tx.addOutput(changeOutput);
                    result.bestChangeOutput = changeOutput;
                }
            }

            for (TransactionOutput selectedOutput : selection.outputs()) {
                TransactionInput input = tx.addInput(selectedOutput);
                // If the scriptBytes don't default to none, our size calculations will be thrown off.
                checkState(input.getScriptBytes().length == 0);
                checkState(!input.hasWitness());
            }

            Coin feeNeeded = estimateFees(tx, selection, req.feePerKb, needAtLeastReferenceFee);

            if (!fee.isLessThan(feeNeeded)) {
                // Done, enough fee included.
                break;
            }

            // Include more fee and try again.
            fee = feeNeeded;
        }
        return result;

    }

    /**
     * Connect unconnected inputs with outputs from the wallet
     *
     * @param candidates A list of spend candidates from a Wallet
     * @param inputs a list of possibly unconnected/unvalued inputs (e.g. from a
     * spend request)
     * @return a list of the same inputs, but connected/valued if not previously
     * valued and found in wallet
     */
    // For testing only
    static List<TransactionInput> connectInputs(List<TransactionOutput> candidates, List<TransactionInput> inputs) {
        return inputs.stream()
                .map(in -> candidates.stream()
                .filter(utxo -> utxo.getOutPointFor().equals(in.getOutpoint()))
                .findFirst()
                .map(o -> new TransactionInput(o.getParentTransaction(), o.getScriptPubKey().program(), o.getOutPointFor(), o.getValue()))
                .orElse(in))
                .collect(StreamUtils.toUnmodifiableList());
    }

    /**
     * Is a UTXO already included (to be spent) in a list of transaction inputs?
     *
     * @param inputs the list of inputs to check
     * @param output the transaction output
     * @return true if it is already included, false otherwise
     */
    private boolean alreadyIncluded(List<TransactionInput> inputs, TransactionOutput output) {
        return inputs.stream().noneMatch(i -> i.getOutpoint().equals(output.getOutPointFor()));
    }

    /**
     * Reduce the value of the first output of a transaction to pay the given
     * feePerKb as appropriate for its size. If ensureMinRequiredFee is true,
     * feePerKb is set to at least
     * {@link Transaction#REFERENCE_DEFAULT_MIN_TX_FEE}.
     *
     * @return true if output is not dust
     */
    private boolean adjustOutputDownwardsForFee(Transaction tx, CoinSelection coinSelection, Coin feePerKb,
            boolean ensureMinRequiredFee) {
        Coin fee = estimateFees(tx, coinSelection, feePerKb, ensureMinRequiredFee);
        TransactionOutput output = tx.getOutput(0);
        output.setValue(output.getValue().subtract(fee));
        return !output.isDust();
    }

    private void addSuppliedInputs(Transaction tx, List<TransactionInput> originalInputs) {
        for (TransactionInput input : originalInputs) {
            tx.addInput(TransactionInput.read(input.toProto(input.hasWitness()), tx));
        }
    }

    private Coin estimateFees(Transaction tx, CoinSelection coinSelection, Coin requestedFeePerKb, boolean ensureMinRequiredFee) {
        Coin feePerKb = (ensureMinRequiredFee && requestedFeePerKb.isLessThan(Transaction.REFERENCE_DEFAULT_MIN_TX_FEE))
                ? Transaction.REFERENCE_DEFAULT_MIN_TX_FEE
                : requestedFeePerKb;
        int vSize = tx.getVsize() + estimateVirtualBytesForSigning(coinSelection.outputs());
        return feePerKb.multiply(vSize).divide(1000);
    }

    private int estimateVirtualBytesForSigning(List<TransactionOutput> outputs) {
        return outputs.stream()
                .map(TransactionOutput::getScriptPubKey)
                .mapToInt(this::estimateVirtualBytesForSigning)
                .sum();
    }

    private int estimateVirtualBytesForSigning(Script script) {
        try {
            if (ScriptPattern.isP2WPKH(script)) {
                byte[] extractHashFromP2WH = ScriptPattern.extractHashFromP2WH(script);

                PublicKey key = null;
                if (Arrays.equals(extractHashFromP2WH, identity.getNodeId().getId())) {
                    key = identity.getKeyPair().getPublic();
                }

                Objects.requireNonNull(key, "Coin selection includes unspendable outputs");
                return IntMath.divide(script.getNumberOfBytesRequiredToSpend(key, Script.parse(extractHashFromP2WH)), 4,
                        RoundingMode.CEILING); // round up
            } else if (ScriptPattern.isP2WSH(script)) {
                byte[] extractHashFromP2SH = ScriptPattern.extractHashFromP2SH(script);

                Script redeemScript = Script.parse(extractHashFromP2SH);
                Objects.requireNonNull(redeemScript, "Coin selection includes unspendable outputs");
                return script.getNumberOfBytesRequiredToSpend(identity.getKeyPair().getPublic(), redeemScript);
            } else {
                return script.getNumberOfBytesRequiredToSpend(identity.getKeyPair().getPublic(), null);
            }
        } catch (ScriptException e) {
            // If this happens it means an output script in a wallet tx could not be understood. That should never
            // happen, if it does it means the wallet has got into an inconsistent state.
            throw new IllegalStateException(e);
        }
    }

    /**
     * Sends coins to the given address but does not broadcast the resulting
     * pending transaction.It is still stored in the wallet, so when the wallet
     * is added to a {@link PeerGroup} or {@link Peer} the transaction will be
     * announced to the network.The given {@link SendRequest} is completed first
     * using {@link Wallet#completeTx(SendRequest)} to make it valid.
     *
     * @param request
     * @return the Transaction that was created
     * @throws InsufficientMoneyException if the request could not be completed
     * due to not enough balance.
     * @throws IllegalArgumentException if you try and complete the same
     * SendRequest twice
     */
    public Transaction sendCoinsOffline(SendRequest request)
            throws InsufficientMoneyException, CompletionException {
        lock.lock();
        try {
            completeTx(request);
            commitTx(request.tx);
            return request.tx;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Updates the wallet with the given transaction: puts it into the pending
     * pool, sets the spent flags and runs the onCoinsSent/onCoinsReceived event
     * listener. Used in two situations:
     * <ol>
     * <li>When we have just successfully transmitted the tx we created to the
     * network.</li>
     * <li>When we receive a pending transaction that didn't appear in the chain
     * yet, and we did not create it.</li>
     * </ol>
     * Triggers an auto save (if enabled.)
     * <p>
     * Unlike {@link Wallet#maybeCommitTx} {@code commitTx} throws an exception
     * if the transaction was already added to the wallet.
     *
     * @param tx transaction to commit
     * @throws VerificationException if transaction was already in the pending
     * pool
     */
    public void commitTx(Transaction tx) throws VerificationException {
        checkArgument(maybeCommitTx(tx), ()
                -> "commitTx called on the same transaction twice");
    }

    /**
     * Updates the wallet with the given transaction: puts it into the pending
     * pool, sets the spent flags and runs the onCoinsSent/onCoinsReceived event
     * listener.
     * <p>
     * Triggers an auto save (if enabled.)
     * <p>
     * Unlike {@link Wallet#commitTx} this method does not throw an exception if
     * the transaction was already added to the wallet, instead it will return
     * {@code false}
     *
     * @param tx transaction to commit
     * @return true if the tx was added to the wallet, or false if it was
     * already in the pending pool
     * @throws VerificationException If transaction fails to verify
     */
    public boolean maybeCommitTx(Transaction tx) throws VerificationException {
        Transaction.verify(network, tx);
        lock.lock();
        try {
            if (pending.containsKey(tx.getTxId())) {
                return false;
            }
            log.log(Level.INFO, "commitTx of {0}", tx.getTxId());
            Coin balance = getBalance();
            tx.setUpdateTime(TimeUtils.currentTime());
            // Put any outputs that are sending money back to us into the unspents map, and calculate their total value.
            Coin valueSentToMe = Coin.ZERO;
            for (TransactionOutput o : tx.getOutputs()) {
                if (!o.isMine(identity)) {
                    continue;
                }
                valueSentToMe = valueSentToMe.add(o.getValue());
            }
            // Mark the outputs we're spending as spent so we won't try and use them in future creations. This will also
            // move any transactions that are now fully spent to the spent map so we can skip them when creating future
            // spends.
            updateForSpends(tx, false);

            Set<Transaction> doubleSpendPendingTxns = findDoubleSpendsAgainst(tx, pending);
            Set<Transaction> doubleSpendUnspentTxns = findDoubleSpendsAgainst(tx, unspent);
            Set<Transaction> doubleSpendSpentTxns = findDoubleSpendsAgainst(tx, spent);

            if (!doubleSpendUnspentTxns.isEmpty()
                    || !doubleSpendSpentTxns.isEmpty()
                    || !isNotSpendingTxnsInConfidenceType(tx, ConfidenceType.DEAD)) {
                // tx is a double spend against a tx already in the best chain or spends outputs of a DEAD tx.
                // Add tx to the dead pool and schedule confidence listener notifications.
                log.log(Level.INFO, "->dead: {0}", tx.getTxId());
                getConfidence(tx).setConfidenceType(ConfidenceType.DEAD);
                confidenceChanged.put(tx, TransactionConfidence.Listener.ChangeReason.TYPE);
                addWalletTransaction(Pool.DEAD, tx);
            } else if (!doubleSpendPendingTxns.isEmpty()
                    || !isNotSpendingTxnsInConfidenceType(tx, ConfidenceType.IN_CONFLICT)) {
                // tx is a double spend against a pending tx or spends outputs of a tx already IN_CONFLICT.
                // Add tx to the pending pool. Update the confidence type of tx, the txns in conflict with tx and all
                // their dependencies to IN_CONFLICT and schedule confidence listener notifications.
                log.log(Level.INFO, "->pending (IN_CONFLICT): {0}", tx.getTxId());
                addWalletTransaction(Pool.PENDING, tx);
                doubleSpendPendingTxns.add(tx);
                addTransactionsDependingOn(doubleSpendPendingTxns, getTransactions(true));
                for (Transaction doubleSpendTx : doubleSpendPendingTxns) {
                    getConfidence(doubleSpendTx).setConfidenceType(ConfidenceType.IN_CONFLICT);
                    confidenceChanged.put(doubleSpendTx, TransactionConfidence.Listener.ChangeReason.TYPE);
                }
            } else {
                // No conflict detected.
                // Add to the pending pool and schedule confidence listener notifications.
                log.log(Level.INFO, "->pending: {0}", tx.getTxId());
                getConfidence(tx).setConfidenceType(ConfidenceType.PENDING);
                confidenceChanged.put(tx, TransactionConfidence.Listener.ChangeReason.TYPE);
                addWalletTransaction(Pool.PENDING, tx);
            }
            if (log2.isInfoEnabled()) {
                log2.info("Estimated balance is now: {}", getBalance(BalanceType.ESTIMATED).toFriendlyString());
            }

            // Mark any keys used in the outputs as "used", this allows wallet UI's to auto-advance the current key
            // they are showing to the user in qr codes etc.
//            markKeysAsUsed(tx);
            try {
                Coin valueSentFromMe = tx.getValueSentFromMe(this);
                Coin newBalance = balance.add(valueSentToMe).subtract(valueSentFromMe);
                if (valueSentToMe.signum() > 0) {
                    checkBalanceFuturesLocked();
                    queueOnCoinsReceived(tx, balance, newBalance);
                }
                if (valueSentFromMe.signum() > 0) {
                    queueOnCoinsSent(tx, balance, newBalance);
                }

                maybeQueueOnWalletChanged();
            } catch (ScriptException e) {
                // Cannot happen as we just created this transaction ourselves.
                throw new RuntimeException(e);
            }

            isConsistentOrThrow();
            informConfidenceListenersIfNotReorganizing();
//            saveNow();
        } finally {
            lock.unlock();
        }
        return true;
    }

    private void informConfidenceListenersIfNotReorganizing() {
        if (insideReorg) {
            return;
        }
        for (Map.Entry<Transaction, TransactionConfidence.Listener.ChangeReason> entry : confidenceChanged.entrySet()) {
            final Transaction tx = entry.getKey();
            getConfidence(tx).queueListeners(entry.getValue());
            queueOnTransactionConfidenceChanged(tx);
        }
        confidenceChanged.clear();
    }

    /**
     * Returns if this wallet is structurally consistent, so e.g.no duplicate
     * transactions. First inconsistency and a dump of the wallet will be
     * logged.
     *
     * @return
     */
    public boolean isConsistent() {
        try {
            isConsistentOrThrow();
            return true;
        } catch (IllegalStateException x) {
            log2.error(x.getMessage());
            try {
                log2.error(toString());
            } catch (RuntimeException x2) {
                log2.error("Printing inconsistent wallet failed", x2);
            }
            return false;
        }
    }

    /**
     * Variant of {@link Wallet#isConsistent()} that throws an
     * {@link IllegalStateException} describing the first inconsistency.
     */
    public void isConsistentOrThrow() throws IllegalStateException {
        lock.lock();
        try {
            Set<Transaction> transactions = getTransactions(true);

            Set<Sha256Hash> hashes = new HashSet<>();
            for (Transaction tx : transactions) {
                hashes.add(tx.getTxId());
            }

            int size1 = transactions.size();
            if (size1 != hashes.size()) {
                throw new IllegalStateException("Two transactions with same hash");
            }

            int size2 = unspent.size() + spent.size() + pending.size() + dead.size();
            if (size1 != size2) {
                throw new IllegalStateException("Inconsistent wallet sizes: " + size1 + ", " + size2);
            }

            for (Transaction tx : unspent.values()) {
                if (!isTxConsistent(tx, false)) {
                    throw new IllegalStateException("Inconsistent unspent tx: " + tx.getTxId());
                }
            }

            for (Transaction tx : spent.values()) {
                if (!isTxConsistent(tx, true)) {
                    throw new IllegalStateException("Inconsistent spent tx: " + tx.getTxId());
                }
            }
        } finally {
            lock.unlock();
        }
    }

    /*
     * If isSpent - check that all my outputs spent, otherwise check that there at least
     * one unspent.
     */
    // For testing only
    boolean isTxConsistent(final Transaction tx, final boolean isSpent) {
        boolean isActuallySpent = true;
        for (TransactionOutput o : tx.getOutputs()) {
            if (o.isAvailableForSpending()) {
                if (o.isMine(identity)) {
                    isActuallySpent = false;
                }
                if (o.getSpentBy() != null) {
                    log2.error("isAvailableForSpending != spentBy");
                    return false;
                }
            } else {
                if (o.getSpentBy() == null) {
                    log2.error("isAvailableForSpending != spentBy");
                    return false;
                }
            }
        }
        return isActuallySpent == isSpent;
    }

    /**
     * Removes the given event listener object.Returns true if the listener was
     * removed, false if that listener was never added.
     *
     * @param listener
     * @return
     */
    public boolean removeReorganizeEventListener(WalletReorganizeEventListener listener) {
        return ListenerRegistration.removeFromList(listener, reorganizeListeners);
    }

    /**
     * Removes the given event listener object.Returns true if the listener was
     * removed, false if that listener was never added.
     *
     * @param listener
     * @return
     */
    public boolean removeScriptsChangeEventListener(ScriptsChangeEventListener listener) {
        return ListenerRegistration.removeFromList(listener, scriptsChangeListeners);
    }

    /**
     * Removes the given event listener object.Returns true if the listener was
     * removed, false if that listener was never added.
     *
     * @param listener
     * @return
     */
    public boolean removeTransactionConfidenceEventListener(TransactionConfidenceEventListener listener) {
        return ListenerRegistration.removeFromList(listener, transactionConfidenceListeners);
    }

    private void queueOnTransactionConfidenceChanged(final Transaction tx) {
        checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<TransactionConfidenceEventListener> registration : transactionConfidenceListeners) {
            if (registration.executor == Threading.SAME_THREAD) {
                registration.listener.onTransactionConfidenceChanged(this, tx);
            } else {
                registration.executor.execute(() -> registration.listener.onTransactionConfidenceChanged(Wallet.this, tx));
            }
        }
    }

    protected void maybeQueueOnWalletChanged() {
        // Don't invoke the callback in some circumstances, eg, whilst we are re-organizing or fiddling with
        // transactions due to a new block arriving. It will be called later instead.
        checkState(lock.isHeldByCurrentThread());
        checkState(onWalletChangedSuppressions >= 0);
        if (onWalletChangedSuppressions > 0) {
            return;
        }
        for (final ListenerRegistration<WalletChangeEventListener> registration : changeListeners) {
            registration.executor.execute(() -> registration.listener.onWalletChanged(Wallet.this));
        }
    }

    protected void queueOnCoinsReceived(final Transaction tx, final Coin balance, final Coin newBalance) {
        checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<WalletCoinsReceivedEventListener> registration : coinsReceivedListeners) {
            registration.executor.execute(() -> registration.listener.onCoinsReceived(Wallet.this, tx, balance, newBalance));
        }
    }

    protected void queueOnCoinsSent(final Transaction tx, final Coin prevBalance, final Coin newBalance) {
        checkState(lock.isHeldByCurrentThread());
        for (final ListenerRegistration<WalletCoinsSentEventListener> registration : coinsSentListeners) {
            registration.executor.execute(() -> registration.listener.onCoinsSent(Wallet.this, tx, prevBalance, newBalance));
        }
    }

    protected void queueOnReorganize() {
        checkState(lock.isHeldByCurrentThread());
        checkState(insideReorg);
        for (final ListenerRegistration<WalletReorganizeEventListener> registration : reorganizeListeners) {
            registration.executor.execute(() -> registration.listener.onReorganize(Wallet.this));
        }
    }

    protected void queueOnScriptsChanged(final List<Script> scripts, final boolean isAddingScripts) {
        for (final ListenerRegistration<ScriptsChangeEventListener> registration : scriptsChangeListeners) {
            registration.executor.execute(() -> registration.listener.onScriptsChanged(Wallet.this, scripts, isAddingScripts));
        }
    }

    /**
     * Returns a set of all transactions in the wallet.
     *
     * @param includeDead If true, transactions that were overridden by a double
     * spend are included.
     * @return
     */
    public Set<Transaction> getTransactions(boolean includeDead) {
        lock.lock();
        try {
            Set<Transaction> all = new HashSet<>();
            all.addAll(unspent.values());
            all.addAll(spent.values());
            all.addAll(pending.values());
            if (includeDead) {
                all.addAll(dead.values());
            }
            return all;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Finds if tx is NOT spending other txns which are in the specified
     * confidence type
     */
    private boolean isNotSpendingTxnsInConfidenceType(Transaction tx, ConfidenceType confidenceType) {
        for (TransactionInput txInput : tx.getInputs()) {
            Transaction connectedTx = this.getTransaction(txInput.getOutpoint().hash());
            if (connectedTx != null && getConfidence(connectedTx).getConfidenceType().equals(confidenceType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns a transaction object given its hash, if it exists in this wallet,
     * or null otherwise.
     *
     * @param hash
     * @return
     */
//    @Nullable
    public Transaction getTransaction(Sha256Hash hash) {
        lock.lock();
        try {
            return transactions.get(hash);
        } finally {
            lock.unlock();
        }
    }

    /**
     * Finds transactions in the specified candidates that double spend "tx".
     * Not a general check, but it can work even if the double spent inputs are
     * not ours.
     *
     * @return The set of transactions that double spend "tx".
     */
    private Set<Transaction> findDoubleSpendsAgainst(Transaction tx, Map<Sha256Hash, Transaction> candidates) {
        checkState(lock.isHeldByCurrentThread());
//        if (tx.isGenesis()) return new HashSet<>();// not related
        // Compile a set of outpoints that are spent by tx.
        HashSet<TransactionOutPoint> outpoints = new HashSet<>();
        for (TransactionInput input : tx.getInputs()) {
            outpoints.add(input.getOutpoint());
        }
        // Now for each pending transaction, see if it shares any outpoints with this tx.
        Set<Transaction> doubleSpendTxns = new HashSet<>();
        for (Transaction p : candidates.values()) {
            if (p.equals(tx)) {
                continue;
            }
            for (TransactionInput input : p.getInputs()) {
                // This relies on the fact that TransactionOutPoint equality is defined at the protocol not object
                // level - outpoints from two different inputs that point to the same output compare the same.
                TransactionOutPoint outpoint = input.getOutpoint();
                if (outpoints.contains(outpoint)) {
                    // It does, it's a double spend against the candidates, which makes it relevant.
                    doubleSpendTxns.add(p);
                }
            }
        }
        return doubleSpendTxns;
    }

    /**
     * Adds to txSet all the txns in txPool spending outputs of txns in txSet,
     * and all txns spending the outputs of those txns, recursively.
     */
    void addTransactionsDependingOn(Set<Transaction> txSet, Set<Transaction> txPool) {
        Map<Sha256Hash, Transaction> txQueue = new LinkedHashMap<>();
        for (Transaction tx : txSet) {
            txQueue.put(tx.getTxId(), tx);
        }
        while (!txQueue.isEmpty()) {
            Transaction tx = txQueue.remove(txQueue.keySet().iterator().next());
            for (Transaction anotherTx : txPool) {
                if (anotherTx.equals(tx)) {
                    continue;
                }
                for (TransactionInput input : anotherTx.getInputs()) {
                    if (input.getOutpoint().hash().equals(tx.getTxId())) {
                        if (txQueue.get(anotherTx.getTxId()) == null) {
                            txQueue.put(anotherTx.getTxId(), anotherTx);
                            txSet.add(anotherTx);
                        }
                    }
                }
            }
        }
    }

    /**
     * Handle when a transaction becomes newly active on the best chain, either
     * due to receiving a new block or a re-org. Places the tx into the right
     * pool, handles coinbase transactions, handles double-spends and so on.
     */
    private void processTxFromBestChain(Transaction tx, boolean forceAddToPool) throws VerificationException {
        checkState(lock.isHeldByCurrentThread());
        checkState(!pending.containsKey(tx.getTxId()));

        // This TX may spend our existing outputs even though it was not pending. This can happen in unit
        // tests, if keys are moved between wallets, if we're catching up to the chain given only a set of keys,
        // or if a dead coinbase transaction has moved back onto the best chain.
//        boolean isDeadCoinbase = tx.isGenesis() && dead.containsKey(tx.getTxId());
        if (dead.containsKey(tx.getTxId())) {
            // There is a dead coinbase tx being received on the best chain. A coinbase tx is made dead when it moves
            // to a side chain but it can be switched back on a reorg and resurrected back to spent or unspent.
            // So take it out of the dead pool. Note that we don't resurrect dependent transactions here, even though
            // we could. Bitcoin Core nodes on the network have deleted the dependent transactions from their mempools
            // entirely by this point. We could and maybe should rebroadcast them so the network remembers and tries
            // to confirm them again. But this is a deeply unusual edge case that due to the maturity rule should never
            // happen in practice, thus for simplicities sake we ignore it here.
            log2.info("  coinbase tx <-dead: confidence {}", tx.getTxId(),
                    getConfidence(tx).getConfidenceType().name());
            dead.remove(tx.getTxId());
        }

        // Update tx and other unspent/pending transactions by connecting inputs/outputs.
        updateForSpends(tx, true);

        // Now make sure it ends up in the right pool. Also, handle the case where this TX is double-spending
        // against our pending transactions. Note that a tx may double spend our pending transactions and also send
        // us money/spend our money.
        boolean hasOutputsToMe = tx.getValueSentToMe(identity).signum() > 0;
        boolean hasOutputsFromMe = false;
        if (hasOutputsToMe) {
            // Needs to go into either unspent or spent (if the outputs were already spent by a pending tx).
            if (tx.isEveryOwnedOutputSpent(identity)) {
                log2.info("  tx {} ->spent (by pending)", tx.getTxId());
                addWalletTransaction(Pool.SPENT, tx);
            } else {
                log2.info("  tx {} ->unspent", tx.getTxId());
                addWalletTransaction(Pool.UNSPENT, tx);
            }
        } else if (tx.getValueSentFromMe(this).signum() > 0) {
            hasOutputsFromMe = true;
            // Didn't send us any money, but did spend some. Keep it around for record keeping purposes.
            log2.info("  tx {} ->spent", tx.getTxId());
            addWalletTransaction(Pool.SPENT, tx);
        } else if (forceAddToPool) {
            // Was manually added to pending, so we should keep it to notify the user of confidence information
            log2.info("  tx {} ->spent (manually added)", tx.getTxId());
            addWalletTransaction(Pool.SPENT, tx);
        }

        // Kill txns in conflict with this tx
        Set<Transaction> doubleSpendTxns = findDoubleSpendsAgainst(tx, pending);
        if (!doubleSpendTxns.isEmpty()) {
            // no need to addTransactionsDependingOn(doubleSpendTxns) because killTxns() already kills dependencies;
            killTxns(doubleSpendTxns, tx);
        }
        if (!hasOutputsToMe
                && !hasOutputsFromMe
                && !forceAddToPool
                && !findDoubleSpendsAgainst(tx, transactions).isEmpty()) {
            // disconnect irrelevant inputs (otherwise might cause protobuf serialization issue)
            for (TransactionInput input : tx.getInputs()) {
                TransactionOutput output = input.getConnectedOutput();
                if (output != null && !output.isMine(identity)) {
                    input.disconnect();
                }
            }
        }
    }

    /**
     * <p>
     * Updates the wallet by checking if this TX spends any of our outputs, and
     * marking them as spent if so. If fromChain is true, also checks to see if
     * any pending transaction spends outputs of this transaction and marks the
     * spent flags appropriately.</p>
     *
     * <p>
     * It can be called in two contexts. One is when we receive a transaction on
     * the best chain but it wasn't pending, this most commonly happens when we
     * have a set of keys but the wallet transactions were wiped and we are
     * catching up with the block chain. It can also happen if a block includes
     * a transaction we never saw at broadcast time. If this tx double spends,
     * it takes precedence over our pending transactions and the pending tx goes
     * dead.</p>
     *
     * <p>
     * The other context it can be called is from
     * {@link Wallet#receivePending(Transaction, List)}, ie we saw a tx be
     * broadcast or one was submitted directly that spends our own coins. If
     * this tx double spends it does NOT take precedence because the winner will
     * be resolved by the miners - we assume that our version will win, if we
     * are wrong then when a block appears the tx will go dead.</p>
     *
     * @param tx The transaction which is being updated.
     * @param fromChain If true, the tx appeared on the current best chain, if
     * false it was pending.
     */
    private void updateForSpends(Transaction tx, boolean fromChain) throws VerificationException {
        checkState(lock.isHeldByCurrentThread());
        if (fromChain) {
            checkState(!pending.containsKey(tx.getTxId()));
        }
        for (TransactionInput input : tx.getInputs()) {
            TransactionInput.ConnectionResult result = input.connect(unspent, TransactionInput.ConnectMode.ABORT_ON_CONFLICT);
            if (result == TransactionInput.ConnectionResult.NO_SUCH_TX) {
                // Not found in the unspent map. Try again with the spent map.
                result = input.connect(spent, TransactionInput.ConnectMode.ABORT_ON_CONFLICT);
                if (result == TransactionInput.ConnectionResult.NO_SUCH_TX) {
                    // Not found in the unspent and spent maps. Try again with the pending map.
                    result = input.connect(pending, TransactionInput.ConnectMode.ABORT_ON_CONFLICT);
                    if (result == TransactionInput.ConnectionResult.NO_SUCH_TX) {
                        // Doesn't spend any of our outputs or is coinbase.
                        continue;
                    }
                }
            }

            TransactionOutput output = Objects.requireNonNull(input.getConnectedOutput());
            if (result == TransactionInput.ConnectionResult.ALREADY_SPENT) {
                if (fromChain) {
                    // Can be:
                    // (1) We already marked this output as spent when we saw the pending transaction (most likely).
                    //     Now it's being confirmed of course, we cannot mark it as spent again.
                    // (2) A double spend from chain: this will be handled later by findDoubleSpendsAgainst()/killTxns().
                    //
                    // In any case, nothing to do here.
                } else {
                    // We saw two pending transactions that double spend each other. We don't know which will win.
                    // This can happen in the case of bad network nodes that mutate transactions. Do a hex dump
                    // so the exact nature of the mutation can be examined.
                    log2.warn("Saw two pending transactions double spend each other");
                    log2.warn("  offending input is input {}", tx.getInputs().indexOf(input));
                    log2.warn("{}: {}", tx.getTxId(), ByteUtils.formatHex(tx.toProto().toByteArray()));
                    Transaction other = output.getSpentBy().getParentTransaction();
                    log2.warn("{}: {}", other.getTxId(), ByteUtils.formatHex(other.toProto().toByteArray()));
                }
            } else if (result == TransactionInput.ConnectionResult.SUCCESS) {
                // Otherwise we saw a transaction spend our coins, but we didn't try and spend them ourselves yet.
                // The outputs are already marked as spent by the connect call above, so check if there are any more for
                // us to use. Move if not.
                Transaction connected = Objects.requireNonNull(input.getConnectedTransaction());
                log2.info("  marked {} as spent by {}", input.getOutpoint(), tx.getTxId());
                maybeMovePool(connected, "prevtx");
                // Just because it's connected doesn't mean it's actually ours: sometimes we have total visibility.
                if (output.isMine(identity)) {
                    checkState(myUnspents.remove(output));
                }
            }
        }
        // Now check each output and see if there is a pending transaction which spends it. This shouldn't normally
        // ever occur because we expect transactions to arrive in temporal order, but this assumption can be violated
        // when we receive a pending transaction from the mempool that is relevant to us, which spends coins that we
        // didn't see arrive on the best chain yet. For instance, because of a chain replay or because of our keys were
        // used by another wallet somewhere else. Also, unconfirmed transactions can arrive from the mempool in more or
        // less random order.
        for (Transaction pendingTx : pending.values()) {
            for (TransactionInput input : pendingTx.getInputs()) {
                TransactionInput.ConnectionResult result = input.connect(tx, TransactionInput.ConnectMode.ABORT_ON_CONFLICT);
                if (fromChain) {
                    // This TX is supposed to have just appeared on the best chain, so its outputs should not be marked
                    // as spent yet. If they are, it means something is happening out of order.
                    checkState(result != TransactionInput.ConnectionResult.ALREADY_SPENT);
                }
                if (result == TransactionInput.ConnectionResult.SUCCESS) {
                    log2.info("Connected pending tx input {}:{}",
                            pendingTx.getTxId(), pendingTx.getInputs().indexOf(input));
                    // The unspents map might not have it if we never saw this tx until it was included in the chain
                    // and thus becomes spent the moment we become aware of it.
                    if (myUnspents.remove(input.getConnectedOutput())) {
                        log2.info("Removed from UNSPENTS: {}", input.getConnectedOutput());
                    }
                }
            }
        }
        if (!fromChain) {
            maybeMovePool(tx, "pendingtx");
        } else {
            // If the transactions outputs are now all spent, it will be moved into the spent pool by the
            // processTxFromBestChain method.
        }
    }

    // Updates the wallet when a double spend occurs. overridingTx can be null for the case of coinbases
    private void killTxns(Set<Transaction> txnsToKill,
            //            @Nullable
            Transaction overridingTx) {
        LinkedList<Transaction> work = new LinkedList<>(txnsToKill);
        while (!work.isEmpty()) {
            final Transaction tx = work.poll();
            log2.warn("TX {} killed{}", tx.getTxId(),
                    overridingTx != null ? " by " + overridingTx.getTxId() : "");
            log2.warn("Disconnecting each input and moving connected transactions.");
            // TX could be pending (finney attack), or in unspent/spent (coinbase killed by reorg).
            pending.remove(tx.getTxId());
            unspent.remove(tx.getTxId());
            spent.remove(tx.getTxId());
            addWalletTransaction(Pool.DEAD, tx);
            for (TransactionInput deadInput : tx.getInputs()) {
                Transaction connected = deadInput.getConnectedTransaction();
                if (connected == null) {
                    continue;
                }
                if (getConfidence(connected).getConfidenceType() != ConfidenceType.DEAD && deadInput.getConnectedOutput().getSpentBy() != null && deadInput.getConnectedOutput().getSpentBy().equals(deadInput)) {
                    checkState(myUnspents.add(deadInput.getConnectedOutput()));
                    log2.info("Added to UNSPENTS: {} in {}", deadInput.getConnectedOutput(), deadInput.getConnectedOutput().getParentTransaction().getTxId());
                }
                deadInput.disconnect();
                maybeMovePool(connected, "kill");
            }
            getConfidence(tx).setOverridingTxId(overridingTx != null ? overridingTx.getTxId() : null);
            confidenceChanged.put(tx, TransactionConfidence.Listener.ChangeReason.TYPE);
            // Now kill any transactions we have that depended on this one.
            for (TransactionOutput deadOutput : tx.getOutputs()) {
                if (myUnspents.remove(deadOutput)) {
                    log2.info("XX Removed from UNSPENTS: {}", deadOutput);
                }
                TransactionInput connected = deadOutput.getSpentBy();
                if (connected == null) {
                    continue;
                }
                final Transaction parentTransaction = connected.getParentTransaction();
                log2.info("This death invalidated dependent tx {}", parentTransaction.getTxId());
                work.push(parentTransaction);
            }
        }
        if (overridingTx == null) {
            return;
        }
        log2.warn("Now attempting to connect the inputs of the overriding transaction.");
        for (TransactionInput input : overridingTx.getInputs()) {
            TransactionInput.ConnectionResult result = input.connect(unspent, TransactionInput.ConnectMode.DISCONNECT_ON_CONFLICT);
            if (result == TransactionInput.ConnectionResult.SUCCESS) {
                maybeMovePool(input.getConnectedTransaction(), "kill");
                myUnspents.remove(input.getConnectedOutput());
                log2.info("Removing from UNSPENTS: {}", input.getConnectedOutput());
            } else {
                result = input.connect(spent, TransactionInput.ConnectMode.DISCONNECT_ON_CONFLICT);
                if (result == TransactionInput.ConnectionResult.SUCCESS) {
                    maybeMovePool(input.getConnectedTransaction(), "kill");
                    myUnspents.remove(input.getConnectedOutput());
                    log2.info("Removing from UNSPENTS: {}", input.getConnectedOutput());
                }
            }
        }
    }

    /**
     * If the transactions outputs are all marked as spent, and it's in the
     * unspent map, move it. If the owned transactions outputs are not all
     * marked as spent, and it's in the spent map, move it.
     */
    private void maybeMovePool(Transaction tx, String context) {
        checkState(lock.isHeldByCurrentThread());
        if (tx.isEveryOwnedOutputSpent(identity)) {
            // There's nothing left I can spend in this transaction.
            if (unspent.remove(tx.getTxId()) != null) {

                log.log(Level.INFO, "  {0} {1} <-unspent ->spent", new Object[]{tx.getTxId(), context});

                spent.put(tx.getTxId(), tx);
            }
        } else {
            if (spent.remove(tx.getTxId()) != null) {

                log.log(Level.INFO, "  {0} {1} <-spent ->unspent", new Object[]{tx.getTxId(), context});

                unspent.put(tx.getTxId(), tx);
            }
        }
    }

    @Override
    public Map<Sha256Hash, Transaction> getTransactionPool(Pool pool) {
        lock.lock();
        try {
            switch (pool) {
                case UNSPENT -> {
                    return unspent;
                }
                case SPENT -> {
                    return spent;
                }
                case PENDING -> {
                    return pending;
                }
                case DEAD -> {
                    return dead;
                }
                default ->
                    throw new RuntimeException("Unknown wallet transaction type " + pool);
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * Adds the given transaction to the given pools and registers a confidence
     * change listener on it.
     */
    private void addWalletTransaction(Pool pool, Transaction tx) {
        checkState(lock.isHeldByCurrentThread());
        transactions.put(tx.getTxId(), tx);
        switch (pool) {
            case UNSPENT:
                checkState(unspent.put(tx.getTxId(), tx) == null);
                break;
            case SPENT:
                checkState(spent.put(tx.getTxId(), tx) == null);
                break;
            case PENDING:
                checkState(pending.put(tx.getTxId(), tx) == null);
                break;
            case DEAD:
                checkState(dead.put(tx.getTxId(), tx) == null);
                break;
            default:
                throw new RuntimeException("Unknown wallet transaction type " + pool);
        }
        if (pool == Pool.UNSPENT || pool == Pool.PENDING) {
            for (TransactionOutput output : tx.getOutputs()) {
                if (output.isAvailableForSpending() && output.isMine(identity)) {
                    myUnspents.add(output);
                }
            }
        }
        // This is safe even if the listener has been added before, as TransactionConfidence ignores duplicate
        // registration requests. That makes the code in the wallet simpler.
        getConfidence(tx).addEventListener(Threading.SAME_THREAD, txConfidenceListener);
    }

    /**
     * <p>
     * Specifies that the given {@link TransactionBroadcaster}, typically a
     * {@link PeerGroup}, should be used for sending transactions to the Bitcoin
     * network by default.Some sendCoins methods let you specify a broadcaster
     * explicitly, in that case, they don't use this broadcaster. If null is
     * specified then the wallet won't attempt to broadcast transactions
     * itself.</p>
     *
     * <p>
     * You don't normally need to call this. A {@link PeerGroup} will
     * automatically set itself as the wallets broadcaster when you use
     * {@link PeerGroup#addWallet(Wallet)}. A wallet can use the broadcaster
     * when you ask it to send money, but in future also at other times to
     * implement various features that may require asynchronous re-organisation
     * of the wallet contents on the block chain. For instance, in future the
     * wallet may choose to optimise itself to reduce fees or improve
     * privacy.</p>
     *
     * @param broadcaster
     */
    public void setTransactionBroadcaster(TransactionBroadcaster broadcaster) {
        Transaction[] toBroadcast = {};
        lock.lock();
        try {
            if (vTransactionBroadcaster == broadcaster) {
                return;
            }
            vTransactionBroadcaster = broadcaster;
            if (broadcaster == null) {
                return;
            }
            toBroadcast = pending.values().toArray(toBroadcast);
        } finally {
            lock.unlock();
        }
        // Now use it to upload any pending transactions we have that are marked as not being seen by any peers yet.
        // Don't hold the wallet lock whilst doing this, so if the broadcaster accesses the wallet at some point there
        // is no inversion.
        for (Transaction tx : toBroadcast) {
            ConfidenceType confidenceType = getConfidence(tx).getConfidenceType();
            checkState(confidenceType == ConfidenceType.PENDING || confidenceType == ConfidenceType.IN_CONFLICT, ()
                    -> "Tx " + tx.getTxId() + ": expected PENDING or IN_CONFLICT, was " + confidenceType);
            // Re-broadcast even if it's marked as already seen for two reasons
            // 1) Old wallets may have transactions marked as broadcast by 1 peer when in reality the network
            //    never saw it, due to bugs.
            // 2) It can't really hurt.
            log2.info("New broadcaster so uploading waiting tx {}", tx.getTxId());
            broadcaster.broadcastTransaction(tx);
        }
    }
}
