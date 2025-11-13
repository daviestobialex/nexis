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
package org.nexis.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.Address;
import org.nexis.base.Coin;
import org.nexis.base.Manifest;
import org.nexis.base.NexusNetwork;
import org.nexis.net.NioProtoServer;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexis.base.IdentityProvider;
import org.nexis.base.Identity;
import org.nexis.base.StreamConnection;
import org.nexis.internal.MessageDispatcher;
import org.nexis.messages.GetManifestContentMessage;
import org.nexis.messages.GetHeadersRequestMessage;
import org.nexis.messages.handlers.ChallangeResponseHandler;
import org.nexis.messages.handlers.ChallengeMessageHandler;
import org.nexis.messages.handlers.GetManifestContentMessageHandler;
import org.nexis.messages.handlers.GetPeersMessageHandler;
import org.nexis.messages.handlers.GetPeersResponseHandler;
import org.nexis.messages.handlers.ManifestContentMessageHandler;
import org.nexis.messages.handlers.ManifestMessageHandler;
import org.nexis.messages.handlers.PingMessageHandler;
import org.nexis.net.DnsDiscovery;
import org.nexis.net.NioProducer;
import org.nexis.store.ManifestStore;
import org.nexis.store.BlockStore;
import org.nexis.store.MemoryBlockStore;
import org.nexis.validator.ChecksumValidator;
import org.nexis.validator.IsSelfValidator;
import org.nexis.validator.SignatureValidator;
import org.nexis.wallet.Wallet;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.base.Sha256Hash;
import com.google.protobuf.ByteString;
import org.nexis.exceptions.UTXOProviderException;

/**
 * {@code NexisInstance} is the main entry point for running a Nexus P2P node.
 * <p>
 * This class encapsulates both the <b>server</b> (listening for inbound peer
 * connections) and the <b>client</b> (outbound peer discovery/handshakes), as
 * well as protocol message dispatching and manifest synchronization logic.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 * <li>Load or create a node {@link Identity}.</li>
 * <li>Initialize the {@link Manifest} and backing {@link ManifestStore} (index
 * + data files).</li>
 * <li>Configure the {@link ValidationPipeline} with validators (checksums,
 * signatures).</li>
 * <li>Register protocol {@link org.nexis.internal.MessageHandler}
 * implementations.</li>
 * <li>Start/stop the node server (Netty based) and discovery services.</li>
 * <li>Periodically request and synchronize manifests from peers.</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 * <li>Instantiate with a target {@link NexusNetwork}.</li>
 * <li>Call {@link #start(int, int, boolean)} to bind the server port and begin
 * peer discovery.</li>
 * <li>Use {@link #requestManifestContent()} to request manifests from connected
 * peers.</li>
 * <li>Call {@link #stop()} to cleanly shutdown.</li>
 * </ol>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * NexusNetwork network = NexusNetwork.MAINNET;
 * NexisInstance node = new NexisInstance(network);
 *
 * // Start server on port 8080, allow up to 50 peers, enable propagation
 * node.start(8080, 50, true);
 *
 * // Periodically request manifests
 * node.requestManifestContent();
 *
 * // On shutdown
 * node.stop();
 * }</pre>
 *
 * @author daviestobialex
 */
public class NexisInstance {

    /**
     * The node's cryptographic identity (Ed25519-based).
     */
    private final Identity identity;

    /**
     * The local manifest file loaded at startup.
     */
    private final Manifest manifest;

    /**
     * Channel initializer used for server-side pipeline setup.
     */
    private final ChannelInitializer connectionServer;

    /**
     * Network (e.g. MAINNET, TESTNET) this node belongs to.
     */
    private final NexusNetwork network;

    /**
     * Shared event loop group for both server and client channels.
     */
    private final EventLoopGroup group = new NioEventLoopGroup();

    /**
     * Logger for node life-cycle and events.
     */
    private static final Logger log = Logger.getLogger(NexisInstance.class.getName());

