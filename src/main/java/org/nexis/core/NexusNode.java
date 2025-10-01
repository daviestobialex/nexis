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
import java.util.Set;
import java.util.logging.Logger;
import org.nexis.base.Manifest;
import org.nexis.base.NexusNetwork;
import org.nexis.net.NioProtoServer;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexis.base.IdentityProvider;
import org.nexis.base.Identity;
import org.nexis.net.DnsDiscovery;

/**
 *
 * @author daviestobialex
 */
public class NexusNode {

    private final Identity identity;
    private final Manifest manifest;
    private final ChannelInitializer connectionServer;
    private final NexusNetwork network;
    private final EventLoopGroup group = new NioEventLoopGroup();
    private final static Logger LOGGER = Logger.getLogger(NexusNode.class.getName());

    public NexusNode(NexusNetwork network) throws FileNotFoundException {
        IdentityProvider identityProvider = new Ed25519IdentityProvider();
        this.identity = identityProvider.loadOrCreateIdentity();
        this.manifest = Manifest.resolve("manifest.json");
        this.network = network;
        this.connectionServer = new NioProtoServer(
                NexusNetworkConfiguration.of(network), this.identity, manifest);
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
        DnsDiscovery dnsDiscovery = new DnsDiscovery(network, group, connectionServer, identity);
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
     * request manifest from store or active peers
     *
     * @param cid
     */
    public void requestManifestContent(String cid) {

    }

    /**
     * get
     *
     * @param category
     * @return
     */
    public Set<String> getCidsByCategory(String category) {
        throw new UnsupportedOperationException("operation not currently supported");
    }

    /**
     * Remote Procedural Call
     */
    public void rpc() {

    }
}
