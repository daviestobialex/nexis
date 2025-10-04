/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.listeners;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import java.net.InetSocketAddress;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.nexis.core.Peer;
import org.nexis.core.PeerRegistry;

/**
 *
 * @author daviestobialex
 */
public class PeerConnectListener implements ChannelFutureListener {

    private static final Logger LOGGER = Logger.getLogger(PeerConnectListener.class.getName());

    private final InetSocketAddress socketAddress;
    private final Bootstrap bootstrap;
    private final EventLoop eventLoop;
    private final Peer peer;

    private final PeerRegistry peerRegistry;
    private final int maxRetries;
    private final long maxBackoffSeconds;
    private int attempt = 0;

    private final Random random = new Random();

    public PeerConnectListener(
            InetSocketAddress socketAddress,
            Bootstrap bootstrap,
            EventLoop eventLoop,
            int maxRetries,
            long maxBackoffSeconds,
            Peer peer) {
        this.socketAddress = socketAddress;
        this.bootstrap = bootstrap;
        this.eventLoop = eventLoop;
        this.maxRetries = maxRetries;
        this.maxBackoffSeconds = maxBackoffSeconds;

        // add root network 
        this.peerRegistry = PeerRegistry.getInstance();
        this.peer = peer;
    }

    @Override
    public void operationComplete(ChannelFuture future) {

        if (future.isSuccess()) {
            LOGGER.info(" Connected to peer: " + peerId());
            peerRegistry.addPendingPeer(peer, future.channel());
            // Reset attempt count on success
            attempt = 0;
        } else {
            attempt++;
            LOGGER.warning("Failed to connect to peer: " + peerId() + " (attempt " + attempt + ")");

            if (attempt < maxRetries) {
                // exponential backoff with jitter
                long baseDelay = Math.min((1L << attempt), maxBackoffSeconds); // 2^n capped
                long jitter = random.nextInt((int) Math.max(1, baseDelay / 2)); // 0..baseDelay/2
                long delay = baseDelay + jitter;

                LOGGER.info("Retrying " + peerId() + " in " + delay + " seconds");

                eventLoop.schedule(() -> {
                    bootstrap.connect(socketAddress)
                            .addListener(this); // reuse same listener
                }, delay, TimeUnit.SECONDS);

            } else {
                LOGGER.warning("Max retries reached for peer: " + peerId());
                peerRegistry.markPeerFailed(peer);
            }
        }
        LOGGER.info(" active peer size : " + peerRegistry.getActivePeerSize()
                + " pending peer size: " + peerRegistry.getPendingPeerSize()
                + " failed peer size: " + peerRegistry.getFailedPeerSize()
                + " peer index size : " + peerRegistry.getPeerIndexSize());

    }

    private String peerId() {
        return this.socketAddress.getHostName() + ":" + socketAddress.getPort();
    }
}
