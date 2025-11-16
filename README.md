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
  Every node publishes a signed **Manifest** (organization identity, services, API spec/ISO 2022).

  * Verifiable via SHA-256 + ED25519 signatures.
  # Nexis

  Network Exchange Integration System — a lightweight peer-to-peer protocol and toolkit for decentralized marketplaces and interoperable services.

  This repository contains the Nexis core: networking, manifesting, message validation, storage primitives, and utilities used to build and run Nexis nodes.

  Project repo: https://github.com/daviestobialex/nexis

  Reference white paper: https://docs.google.com/document/d/1F-iJ1vNIZSe56MH7gDHZvyG6Clv4w0d2hrckXszotKI

  ## Status

  Prototype. Core components (networking, manifests, validation pipeline, wallet primitives) are implemented and under active development. Expect API changes.

  ## Key Technologies

  - Java (17+ recommended)
  - Maven for build and dependency management
  - Protocol Buffers (protobuf) for message definitions
  - Netty for async networking
  - BouncyCastle for cryptography (ED25519, SHA-256)
  - Docker (optional) for containerized runs

  ## Highlights / Features

  - Peer-to-peer networking with handshake, manifest exchange and peer discovery
  - Signed manifests (ED25519 + SHA-256) to represent identities and services
  - Modular, pluggable validation pipeline for incoming messages
  - Persistent manifest store with index and data files
  - Protobuf-based message protocol and handlers

  ## Quickstart

  Prerequisites

  - JDK 17+ installed and JAVA_HOME set
  - Maven 3.6+ (or use the bundled wrapper if added)
  - Docker (optional) if you want to run in a container

  Clone and build

  ```bash
  git clone https://github.com/daviestobialex/nexis.git
  cd nexis
  mvn clean install -DskipTests
  ```

  Run unit tests

  ```bash
  mvn test
  ```

  Run a single node locally (IDE or command-line)

  You can run the test entrypoint (example) from your IDE or with Maven exec if configured. If you have a main class such as `org.nexis.example.NexusTestPoint` you can run it with:

  ```bash
  mvn -Dexec.mainClass="org.nexis.example.NexusTestPoint" -Dexec.classpathScope="runtime" org.codehaus.mojo:exec-maven-plugin:3.0.0:java
  ```

  Or build a runnable jar (if a jar/assembly target is configured) and run with:

  ```bash
  java -jar target/nexis-<version>-jar-with-dependencies.jar
  ```

  Docker (optional)

  This repo contains a Dockerfile you can use to build and run a containerized node.

  ```bash
  docker build -t nexis:latest .
  docker run -p 9004:9004 nexis:latest
  ```

  Adjust the port and environment variables as needed for your test topology.

  ## How the project is organized

  - `src/main/java/org/nexis/base` — core domain objects (Identity, Manifest, Address, Coin, etc.)
  - `src/main/java/org/nexis/core` — protocol logic, message handlers, validators
  - `src/main/java/org/nexis/net` — networking code (Netty handlers)
  - `src/main/java/org/nexis/store` — manifest storage (index + data files)
  - `src/main/java/org/nexis/messages` — protobuf-based message envelopes and handlers

  Read the Java packages for the detailed API and examples.

  ## Development notes

  - The `Manifest` format is a JSON structure signed by a node's ED25519 keypair and addressed via a Content ID (CID).
  - The project uses a validation pipeline — validators live under `org.nexis.validator` and can be extended to add custom checks.
  - There are convenience test utilities under `src/test/java/org/nexis/tests` used by unit tests.

  ## Running tests and linting

  Run unit tests:

  ```bash
  mvn test
  ```

  Checkstyle / lints (if configured) can be run with the Maven plugin configured in `pom.xml`.

  ## Contributing

  Contributions are welcome. A minimal workflow:

  1. Fork the repo
  2. Create a feature branch: `git checkout -b feat/my-change`
  3. Run tests locally and ensure they pass
  4. Open a pull request describing the change and rationale

  For larger design changes, please open an issue first to discuss the approach.

  ## Roadmap & Issues

  The project uses an issue tracker (GitHub Issues). See the repository board for current tasks and roadmap items.

  ## License

  This project is licensed under the Apache License 2.0 — see `LICENSE` for details.

  ## Contact

  email: daviestobialex@nxis.org

  If you'd like help running the project or contributing, open an issue or reach out via GitHub.
