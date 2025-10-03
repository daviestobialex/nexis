# Welcome to Nexis

## Network Exchange Integration System - Peer-to-Peer Protocol

Link to [white paper](https://docs.google.com/document/d/1F-iJ1vNIZSe56MH7gDHZvyG6Clv4w0d2hrckXszotKI/edit?usp=sharing)

**Nexis P2P** is a lightweight peer-to-peer networking and cryptographic toolkit designed to power decentralized marketplaces and financial systems.

The concept draws inspiration from the African Continental Free Trade Area (AfCFTA) — a landmark African Union agreement to create a single, integrated market for goods and services across 54 nations. AfCFTA’s vision of breaking down barriers, fostering industrialization, and enabling seamless cross-border trade mirrors the technical challenge of building open, interoperable digital systems.

While rooted in this African vision, the problem is global. Even advanced economies, like the United States, face fragmented financial and banking systems across states and institutions. Nexus P2P seeks to serve as a union layer — a shared open standard where businesses, applications, and services can integrate seamlessly without friction.

Thanks to the pioneering work of Satoshi Nakamoto and projects like BitcoinJ
, we now have the architectural blueprints to build such frameworks. Nexus P2P extends these ideas beyond currency into a more general foundation for peer-to-peer interoperability.

---

## Technologies

* Java 21+
* (https://maven.org/)[ Maven (17.0.13) ] Build and dependency management
* https://github.com/google/protobuf[Google Protocol Buffers] - for use with serialization and hardware communications
* Netty – Event-driven asynchronous I/O for peer communication
* BouncyCastle – Cryptography provider (ED25519 signatures, SHA-256 hashing, secure randomness)

---

## ✨ Key Features

* **Peer-to-Peer Networking**
  Secure, asynchronous communication between nodes using Netty.

* **Manifest System**
  Every node publishes a signed **Manifest** (organization identity, services, API spec).

  * Verifiable via SHA-256 + ED25519 signatures.
  * Cached locally with efficient indexing and compression.
  * Addressed by CID (Content Identifier).

* **Cryptography**

  * ED25519 signatures (BouncyCastle provider).
  * SHA-256 hashing.
  * Signed envelopes to guarantee authenticity and integrity.

* **Validation Pipeline**
  Modular message validation via pluggable validators.

* **Extensible Message Protocol**
  Protobuf definitions for all messages.
  Support for replay protection, structured request/response, and message dispatch.

---

## 🏗 Architecture Overview

### 🔹 Message Flow

```
Peer A → HandshakeRequest  (nonce)
Peer B → HandshakeResponse/ChallengeResponse   (public key exchange) 

Peer A → ManifestRequest (CID + category)  
Peer B → ManifestResponse (Manifest JSON + signature)  

Peer A → GetPeersRequest  
Peer B → GetPeersResponse (peer list)  

Cycle repeats as new peers are discovered.
```

### 🔹 Core Modules

* **`org.nexis.base`**
  Base abstractions: `Identity`, `Manifest`, `SignedManifest`, `ContentRegistry`.

* **`org.nexis.core`**
  Protocol logic, Netty integration, Protobuf message handlers, ValidationPipeline.

* **`org.nexis.net`**
  Networking (NIO server/client setup, connection handlers, message dispatch).

* **`org.nexis.store`**
  Persistent storage for manifests and content:

  * `ManifestIndex` (sorted `.idx` file for O(log n) lookup).
  * `ManifestDataFile` (`.dat` file with offsets for O(1) retrieval).
  * `LruCache` (bounded in-memory cache).
  * `ManifestStore` (composite store implementing `Storage`).

* **`org.nexis.utilities`**
  Utility classes for cryptography, encoding, compression.

---

## 📂 Manifest

The **Manifest** is a structured JSON file describing an entity on the network.
It is signed by the node’s private key, producing a verifiable **SignedManifest**.

### Example Fields

* `organizationName`
* `organizationUrl`
* `registrationNumber`
* `countries` (list of ISO-3166-1 alpha-2 country codes)
* `services` (API endpoints, capabilities)
* `publicKey`
* `manifestVersion`
* `timestamp`

### Example JSON

```json
{
  "organizationName": "FXBud Ltd",
  "organizationUrl": "https://fxbud.com",
  "registrationNumber": "RC123456",
  "countries": ["NG", "KE", "GB"],
  "services": {
    "fxRates": "/api/v1/rates",
    "trading": "/api/v1/trade"
  },
  "publicKey": "ed25519:abc123...",
  "manifestVersion": 1,
  "timestamp": 1738234823
}
```

---

## 📦 Installation

Clone the repo:

```bash
git clone https://github.com/your-org/nexis-p2p.git
cd nexis-p2p
```

Build with Maven:

```bash
mvn clean install
```

---

## 🚀 Getting Started

### Start a Node

```java
       public class NexusTestPoint {

    // for test purposes alone and will be removed
    public static void main(String[] args) throws Exception {

        NexisInstance businessInstance = new NexisInstance(NexusNetwork.LOCALHOSTTEST);

        node.start(9000, 5, true);

        // Keep the JVM alive
        Thread.currentThread().join();
    }
   }

```

---

## 🧪 Testing

Run the unit tests:

```bash
mvn test
```

---

## 📚 Roadmap

* [x] Base architecture
* [x] Identity & Manifest structures
* [x] SignedManifest & CID handling
* [x] Storage (LRU + Index/Data files)
* [X] Message and Signature Validation with (ED25519)[https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html]
* [X] Manifest propagation across peers
* [ ] Persistent peer address book (blockchain format)
* [ ] DHT integration for discovery
* [ ] Gossip protocol for manifest relay
* [ ] Block & transaction relay
* [ ] Peer RPC / downstream operation execution

---

## 🤝 Contributing

We welcome contributions!

* Fork the repo
* Create a feature branch
* Submit a PR

Check the [issues](https://github.com/your-org/nexis-p2p/issues) for open tasks.

---

## 📜 License

Licensed under the [Apache 2.0 License](LICENSE).