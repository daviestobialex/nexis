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

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.store.ManifestStore;
import org.nexis.store.Storage;

/**
 *
 * @author daviestobialex
 */
public final class ManifestRegistry {

    private final ManifestStore manifestStore;
    /**
     * contains categories and CIDs
     */
    private final ConcurrentHashMap<String, Set<String>> manifests;

    private final ConcurrentHashMap<String, CompletableFuture<String>> pendingRequests;

    /**
     * Singleton instance (lazy-loaded, thread-safe)
     */
    private static class Holder {

        private static final ManifestRegistry INSTANCE = new ManifestRegistry();
    }

    private ManifestRegistry() {
        this.manifests = new ConcurrentHashMap<>();
        this.pendingRequests = new ConcurrentHashMap<>();
        try {
            Path index = Paths.get("./", "manifest.idx");
            Path store = Paths.get("./", "manifest.dat");
            this.manifestStore = new ManifestStore(index.toFile(), store.toFile(), 10);
        } catch (IOException e) {
            throw new RuntimeException("manifest store failed to instantiate, manifest registery creation failed");
        }
    }

    public static ManifestRegistry getInstance() {
        return ManifestRegistry.Holder.INSTANCE;
    }

    public void put(String category, String cid) {
        System.out.println("ADDING TO MANIFEST :: category : " + category + " cid :" + cid);
        Set<String> cids = manifests.get(category);
        if (cids == null) {
            cids = new HashSet();
            cids.add(cid);
        } else {
            cids.add(cid);
        }
        manifests.put(category, cids);
    }

    public boolean hasContent(String cid) {
        return manifestStore.contains(new BigInteger(cid.getBytes()));
    }

    public byte[] getContent(String cid) {
        try {
            return manifestStore.get(new BigInteger(cid.getBytes()));
        } catch (IOException ex) {
            Logger.getLogger(ManifestRegistry.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    public CompletableFuture<String> register(String cid) {
        CompletableFuture<String> future = new CompletableFuture<>();
        pendingRequests.put(cid, future);
        return future;
    }

    public void complete(String cid, String content) {
        CompletableFuture<String> future = pendingRequests.remove(cid);
        if (future != null && !future.isDone()) {
            future.complete(content);
        }
    }

    public void fail(String cid, Throwable t) {
        CompletableFuture<String> future = pendingRequests.remove(cid);
        if (future != null && !future.isDone()) {
            future.completeExceptionally(t);
        }
    }

    /**
     * get all CID by category
     *
     * @param category
     * @return
     */
    public Set<String> getByCategory(String category) {
        return manifests.get(category);
    }

    public void save(String cid, byte[] content) throws IOException {
        manifestStore.put(new BigInteger(cid.getBytes()), content);
    }

    public ConcurrentHashMap<String, Set<String>> getManifests() {
        return manifests;
    }

    public Storage getStore() {
        return manifestStore;
    }
}
