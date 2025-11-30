# Nexis Manifest System

## Overview

The **Manifest** is a signed, immutable declaration of a node's identity, capabilities, and services within the Nexis peer-to-peer network. It serves as both a business card and a technical contract, enabling nodes to discover, verify, and interact with each other in a trustless, decentralized manner.

Think of the Manifest as a combination of:
- **Business Identity**: Who you are, what you do, where you operate
- **Technical Specification**: What APIs/services you expose and how to interact with them
- **Cryptographic Proof**: Verifiable signatures that prove authenticity

---

## Business Context

### Why Manifests Matter

In traditional centralized systems, service discovery and identity verification rely on trusted intermediaries (DNS, certificate authorities, service registries). In a decentralized network like Nexis, there's no central authority. The Manifest solves this by:

1. **Self-Sovereign Identity**: Organizations publish their own identity without needing permission
2. **Verifiable Claims**: Cryptographic signatures prove the organization controls the declared identity
3. **Service Discovery**: Peers can discover what services a node offers without a central directory
4. **Regulatory Compliance**: Registration numbers and country codes enable compliance tracking
5. **Business Networks**: Categories (e.g., "payments", "governor") allow formation of specialized sub-networks

### Real-World Use Cases

- **Cross-Border Payments**: A remittance provider in Kenya publishes a manifest declaring their payment APIs, license numbers, and supported currencies
- **Supply Chain**: A manufacturer publishes inventory APIs and product specifications
- **Financial Services**: Banks publish account lookup and transaction APIs with proper regulatory identifiers
- **Identity Verification**: KYC providers publish verification endpoints with compliance details

---

## Technical Context

### Manifest Structure

A manifest is a JSON document with the following key sections:

#### 1. **Identity & Metadata**
```json
{
  "manifestVersion": 1,
  "protocolVersion": 1,
  "organizationName": "Fxbud Limited",
  "organizationUrl": "https://fxbud.com",
  "organizationCountryCodes": ["NG", "GB", "KE"],
  "organizationRegistrationNumbers": {
    "NG": "RC1234567",
    "KE": "C.789012",
    "GB": "12345678"
  }
}
```

- **Business Purpose**: Establishes who the organization is and where they're legally registered
- **Technical Purpose**: Enables regulatory filtering, compliance checks, and jurisdiction-based routing

#### 2. **Categories & Capabilities**
```json
{
  "category": ["payments", "governor"],
  "categoryMetadata": {
    "capabilities": ["forex", "remittance", "p2p-transfer"]
  }
}
```

- **Business Purpose**: Declares what type of services the node provides
- **Technical Purpose**: Enables peer filtering and network segmentation (e.g., only connect to "governor" nodes for validation)

**Reserved Categories**:
- `governor`: Nodes that validate transactions and govern the network
- `payments`: Payment service providers
- `card-payments`: Card processing services

#### 3. **Contact Information**
```json
{
  "contact": {
    "fullName": "Alex Davies",
    "email": "alex@fxbud.com",
    "mobileNumber": "2348124500619",
    "organisationRole": "CTO"
  }
}
```

- **Business Purpose**: Provides accountability and a point of contact for disputes or compliance issues
- **Technical Purpose**: Enables out-of-band communication for node operators

#### 4. **Service Specification**
```json
{
  "specification": {
    "type": "openapi",
    "version": "2.0",
    "specDirectory": "",
    "swagger": "2.0",
    "host": "api.fxbud.com",
    "basePath": "/v2",
    "paths": {
      "/transfer": { ... },
      "/balance": { ... }
    }
  }
}
```

- **Business Purpose**: Documents the APIs and services offered, enabling integration
- **Technical Purpose**: Provides machine-readable API definitions for automated client generation

**Supported Specification Types**:
- `openapi`: OpenAPI/Swagger 2.x and 3.x specifications
- `iso2022`: ISO 20022 XML financial messaging standard (future support)

---

## Manifest Properties Reference

### Top-Level Properties

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `manifestVersion` | integer | Yes | Version of the manifest schema (currently 1) |
| `protocolVersion` | integer | Yes | Version of the Nexis protocol this node supports |
| `organizationName` | string | Yes | Legal name of the organization |
| `organizationUrl` | string | Yes | Official website of the organization |
| `organizationCountryCodes` | array[string] | Yes | ISO 3166-1 alpha-2 country codes where the organization operates |
| `organizationRegistrationNumbers` | object | Yes | Map of country code → registration number (e.g., company registration) |
| `category` | array[string] | Yes | Service categories this node provides |
| `categoryMetadata` | object | No | Additional metadata about capabilities |
| `contact` | object | Yes | Contact information for the node operator |
| `organizationPolicy` | string | Yes | URL to the organization's privacy policy |
| `organizationTermsAndConditions` | string | Yes | URL to the organization's terms of service |
| `dependencies` | object | No | Declares required peer nodes or categories |
| `specification` | object | Yes | API specification (OpenAPI, ISO 20022, etc.) |

### Specification Object

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `type` | string | Yes | Specification format: "openapi" or "iso2022" |
| `version` | string | Yes | Version of the specification format |
| `specDirectory` | string | No | Directory path for ISO 20022 XML files (required for iso2022 type) |
| `swagger` / `openapi` | string | Conditional | Version indicator for OpenAPI specs |
| `host` | string | Yes | Base hostname for API endpoints |
| `basePath` | string | Yes | Base path prefix for all API routes |
| `paths` | object | Yes | OpenAPI paths definition (for openapi type) |

### Contact Object

