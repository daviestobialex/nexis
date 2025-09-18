/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.logging.Logger;
import org.nexus.base.NexusEnvelopBuilder;
import org.nexus.base.NexusNetwork;
import org.nexus.base.NioProducer;
import org.nexus.base.NodeId;
import org.nexus.base.messages.ManifestRequestMessage;
import org.nexus.base.proto.NexusProtocol;
import org.nexus.listeners.PeerConnectListener;
import org.nexus.net.NioProtoServer;
import org.nexus.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class PeerGroup {

    // Currently active peers. This is an ordered list rather than a set to make unit tests predictable.
    private final CopyOnWriteArraySet<Channel> peers = new CopyOnWriteArraySet<>();
    private final NexusNetworkConfiguration params;
    private static final int DEFAULT_MAX_CONNECTIONS = 1;
    private final EventLoopGroup group;
    private static final Logger LOGGER = Logger.getLogger(PeerConnectListener.class.getName());

    /**
     * Creates a PeerGroup for the given network.No chain is provided so this
     * node will report its chain height as zero to other peers. This
     * constructor is useful if you just want to explore the network but aren't
     * interested in downloading block data.
     *
     * @param network the P2P network to connect to
     * @param group
     */
    public PeerGroup(NexusNetwork network, EventLoopGroup group) {
        this(NexusNetworkConfiguration.of(Objects.requireNonNull(network)), group);
    }
    
    public PeerGroup(NexusNetwork network, EventLoopGroup group, int maxConnections) {
        this(NexusNetworkConfiguration.of(Objects.requireNonNull(network)), group, maxConnections);
    }
    
    protected PeerGroup(NexusNetworkConfiguration params, EventLoopGroup group) {
        this(params, group, DEFAULT_MAX_CONNECTIONS);
    }
    
    protected PeerGroup(NexusNetworkConfiguration params, EventLoopGroup group, int maxConnections) {
        
        this.params = params;
        String host = params.getNetwork().id();
        int port = params.getPort();
        this.group = group;
        
        connectToPeer(host, port);
        
        seed(maxConnections);
    }
    
    private void seed(int maxConnections) {
        
        String[] dnsSeeds = params.getDnsSeeds();
        
        for (String address : dnsSeeds) {
            if (peers.size() > maxConnections) {
                break;
            }
            connectToPeer(address, params.getPort());
        }
    }
    
    private void connectToPeer(String host, int port) {
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new NioProtoServer());
        
        EventLoop eventLoop = group.next();
        
        b.connect(new InetSocketAddress(host, port))
                .addListener(new PeerConnectListener(
                        params,
                        b,
                        eventLoop,
                        peers,
                        10,
                        10));
    }

    /**
     * node will not begin to initiate contact with other connected peers until
     * this is called
     *
     * @param builder
     */
    public void beginMessagePropagation(NexusEnvelopBuilder builder) {
        LOGGER.info("PROPAGATING");
        for (Channel activeChannel : peers) {
            NodeId nodeId = builder.getNode().getNodeId(builder.getNode().getKeyPair().getPublic().toString());
            // get peers and send all of them manifest messages
            NexusProtocol.Manifest manifest = NexusProtocol.Manifest.newBuilder()
                    .build();
            ManifestRequestMessage manifestMessage = new ManifestRequestMessage(params, manifest, nodeId.getId());
            NexusProtocol.NexusEnvelop envelop = builder.build(manifestMessage);
            activeChannel.writeAndFlush(envelop);
            // track response with message ids 
            // validate response and build request signatures to messages
        }
    }
}
