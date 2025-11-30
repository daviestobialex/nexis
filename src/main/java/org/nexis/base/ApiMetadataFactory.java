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

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.internal.ManifestSchema;

/**
 * Factory for loading API metadata from different specification formats.
 * Supports OpenAPI and (future) ISO 20022 specifications.
 *
 * @author daviestobialex
 */
public class ApiMetadataFactory {

    private static final Logger LOGGER = Logger.getLogger(ApiMetadataFactory.class.getName());

    private final Manifest manifest;
    private final Map<String, ApiMetadata> metadataCache;

    public ApiMetadataFactory(Manifest manifest) {
        this.manifest = manifest;
        this.metadataCache = new HashMap<>();
        loadMetadata();
    }

    /**
     * Load all API metadata from the manifest specification. The manifest has
     * already been parsed, so we just extract the endpoint information.
     */
    private void loadMetadata() {
        try {
            // The Manifest class has already parsed and validated the specification
            // We just need to extract the endpoints from the parsed context
            loadOpenApiMetadata();

            LOGGER.log(Level.INFO, "Loaded {0} API endpoints from manifest specification",
                    new Object[]{metadataCache.size()});

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load API metadata from manifest", e);
            throw new RuntimeException("Failed to initialize API metadata", e);
        }
    }

    /**
     * Load metadata from OpenAPI/Swagger specification. Uses the already-parsed
     * endpoints from manifest.getContext().
     */
    private void loadOpenApiMetadata() {
        // Get the already-parsed endpoints from the Manifest's ApiClientContext
        Map<String, ManifestSchema.EndpointDescriptor> endpoints
                = manifest.getContext().getEndpoints();

        // Get organization name - ManifestObject is already parsed
        String organizationName = getOrganizationName();

        for (Map.Entry<String, ManifestSchema.EndpointDescriptor> entry : endpoints.entrySet()) {
            ManifestSchema.EndpointDescriptor descriptor = entry.getValue();

            // Extract cost from descriptor
            Coin cost = descriptor.cost > 0.0 ? Coin.valueOf((long) (descriptor.cost * Coin.COIN.value)) : Coin.ZERO;

            // Determine API type based on cost, transactional flag, and method
            ApiType type = determineApiType(descriptor.httpMethod, cost, descriptor.transactional);

            // Create metadata
            ApiMetadata metadata = new ApiMetadata(
                    descriptor.path,
                    descriptor.httpMethod,
                    type,
                    cost,
                    organizationName, // service provider
                    descriptor.summary
            );

            metadataCache.put(entry.getKey(), metadata);
            metadataCache.put(descriptor.path, metadata); // Also index by path alone
        }
    }

    /**
     * Get organization name from the already-parsed ManifestObject via Manifest
     * getter.
     */
    private String getOrganizationName() {
        String orgName = manifest.getOrganizationName();
        return (orgName != null && !orgName.isEmpty()) ? orgName : "Unknown";
    }

    /**
     * Determine the API type based on HTTP method and cost. - If cost > 0:
     * PAYMENT_REQUIRED - If POST/PUT/PATCH/DELETE: TRANSACTIONAL - If
     * GET/HEAD/OPTIONS: READ_ONLY
     */
    private ApiType determineApiType(String httpMethod, Coin cost, boolean transactionalFlag) {
        // If there's a cost, it's a payment-required API
        if (cost.isPositive()) {
            return ApiType.PAYMENT_REQUIRED;
        }

        // If explicitly marked transactional, record on-chain even without cost
        if (transactionalFlag) {
            return ApiType.TRANSACTIONAL;
        }

        String method = httpMethod.toUpperCase();

        // GET requests are typically read-only
        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            return ApiType.READ_ONLY;
        }

        // Mutating methods without cost and not marked transactional are non-transactional
        if ("POST".equals(method) || "PUT".equals(method)
                || "PATCH".equals(method) || "DELETE".equals(method)) {
            return ApiType.NON_TRANSACTIONAL;
        }

        // Default to read-only for unknown methods
        return ApiType.READ_ONLY;
    }

    /**
     * Get API metadata for a given endpoint
     *
     * @param apiId the API endpoint identifier (e.g., "/transfer" or "POST
     * /transfer")
     * @return ApiMetadata or null if not found
     */
    public ApiMetadata getApiMetadata(String apiId) {
        // Try exact match first
        ApiMetadata metadata = metadataCache.get(apiId);

        if (metadata == null) {
            // Try with different case variations
            for (Map.Entry<String, ApiMetadata> entry : metadataCache.entrySet()) {
                if (entry.getKey().equalsIgnoreCase(apiId)) {
                    return entry.getValue();
                }
            }

            LOGGER.log(Level.WARNING, "API metadata not found for: {0}", apiId);
        }

        return metadata;
    }

    /**
     * Get all loaded API metadata
     *
     * @return
     */
    public Map<String, ApiMetadata> getAllMetadata() {
        return new HashMap<>(metadataCache);
    }

    /**
     * Check if an API endpoint exists
     */
    public boolean hasApi(String apiId) {
        return metadataCache.containsKey(apiId);
    }
}
