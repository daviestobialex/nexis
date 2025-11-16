/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import com.google.common.annotations.VisibleForTesting;
import io.netty.channel.ChannelFuture;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.PeerConnection;
import org.nexis.exceptions.RejectedTransactionException;
import org.nexis.internal.Threading;
import org.nexis.listeners.PreMessageReceivedEventListener;
import static org.nexis.utilities.Preconditions.checkState;
import org.nexis.wallet.Wallet;
import org.nexus.base.proto.NexusProtocol;

/**
 * Represents a single transaction broadcast that we are performing. A broadcast
 * occurs after a new transaction is created (typically by a {@link Wallet}) and
 * needs to be sent to the network. A broadcast can succeed or fail. A success
 * is defined as seeing the transaction be announced by peers via inv messages,
 * thus indicating their acceptance. A failure is defined as not reaching
 * acceptance within a timeout period, or getting an explicit reject message
 * from a peer indicating that the transaction was not acceptable.
 */
public class TransactionBroadcast {

    private static final Logger log = Logger.getLogger(TransactionBroadcast.class.getName());

    // This future completes when all broadcast messages were sent (to a buffer)
    private final CompletableFuture<TransactionBroadcast> sentFuture = new CompletableFuture<>();

    // This future completes when we have verified that more than numWaitingFor Peers have seen the broadcast
    private final CompletableFuture<TransactionBroadcast> seenFuture = new CompletableFuture<>();
//    private final PeerGroup peerGroup;
    private final Transaction tx;
    private int numWaitingFor;

    /**
     * Used for shuffling the peers before broadcast: unit tests can replace
     * this to make themselves deterministic.
     */
    @VisibleForTesting
    public static Random random = new Random();

    // Tracks which nodes sent us a reject message about this broadcast, if any. Useful for debugging.
    private final Map<Peer, RejectMessage> rejects = Collections.synchronizedMap(new HashMap<>());

    public TransactionBroadcast(Transaction tx) {
//        this.peerGroup = peerGroup;
        this.tx = tx;
    }

    // Only for mock broadcasts.
//    private TransactionBroadcast(Transaction tx) {
    ////        this.peerGroup = null;
//        this.tx = tx;
//    }

    /**
     * 
     * @return 
     */
    public Transaction transaction() {
        return tx;
    }

    private final PreMessageReceivedEventListener rejectionListener = new PreMessageReceivedEventListener() {
        @Override
        public NexusProtocol.NexusMessage onPreMessageReceived(Peer peer, NexusProtocol.NexusMessage m) {

            RejectMessage rejectMessage = RejectMessage.read(m.getRejectedMessage());

            if (tx.getTxId().equals(rejectMessage.getRejectedObjectHash())) {
                rejects.put(peer, rejectMessage);
                int size = rejects.size();
                long threshold = Math.round(numWaitingFor / 2.0);
                if (size > threshold) {
                    log.log(Level.WARNING, "Threshold for considering broadcast rejected has been reached ({}/{})",
                            new Object[]{size, threshold});
                    seenFuture.completeExceptionally(new RejectedTransactionException(tx, rejectMessage));

                }
            }

            return m;
        }
    };

    // TODO: Should this method be moved into the PeerGroup?
    /**
     * Broadcast this transaction to the proper calculated number of peers.
     * Returns a future that completes when the message has been "sent" to a set
     * of remote peers. The {@link TransactionBroadcast} itself is the returned
     * type/value for the future.
     * <p>
     * The complete broadcast process includes the following steps:
     * <ol>
     * <li>Wait until enough {@link org.bitcoinj.core.Peer}s are connected.</li>
     * <li>Broadcast the transaction to a determined number of
     * {@link org.bitcoinj.core.Peer}s</li>
     * <li>Wait for confirmation from a determined number of remote peers that
     * they have received the broadcast</li>
     * <li>Mark {@link TransactionBroadcast#awaitRelayed()} ()} ("seen future")
     * as complete</li>
     * </ol>
     * The future returned from this method completes when Step 2 is completed.
     * <p>
     * It should further be noted that "broadcast" in this class means that
     * {@link org.bitcoinj.net.MessageWriteTarget#writeBytes} has completed
     * successfully which means the message has been sent to the "OS network
     * buffer" -- see {@link org.bitcoinj.net.MessageWriteTarget#writeBytes} or
     * its implementation.
     *
     */
    public void broadcastOnly() {

        final Context context = Context.get();
        PeerRegistry.getInstance().getActivePeers().forEach(peer -> {
            Context.propagate(context);
            // Prepare to send the transaction by adding a listener that'll be called when confidence changes.
            tx.getConfidence().addEventListener(new ConfidenceChange());
            broadcastOne(peer);
        });

    }

    /**
     * Broadcast the transaction and wait for confirmation that the transaction
     * has been received by the appropriate number of Peers before completing.
     *
     * @return A future that completes when the message has been relayed by the
     * appropriate number of remote peers
     */
//    public CompletableFuture<TransactionBroadcast> broadcastAndAwaitRelay() {
//        return broadcastOnly()
//                .thenCompose(broadcast -> this.seenFuture);
//    }
    /**
     * Wait for confirmation the transaction has been relayed.
     *
     * @return A future that completes when the message has been relayed by the
     * appropriate number of remote peers
     */
    public CompletableFuture<TransactionBroadcast> awaitRelayed() {
        return seenFuture;
    }