| Property | Type | Required | Description |
|----------|------|----------|-------------|
| `fullName` | string | Yes | Full name of the contact person |
| `email` | string | Yes | Email address (validated format) |
| `mobileNumber` | string | Yes | Mobile phone number |
| `organisationRole` | string | Yes | Role/title within the organization |

---

## Cryptographic Identity

### Manifest ID

The **Manifest ID** is a SHA-256 hash computed over:
1. Organization name
2. Categories (sorted alphabetically)
3. Registration numbers (sorted by country code)
4. Protocol version
5. Manifest version
6. Node's public key (ED25519)

This creates a stable, unique identifier for each node/organization combination.

### Signature

Manifests are signed using ED25519 cryptography:
- The node's private key signs the entire manifest content
- Other nodes verify the signature using the node's public key
- This proves the manifest was created by the holder of the private key

### Content Hash

A separate SHA-256 hash is computed over the raw manifest JSON. This enables:
- Content integrity verification
- Efficient manifest comparison
- Cache invalidation detection

---

## Validation Rules

### Schema Validation

The `ManifestSchemaV1` class enforces:

1. **Required Fields**: All mandatory properties must be present
2. **Version Check**: `manifestVersion` must match the schema version (1)
3. **Category Format**: Must be a non-empty array
4. **Contact Validation**: Email format, all required contact fields
5. **Specification Type**: Must be "openapi" or "iso2022"
6. **Security**: Host URLs cannot expose private IP addresses (localhost, 192.168.x.x, 10.x.x.x)

### OpenAPI Validation

For `type: "openapi"`:
- Swagger version must be 2.x or 3.x
- Must include `info` and `paths` sections
- Host must be a public, routable address

### ISO 20022 Validation (Future)

For `type: "iso2022"`:
- Must specify `specDirectory` pointing to XML schema files
- Validation against ISO 20022 XSD schemas
- Message type identification and routing

---

## Manifest Lifecycle

### 1. Creation
Node operator creates manifest.json with organization details and API specs

### 2. Signing
Node generates ED25519 key pair and signs the manifest

### 3. Publishing
Manifest is broadcast to the network via the `Manifest` protobuf message

### 4. Discovery
Peers receive and validate the manifest signature

### 5. Storage
Valid manifests are stored in local peer registries

### 6. Execution
HTTP client executors are generated from API specifications for peer-to-peer calls

---

## Integration with Proof-of-Service

The manifest specification's `type` field determines how API calls are handled:

### OpenAPI (type: "openapi")
- APIs are automatically parsed and made available for invocation
- Read-only GET requests: Response sent immediately, no blockchain transaction
- Transactional APIs (POST/PUT/DELETE): Response sent immediately + blockchain transaction created for audit
- Payment-required APIs: Transaction includes fee split to governance nodes

### ISO 20022 (type: "iso2022")
- Financial messaging standard for banking/payments
- XML message validation against ISO schemas
- Transaction recording for compliance and audit trails
- Future support for SWIFT-like messaging patterns

---

## Example: Payment Provider Manifest

```json
{
  "manifestVersion": 1,
  "protocolVersion": 1,
  "organizationName": "Acme Payments Ltd",
  "organizationUrl": "https://acme-pay.com",
  "organizationCountryCodes": ["KE"],
  "organizationRegistrationNumbers": {
    "KE": "PVT-2024-12345"
  },
  "category": ["payments"],
  "categoryMetadata": {
    "capabilities": ["mobile-money", "bank-transfer", "forex"]
  },
  "contact": {
    "fullName": "Jane Doe",
    "email": "jane@acme-pay.com",
    "mobileNumber": "+254712345678",
    "organisationRole": "Head of Engineering"
  },
  "organizationPolicy": "https://acme-pay.com/privacy",
  "organizationTermsAndConditions": "https://acme-pay.com/terms",
  "specification": {
    "type": "openapi",
    "version": "3.0.0",
    "host": "api.acme-pay.com",
    "basePath": "/v1",
    "paths": {
      "/transfer": {
        "post": {
          "summary": "Initiate money transfer",
          "operationId": "createTransfer",
          "requestBody": { ... },
          "responses": { ... }
        }
      },
      "/balance": {
        "get": {
          "summary": "Check account balance",
          "operationId": "getBalance",
          "parameters": [ ... ]
        }
      }
    }
  }
}
```

---

## Security Considerations

1. **Private Key Protection**: Node private keys must be stored securely (HSM, encrypted storage)
2. **Host Validation**: Private IPs are blocked to prevent internal network exposure
3. **Signature Verification**: Always verify manifest signatures before trusting content
4. **Category Filtering**: Only connect to nodes with appropriate categories for your use case
5. **Rate Limiting**: Implement rate limits on manifest updates to prevent spam

---

## Future Enhancements

- **Multi-signature Manifests**: Support for organizational multi-sig approval
- **Manifest Updates**: Versioned updates with backward compatibility
- **Revocation Lists**: Mechanism to revoke compromised manifests
- **ISO 20022 Support**: Full implementation of financial messaging standards
- **SLA Declarations**: Service level agreements in manifest metadata
- **Operations Support/Messaging**: Dispute Resolution

---

## Related Documentation

- [Nexis White Paper](https://docs.google.com/document/d/1F-iJ1vNIZSe56MH7gDHZvyG6Clv4w0d2hrckXszotKI/edit?usp=sharing)
- [README.md](./README.md) - Project overview and quick start
- [API Handler Implementation](./src/main/java/org/nexis/messages/handlers/FunctionCallMessageHandler.java)

---

## Questions & Support

For manifest-related questions or issues, contact the Nexis development team or open an issue on the GitHub repository.
