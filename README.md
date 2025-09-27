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
* https://maven.org/[Maven]
** Maven (17.0.13,) for building the whole project
* https://github.com/google/protobuf[Google Protocol Buffers] - for use with serialization and hardware communications
* Netty – Event-driven asynchronous I/O for peer communication
* BouncyCastle – Cryptography provider (ED25519 signatures, SHA-256 hashing, secure randomness)
---

## Message Protocol

The Nexis Messaging Protocol is a binary, Protobuf-based specification that governs how nodes exchange 
information with guarantees of integrity, authenticity, and replay protection.

### Peer Discovery and how it works

````
Peer A → HandshakeRequest  
Peer B → HandshakeResponse  
Peer A → Challenge(nonce)  
Peer B → ChallengeResponse(signed nonce)  
Peer A → ManifestRequest (API spec + signature) 
Peer B → ManifestResponse (API spec + signature)  
Peer A → GetPeersRequest  
Peer B → GetPeersResponse (list of peers)  

Repeat cycle for new peers
````

---

## 🗂 Manifest

The Manifest is a structured description of an entity joining the network. It contains metadata such as organization identity, available services, and intended interfaces.

Over time, the Manifest will evolve into a programmable contract, enabling:

Binding services to specific events or triggers

Enforcing service-level rules (quotas, restrictions)

External calls and integrations with smart-contract-like semantics

## 📦 Installation

Clone the repo:

```bash
git clone https://github.com/your-org/nexus-p2p.git
cd nexus-p2p
```

Build with Maven:

```bash
mvn clean install
```

---

## 🚀 Getting Started


---

## 🧪 Testing

Run the unit tests:

```bash
mvn test
```

---

## 📚 Roadmap

* [X] Base Architecture
* [X] Message and Signature Validation with (ED25519)[https://docs.oracle.com/en/java/javase/21/docs/specs/security/standard-names.html]
* [X] Manifest Structure and Parsing
* [X] Message protocol definitions
* [ ] Manifest Propagation Using IPFS and CID (Version 2 will involve Markel DAG chunking for larger manifest files)
* [ ] Persistent peer storage & address book using blockchain formats.
* [ ] DHT integration for decentralized peer discovery.
* [ ] Gossip protocol for manifest propagation.
* [ ] Block & transaction relay.
* [ ] Downstream Peer Operation Call/Execution

---

## 🤝 Contributing

Contributions are welcome! Please fork the repo and open a PR.
Check the [issues](https://github.com/your-org/nexus-p2p/issues) page for open tasks.

---

## 📜 License

Licensed under the [Apache 2.0 License](LICENSE).

---
