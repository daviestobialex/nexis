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

import java.math.BigInteger;
import java.util.Objects;
import org.nexis.base.Sha256Hash;

/**
 * Wraps a {@link Block} with additional metadata used by the block chain,
 * such as chain work and height.
 *
 * <p>
 * Instances are immutable and thread-safe. This design is inspired by bitcoinj.
 * </p>
 *
 * @author daviestobialex
 */
public class StoredBlock {

    private final Block header;
    private final BigInteger chainWork;
    private final int height;

    /**
     * Constructs a StoredBlock wrapping a Block header with chain metadata.
     *
     * @param header the block header
     * @param chainWork accumulated work up to and including this block
     * @param height the block height (0 for genesis)
     */
    public StoredBlock(Block header, BigInteger chainWork, int height) {
        this.header = Objects.requireNonNull(header, "header cannot be null");
        this.chainWork = Objects.requireNonNull(chainWork, "chainWork cannot be null");
        this.height = height;
    }

    /**
     * Returns the block header.
     */
    public Block getHeader() {
        return header;
    }

    /**
     * Returns the accumulated chain work up to and including this block.
     * 
     */
    public BigInteger getChainWork() {
        return chainWork;
    }

    /**
     * Returns the block height (0 for genesis).
     */
    public int getHeight() {
        return height;
    }

    /**
     * Returns the hash of this block (shorthand for getHeader().getHash()).
     */
    public Sha256Hash getHash() {
        return header.getHash();
    }

    /**
     * Returns the hash of the previous block.
     */
    public Sha256Hash getPrevBlockHash() {
        return header.getPrevBlockHash();
    }

    /**
     * Returns a human-readable representation.
     */
    @Override
    public String toString() {
        return String.format("StoredBlock{hash=%s, height=%d, chainWork=%s}",
                getHash(), height, chainWork);
    }

    /**
     * Two StoredBlocks are equal if their hashes match.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof StoredBlock)) return false;
        StoredBlock other = (StoredBlock) obj;
        return this.getHash().equals(other.getHash());
    }

    /**
     * Hash code based on the block hash.
     */
    @Override
    public int hashCode() {
        return getHash().hashCode();
    }
}
