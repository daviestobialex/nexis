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

import java.io.Closeable;
import java.io.IOException;
import java.math.BigInteger;

/**
 * {@code Storage} defines a minimal contract for storing and retrieving
 * manifest blobs (or other binary data) addressed by a unique content
 * identifier (CID).
 *
 * <p>
 * The interface is intentionally small and generic, allowing different storage
 * backends (e.g., {@link ManifestStore}, in-memory store, remote store) to be
 * plugged in without changing client code.</p>
 *
 * <p>
 * Typical usage pattern:</p>
 * <pre>{@code
 * Storage store = new ManifestStore(idxFile, datFile, 1024);
 *
 * BigInteger cid = ...;   // some unique identifier
 * byte[] blob = ...;      // compressed manifest bytes
 *
 * store.put(cid, blob);   // persist the manifest
 * byte[] loaded = store.get(cid); // retrieve it later
 *
 * store.close();          // always close to release resources
 * }</pre>
 *
 * <p>
 * <b>Thread Safety:</b> Implementations are not required to be thread-safe
 * unless explicitly documented. Callers should apply external synchronization
 * if multiple threads access the same store instance.</p>
 *
 * @author daviestobialex
 */
public interface Storage extends Closeable {

    /**
     * Stores a new blob under the given CID.
     *
     * <p>
     * Implementations may persist the data to disk, memory, or a remote service
     * depending on the concrete class. If a CID already exists, behavior is
     * implementation-specific (e.g., overwrite or reject).</p>
     *
     * @param cid the unique content identifier (CID) for the blob
     * @param compressedData the binary data to store, typically already
     * compressed
     * @throws IOException if the storage operation fails
     */
    void put(BigInteger cid, byte[] compressedData) throws IOException;

    /**
     * Retrieves a blob previously stored under the given CID.
     *
     * @param cid the unique content identifier (CID) to look up
     * @return the stored binary data, or {@code null} if no entry exists for
     * the CID
     * @throws IOException if the retrieval operation fails
     */
    byte[] get(BigInteger cid) throws IOException;
}
