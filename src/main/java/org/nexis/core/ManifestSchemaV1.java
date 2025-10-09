/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.nexis.exceptions.ManifestValidationException;
import org.nexis.internal.ManifestSchema;

/**
 *
 * @author daviestobialex
 */
public final class ManifestSchemaV1 implements ManifestSchema {

    private static final String VERSION = "1";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void validate(String manifestJson) throws ManifestValidationException {
        try {
            JsonNode root = objectMapper.readTree(manifestJson);

            // Validate version
            if (!root.has("version")) {
                throw new ManifestValidationException("Missing required field: version");
            }

            // Validate category is present
            if (!root.has(CATEGORY)) {
                throw new ManifestValidationException("Missing required field: category");
            }

            // incase it changes to an array
//            JsonNode categoryNode = root.get(CATEGORY);
//            if (!categoryNode.isArray() || categoryNode.size() == 0) {
//                throw new ManifestValidationException("Category must be a non-empty array");
//            }
            // Validate specifications if present
            if (root.has(SPECIFICATIONS)) {
                validateSpecifications(root.get(SPECIFICATIONS));
            }

            // Validate required company fields
            validateRequiredFields(root,
                    "organizationName",
                    "organizationUrl",
                    "organizationCountryCodes",
                    "organizationRegistrationNumbers"
            );

            // Validate contact information
            if (!root.has(CONTACT)) {
                throw new ManifestValidationException("Missing required field: contact");
            }
            validateContactInfo(root.get(CONTACT));

        } catch (JsonProcessingException e) {
            throw new ManifestValidationException("Invalid JSON format", e);
        }
    }

    private void validateSpecifications(JsonNode specifications) throws ManifestValidationException {
        if (!specifications.isArray()) {
            throw new ManifestValidationException("specifications must be an array");
        }

        for (JsonNode spec : specifications) {
            // Check if it's a Swagger/OpenAPI specification
            if (spec.has("swagger")) {
                String swaggerVersion = spec.get("swagger").asText();
                if (!swaggerVersion.startsWith("2.") && !swaggerVersion.startsWith("3.")) {
                    throw new ManifestValidationException(
                            "Unsupported Swagger version: " + swaggerVersion);
                }

                // Validate required Swagger fields
                if (!spec.has("info") || !spec.has("paths")) {
                    throw new ManifestValidationException(
                            "Swagger specification missing required fields (info, paths)");
                }

                // Scrub URL - ensure host is not exposing internal details
                if (spec.has("host")) {
                    String host = spec.get("host").asText();
                    validateHostSecurity(host);
                }
            }
        }
    }

    private void validateHostSecurity(String host) throws ManifestValidationException {
        // Prevent internal/private IP exposure
        if (host.contains("localhost")
                || host.contains("127.0.0.1")
                || host.matches(".*192\\.168\\..*")
                || host.matches(".*10\\..*")
                || host.matches(".*172\\.(1[6-9]|2[0-9]|3[0-1])\\..*")) {
            throw new ManifestValidationException(
                    "Host cannot expose internal/private IP addresses");
        }
    }

    private void validateRequiredFields(JsonNode root, String... fields)
            throws ManifestValidationException {
        for (String field : fields) {
            if (!root.has(field)) {
                throw new ManifestValidationException("Missing required field: " + field);
            }
        }
    }

    private void validateContactInfo(JsonNode contact) throws ManifestValidationException {
        validateRequiredFields(contact, "fullName", EMAIL, "mobileNumber", "organisationRole");

        // Validate email format
        String email = contact.get(EMAIL).asText();
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ManifestValidationException("Invalid email format");
        }
    }

    @Override
    public ManifestObject parse(String manifestJson) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(manifestJson);

        // Parse country codes
        List<String> countryCodes = new ArrayList<>();
        root.get("organizationCountryCodes").forEach(node
                -> countryCodes.add(node.asText()));

        Map<String, String> registerationNumbers = new HashMap<>();

        root.get("organizationRegistrationNumbers").forEachEntry((key, value)
                -> registerationNumbers.put(key, value.asText()));

        // Parse categories
        List<String> categories = new ArrayList<>();
        root.get(CATEGORY).forEach(node -> categories.add(node.asText()));

        // Parse contact
        JsonNode contactNode = root.get(CONTACT);
        ManifestObject.ContactInfo contact = new ManifestObject.ContactInfo(
                contactNode.get("fullName").asText(),
                contactNode.get(EMAIL).asText(),
                contactNode.get("mobileNumber").asText(),
                contactNode.get("organisationRole").asText()
        );

        // Parse specifications
        ArrayList<String> specifications = new ArrayList<>();
        if (root.has(SPECIFICATIONS)) {
            root.get(SPECIFICATIONS).forEach(spec -> {
                try {
                    String swagger = spec.get("swagger").asText();
                    if (Objects.equals(swagger, "2.0")) {
                        specifications.add(
                                objectMapper.writeValueAsString(spec));
                    }
                } catch (JsonProcessingException e) {
                    throw new RuntimeException("Failed to serialize specification", e);
                }
            });
        }

        return new ManifestObject.Builder()
                .version(root.get("version").asText())
                .organizationName(root.get("organizationName").asText())
                .organizationUrl(root.get("organizationUrl").asText())
                .countryCodes(countryCodes)
                .organizationRegistrationNumbers(registerationNumbers)
                .category(root.get(CATEGORY).asText())
                .contact(contact)
                .policyUrl(root.get("organizationPolicy").asText())
                .termsUrl(root.get("organizationTermsAndConditions").asText())
                .specifications(specifications)
                .build();
    }

    @Override
    public String getVersion() {
        return VERSION;
    }

    @Override
    public Set<String> getReservedCategories() {
        return RESERVED_CATEGORIES;
    }

    @Override
    public boolean requiresProtocolInitiation(String category) {
        return PROTOCOL_CATEGORIES.contains(category);
    }
}
