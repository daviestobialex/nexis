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
package org.nexis.internal;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.Manifest;
import org.nexis.core.ManifestObject;
import org.nexis.exceptions.ManifestValidationException;

/**
 * Interface defining the contract for Manifest Schema validation and parsing.
 * Implementations handle version-specific manifest structures and validation
 * rules.
 *
 * @author daviestobialex
 */
public interface ManifestSchema {

    public static final Set<String> RESERVED_CATEGORIES = Set.of(
            "governor", "payments", "card-payments"
    );
    public static final String CONTACT = "contact";
    public static final String SPECIFICATIONS = "specifications";
    public static final String CATEGORY = "category";
    public static final String EMAIL = "email";

    // Categories that trigger sub-protocol initialization
    public static final Set<String> PROTOCOL_CATEGORIES = Set.of(
            "governor", "payments", "card-payments"
    );

    /**
     * Validate the manifest JSON structure against the schema
     *
     * @param manifestJson the raw JSON string
     * @throws ManifestValidationException if validation fails
     */
    void validate(String manifestJson) throws ManifestValidationException;

    /**
     * Get the version number this schema handles
     *
     * @return
     */
    String getVersion();

    /**
     * Parse the manifest into structured data
     *
     * @param manifestJson
     * @return
     * @throws com.fasterxml.jackson.core.JsonProcessingException
     */
    ManifestObject parse(String manifestJson) throws JsonProcessingException;

    /**
     * Get reserved categories for this schema version
     *
     * @return
     */
    Set<String> getReservedCategories();

    /**
     * Check if a category requires special protocol handling
     *
     * @param category
     * @return
     */
    boolean requiresProtocolInitiation(String category);

    public default String getCategory(String manifestJson) {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode rootNode = objectMapper.readValue(manifestJson, JsonNode.class);
            return rootNode.get(CATEGORY).asText();
        } catch (JsonProcessingException ex) {
            Logger.getLogger(Manifest.class.getName()).log(Level.SEVERE, null, ex);
            throw new RuntimeException("category not found in manifest file");
        }
    }
}