    /**
     * Validation pipeline used for incoming message integrity checks.
     */
    private final ValidationPipeline pipeline = new ValidationPipeline();

    /**
     * Dispatcher for handling protocol messages.
     */
    private final MessageDispatcher dispatcher = new MessageDispatcher();

    /**
     * Builder for constructing signed envelopes for outbound messages.
     */
    private final NexusEnvelopBuilder builder;

    /**
     * Outbound connection client for peer discovery and propagation.
     */
    private final StreamConnection connectionClient;
    /**
     * Dns discovery
     */
    private DnsDiscovery dnsDiscovery;

    /**
     * I currently cannot auto detect localhost testing, between two peers
     */
    private final boolean canPropagate;
    private final ManifestRegistry registry;

    private final Wallet wallet;

    /**
     * Block store for persisting and retrieving blocks.
     */
    private final BlockStore blockStore;

    static {
        new Context().initialize();
    }

    // for testing
    public NexisInstance(NexusNetwork network, boolean doPropagate) throws FileNotFoundException {
        IdentityProvider identityProvider = new Ed25519IdentityProvider();
        this.identity = identityProvider.loadOrCreateIdentity();
        log.info("identity PUB " + identity.getNodeId().toHex()
                + " segwit " + NodeId.toSegwitHex(identity.getNodeId().getPublicKey()));

        this.manifest = Manifest.resolve("manifest.json", identity);
        this.network = network;
        this.builder = new NexusEnvelopBuilder(identity);
        registry = ManifestRegistry.getInstance();

        NexusNetworkConfiguration params = NexusNetworkConfiguration.of(this.network);

        // Initialize block store (in-memory for now)
        this.blockStore = new MemoryBlockStore(
                new StoredBlock(params.getGenesisBlock(), java.math.BigInteger.ONE, 0)
        );

        // Configure validators
        pipeline.addValidator(new ChecksumValidator(params));
        pipeline.addValidator(new SignatureValidator(params));
        pipeline.addValidator(new IsSelfValidator(identity));

        // Set up server and client connections
        this.connectionServer = new NioProtoServer(group, pipeline, dispatcher);
        this.connectionClient = new NioProducer(connectionServer, group,
                params.getNetwork().id(), params.getPort());

        // Register protocol handlers
        dispatcher.registerHandler(new ManifestMessageHandler(builder, params, manifest));
        dispatcher.registerHandler(new ChallengeMessageHandler(builder, params));
        dispatcher.registerHandler(new PingMessageHandler());
        dispatcher.registerHandler(new ChallangeResponseHandler(params, builder, manifest));
        dispatcher.registerHandler(new GetPeersMessageHandler(builder, params));
        dispatcher.registerHandler(new GetPeersResponseHandler(params, connectionClient));
        dispatcher.registerHandler(new GetManifestContentMessageHandler(builder, params));
        dispatcher.registerHandler(new ManifestContentMessageHandler(builder, params, manifest));

        this.canPropagate = doPropagate;

        if (doPropagate) {
            // Immediately attempt client connection
            this.connectionClient.connectionOpened();
        }

        wallet = Wallet.of(identity, params);

        Address currentAddress = wallet.currentAddress();
        Coin balance = wallet.getBalance();
        log.info("WALLET ADDRESS " + currentAddress.toString()
                + "BASE 58 ADDRESS " + currentAddress.toStringBase58() + " BALANCE " + balance.getValue());

//        try {
//            wallet.setTransactionBroadcaster((Transaction tx) -> {
//                final TransactionBroadcast broadcast = new TransactionBroadcast(tx);
//                broadcast.broadcastOnly();
//                return broadcast;
//            });
//            wallet.sendCoins(SendRequest
//                    .to(SegwitAddress.fromBech32("tb1qkmfnxdkvuxrpkg5uz8t2e9dtqd6edsjdnjyya0yd3pv4ucaa6tus7d0pgc",
//                            params.getNetwork()), Coin.valueOf(1000L)));
//        } catch (InsufficientMoneyException | Wallet.CompletionException ex) {
//            Logger.getLogger(NexisInstance.class.getName()).log(Level.SEVERE, null, ex);
//        }
    }