    /**
     * Wait for confirmation the transaction has been sent to a remote peer. (Or
     * at least buffered to be sent to a peer.)
     *
     * @return A future that completes when the message has been relayed by the
     * appropriate number of remote peers
     */
    public CompletableFuture<TransactionBroadcast> awaitSent() {
        return sentFuture;
    }

    private void broadcastOne(PeerConnection peer) {
        peer.channel().writeAndFlush(tx.toProto());
    }

    private int numSeemPeers;
    private boolean mined;

    private class ConfidenceChange implements TransactionConfidence.Listener {

        @Override
        public void onConfidenceChanged(TransactionConfidence conf, ChangeReason reason) {
            // The number of peers that announced this tx has gone up.
            int numSeenPeers = conf.numBroadcastPeers() + rejects.size();
//            boolean mined = tx.getAppearsInHashes() != null;
            log.log(Level.INFO, "broadcastTransaction: {0}:  TX {1} seen by {2} peers{3}", new Object[]{reason, tx.getTxId(),
                numSeenPeers, mined ? " and mined" : ""});

            // Progress callback on the requested thread.
            invokeAndRecord(numSeenPeers, mined);

            if (numSeenPeers >= numWaitingFor || mined) {
                // We've seen the min required number of peers announce the transaction, or it was included
                // in a block. Normally we'd expect to see it fully propagate before it gets mined, but
                // it can be that a block is solved very soon after broadcast, and it's also possible that
                // due to version skew and changes in the relay rules our transaction is not going to
                // fully propagate yet can get mined anyway.
                //
                // Note that we can't wait for the current number of connected peers right now because we
                // could have added more peers after the broadcast took place, which means they won't
                // have seen the transaction. In future when peers sync up their memory pools after they
                // connect we could come back and change this.
                //
                // We're done! It's important that the PeerGroup lock is not held (by this thread) at this
                // point to avoid triggering inversions when the Future completes.
                log.log(Level.INFO, "broadcastTransaction: {0} complete", tx.getTxId());
                conf.removeEventListener(this);
                seenFuture.complete(TransactionBroadcast.this);  // RE-ENTRANCY POINT
            }
        }
    }

    private void invokeAndRecord(int numSeenPeers, boolean mined) {
        synchronized (this) {
            this.numSeemPeers = numSeenPeers;
            this.mined = mined;
        }
        invokeProgressCallback(numSeenPeers, mined);
    }

    private void invokeProgressCallback(int numSeenPeers, boolean mined) {
        final ProgressCallback callback;
        Executor executor;
        synchronized (this) {
            callback = this.callback;
            executor = this.progressCallbackExecutor;
        }
        if (callback != null) {
            final double progress = Math.min(1.0, mined ? 1.0 : numSeenPeers / (double) numWaitingFor);
            checkState(progress >= 0.0 && progress <= 1.0, ()
                    -> "" + progress);
            try {
                if (executor == null) {
                    callback.onBroadcastProgress(progress);
                } else {
                    executor.execute(() -> callback.onBroadcastProgress(progress));
                }
            } catch (Throwable e) {
                log.log(Level.SEVERE, "Exception during progress callback {0}", e);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /** An interface for receiving progress information on the propagation of the tx, from 0.0 to 1.0 */
    public interface ProgressCallback {

        /**
         * onBroadcastProgress will be invoked on the provided executor when the
         * progress of the transaction broadcast has changed, because the
         * transaction has been announced by another peer or because the
         * transaction was found inside a mined block (in this case progress
         * will go to 1.0 immediately). Any exceptions thrown by this callback
         * will be logged and ignored.
         */
        void onBroadcastProgress(double progress);
    }

//    @Nullable
    private ProgressCallback callback;
//    @Nullable 
    private Executor progressCallbackExecutor;

    /**
     * Sets the given callback for receiving progress values, which will run on
     * the user thread.See {@link Threading} for details. If the broadcast has
     * already started then the callback will be invoked immediately with the
     * current progress.
     *
     * @param callback
     */
    public void setProgressCallback(ProgressCallback callback) {
        setProgressCallback(callback, Threading.USER_THREAD);
    }

    /**
     * Sets the given callback for receiving progress values, which will run on
     * the given executor.If the executor is null then the callback will run on
     * a network thread and may be invoked multiple times in parallel.You
     * probably want to provide your UI thread or Threading.USER_THREAD for the
     * second parameter. If the broadcast has already started then the callback
     * will be invoked immediately with the current progress.
     *
     * @param callback
     * @param executor
     */
    public void setProgressCallback(ProgressCallback callback,
            //            @Nullable
            Executor executor) {
        boolean shouldInvoke;
        int num;
        boolean mined;
        synchronized (this) {
            this.callback = callback;
            this.progressCallbackExecutor = executor;
            num = this.numSeemPeers;
            mined = this.mined;
            shouldInvoke = numWaitingFor > 0;
        }
        if (shouldInvoke) {
            invokeProgressCallback(num, mined);
        }
    }
}
