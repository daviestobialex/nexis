/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import org.nexis.base.PeerAddress;
import io.netty.channel.Channel;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

/**
 *
 * @author daviestobialex
 */
public final class PeerRegistry {

    private final ConcurrentMap<PeerAddress, Channel> activePeers;
    private final ConcurrentMap<PeerAddress, Channel> pendingPeers;// oter states
    private final CopyOnWriteArraySet<PeerAddress> failedPeers;// will contain bad actors, banned actors and failed or disconnected actors
    // secondary map to improve O(1) search, scarificing memory for performance
    private final ConcurrentMap<String, PeerAddress> peerIndex;
    private final Set<Long> nonceIndex = ConcurrentHashMap.newKeySet();
    public static final int DEFAULT_MAX_CONNECTIONS = 10;
    private final AtomicInteger connectionCounter;

    /**
     * Singleton instance (lazy-loaded, thread-safe)
     */
    private static class Holder {

        private static final PeerRegistry INSTANCE = new PeerRegistry();
    }

    private PeerRegistry() {
        this.activePeers = new ConcurrentHashMap<>();
        this.pendingPeers = new ConcurrentHashMap<>();
        this.failedPeers = new CopyOnWriteArraySet<>();
        this.connectionCounter = new AtomicInteger(0);
        this.peerIndex = new ConcurrentHashMap<>();
    }

    public static PeerRegistry getInstance() {
        return Holder.INSTANCE;
    }

    public ConcurrentMap<PeerAddress, Channel> getActivePeers() {
        return activePeers;
    }

    public ConcurrentMap<PeerAddress, Channel> getPendingPeers() {
        return pendingPeers;
    }

    public CopyOnWriteArraySet<PeerAddress> getFailedPeers() {
        return failedPeers;
    }

    public Set<Long> getNonceIndex() {
        return nonceIndex;
    }

    /**
     *
     * @param peer
     * @param channel
     */
    public void addPendingPeer(PeerAddress peer, Channel channel) {
        pendingPeers.put(peer, channel);
        if (peer.getId() != null) {
            peerIndex.put(idKey(peer.getId()), peer);
        }
    }

    public void addActivePeer(PeerAddress peer, Channel channel) {
        int currentCount = connectionCounter.get();
        if (currentCount >= DEFAULT_MAX_CONNECTIONS) {
            pendingPeers.put(peer, channel);// max connections reached, add to pending peer
        }
        // attempt to increment counter atomically
        if (connectionCounter.compareAndSet(currentCount, currentCount + 1)) {
            // safely incremented, now add peer
            peerIndex.put(idKey(peer.getId()), peer);
            activePeers.put(peer, channel);
        }
    }

    public void removeActivePeer(PeerAddress peer) {
        activePeers.remove(peer);
        connectionCounter.decrementAndGet();
    }

    public void markPeerFailed(PeerAddress peer) {
        pendingPeers.remove(peer);
        failedPeers.add(peer);
    }

    public PeerAddress getNodeById(byte[] id) {
        if (id == null) {
            return null;
        }
        return peerIndex.get(idKey(id));
    }

    public Channel getActivePeerById(byte[] id) {
        PeerAddress nodeProperty = getNodeById(id);
        return activePeers.get(nodeProperty);
    }

    // wrap byte[] in Base64 or Hex string to avoid array equality issues
    private static String idKey(byte[] id) {
        return java.util.Base64.getEncoder().encodeToString(id);
    }

    public int getActivePeerSize() {
        return getActivePeers().size();
    }
}
