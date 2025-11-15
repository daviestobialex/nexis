/*
 * Tracks peer spending limits across epochs.
 */
package org.nexis.core;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Tracks how much each peer has spent in the current epoch.
 * Used to enforce the 50% spending limit on peer stake.
 * 
 * Epoch boundaries are defined by block height (e.g., every 1000 blocks = 1 epoch).
 * At epoch boundaries, spending limits are reset.
 * 
 * Thread-safe with ReentrantReadWriteLock.
 * 
 * @author daviestobialex
 */
public class SpendingLimitTracker {

    /**
     * Map from peer ID (hex string) to total amount spent in current epoch.
     */
    private final Map<String, Long> spentInEpoch = new HashMap<>();

    /**
     * Current epoch number (derived from block height).
     */
    private int currentEpoch = 0;

    /**
     * Lock for thread-safe access.
     */
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    /**
     * Block height interval for epoch boundaries.
     * E.g., 1000 = reset limits every 1000 blocks.
     */
    private final int epochBlockHeight;

    /**
     * Creates a tracker with default epoch interval (1000 blocks).
     */
    public SpendingLimitTracker() {
        this.epochBlockHeight = 1000;
    }

    /**
     * Creates a tracker with specified epoch interval.
     * 
     * @param epochBlockHeight blocks between epoch resets
     */
    public SpendingLimitTracker(int epochBlockHeight) {
        this.epochBlockHeight = epochBlockHeight;
    }

    /**
     * Records that a peer spent a certain amount.
     * 
     * @param peerId the peer (20 bytes)
     * @param amountSpent amount in satoshis
     */
    public void recordSpending(byte[] peerId, long amountSpent) {
        lock.writeLock().lock();
        try {
            String peerHex = toHex(peerId);
            long current = spentInEpoch.getOrDefault(peerHex, 0L);
            spentInEpoch.put(peerHex, current + amountSpent);
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets total amount a peer has spent in current epoch.
     * 
     * @param peerId the peer ID
     * @return amount spent in satoshis
     */
    public long getTotalSpentInEpoch(byte[] peerId) {
        lock.readLock().lock();
        try {
            String peerHex = toHex(peerId);
            return spentInEpoch.getOrDefault(peerHex, 0L);
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Updates current epoch based on block height.
     * If epoch has changed, resets all spending limits.
     * 
     * Call this when processing a new block.
     * 
     * @param blockHeight current block height
     */
    public void updateEpoch(int blockHeight) {
        lock.writeLock().lock();
        try {
            int newEpoch = blockHeight / epochBlockHeight;
            if (newEpoch != currentEpoch) {
                currentEpoch = newEpoch;
                spentInEpoch.clear();  // Reset limits for new epoch
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Manually resets epoch and clears all spending records.
     * 
     * @param blockHeight current block height (for logging/future use)
     */
    public void resetEpoch(int blockHeight) {
        lock.writeLock().lock();
        try {
            currentEpoch = blockHeight / epochBlockHeight;
            spentInEpoch.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * Gets current epoch number.
     */
    public int getCurrentEpoch() {
        lock.readLock().lock();
        try {
            return currentEpoch;
        } finally {
            lock.readLock().unlock();
        }
    }

    /**
     * Clears all spending records (for testing or reorg handling).
     */
    public void clear() {
        lock.writeLock().lock();
        try {
            spentInEpoch.clear();
            currentEpoch = 0;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String toString() {
        lock.readLock().lock();
        try {
            return "SpendingLimitTracker(epoch=" + currentEpoch + ", tracked=" + spentInEpoch.size() + " peers)";
        } finally {
            lock.readLock().unlock();
        }
    }

    // Helper

    private static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
