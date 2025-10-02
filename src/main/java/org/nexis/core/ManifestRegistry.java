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

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.nexis.base.ContentRegistry;

/**
 *
 * @author daviestobialex
 */
public final class ManifestRegistry implements ContentRegistry<String, String> {

    /**
     * contains categories and CIDs
     */
    private final ConcurrentHashMap<String, Set<String>> manifests;

    /**
     * Singleton instance (lazy-loaded, thread-safe)
     */
    private static class Holder {

        private static final ManifestRegistry INSTANCE = new ManifestRegistry();
    }

    private ManifestRegistry() {
        this.manifests = new ConcurrentHashMap<>();
    }

    public static ManifestRegistry getInstance() {
        return ManifestRegistry.Holder.INSTANCE;
    }

    @Override
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

    @Override
    public boolean hasContent(String cid) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public byte[] getContent(String cid) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public void requestContent(String cid) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
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

    @Override
    public void save(byte[] content) {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public ConcurrentHashMap<String, Set<String>> getManifests() {
        return manifests;
    }
}
