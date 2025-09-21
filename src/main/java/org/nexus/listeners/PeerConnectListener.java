/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.listeners;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.nexus.core.Peer;
import org.nexus.core.PeerRegistry;
import org.nexus.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class PeerConnectListener implements ChannelFutureListener {

    private static final Logger LOGGER = Logger.getLogger(PeerConnectListener.class.getName());

    private final NexusNetworkConfiguration networkParams;
    private final Bootstrap bootstrap;
    private final EventLoop eventLoop;

    private final PeerRegistry peerRegistry;
    private final int maxRetries;
    private final long maxBackoffSeconds;
    private int attempt = 0;

    private final Random random = new Random();

    public PeerConnectListener(
            NexusNetworkConfiguration networkParams,
            Bootstrap bootstrap,
            EventLoop eventLoop,
            int maxRetries,
            long maxBackoffSeconds) {
        this.networkParams = networkParams;
        this.bootstrap = bootstrap;
        this.eventLoop = eventLoop;
        this.maxRetries = maxRetries;
        this.maxBackoffSeconds = maxBackoffSeconds;

        // add root network 
        this.peerRegistry = PeerRegistry.getInstance();
    }

    @Override
    public void operationComplete(ChannelFuture future) {
        if (future.isSuccess()) {
            LOGGER.info(" Connected to peer: " + peerId());
            peerRegistry.addActivePeer(new Peer(this.networkParams.getNetwork().id()), future.channel());
            LOGGER.info(" active peer size : " + peerRegistry.getActivePeers().size() + " pending peer size: " + peerRegistry.getPendingPeers().size() + " failed peer size: " + peerRegistry.getFailedPeers().size());

            // Reset attempt count on success
            attempt = 0;

        } else {
            attempt++;
            LOGGER.warning("Failed to connect to peer: " + peerId() + " (attempt " + attempt + ")");
//            peerRegistry.remove(peerId());

            if (attempt < maxRetries) {
                // exponential backoff with jitter
                long baseDelay = Math.min((1L << attempt), maxBackoffSeconds); // 2^n capped
                long jitter = random.nextInt((int) Math.max(1, baseDelay / 2)); // 0..baseDelay/2
                long delay = baseDelay + jitter;

                LOGGER.info("Retrying " + peerId() + " in " + delay + " seconds");

                eventLoop.schedule(() -> {
//                    peerRegistry.addPendingPeer(peerId());
                    bootstrap.connect(networkParams.getNetwork().id(), networkParams.getPort())
                            .addListener(this); // reuse same listener
                }, delay, TimeUnit.SECONDS);

            } else {
                LOGGER.warning("Max retries reached for peer: " + peerId());
//                peerRegistry.markPeerFailed(peer, channel);
            }
        }
    }

    private String peerId() {
        return this.networkParams.getNetwork().id() + ":" + networkParams.getPort();
    }
}
