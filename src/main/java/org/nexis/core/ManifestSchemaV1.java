/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.nexis.exceptions.ManifestValidationException;
import org.nexis.internal.ManifestSchema;

/**
 * this manifest schema class parses the specifications based on open-api
 * version 2.*.* and 3.*.* and extracts it properties so that it can build an
 * executable object that can be interacted with HttpClientExecutor
 *
 * @author daviestobialex
 */
public final class ManifestSchemaV1 implements ManifestSchema {

    private final int VERSION = 1;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Parse all endpoints from OpenAPI spec
     *
     * @param specificationJson
     * @return
     */
    @Override
    public Map<String, EndpointDescriptor> parseEndpoints(String specificationJson) throws JsonProcessingException {
        JsonNode spec = objectMapper.readTree(specificationJson);
        Map<String, EndpointDescriptor> endpoints = new HashMap<>();

        JsonNode paths = spec.get("paths");
        if (paths == null || !paths.isObject()) {
            return endpoints;
        }

        Iterator<Map.Entry<String, JsonNode>> pathEntries = paths.fields();
        while (pathEntries.hasNext()) {
            Map.Entry<String, JsonNode> pathEntry = pathEntries.next();
            String path = pathEntry.getKey();
            JsonNode pathItem = pathEntry.getValue();

            // Parse each HTTP method
            for (String method : Arrays.asList("get", "post", "put", "delete", "patch", "head", "options")) {
                if (pathItem.has(method)) {
                    JsonNode operation = pathItem.get(method);
                    EndpointDescriptor descriptor = parseOperation(path, method, operation);
                    endpoints.put(descriptor.getKey(), descriptor);
                }
            }
        }

        return endpoints;
    }

    @Override
    public void validate(String manifestJson) throws ManifestValidationException {
        try {
            JsonNode root = objectMapper.readTree(manifestJson);

            // Validate version
            if (!root.has("manifestVersion")) {
                throw new ManifestValidationException("Missing required field: version");
            }

            if (root.get("manifestVersion").asInt() != VERSION) {
                throw new ManifestValidationException("invalid version");
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

    /**
     * Parse a single operation from OpenAPI spec
     *
     * @param path
     * @param method
     * @param operation
     * @return
     */
    private EndpointDescriptor parseOperation(String path, String method, JsonNode operation) {
        String operationId = operation.has("operationId")
                ? operation.get("operationId").asText() : method + path.replace("/", "_");

        String summary = operation.has("summary")
                ? operation.get("summary").asText() : "";

        // Parse parameters
        List<ParameterDescriptor> parameters = new ArrayList<>();
        if (operation.has("parameters")) {
            JsonNode params = operation.get("parameters");
            if (params.isArray()) {
                for (JsonNode param : params) {
                    parameters.add(new ParameterDescriptor(
                            param.get("name").asText(),
                            param.get("in").asText(),
                            param.has("required") && param.get("required").asBoolean(),
                            param.has("schema") ? param.get("schema") : null
                    ));
                }
            }
        }

        // Parse request body
        JsonNode requestBodySchema = null;
        if (operation.has("requestBody")) {
            JsonNode requestBody = operation.get("requestBody");
            if (requestBody.has("content")) {
                JsonNode content = requestBody.get("content");
                if (content.has("application/json")) {
                    JsonNode jsonContent = content.get("application/json");
                    if (jsonContent.has("schema")) {
                        requestBodySchema = jsonContent.get("schema");
                    }
                }
            }
        }

        // Parse response schema
        JsonNode responseSchema = null;
        if (operation.has("responses")) {
            JsonNode responses = operation.get("responses");
            // Look for 200 or 201 response
            for (String status : Arrays.asList("200", "201", "default")) {
                if (responses.has(status)) {
                    JsonNode response = responses.get(status);
                    if (response.has("content")) {
                        JsonNode content = response.get("content");
                        if (content.has("application/json")) {
                            JsonNode jsonContent = content.get("application/json");
                            if (jsonContent.has("schema")) {
                                responseSchema = jsonContent.get("schema");
                                break;
                            }
                        }
                    }
                }
            }
        }

        return new EndpointDescriptor(
                operationId, method, path, summary, parameters,
                requestBodySchema, responseSchema
        );
    }

    private void validateSpecifications(JsonNode specifications) throws ManifestValidationException {

        // Check if it's a Swagger/OpenAPI specification
        if (specifications.has("swagger")) {
            String swaggerVersion = specifications.get("swagger").asText();
            if (!swaggerVersion.startsWith("2.") && !swaggerVersion.startsWith("3.")) {
                throw new ManifestValidationException(
                        "Unsupported Swagger version: " + swaggerVersion);
            }

            // Validate required Swagger fields
            if (!specifications.has("info") || !specifications.has("paths")) {
                throw new ManifestValidationException(
                        "Swagger specification missing required fields (info, paths)");
            }

            // Scrub URL - ensure host is not exposing internal details
            if (specifications.has("host")) {
                String host = specifications.get("host").asText();
                validateHostSecurity(host);
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
        String specification;
        String baseUrl;

        JsonNode spec = root.get(SPECIFICATIONS);

        String host = spec.get("host").asText();
        String basePath = spec.get("basePath").asText();

        baseUrl = host.concat(basePath);

        specification = objectMapper.writeValueAsString(spec);

        return new ManifestObject.Builder()
                .version(VERSION)
                .protocolVersion(root.get("protocolVersion").asInt())
                .organizationName(root.get("organizationName").asText())
                .organizationUrl(root.get("organizationUrl").asText())
                .countryCodes(countryCodes)
                .organizationRegistrationNumbers(registerationNumbers)
                .category(root.get(CATEGORY).asText())
                .contact(contact)
                .baseUrl(baseUrl)
                .policyUrl(root.get("organizationPolicy").asText())
                .termsUrl(root.get("organizationTermsAndConditions").asText())
                .specifications(specification)
                .build();
    }

    @Override
    public int getVersion() {
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
