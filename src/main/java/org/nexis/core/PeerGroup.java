/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import org.nexis.base.PeerAddress;
import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import org.nexis.base.NexusNetwork;
import org.nexis.base.StreamConnection;
import org.nexus.base.proto.NexusProtocol;
import static org.nexis.core.PeerRegistry.DEFAULT_MAX_CONNECTIONS;
import org.nexis.messages.ChallengeRequestMessage;
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
    private static final Logger LOGGER = Logger.getLogger(PeerGroup.class.getName());
    private int maxConnections;
    private final StreamConnection connection;

    /**
     * Creates a PeerGroup for the given network.No chain is provided so this
     * node will report its chain height as zero to other peers.This constructor
     * is useful if you just want to explore the network but aren't interested
     * in downloading block data.
     *
     * @param network the P2P network to connect to
     * @param group
     * @param connectionServer
     * @param connection
     */
    public PeerGroup(
            NexusNetwork network,
            EventLoopGroup group,
            ChannelInitializer connectionServer,
            StreamConnection connection) {
        this(NexusNetworkConfiguration.of(Objects.requireNonNull(network)), group, connectionServer, connection);
    }

    public PeerGroup(
            NexusNetwork network,
            EventLoopGroup group,
            int maxConnections,
            ChannelInitializer connectionServer,
            StreamConnection connection) {
        this(NexusNetworkConfiguration.of(Objects.requireNonNull(network)),
                group,
                maxConnections, connectionServer, connection);
    }

    protected PeerGroup(NexusNetworkConfiguration params, EventLoopGroup group,
            ChannelInitializer channelInitializer, StreamConnection connection) {
        this(params, group, DEFAULT_MAX_CONNECTIONS, channelInitializer, connection);
    }

    protected PeerGroup(NexusNetworkConfiguration params,
            EventLoopGroup group, int maxConnections,
            ChannelInitializer channelInitializer,
            StreamConnection connection) {

        this.params = params;
        this.group = group;
        this.connectionServer = channelInitializer;
        this.maxConnections = maxConnections;
        this.connection = connection;
    }

    /**
     * connects to static DNS seeds
     */
    public void seed() {

        String[] dnsSeeds = params.getDnsSeeds();

        for (String address : dnsSeeds) {
            if (peerRegistry.getActivePeerSize() > maxConnections) {
                break;
            }
            connection.connectionOpened(address, params.getPort());
        }
    }

    /**
     * node will not begin to initiate contact with other connected peers until
     * this is called, this would be the center where it determines what
     * messages that are sent to other nodes/peers
     *
     * @param builder
     */
    public void initiateHandshakeWithPeers(NexusEnvelopBuilder builder) {
        ConcurrentMap<PeerAddress, Channel> activePeers = peerRegistry.getActivePeers();
        ConcurrentMap<PeerAddress, Channel> pendingPeers = peerRegistry.getPendingPeers();

        // Merge into one
        ConcurrentMap<PeerAddress, Channel> peers = new ConcurrentHashMap<>(activePeers);
        peers.putAll(pendingPeers);

        LOGGER.info("PROPAGATING peer size: " + peers.size());
        peers.forEach((peer, activeChannel) -> {
            NodeId nodeId = builder.getNode().getNodeId();
            long nonce = ThreadLocalRandom.current().nextLong();
            peerRegistry.getNonceIndex().add(nonce);// track nonce
            doHandshake(nonce, activeChannel, nodeId, builder);
        });
    }

    /**
     * writes handshake message to channel
     *
     * @param nonce
     * @param activeChannel
     * @param nodeId
     * @param builder
     */
    private void doHandshake(long nonce, Channel activeChannel, NodeId nodeId, NexusEnvelopBuilder builder) {
        NexusProtocol.Challenge handshake = NexusProtocol.Challenge.newBuilder()
                .setNonce(nonce)
                .build();
        ChallengeRequestMessage handshakeMessage = new ChallengeRequestMessage(
                params, handshake, nodeId.getId());

        NexusProtocol.NexusEnvelop envelop = builder
                .build(handshakeMessage);

        activeChannel.writeAndFlush(envelop);
    }
}
