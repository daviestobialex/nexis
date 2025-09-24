/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.Manifest;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.NexusNetwork;
import org.nexis.net.NioProtoServer;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexis.base.IdentityProvider;
import org.nexis.base.Identity;

/**
 *
 * @author daviestobialex
 */
public class NexusBootstrap {

    private final Identity identity;
    private final Manifest manifest;
    private final ChannelInitializer connectionServer;
    private final NexusNetwork network;

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final static Logger LOGGER = Logger.getLogger(NexusBootstrap.class.getName());

    public NexusBootstrap(NexusNetwork network) throws FileNotFoundException {
        IdentityProvider identityProvider = new Ed25519IdentityProvider();
        this.identity = identityProvider.loadOrCreateIdentity();
        this.manifest = Manifest.resolve("manifest.json");
        this.connectionServer = new NioProtoServer(NexusNetworkConfiguration.of(network), this.identity);
        this.network = network;
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
        seedPeers(network, maxConnections, propagate);
    }

    private void bind(int port) throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(connectionServer);
        b.bind(port).sync();
        LOGGER.info("Listening on port " + port);
    }

    private void seedPeers(NexusNetwork network, int maxConnections, boolean propagate) {

        // connect to peers and seed
        PeerGroup peer = new PeerGroup(network, group, maxConnections, connectionServer);
        try {
            // begin message propagagtions to active peers, a class would handle this
            Thread.sleep(Duration.ofSeconds(10));
        } catch (InterruptedException ex) {
            Logger.getLogger(NexusBootstrap.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (propagate) {
            peer.beginMessagePropagation(new NexusEnvelopBuilder(identity));
        }
    }
}
