/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.base.listeners;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.EventLoop;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.nexus.base.networks.NexusNetworkParams;

/**
 *
 * @author daviestobialex
 */
public class PeerConnectListener implements ChannelFutureListener {

    private static final Logger LOGGER = Logger.getLogger(PeerConnectListener.class.getName());

    private final NexusNetworkParams networkParams;
    private final Bootstrap bootstrap;
    private final EventLoop eventLoop;

    private final CopyOnWriteArraySet<Channel> activePeers;
    private final CopyOnWriteArraySet<String> pendingPeers;
    private final CopyOnWriteArraySet<String> failedPeers;

    private final int maxRetries;
    private final long maxBackoffSeconds;
    private int attempt = 0;

    private final Random random = new Random();

    public PeerConnectListener(
            NexusNetworkParams networkParams,
            Bootstrap bootstrap,
            EventLoop eventLoop,
            CopyOnWriteArraySet<Channel> activePeers,
            int maxRetries,
            long maxBackoffSeconds) {
        this.networkParams = networkParams;
        this.bootstrap = bootstrap;
        this.eventLoop = eventLoop;
        this.activePeers = activePeers;
        this.pendingPeers = new CopyOnWriteArraySet<>();
        this.failedPeers = new CopyOnWriteArraySet<>();
        this.maxRetries = maxRetries;
        this.maxBackoffSeconds = maxBackoffSeconds;

        pendingPeers.add(peerId());
    }

    @Override
    public void operationComplete(ChannelFuture future) {
        if (future.isSuccess()) {
            LOGGER.info(" Connected to peer: " + peerId());
            activePeers.add(future.channel());
            pendingPeers.remove(peerId());
            LOGGER.info(" active peer size : " + activePeers.size() + " pending peer size: " + pendingPeers.size() + " failed peer size: " + failedPeers.size());
            
            // Reset attempt count on success
            attempt = 0;

        } else {
            attempt++;
            LOGGER.warning("Failed to connect to peer: " + peerId() + " (attempt " + attempt + ")");
            pendingPeers.remove(peerId());

            if (attempt < maxRetries) {
                // exponential backoff with jitter
                long baseDelay = Math.min((1L << attempt), maxBackoffSeconds); // 2^n capped
                long jitter = random.nextInt((int) Math.max(1, baseDelay / 2)); // 0..baseDelay/2
                long delay = baseDelay + jitter;

                LOGGER.info("Retrying " + peerId() + " in " + delay + " seconds");

                eventLoop.schedule(() -> {
                    pendingPeers.add(peerId());
                    bootstrap.connect(networkParams.getNetwork().id(), networkParams.getPort())
                            .addListener(this); // reuse same listener
                }, delay, TimeUnit.SECONDS);

            } else {
                LOGGER.warning("Max retries reached for peer: " + peerId());
                failedPeers.add(peerId());
            }
        }
    }

    private String peerId() {
        return this.networkParams.getNetwork().id() + ":" + networkParams.getPort();
    }
}
