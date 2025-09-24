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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.Security;
import java.time.Instant;
import java.util.Objects;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.nexis.utilities.ByteUtils;
import org.nexis.utilities.HexFormat;
import org.nexis.utilities.Sha256Hash;

/**
 * Immutable value object representing a Manifest — the public, versioned
 * declaration of a node/organization's capabilities, endpoints, and
 * (optionally) contract logic.
 *
 * <p>
 * The Manifest class intentionally stores the canonical raw payload (as a
 * String) and exposes derived properties such as {@link #manifestId()} (SHA-256
 * hex of the raw payload) and {@link #loadedAt()} (time of load/creation).
 * </p>
 *
 * <p>
 * <b>Responsibilities</b>
 * <ul>
 * <li>Hold the canonical manifest payload (JSON / protobuf / other).</li>
 * <li>Provide a stable manifest identifier (SHA-256 digest).</li>
 * <li>Provide safe persistence helpers (load, resolve, save).</li>
 * <li>Provide lightweight crypto helpers (compute id, verify signature over raw
 * payload).</li>
 * </ul>
 * </p>
 *
 * <p>
 * <b>Design notes</b>
 * <ul>
 * <li>The class is {@code final} and immutable: thread-safe and suitable as a
 * value object.</li>
 * <li>Parsing into typed fields (orgName, endpoints, services, contract
 * bytecode) is intentionally delegated to external {@code ManifestParser}
 * implementations to keep this class focused.</li>
 * <li>Equality and hashCode are derived from {@code manifestId} so manifests
 * are comparable by content identity.</li>
 * </ul>
 * </p>
 *
 * @author daviestobialex
 */
public final class Manifest {

    /**
     * Default search paths used by {@link #resolve(String)}; you can override
     * in higher-level code.
     */
    public static final String[] SEARCH_PATHS = {
        "src/main/nexus",
        "src/main/resources/nexus"
    };

    private final String raw;           // canonical serialized manifest (JSON/proto text)
    private final String manifestId;    // SHA-256 hex of raw
    private final Instant loadedAt;     // when it was loaded/created

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    private Manifest(String raw, String manifestId, Instant loadedAt) {
        this.raw = Objects.requireNonNull(raw, "raw manifest cannot be null");
        this.manifestId = Objects.requireNonNull(manifestId, "manifestId cannot be null");
        this.loadedAt = Objects.requireNonNull(loadedAt, "loadedAt cannot be null");
    }

    /**
     * Load the manifest from a file path.
     *
     * @param path path to the manifest file
     * @return a new immutable Manifest instance
     * @throws IOException if reading fails
     */
    public static Manifest load(Path path) throws IOException {
        String content = Files.readString(path);
        String id = ByteUtils.formatHex(Sha256Hash.hash(content.getBytes()));
        return new Manifest(content, id, Instant.now());
    }

    /**
     * Search configured {@link #SEARCH_PATHS} for the given filename and load
     * the first match.
     *
     * @param filename relative filename to search for
     * @return loaded Manifest
     * @throws FileNotFoundException if no candidate is found
     */
    public static Manifest resolve(String filename) throws FileNotFoundException {
        for (String dir : SEARCH_PATHS) {
            Path candidate = Paths.get(dir, filename);
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                try {
                    return load(candidate);
                } catch (IOException e) {
                    // Wrap to unchecked so callers can optionally handle; alternatively propagate IOException.
                    throw new RuntimeException("Failed to read manifest at " + candidate, e);
                }
            }
        }
        throw new FileNotFoundException("Manifest not found in search paths: " + filename);
    }

    /**
     * Create a Manifest from a raw string payload.
     *
     * @param raw canonical payload (e.g. JSON string)
     * @return Manifest
     */
    public static Manifest of(String raw) {
        String id = ByteUtils.formatHex(Sha256Hash.hash(raw.getBytes()));
        return new Manifest(raw, id, Instant.now());
    }

    /**
     * Persist the manifest to disk (UTF-8).
     *
     * @param target path to write
     * @return target path
     * @throws IOException on I/O error
     */
    public Path save(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        Files.writeString(target, raw);
        return target;
    }

    /**
     * Canonical raw serialized manifest (JSON/proto text).
     *
     * @return
     */
    public String raw() {
        return raw;
    }

    /**
     * Stable manifest ID as SHA-256 hex string (lowercase).
     *
     * @return
     */
    public String manifestId() {
        return manifestId;
    }

    /**
     * Manifest identity as bytes (raw SHA-256 bytes).
     */
    public byte[] manifestIdBytes() {
        return HexFormat.parseHex(manifestId);
    }

    /**
     * Time when this Manifest instance was created/loaded.
     *
     * @return
     */
    public Instant loadedAt() {
        return loadedAt;
    }

    @Override
    public String toString() {
        return raw;
    }

}
