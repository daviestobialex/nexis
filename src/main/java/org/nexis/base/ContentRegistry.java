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
package org.nexis.base;

/**
 * ContentRegistry provides an IPFS-like interface for managing content
 * addressed by CIDs (Content Identifiers).
 * <p>
 * Implementations are responsible for:
 * <ul>
 * <li>Tracking whether content is stored locally.</li>
 * <li>Retrieving locally available content by CID.</li>
 * <li>Requesting missing content from peers in the network.</li>
 * </ul>
 *
 * A {@code CID} (Content Identifier) is typically the cryptographic hash of the
 * underlying data, ensuring both uniqueness and integrity.
 *
 * This registry acts as the main entry point for higher-level components (e.g.
 * manifests, blocks, transactions) to interact with the distributed content
 * storage layer.
 *
 * @author daviestobialex
 */
public interface ContentRegistry {

    /**
     * Checks whether the specified content is already stored locally.
     *
     * @param cid the content identifier (usually a SHA-256 hash of the data)
     * @return {@code true} if the content is available locally, {@code false}
     * otherwise
     */
    boolean hasContent(String cid);

    /**
     * Retrieves the locally stored content for the given CID.
     *
     * @param cid the content identifier
     * @return the raw content bytes if present, or {@code null} if the content
     * is not available locally
     */
    byte[] getContent(String cid);

    /**
     * Issues a request to peers for the specified CID.
     * <p>
     * Implementations should enqueue a "GetContent" message to connected peers
     * that are known to provide or advertise this CID. If successful, the
     * content will eventually be retrieved, validated against the CID, and
     * stored locally.
     *
     * @param cid the content identifier to fetch from peers
     */
    void requestContent(String cid);
}
