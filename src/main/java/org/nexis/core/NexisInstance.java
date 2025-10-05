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

import com.google.protobuf.ByteString;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Stream;
import org.nexis.base.Manifest;
import org.nexis.base.NexusNetwork;
import org.nexis.net.NioProtoServer;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexis.base.IdentityProvider;
import org.nexis.base.Identity;
import org.nexis.base.StreamConnection;
import org.nexis.internal.MessageDispatcher;
import org.nexis.messages.GetManifestContentMessage;
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
import org.nexis.validator.ChecksumValidator;
import org.nexis.validator.IsSelfValidator;
import org.nexis.validator.SignatureValidator;
import org.nexus.base.proto.NexusProtocol;

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
     * Logger for node lifecycle and events.
     */
    private static final Logger LOGGER = Logger.getLogger(NexisInstance.class.getName());

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

    // for testing
    public NexisInstance(NexusNetwork network, boolean doPropagate) throws FileNotFoundException {
        IdentityProvider identityProvider = new Ed25519IdentityProvider();
        this.identity = identityProvider.loadOrCreateIdentity();
        this.manifest = Manifest.resolve("manifest.json");
        this.network = network;
        this.builder = new NexusEnvelopBuilder(identity);

        NexusNetworkConfiguration params = NexusNetworkConfiguration.of(this.network);
        Path index = Paths.get("./", "manifest.idx");
        Path store = Paths.get("./", "manifest.dat");

        try {
            ManifestStore manifestStore = new ManifestStore(index.toFile(), store.toFile(), 10);

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
            dispatcher.registerHandler(new GetManifestContentMessageHandler(builder, params, manifestStore));
            dispatcher.registerHandler(new ManifestContentMessageHandler(builder, params, manifestStore, manifest));

        } catch (IOException ex) {
            throw new RuntimeException("Error loading manifest index and store", ex);
        }

        this.canPropagate = doPropagate;

        if (doPropagate) {
            // Immediately attempt client connection
            this.connectionClient.connectionOpened();
        }

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
     * initiating peer discovery.
     *
     * @param maxConnections
     */
    public void connect(int maxConnections) {
        if (this.canPropagate) {
            dnsDiscovery.seedPeers(maxConnections);
        }
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
        LOGGER.info("Listening on port " + port);
    }

    public void requestManifestContent(String CID, Consumer<String> getJsonManifest) {
        throw new UnsupportedOperationException("operation not supported");
    }

    /**
     * Requests manifest content from all currently active peers.
     * <p>
     * Iterates over each known manifest in the {@link ManifestRegistry} and
     * sends {@link GetManifestContentMessage} requests to peers.
     * </p>
     *
     * @param getJsonManifests
     */
    public void requestManifestContentFromAllActivePeers(Consumer<Stream<String>> getJsonManifests) {
        PeerRegistry.getInstance().getActivePeers()
                .forEach(peerConnection -> {
                    ConcurrentHashMap<String, Set<String>> manifests
                            = ManifestRegistry.getInstance().getManifests();

                    manifests.forEach((category, cmanifests) -> {
                        for (String cmanifest : cmanifests) {
                            NexusProtocol.GetManifestContent getContent
                                    = NexusProtocol.GetManifestContent.newBuilder()
                                            .setCid(ByteString.copyFrom(cmanifest.getBytes()))
                                            .build();

                            NodeId nodeId = new NexusEnvelopBuilder(identity).getNode().getNodeId();

                            GetManifestContentMessage getManifestContentMessage
                                    = new GetManifestContentMessage(
                                            NexusNetworkConfiguration.of(network),
                                            getContent,
                                            nodeId.getId());

                            peerConnection.channel().writeAndFlush(builder.build(getManifestContentMessage));
                        }
                    });
                });
    }

    /**
     * Returns an iterator of CIDs for a given category.
     *
     * @param categories the manifest category
     * @return an iterator of CIDs
     * @throws UnsupportedOperationException currently not implemented
     */
    public Iterator<String> getCidsByCategory(String... categories) {
        throw new UnsupportedOperationException("operation not currently supported");
    }

    /**
     * Returns an iterator over all known manifest categories.
     *
     * @return iterator of categories
     */
    public Iterator<String> categories() {
        return ManifestRegistry.getInstance().getManifests().keys().asIterator();
    }

    /**
     * Placeholder for Remote Procedure Call (RPC) implementation.
     *
     * @param CID
     * @param rpcId
     * @param request
     */
    public void rpc(String CID, String rpcId, byte[] request) {
        // Future work
        throw new UnsupportedOperationException("operation not supported yet");
    }
}
