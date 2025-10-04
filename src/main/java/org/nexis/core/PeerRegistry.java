/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.core;

import org.nexis.base.PeerAddress;
import io.netty.channel.Channel;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.nexis.base.PeerConnection;

/**
 * PeerRegistry maintains the active, pending, and failed peers for a Nexis
 * node.
 *
 * <p>
 * This class provides:
 * <ul>
 * <li>Thread-safe, non-blocking data structures for peer management</li>
 * <li>FIFO ordering using {@link ConcurrentLinkedQueue} for connection
 * prioritization</li>
 * <li>O(1) peer lookup by node ID using {@link ConcurrentHashMap}</li>
 * <li>Memory safety via configurable connection limits and automatic
 * eviction</li>
 * <li>Event-driven notifications when peers become active</li>
 * </ul>
 *
 * <p>
 * The registry uses a singleton pattern to ensure a single shared instance
 * across the node runtime.
 *
 * @author daviestobialex
 */
public final class PeerRegistry {

    /**
     * Active peers currently participating in the network (FIFO order).
     */
    private final ConcurrentLinkedQueue<PeerConnection> activePeers;

    /**
     * Pending peers waiting to become active (due to max connection limits or
     * verification delay).
     */
    private final ConcurrentLinkedQueue<PeerConnection> pendingPeers;//TODO: grows indefinitely look at

    /**
     * Failed, banned, or disconnected peers to prevent re-connection attempts.
     */
    private final CopyOnWriteArraySet<PeerAddress> failedPeers;//TODO: grows indefinitely look at

    /**
     * Fast lookup index for peer connections by node ID (base64-encoded).
     */
    private final ConcurrentHashMap<String, PeerConnection> peerIndex;

    /**
     * Nonce index used for replay protection or unique connection identifiers.
     */
    private final Set<Long> nonceIndex = ConcurrentHashMap.newKeySet();

    /**
     * Maximum number of active peer connections allowed.
     */
    public static final int DEFAULT_MAX_CONNECTIONS = 500;

    /**
     * Counter tracking the number of active connections.
     */
    private final AtomicInteger connectionCounter;

    /**
     * Listeners triggered when a new peer becomes active.
     */
    private final List<Consumer<Channel>> activePeerListeners = new CopyOnWriteArrayList<>();

    /**
     * Lazy-loaded singleton holder pattern (thread-safe without
     * synchronization).
     */
    private static class Holder {

        private static final PeerRegistry INSTANCE = new PeerRegistry();
    }

    private PeerRegistry() {
        this.activePeers = new ConcurrentLinkedQueue<>();
        this.pendingPeers = new ConcurrentLinkedQueue<>();
        this.failedPeers = new CopyOnWriteArraySet<>();
        this.connectionCounter = new AtomicInteger(0);
        this.peerIndex = new ConcurrentHashMap<>();
    }

    public static PeerRegistry getInstance() {
        return Holder.INSTANCE;
    }

    public Iterable<PeerConnection> getActivePeers() {
        return activePeers;
    }

    public Iterable<PeerConnection> getPendingPeers() {
        return pendingPeers;
    }

    public Iterable<PeerAddress> getFailedPeers() {
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
        PeerConnection peerConnection = new PeerConnection(peer, channel);

        notifyPeerListeners(channel);

        if (peer.getId() != null) {
            pendingPeers.add(peerConnection);
            peerIndex.put(idKey(peer.getId()), peerConnection);
        }
    }

    public void addActivePeer(PeerAddress peer, Channel channel) {
        String nodeId = idKey(peer.getId());
        System.out.println("NODE ID KEY TO PEER " + nodeId);
        PeerConnection peerConnection = new PeerConnection(peer, channel);

        // Avoid duplicates
        if (peerIndex.putIfAbsent(nodeId, peerConnection) == null) {
            activePeers.add(peerConnection);
            int current = connectionCounter.incrementAndGet();

            // Evict oldest if full
            if (current > DEFAULT_MAX_CONNECTIONS) {
                evictOldestPeer();
            }
        }
    }

    /**
     * Removes the oldest peer (FIFO) from both the queue and the index.
     */
    private void evictOldestPeer() {
        PeerConnection oldest = activePeers.poll();
        if (oldest != null) {
            peerIndex.remove(idKey(oldest.peer().getId()));
            connectionCounter.decrementAndGet();
            pendingPeers.add(oldest);// max connections reached, add to pending peer
        }
    }

    // Register a listener
    public void onActivePeerConnected(Consumer<Channel> listener) {
        activePeerListeners.add(listener);
    }

    private void notifyPeerListeners(Channel channel) {
        for (Consumer<Channel> listener : activePeerListeners) {
            listener.accept(channel);
        }
    }

    public void removeActivePeer(PeerAddress peer) {
        String nodeId = idKey(peer.getId());
        PeerConnection removed = peerIndex.remove(nodeId);
        if (removed != null) {
            activePeers.remove(removed);
            connectionCounter.decrementAndGet();
        }
    }

    public void removePendingPeer(PeerAddress peer) {
        String nodeId = idKey(peer.getId());
        PeerConnection removed = peerIndex.remove(nodeId);
        if (removed != null) {
            pendingPeers.remove(removed);
        }
    }

    public void markPeerFailed(PeerAddress peer) {
        String nodeId = idKey(peer.getId());
        PeerConnection removed = peerIndex.remove(nodeId);
        if (removed != null) {
            pendingPeers.remove(removed);
            failedPeers.add(peer);
        }
    }

    /**
     * this is an O(n) search as looking by ost id means it s only n t pending
     * peers list and you do not want to connect twice
     *
     * @param id
     * @return
     */
    public PeerConnection getPeerByAddress(String id) {
        return pendingPeers.stream()
                .filter(peer -> id.equals(peer.peer().id()))
                .findFirst().orElse(null);
    }

    public PeerAddress getPeerById(byte[] id) {

        PeerConnection peerConnection = peerIndex.get(idKey(id));
        if (peerConnection != null) {
            return peerConnection.peer();
        }

        return null;
    }

    public Channel getActivePeerById(byte[] id) {
        return peerIndex.get(idKey(id)).channel();
    }

    // wrap byte[] in Base64 or Hex string to avoid array equality issues
    private static String idKey(byte[] id) {
        return java.util.Base64.getEncoder().encodeToString(id);
    }

    public int getActivePeerCount() {
        return connectionCounter.get();
    }

    public int getActivePeerSize() {
        return activePeers.size();
    }

    public int getPendingPeerSize() {
        return pendingPeers.size();
    }

    public int getPeerIndexSize() {
        return peerIndex.size();
    }

    public int getFailedPeerSize() {
        return failedPeers.size();
    }
}
