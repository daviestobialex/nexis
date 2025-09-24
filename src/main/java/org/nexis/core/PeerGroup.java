/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import org.nexis.base.NexusEnvelopBuilder;
import org.nexis.base.NexusNetwork;
import org.nexis.base.PublicNodeProperties;
import org.nexus.base.proto.NexusProtocol;
import static org.nexis.core.PeerRegistry.DEFAULT_MAX_CONNECTIONS;
import org.nexis.listeners.PeerConnectListener;
import org.nexis.messages.HandshakeRequestMessage;
import org.nexis.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class PeerGroup {

    private final PeerRegistry peerRegistry = PeerRegistry.getInstance();

    // Currently active peers. This is an ordered list rather than a set to make unit tests predictable.
    private final NexusNetworkConfiguration params;
    private final EventLoopGroup group;
    private final ChannelInitializer connectionServer;
    private static final Logger LOGGER = Logger.getLogger(PeerConnectListener.class.getName());

    /**
     * Creates a PeerGroup for the given network.No chain is provided so this
     * node will report its chain height as zero to other peers.This constructor
     * is useful if you just want to explore the network but aren't interested
     * in downloading block data.
     *
     * @param network the P2P network to connect to
     * @param group
     * @param connectionServer
     */
    public PeerGroup(NexusNetwork network, EventLoopGroup group, ChannelInitializer connectionServer) {
        this(NexusNetworkConfiguration.of(Objects.requireNonNull(network)), group, connectionServer);
    }

    public PeerGroup(NexusNetwork network, EventLoopGroup group, int maxConnections, ChannelInitializer connectionServer) {
        this(NexusNetworkConfiguration.of(Objects.requireNonNull(network)),
                group,
                maxConnections, connectionServer);
    }

    protected PeerGroup(NexusNetworkConfiguration params, EventLoopGroup group, ChannelInitializer connectionServer) {
        this(params, group, DEFAULT_MAX_CONNECTIONS, connectionServer);
    }

    protected PeerGroup(NexusNetworkConfiguration params, EventLoopGroup group, int maxConnections, ChannelInitializer connectionServer) {

        this.params = params;
        String host = params.getNetwork().id();
        int port = params.getPort();
        this.group = group;
        this.connectionServer = connectionServer;

        connectToPeer(host, port);

        seed(maxConnections);
    }

    /**
     * connect to static dns seeds
     *
     * @param maxConnections
     */
    private void seed(int maxConnections) {

        String[] dnsSeeds = params.getDnsSeeds();

        for (String address : dnsSeeds) {
            if (peerRegistry.getActivePeerSize() > maxConnections) {
                break;
            }
            connectToPeer(address, params.getPort());
        }
    }

    private void connectToPeer(String host, int port) {
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(connectionServer);

        EventLoop eventLoop = group.next();

        b.connect(new InetSocketAddress(host, port))
                .addListener(new PeerConnectListener(
                        params,
                        b,
                        eventLoop,
                        10,
                        10,
                        new Peer(this.params.getNetwork().id())));
    }

    /**
     * node will not begin to initiate contact with other connected peers until
     * this is called
     *
     * @param builder
     */
    public void beginMessagePropagation(NexusEnvelopBuilder builder) {
        ConcurrentMap<PeerAddress, Channel> activePeers = peerRegistry.getActivePeers();
        ConcurrentMap<PeerAddress, Channel> pendingPeers = peerRegistry.getPendingPeers();

        // Merge into one
        ConcurrentMap<PublicNodeProperties, Channel> peers = new ConcurrentHashMap<>(activePeers);
        peers.putAll(pendingPeers);

        LOGGER.info("PROPAGATING peer size: " + peers.size());
        peers.forEach((peer, activeChannel) -> {
            NodeId nodeId = builder.getNode().getNodeId(builder.getNode().getKeyPair().getPublic().toString());
//            // get peers and send all of them manifest messages
//            NexusProtocol.Manifest manifest = NexusProtocol.Manifest.newBuilder()
//                    .setOrgName("Fxbud Limited")
//                    .setOrgUrl("https://fxbud.com/")
//                    .setPubkey(ByteString.copyFrom(nodeId.getId()))
//                    .setProtocolVersion(1)
//                    .build();
//            ManifestRequestMessage manifestMessage = new ManifestRequestMessage(params, manifest, nodeId.getId());
            long nonce = ThreadLocalRandom.current().nextLong();
            NexusProtocol.Handshake handshake = NexusProtocol.Handshake.newBuilder()
                    .setNonce(nonce)
                    .build();
            HandshakeRequestMessage handshakeMessage = new HandshakeRequestMessage(params, handshake, nodeId.getId());

            NexusProtocol.NexusEnvelop envelop = builder
                    .build(handshakeMessage);

            activeChannel.writeAndFlush(envelop);
            // track response with message ids 
            // validate response and build request signatures to messages
        });
    }
}
