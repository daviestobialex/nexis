/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.core;

import io.netty.channel.Channel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import org.nexus.base.PublicNodeProperties;

/**
 *
 * @author daviestobialex
 */
public final class PeerRegistry {

    // === Peer sets ===
    private final ConcurrentMap<PublicNodeProperties, Channel> activePeers;
    private final ConcurrentMap<PublicNodeProperties, Channel> pendingPeers;// oter states
    private final CopyOnWriteArraySet<PublicNodeProperties> failedPeers;
    // secondary map to improve O(1) search, scarificing memory for performance
    private final ConcurrentMap<String, PublicNodeProperties> peerIndex = new ConcurrentHashMap<>();
    public static final int DEFAULT_MAX_CONNECTIONS = 10;
    private final AtomicInteger connectionCounter;

    // === Singleton instance (lazy-loaded, thread-safe) ===
    private static class Holder {

        private static final PeerRegistry INSTANCE = new PeerRegistry();
    }

    // === Private constructor prevents outside instantiation ===
    private PeerRegistry() {
        this.activePeers = new ConcurrentHashMap<>();
        this.pendingPeers = new ConcurrentHashMap<>();
        this.failedPeers = new CopyOnWriteArraySet<>();
        this.connectionCounter = new AtomicInteger(0);
    }

    // === Access point ===
    public static PeerRegistry getInstance() {
        return Holder.INSTANCE;
    }

    // === Getters (you can add add/remove helpers too) ===
    public ConcurrentMap<PublicNodeProperties, Channel> getActivePeers() {
        return activePeers;
    }

    public ConcurrentMap<PublicNodeProperties, Channel> getPendingPeers() {
        return pendingPeers;
    }

    public CopyOnWriteArraySet<PublicNodeProperties> getFailedPeers() {
        return failedPeers;
    }

    // === Utility methods ===
    public void addActivePeer(PublicNodeProperties peer, Channel channel) {
        int currentCount = connectionCounter.get();
        if (currentCount >= DEFAULT_MAX_CONNECTIONS) {
            pendingPeers.put(peer, channel);// max connections reached, add to pending peer
        }
        // attempt to increment counter atomically
        if (connectionCounter.compareAndSet(currentCount, currentCount + 1)) {
            // safely incremented, now add peer
            if (peer.getId() != null) {
                peerIndex.put(idKey(peer.getId()), peer);
                activePeers.put(peer, channel);
            } else {
                pendingPeers.put(peer, channel);
            }
        }
    }

    public void removeActivePeer(PublicNodeProperties peer) {
        activePeers.remove(peer);
        connectionCounter.decrementAndGet();
    }

    public void addPendingPeer(PublicNodeProperties peer, Channel channel) {
        pendingPeers.put(peer, channel);
    }

    public void markPeerFailed(PublicNodeProperties peer, Channel channel) {
        pendingPeers.remove(peer);
        failedPeers.add(peer);
    }

    public PublicNodeProperties getNodeById(byte[] id) {
        return peerIndex.get(idKey(id));
    }

    public Channel getActivePeerById(byte[] id) {
        PublicNodeProperties nodeProperty = getNodeById(id);
        return activePeers.get(nodeProperty);
    }

    // wrap byte[] in Base64 or Hex string to avoid array equality issues
    private static String idKey(byte[] id) {
        return java.util.Base64.getEncoder().encodeToString(id);
    }
}
