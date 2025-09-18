Got it ✅ — here’s a **README.md** draft in **open-source style** for your Nexis P2P MVP, written the way projects like BitcoinJ, Netty, or libp2p would document themselves.

---

## Nexis P2P

# Network Exchange Integration System - Peer-to-Peer Protocol

**Nexis P2P** is a lightweight peer-to-peer networking and cryptographic toolkit inspired by [BitcoinJ](https://bitcoinj.github.io/) and other distributed systems.
It provides the foundation for building ...

---

## ✨ Getting Started


---

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

### 1. Generate or Load Node Keys

```java
NodeCipher node = new NodeCipher() {
    @Override
    public String getNodeId() {
        return Base64.getEncoder().encodeToString(getKeyPair().getPublic().getEncoded());
    }
    @Override
    public KeyPair getKeyPair() {
        try {
            return generateKeys();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
};
```

Or load existing keys from disk:

```java
KeyPair keyPair = KeyLoader.loadKeyPair("src/main/resources/private.pem",
                                        "src/main/resources/public.pem");
```

---

### 2. Define Your Network Parameters

```java
NexisNetwork network = NexisNetwork.MAINNET;
System.out.println("Running on network: " + network.id());
```

---

### 3. Connect to Peers

```java
EventLoopGroup group = new NioEventLoopGroup();
Bootstrap bootstrap = new Bootstrap()
        .group(group)
        .channel(NioSocketChannel.class)
        .handler(new NioProtoServer());

PeerConnectListener listener = new PeerConnectListener(
        "127.0.0.1", 8333,
        bootstrap,
        group.next(),
        activePeers,
        pendingPeers,
        failedPeers,
        10,   // max retries
        300   // max backoff (seconds)
);

bootstrap.connect("127.0.0.1", 8333).addListener(listener);
```

The listener will automatically retry connections with exponential backoff and jitter.

---

### 4. Manage Peers

* **Active Peers** → Connected and healthy.
* **Pending Peers** → In the middle of connecting.
* **Failed Peers** → Gave up after max retries.

This allows your node to track the overall network state at runtime.

---

## ⚙️ Configuration

| Parameter           | Default   | Description                                 |
| ------------------- | --------- | ------------------------------------------- |
| `maxRetries`        | `10`      | Maximum retries before marking peer as dead |
| `maxBackoffSeconds` | `300`     | Max wait time between retries (5 minutes)   |
| `NexisNetwork`      | `MAINNET` | Selects which network parameters to use     |

---

## 🧪 Testing

Run the unit tests:

```bash
mvn test
```

---

## 📚 Roadmap

* [ ] Message protocol definitions (`Ping`, `Pong`, `ManifestAnnounce`).
* [ ] Persistent peer storage & address book.
* [ ] DHT integration for decentralized peer discovery.
* [ ] Gossip protocol for manifest propagation.
* [ ] Block & transaction relay.

---

## 🤝 Contributing

Contributions are welcome! Please fork the repo and open a PR.
Check the [issues](https://github.com/your-org/nexus-p2p/issues) page for open tasks.

---

## 📜 License

Licensed under the [Apache 2.0 License](LICENSE).

---