    /**
     * Constructs a new {@code NexisInstance}.
     *
     * <p>
     * This sets up the node identity, manifest store, validators, and all
     * registered protocol message handlers. Both the client and server
     * networking pipelines are initialized.</p>
     *
     * @param network the target {@link NexusNetwork} to join
     * @throws FileNotFoundException if the manifest file cannot be found
     */
    public NexisInstance(NexusNetwork network) throws FileNotFoundException {
        this(network, false);
    }

    /**
     * Starts the node by binding a listening port
     *
     * @param port the port to bind the server socket
     * @return
     * @throws InterruptedException if the server binding is interrupted
     */
    public NexisInstance start(int port) throws InterruptedException {
        bind(port);

        // Begin DNS discovery / seeding
        dnsDiscovery = new DnsDiscovery(
                NexusNetworkConfiguration.of(network),
                connectionClient,
                identity);

        return this;
    }

    /**
     * begins the initiation of the block chain by request for headers and then
     * ensuring they are on the same network fork, then it can then begin to
     * download the rest of the block data
     *
     * @return
     */
    public NexisInstance startBlockChainSync() {
        // Build a block locator following bitcoinj: chain head, previous blocks, fallback to genesis
        NexusNetworkConfiguration params = NexusNetworkConfiguration.of(this.network);
        List<Sha256Hash> locatorHashes = new ArrayList<>();

        // Try to get chain head from BlockStore
        try {
            StoredBlock chainHead = blockStore.getChainHead();
            if (chainHead != null) {
                // Add chain head hash to locator
                locatorHashes.add(chainHead.getHash());
                log.info("Block locator using chain head: " + chainHead.getHash()
                        + " at height " + chainHead.getHeight());
            }
        } catch (Exception ex) {
            log.log(Level.WARNING, "Error accessing BlockStore chain head, will fallback to genesis", ex);
        }

        // Always add genesis as fallback if locator is empty
        if (locatorHashes.isEmpty()) {
            try {
                Sha256Hash genesis = params.getGenesisBlock().getHash();
                locatorHashes.add(genesis);
                log.info("Block locator fallback to genesis hash: " + genesis);
            } catch (Exception ex) {
                log.log(Level.SEVERE, "Unable to determine genesis hash for block locator", ex);
            }
        }

        // Build protobuf Header (used for getHeader messages)
        NexusProtocol.Header.Builder headerBuilder = NexusProtocol.Header.newBuilder();
        for (Sha256Hash h : locatorHashes) {
            headerBuilder.addHash(ByteString.copyFrom(h.serialize()));
        }
        NexusProtocol.Header headerProto = headerBuilder.build();

        // Wrap into our Nexus message and broadcast to active peers
        GetHeadersRequestMessage msg = new GetHeadersRequestMessage(params, headerProto, new NexusEnvelopBuilder(identity).getNode().getNodeId().getId());
        NexusProtocol.NexusEnvelop envelop = builder.build(msg);

        if (envelop == null) {
            log.warning("Could not build getheaders envelope (signing failed)");
            return this;
        }

        PeerRegistry.getInstance().getActivePeers()
                .forEach(peer -> {
                    try {
                        peer.channel().writeAndFlush(envelop);
                        log.info("Sent getheaders to peer " + peer.peer().id());
                    } catch (Exception e) {
                        log.log(Level.WARNING, "Failed to send getheaders to peer", e);
                    }
                });

        MemoryBlockUTXOProvider memoryBlockUTXOProvider = new MemoryBlockUTXOProvider(blockStore, network);
        try {
            memoryBlockUTXOProvider.getOpenTransactionOutputs(
                    Arrays.asList(identity.getKeyPair().getPublic())
            );
        } catch (UTXOProviderException ex) {
            System.getLogger(NexisInstance.class.getName()).log(System.Logger.Level.ERROR, (String) null, ex);
        }
        wallet.setUTXOProvider(memoryBlockUTXOProvider);// set provider at the end of sync
        log.info("AFTER UTXO WALLET BALANCE " + wallet.getBalance().getValue());

        // The response handling should be in a Header/GetHeader message handler
        // which will validate and store incoming headers and then request blocks.
        return this;
    }

