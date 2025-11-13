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
package org.nexis.store;

import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.nexis.base.Sha256Hash;
import org.nexis.core.StoredBlock;
import org.nexis.exceptions.BlockStoreException;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A BlockStore implementation that holds all blocks in memory.
 * 
 * <p>
 * This is suitable for testing, lightweight nodes, or temporary block storage.
 * For production use with long-running nodes, consider a disk-based implementation.
 * </p>
 *
 * <p>
 * Thread-safe via ReentrantReadWriteLock for chain head and ConcurrentHashMap
 * for block storage.
 * </p>
 *
 * @author daviestobialex
 */
public class MemoryBlockStore implements BlockStore {

    // Map of block hash -> StoredBlock
    private final ConcurrentHashMap<Sha256Hash, StoredBlock> blocks = new ConcurrentHashMap<>();

    // Lock protecting chain head updates
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private StoredBlock chainHead;

    /**
     * Constructs an empty MemoryBlockStore with no chain head.
     */
    public MemoryBlockStore() {
        this.chainHead = null;
    }

    /**
     * Constructs a MemoryBlockStore with an initial chain head (e.g., genesis).
     *
     * @param genesisBlock the genesis block to start with
     */
    public MemoryBlockStore(StoredBlock genesisBlock) {
        this.chainHead = genesisBlock;
        this.blocks.put(genesisBlock.getHash(), genesisBlock);
    }

    @Override
    public void put(StoredBlock block) throws BlockStoreException {
        if (block == null) {
            throw new BlockStoreException("Cannot store null block");
        }
        blocks.put(block.getHash(), block);
        // If this block has more chain work than the current chain head, update the chain head.
        lock.readLock().lock();
        try {
            if (chainHead == null) {
                // upgrade to write lock
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    this.chainHead = block;
                } finally {
                    lock.writeLock().unlock();
                }
            } else if (block.getChainWork().compareTo(chainHead.getChainWork()) > 0) {
                // new best chain by work
                lock.readLock().unlock();
                lock.writeLock().lock();
                try {
                    this.chainHead = block;
                } finally {
                    lock.writeLock().unlock();
                }
            } else {
                // keep current chain head
                lock.readLock().unlock();
            }
        } catch (RuntimeException rex) {
            // ensure lock is released in case of exception
            if (lock.getReadHoldCount() > 0) {
                lock.readLock().unlock();
            }
            throw rex;
        }
    }

    @Override
    public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
        if (hash == null) {
            throw new BlockStoreException("Hash cannot be null");
        }
        StoredBlock block = blocks.get(hash);
        if (block == null) {
            throw new BlockStoreException("Block not found: " + hash);
        }
        return block;
    }

    /**
     * Returns the block if it exists, or null if not found (does not throw).
     */
    public StoredBlock getOrNull(Sha256Hash hash) {
        return blocks.get(hash);
    }

    @Override
    public StoredBlock getChainHead() throws BlockStoreException {
        lock.readLock().lock();
        try {
            if (chainHead == null) {
                throw new BlockStoreException("No chain head set");
            }
            return chainHead;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
        if (chainHead == null) {
            throw new BlockStoreException("Chain head cannot be null");
        }
        // Ensure block is in the store
        if (!blocks.containsKey(chainHead.getHash())) {
            blocks.put(chainHead.getHash(), chainHead);
        }
        lock.writeLock().lock();
        try {
            this.chainHead = chainHead;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void close() throws BlockStoreException {
        // No resources to release for in-memory store
    }

    /**
     * Returns the number of blocks stored.
     */
    public int getBlockCount() {
        return blocks.size();
    }

    /**
     * Clears all blocks from the store.
     */
    public void clear() {
        blocks.clear();
        lock.writeLock().lock();
        try {
            chainHead = null;
        } finally {
            lock.writeLock().unlock();
        }
    }
}
