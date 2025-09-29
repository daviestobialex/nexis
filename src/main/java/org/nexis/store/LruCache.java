/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.store;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * {@code LruCache} is a simple, fixed-capacity in-memory cache that evicts the
 * least recently used (LRU) entries when the cache exceeds its configured
 * capacity.
 *
 * <p>
 * This class is implemented by extending {@link LinkedHashMap} with
 * access-order enabled, ensuring that entries are maintained in the order of
 * most recently accessed. When a new entry is added and the maximum capacity is
 * exceeded, the eldest (least recently accessed) entry is automatically
 * removed.
 * </p>
 *
 * <h2>Features</h2>
 * <ul>
 * <li>Constant-time {@code get} and {@code put} operations (delegated to
 * {@code LinkedHashMap}).</li>
 * <li>Access-order iteration: the most recently accessed entry is moved to the
 * end of the map.</li>
 * <li>Automatic eviction when size exceeds the configured capacity.</li>
 * <li>Lightweight and suitable for small to medium-sized in-memory caches.</li>
 * </ul>
 *
 * <h2>Thread-safety</h2>
 * <p>
 * This class is <b>not</b> thread-safe. If multiple threads access the cache
 * concurrently and at least one thread modifies it, external synchronization
 * must be used. For concurrent access, consider wrapping it with
 * {@link java.util.Collections#synchronizedMap(Map)} or using a
 * {@code ConcurrentHashMap}-based cache.
 * </p>
 *
 * <h2>Example usage</h2>
 * <pre>{@code
 * LruCache<String, byte[]> manifestCache = new LruCache<>(100);
 *
 * manifestCache.put("cid123", new byte[]{...});
 * byte[] data = manifestCache.get("cid123");
 *
 * // If more than 100 entries are inserted, the least recently used one is evicted.
 * }</pre>
 *
 * @param <K> the type of keys maintained by this cache
 * @param <V> the type of cached values
 *
 * @author daviestobialex
 */
public class LruCache<K, V> extends LinkedHashMap<K, V> {

    private final int capacity;

    /**
     * Constructs a new {@code LruCache} with the specified maximum capacity.
     *
     * @param capacity the maximum number of entries this cache can hold
     * @throws IllegalArgumentException if {@code capacity} is non-positive
     */
    public LruCache(int capacity) {
        super(capacity, 0.75f, true); // access-order mode
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be greater than 0");
        }
        this.capacity = capacity;
    }

    /**
     * Determines whether the eldest entry should be removed when a new entry is
     * added. This method is automatically invoked by {@link LinkedHashMap}.
     *
     * @param eldest the least recently accessed entry
     * @return {@code true} if the current size exceeds the configured capacity,
     * causing the eldest entry to be removed
     */
    @Override
    protected boolean removeEldestEntry(Map.Entry<K, V> eldest) {
        return size() > capacity;
    }
}