    /**
     * initiating peer discovery.
     *
     * @param maxConnections
     */
    public NexisInstance connect(int maxConnections) {
        if (this.canPropagate) {
            dnsDiscovery.seedPeers(maxConnections);
        }
        return this;
    }

    /**
     * Stops the node by shutting down the server and client connections.
     */
    public void stop() {
        group.close();
        connectionClient.connectionClosed();
    }

    /**
     * Binds the server to a TCP port to accept incoming peer connections.
     *
     * @param port the port to bind
     * @throws InterruptedException if binding is interrupted
     */
    private void bind(int port) throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(connectionServer);
        b.bind(port).sync();
        log.info("Listening on port " + port);
    }

    public void requestManifestContent(String CID, Consumer<String> getJsonManifest) {
        throw new UnsupportedOperationException("operation not supported");
    }

    /**
     * to listen to blockchain download
     *
     * @return
     */
    public NexisInstance setDownloadListener() {

        return this;
    }

    /**
     * Requests manifest content from all currently active peers.
     * <p>
     * Iterates over each known manifest in the {@link ManifestRegistry} and
     * sends {@link GetManifestContentMessage} requests to peers.
     * </p>
     *
     * @return
     */
    public CompletableFuture<Map<byte[], String>> requestManifestsContent() {
        Map<byte[], String> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> pending = new ArrayList<>();

        registry.getManifests().forEach((category, cids)
                -> cids.forEach(cid -> {
                    byte[] cached = registry.getContent(cid);
                    if (cached != null) {
                        results.put(cid, new String(cached));
                    } else {
                        pending.add(
                                registry.register(cid)
                                        .thenAccept(content -> results.put(cid, content))
                        );
                        sendManifestRequest(cid);
                    }
                })
        );

        // No array allocation - using method reference
        return pending.isEmpty()
                ? CompletableFuture.completedFuture(results)
                : CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new))
                        .thenApply(v -> results);
    }

    /**
     * Alternative: Stream results as they arrive Better for large numbers of
     * manifests
     *
     * @param onEachResult Called immediately for each result (cached or
     * fetched)
     * @return CompletableFuture that completes when all fetching is done
     */
    public CompletableFuture<Integer> requestManifestContentStreamingResults(
            java.util.function.BiConsumer<byte[], String> onEachResult) {

        List<CompletableFuture<Void>> pendingFutures = new ArrayList<>();
        ConcurrentHashMap<String, Set<byte[]>> manifests = registry.getManifests();

        manifests.forEach((category, cids) -> {
            for (byte[] cid : cids) {
                byte[] cachedContent = registry.getContent(cid);

                if (cachedContent != null) {
                    // Return cached content immediately
                    onEachResult.accept(cid, new String(cachedContent));
                } else {
                    // Fetch from network and stream result when available
                    CompletableFuture<String> future = registry.register(cid);

                    CompletableFuture<Void> mapped = future.thenAccept(content -> {
                        if (content != null) {
                            onEachResult.accept(cid, content);
                        }
                    });
                    pendingFutures.add(mapped);

                    sendManifestRequest(cid);
                }
            }
        });

        if (pendingFutures.isEmpty()) {
            return CompletableFuture.completedFuture(0);
        }

        return CompletableFuture.allOf(pendingFutures.toArray(CompletableFuture[]::new))
                .handle((v, ex) -> pendingFutures.size());
    }

    /**
     * Request specific CIDs with mixed cached/network results
     *
     * @param cids Collection of CIDs to request
     * @return CompletableFuture with results map
     */
    public CompletableFuture<Map<byte[], String>> requestManifests(Collection<byte[]> cids) {
        Map<byte[], String> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> pendingFutures = new ArrayList<>();

        for (byte[] cid : cids) {
            byte[] cachedContent = registry.getContent(cid);

            if (cachedContent != null) {
                results.put(cid, new String(cachedContent));
            } else {
                CompletableFuture<String> future = registry.register(cid);
                pendingFutures.add(future.thenAccept(content -> {
                    if (content != null) {
                        results.put(cid, content);
                    }
                }));
                sendManifestRequest(cid);
            }
        }

        if (pendingFutures.isEmpty()) {
            return CompletableFuture.completedFuture(results);
        }

        return CompletableFuture.allOf(pendingFutures.toArray(CompletableFuture[]::new))
                .thenApply(v -> results);
    }

    /**
     * Helper method to send manifest request to all active peers
     *
     * @param cid
     */
    private void sendManifestRequest(byte[] cid) {
        NexusProtocol.GetManifestContent getContent = NexusProtocol.GetManifestContent.newBuilder()
                .setCid(ByteString.copyFrom(cid))
                .build();

        NodeId nodeId = new NexusEnvelopBuilder(identity).getNode().getNodeId();

        GetManifestContentMessage message = new GetManifestContentMessage(
                NexusNetworkConfiguration.of(network),
                getContent,
                nodeId.getId()
        );

        NexusProtocol.NexusEnvelop envelop = builder.build(message);

        // Broadcast to all active peers
        PeerRegistry.getInstance().getActivePeers()
                .forEach(peer -> peer.channel().writeAndFlush(envelop));
    }

    /**
     * Returns an iterator of CIDs for a given category.
     *
     * @param categories the manifest category
     * @return an iterator of CIDs
     * @throws UnsupportedOperationException currently not implemented
     */
    public Iterator<byte[]> getCidsByCategory(String... categories) {
        ConcurrentHashMap<String, Set<byte[]>> manifests = registry.getManifests();

        // If no categories are specified, return all CIDs from all categories
        if (categories == null || categories.length == 0) {
            return manifests.values().stream()
                    .flatMap(Set::stream)
                    .iterator();
        }

        // Otherwise, collect only CIDs for the given categories
        return Arrays.stream(categories)
                .filter(manifests::containsKey)
                .flatMap(category -> manifests.get(category).stream())
                .iterator();
    }

    /**
     * Returns an iterator over all known manifest categories.
     *
     * @return iterator of categories
     */
    public Iterator<String> categories() {
        return registry.getManifests().keys().asIterator();
    }

    /**
     * Placeholder for Remote Procedure Call (RPC) implementation.
     *
     * @param cid
     * @param path
     * @param request
     */
    public void call(byte[] cid, String path, byte[] request) {
        byte[] manifestjson = registry.getContent(cid);
        // get spec from manifest json
        // owner of API may/may not charge 
        // compute transaction value if any
        // scope dependencies
        // compute distribute fee claims
        // await response and gossip block to network for approval
        throw new UnsupportedOperationException("operation not supported yet");
    }

    public CompletableFuture<TransactionBroadcast> sendTransaction(SendRequest sendRequest) {
        throw new UnsupportedOperationException("operation not supported yet");
    }

    public CompletableFuture<TransactionBroadcast> sendApproval(SendRequest sendRequest) {
        throw new UnsupportedOperationException("operation not supported yet");
    }

    /**
     * get wallet address
     *
     * @return
     */
    public Address getWalletAddress() {
        return wallet.currentAddress();
    }

    // set listeners for transactions, blocks, approvals/new entrants, messages, disputes
}
