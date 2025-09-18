/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.core;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import java.io.FileNotFoundException;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexus.base.Manifest;
import org.nexus.base.NexusEnvelopBuilder;
import org.nexus.base.NexusNetwork;
import org.nexus.base.NodeIdentity;
import org.nexus.base.NodeIdentityProvider;
import org.nexus.net.NioProtoServer;

/**
 *
 * @author daviestobialex
 */
public class NexusBootstrap {

    private final NodeIdentity identity;
    private final Manifest manifest;

    private final EventLoopGroup group = new NioEventLoopGroup();
    private final static Logger LOGGER = Logger.getLogger(NexusBootstrap.class.getName());

    public NexusBootstrap() throws FileNotFoundException {
        NodeIdentityProvider identityProvider = new Ed25519IdentityProvider();
        this.identity = identityProvider.loadOrCreateIdentity();
        this.manifest = Manifest.resolve("manifest.json");
    }

    /**
     * creates and starts a node that can receive instructions from peers
     *
     * @param port
     * @param network
     * @param maxConnections
     * @throws InterruptedException
     */
    public void start(int port, NexusNetwork network, int maxConnections) throws InterruptedException {

        bind(port);

        // create or load existing block chain
        // check for prod, check for test
        // start seeding based on network
        seedPeers(network, maxConnections);
    }

    private void bind(int port) throws InterruptedException {
        ServerBootstrap b = new ServerBootstrap();
        b.group(group)
                .channel(NioServerSocketChannel.class)
                .childHandler(new NioProtoServer());
        b.bind(port).sync();
        LOGGER.info("Listening on port " + port);
    }

    private void seedPeers(NexusNetwork network, int maxConnections) {

        // connect to peers and seed
        PeerGroup peer = new PeerGroup(network, group, maxConnections);
        try {
            // begin message propagagtions to active peers, a class would handle this
            Thread.sleep(Duration.ofSeconds(5));
        } catch (InterruptedException ex) {
            Logger.getLogger(NexusBootstrap.class.getName()).log(Level.SEVERE, null, ex);
        }
        peer.beginMessagePropagation(new NexusEnvelopBuilder(identity));
    }
}
