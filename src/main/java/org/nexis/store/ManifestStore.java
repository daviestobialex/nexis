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
import java.io.File;
import java.io.IOException;
import java.math.BigInteger;

/**
 * {@code ManifestStore} provides persistent storage and retrieval of manifest
 * blobs identified by their Content Identifier (CID).
 *
 * <p>
 * This class coordinates three layers of storage:
 * <ul>
 * <li><b>Index File ({@link ManifestIndex})</b>: Maps a CID to its data
 * location in the manifest data file. Supports fast lookup using a sorted
 * index.</li>
 * <li><b>Data File ({@link ManifestDataFile})</b>: Stores raw compressed blobs
 * sequentially on disk.</li>
 * <li><b>In-Memory Cache ({@link LruCache})</b>: Keeps the most recently used
 * manifests in memory for fast repeated access.</li>
 * </ul>
 *
 * <p>
 * The workflow is:
 * <ol>
 * <li>On {@link #put(BigInteger, byte[])}, the blob is appended to the data
 * file, its offset and size are recorded in the index, and optionally
 * cached.</li>
 * <li>On {@link #get(BigInteger)}, the cache is checked first; if not found,
 * the index is consulted, the data is loaded from disk, and then cached.</li>
 * <li>{@link #close()} ensures all underlying file handles are properly
 * closed.</li>
 * </ol>
 *
 * <p>
 * This design supports:
 * <ul>
 * <li>O(log n) lookup in the index for CID → offset mapping</li>
 * <li>O(1) random access into the data file using stored offsets</li>
 * <li>Efficient memory usage via LRU caching</li>
 * </ul>
 *
 * <p>
 * <b>Thread Safety:</b> This class is not inherently thread-safe. External
 * synchronization is required if multiple threads will use a shared instance.
 *
 * @author daviestobialex
 */
public class ManifestStore implements Storage {

    /**
     * Index mapping CID → (offset, length) inside the data file.
     */
    private final ManifestIndex index;

    /**
     * Data file storing raw compressed manifest blobs.
     */
    private final ManifestDataFile dataFile;

    /**
     * In-memory least-recently-used cache for fast access.
     */
    private final LruCache<BigInteger, byte[]> cache;

    /**
     * Creates a new {@code ManifestStore} with persistent index and data
     * storage.
     *
     * @param idxFile the index file used to store CID → (offset, length)
     * mappings
     * @param datFile the data file used to store raw compressed manifest blobs
     * @param cacheSize the maximum number of manifests to keep in the in-memory
     * cache
     * @throws IOException if the index or data file cannot be created or opened
     */
    public ManifestStore(File idxFile, File datFile, int cacheSize) throws IOException {
        this.index = new ManifestIndex(idxFile);
        this.dataFile = new ManifestDataFile(datFile);
        this.cache = new LruCache<>(cacheSize);
    }

    /**
     * Stores a new manifest blob, associating it with the given CID.
     *
     * <p>
     * The blob is:
     * <ol>
     * <li>Appended to the data file</li>
     * <li>Indexed by CID → (offset, length)</li>
     * <li>Cached in memory (optional)</li>
     * </ol>
     *
     * @param cid the unique identifier of the manifest
     * @param compressedData the compressed manifest blob
     * @throws IOException if writing to the data or index file fails
     */
    public void put(BigInteger cid, byte[] compressedData) throws IOException {
        long offset = dataFile.append(compressedData);
        index.addEntry(cid, offset, compressedData.length);
        cache.put(cid, compressedData); // optional: cache immediately
    }

    /**
     * Retrieves a manifest by CID.
     *
     * <p>
     * The retrieval steps are:
     * <ol>
     * <li>Check if the manifest exists in the in-memory cache</li>
     * <li>If not cached, consult the index for offset and size</li>
     * <li>Read the manifest from the data file using the offset</li>
     * <li>Cache the manifest for future requests</li>
     * </ol>
     *
     * @param cid the unique identifier of the manifest
     * @return the manifest data, or {@code null} if no entry exists for the CID
     * @throws IOException if reading from the index or data file fails
     */
    public byte[] get(BigInteger cid) throws IOException {
        // 1. Check in-memory cache
        if (cache.containsKey(cid)) {
            return cache.get(cid);
        }
        // 2. Lookup in index
        ManifestIndex.Entry entry = index.find(cid);
        if (entry == null) {
            return null;
        }

        // 3. Read from data file
        byte[] data = dataFile.read(entry.offset, entry.length);

        // 4. Store in cache
        cache.put(cid, data);
        return data;
    }

    @Override
    public void close() throws IOException {
        index.close();
        dataFile.close();
    }
}
