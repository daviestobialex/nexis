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
import java.util.logging.Logger;
import org.nexis.base.Manifest;
import org.nexis.base.NexusNetwork;
import org.nexis.net.NioProtoServer;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexis.base.IdentityProvider;
import org.nexis.base.Identity;
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
import org.nexis.store.ManifestStore;
import org.nexis.validator.ChecksumValidator;
import org.nexis.validator.SignatureValidator;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class NexisInstance {

    private final Identity identity;
    private final Manifest manifest;
    private final ChannelInitializer connectionServer;
    private final NexusNetwork network;
    private final EventLoopGroup group = new NioEventLoopGroup();
    private final static Logger LOGGER = Logger.getLogger(NexisInstance.class.getName());
    private final ValidationPipeline pipeline = new ValidationPipeline();
    private final MessageDispatcher dispatcher = new MessageDispatcher();
    private final NexusEnvelopBuilder builder;

    public NexisInstance(NexusNetwork network) throws FileNotFoundException {
        IdentityProvider identityProvider = new Ed25519IdentityProvider();
        this.identity = identityProvider.loadOrCreateIdentity();
        this.manifest = Manifest.resolve("manifest.json");
        this.network = network;
        this.builder = new NexusEnvelopBuilder(identity);
        NexusNetworkConfiguration params = NexusNetworkConfiguration.of(this.network);
        Path index = Paths.get("src/main/nexus/", "manifest .idx");
        Path store = Paths.get("src/main/nexus/", "manifest.dat");

        try {
            ManifestStore manifestStore = new ManifestStore(index.toFile(), store.toFile(), 10);

            // add pipeline validators
            pipeline.addValidator(new ChecksumValidator(params));
            pipeline.addValidator(new SignatureValidator(params));

            // add dispatchers
            dispatcher.registerHandler(new ManifestMessageHandler(builder, params, manifest));
            dispatcher.registerHandler(new ChallengeMessageHandler(builder, params));
            dispatcher.registerHandler(new PingMessageHandler());
            dispatcher.registerHandler(new ChallangeResponseHandler(params, builder, manifest));
            dispatcher.registerHandler(new GetPeersMessageHandler(builder, params));
            dispatcher.registerHandler(new GetPeersResponseHandler(builder, params, group, manifest));
            dispatcher.registerHandler(new GetManifestContentMessageHandler(builder, params, manifestStore));
            dispatcher.registerHandler(new ManifestContentMessageHandler(builder, params, manifestStore, manifest));
        } catch (IOException ex) {
            throw new RuntimeException("error loading manifest index and store");
        }

        this.connectionServer = new NioProtoServer(group, pipeline, dispatcher);
    }

    /**
     * creates and starts a node that can receive instructions from peers
     *
     * @param port
     * @param maxConnections
     * @param propagate
     * @throws InterruptedException
     */
    public void start(int port, int maxConnections, boolean propagate) throws InterruptedException {

        bind(port);

        // create or load existing block chain
        // start seeding based on network
        DnsDiscovery dnsDiscovery = new DnsDiscovery(NexusNetworkConfiguration.of(network),
                group, connectionServer, identity);
        dnsDiscovery.seedPeers(maxConnections, propagate);
    }

    /**
     * opens a port to receive connections
     *
     * @param port
     * @throws InterruptedException
     */
    private void bind(int port) throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(connectionServer);
        b.bind(port).sync();
        LOGGER.info("Listening on port " + port);
    }

    /**
     * request manifest from active peers
     */
    public void requestManifestContent() {
        // periodically request manifets from all active peers

        PeerRegistry.getInstance().getActivePeers()
                .forEach((peer, channel) -> {

                    ConcurrentHashMap<String, Set<String>> manifests
                            = ManifestRegistry.getInstance().getManifests();

                    manifests.forEach((category, cmanifests) -> {

                        for (String cmanifest : cmanifests) {
                            NexusProtocol.GetManifestContent getContent = NexusProtocol.GetManifestContent.newBuilder()
                                    .setCid(ByteString.copyFrom(cmanifest.getBytes()))
                                    .build();

                            NodeId nodeId = new NexusEnvelopBuilder(identity).getNode().getNodeId();

                            GetManifestContentMessage getManifestContentMessage
                                    = new GetManifestContentMessage(NexusNetworkConfiguration.of(network),
                                            getContent, nodeId.getId());

                            channel.writeAndFlush(builder.build(getManifestContentMessage)
                            );
                        }

                    }
                    );

                });
    }

    /**
     * get
     *
     * @param category
     * @return
     */
    public Iterator<String> getCidsByCategory(String category) {
        throw new UnsupportedOperationException("operation not currently supported");
    }

    public Iterator<String> categories() {
        return ManifestRegistry.getInstance().getManifests().keys().asIterator();
    }

    /**
     * Remote Procedural Call
     */
    public void rpc() {

    }
}
