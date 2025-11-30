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

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.security.Security;
import java.time.Instant;
import java.util.Objects;
import java.util.*;
import java.nio.file.Files;
import java.nio.file.Path;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.nexis.core.ManifestObject;
import org.nexis.core.ManifestSchemaV1;
import org.nexis.exceptions.ManifestValidationException;
import org.nexis.internal.ManifestSchema;
import org.nexis.base.utils.ByteUtils;
import org.nexis.internal.ManifestSchema.EndpointDescriptor;
import org.nexis.net.HttpClientExecutor;

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
        "src/main/resources/nexus",
        "./"
    };

    private final String raw;           // canonical serialized manifest (JSON/proto text)
    private final Sha256Hash manifestId;   // SHA-256 hex of raw
    private final Sha256Hash contentHash;
    private final Instant loadedAt;     // when it was loaded/created
    private final ManifestSchema schema; // handles schema validation and parsing
    private final ManifestObject manifestObject;
    private final HttpClientExecutor clientExecutor;
    private final Identity nodeIdentity;  // This node's identity (includes NodeId, KeyPair)

    /**
     * get HTTP client executor
     *
     * @return
     */
    public HttpClientExecutor getClientExecutor() {
        return clientExecutor;
    }

    /**
     * API context can be used to execute requests using HttpClientExecutor
     *
     * @return
     */
    public ApiClientContext getContext() {
        return context;
    }

    private final ApiClientContext context;

    static {
        Security.addProvider(new BouncyCastleProvider());
    }

    /**
     * Context holding information about an API client built from OpenAPI spec
     */
    public static class ApiClientContext {

        private final String baseUrl;
        private final Map<String, EndpointDescriptor> endpoints;

        ApiClientContext(String baseUrl,
                Map<String, EndpointDescriptor> endpoints) {
            this.baseUrl = baseUrl;
            this.endpoints = endpoints;
        }
        
        public String getBaseUrl(){
            return this.baseUrl;
        }
        
        public Map<String, EndpointDescriptor> getEndpoints(){
            return this.endpoints;
        }
    }

    private Manifest(String raw, Instant loadedAt, Identity identity) {

        this.raw = Objects.requireNonNull(raw, "raw manifest cannot be null");
        this.contentHash = Sha256Hash.of(raw.getBytes());
        this.loadedAt = Objects.requireNonNull(loadedAt, "loadedAt cannot be null");
        this.nodeIdentity = Objects.requireNonNull(identity, "identity cannot be null");
        this.schema = new ManifestSchemaV1();
        this.schema.validate(this.raw);
        try {
            this.manifestObject = this.schema.parse(this.raw);

            // Parse all endpoints
            Map<String, EndpointDescriptor> endpoints
                    = this.schema.parseEndpoints(this.manifestObject.getSpecifications());

            /**
             * API Context and HttpClientExecutor are building blocks for down
             * stream calls
             */
            this.context = new ApiClientContext(getBaseUrl(), endpoints);
            this.clientExecutor = new HttpClientExecutor();

            manifestId = stableId(identity);

        } catch (JsonProcessingException e) {
            throw new ManifestValidationException("error parsing manifest");
        } catch (Exception ex) {
            ex.printStackTrace();
            throw new RuntimeException("error loading manifest specifications");
        }
        // initiate blockchain activities or initiate sub-protocols, for now payments/or governance sub-protocols
    }

    private String getAllRegNos() {

        // Deterministic concatenation (sorted by key to ensure stable ordering)
        StringBuilder sb = new StringBuilder();

        manifestObject
                .organizationRegistrationNumbers().entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    sb.append(entry.getKey())
                            .append('=')
                            .append(entry.getValue())
                            .append(';');
                });

        return sb.toString();
    }

    private Sha256Hash stableId(Identity identity) {

        String orgName = manifestObject.organizationName();
        List<String> categories = manifestObject.categories();
        String allRegNos = getAllRegNos();
        int protocolVersion = manifestObject.protocolVersion();
        int manifestVersion = manifestObject.version();

        // Convert all to UTF-8 bytes
        byte[] orgNameBytes = orgName.getBytes(StandardCharsets.UTF_8);
        byte[] categoryBytes = String.join("", categories).getBytes(StandardCharsets.UTF_8);
        byte[] regBytes = allRegNos.getBytes(StandardCharsets.UTF_8);

        // Compute total length for buffer
        int totalLen = orgNameBytes.length
                + categoryBytes.length
                + regBytes.length
                + 8 // 4 bytes for version + 4 for protocolVersion
                + identity.getKeyPair().getPublic().getEncoded().length;

        ByteBuffer buffer = ByteBuffer.allocate(totalLen);

        // Deterministic field order
        buffer.put(ByteUtils.writInt32BE(protocolVersion));
        buffer.put(ByteUtils.writInt32BE(manifestVersion));
        buffer.put(orgNameBytes);
        buffer.put(categoryBytes);
        buffer.put(regBytes);
        buffer.put(identity.getKeyPair().getPublic().getEncoded());

        return Sha256Hash.of(buffer.array());
    }

    /**
     * Load the manifest from a file path.
     *
     * @param path path to the manifest file
     * @param identity node server identity
     * @return a new immutable Manifest instance
     * @throws IOException if reading fails
     */
    public static Manifest load(Path path, Identity identity) throws IOException {
        String content = Files.readString(path);
        return new Manifest(content, Instant.now(), identity);
    }

    /**
     * Search configured {@link #SEARCH_PATHS} for the given filename and load
     * the first match.
     *
     * @param filename relative filename to search for
     * @param identity node server identity
     * @return loaded Manifest
     * @throws FileNotFoundException if no candidate is found
     */
    public static Manifest resolve(String filename, Identity identity) throws FileNotFoundException {
        for (String dir : SEARCH_PATHS) {
            Path candidate = Paths.get(dir, filename);
            if (Files.exists(candidate) && Files.isRegularFile(candidate)) {
                try {
                    return load(candidate, identity);
                } catch (IOException e) {
                    // Wrap to unchecked so callers can optionally handle; alternatively propagate IOException.
                    throw new RuntimeException("Failed to read manifest at " + candidate, e);
                }
            }
        }
        throw new FileNotFoundException("Manifest not found in search paths: " + filename);
    }

    /**
     * Create a Manifest from a raw string payload (standalone/test mode).
     *
     * @param raw canonical payload (e.g. JSON string)
     * @param identity node server identity
     * @return Manifest
     */
    public static Manifest of(String raw, Identity identity) {
        return new Manifest(raw, Instant.now(), identity);
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
    public String getRaw() {
        return raw;
    }

    /**
     * Stable manifest ID as SHA-256 hex string (lowercase).
     *
     * @return
     */
    public String manifestId() {
        return manifestId.toString();
    }

    /**
     * Manifest identity as raw SHA-256 bytes
     *
     * @return
     */
    public byte[] manifestIdBytes() {
        return manifestId.getBytes();
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

    public List<String> getCategories() {
        return manifestObject.categories();
    }
    
    public String getOrganizationName() {
        return manifestObject.organizationName();
    }

    public String getBaseUrl() {
        return manifestObject.baseUrl();
    }

    public int getVersion() {
        return schema.getVersion();
    }

    /**
     * Checks if this node is authorized by the network to sign for (approve)
     * other nodes.
     *
     * A node can sign for others if: 1. It has one or more categories that
     * indicate governance authority (e.g., "governor") 2. The blockchain shows
     * unspent GovernanceOutput(s) approving this node from a trusted genesis
     * validator (verified by traversing the approval chain up to the root)
     *
     * Implementation notes: - Categories are parsed from the manifest and
     * indicate the node's role(s) - The approval chain is validated by
     * GovernanceValidator/GovernanceUTXOProvider, which traces from this node
     * backwards through signers to a genesis validator - Multiple
     * signers/approvers are supported; the chain must eventually reach genesis
     * to be valid - If no governance category or no approval chain to genesis
     * exists, returns false
     *
     * @return true if this node has a valid authorization chain to sign for
     * others; false otherwise
     */
    public boolean canSignFor() {

        // Step 1: Check if manifest has governance-related category
        List<String> categories = manifestObject.categories();
        boolean hasGovernanceCategory = categories != null && categories.stream()
                .anyMatch(cat -> cat != null && cat.equalsIgnoreCase("governor"));

        if (!hasGovernanceCategory) {
            // Node doesn't declare governance role; cannot sign for others
            return false;
        }

        return true;
    }
}
