/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages.handlers;

import io.netty.channel.ChannelHandlerContext;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.PeerAddress;
import org.nexis.core.ManifestRegistry;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.NodeId;
import org.nexis.core.PeerRegistry;
import org.nexis.internal.MessageHandler;
import org.nexis.messages.GetPeersResponseMessage;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexus.base.proto.NexusProtocol;

/**
 * uses get peers discovery to respond wit available peers to this node
 *
 * @author daviestobialex
 */
public class GetPeersMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(ManifestMessageHandler.class.getName());

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    private final PeerRegistry registery;

    public GetPeersMessageHandler(NexusEnvelopBuilder builder, NetworkConfiguration params) {
        this.builder = builder;
        this.params = params;
        this.registery = PeerRegistry.getInstance();
    }

    // for test
    public GetPeersMessageHandler(NexusEnvelopBuilder builder, NetworkConfiguration params, PeerRegistry registery) {
        this.builder = builder;
        this.params = params;
        this.registery = registery;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasPeersDiscovery();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        System.out.println("recieved Get Peers hasPeersDiscovery step 4");
        NodeId nodeServerId = builder.getNode().getNodeId();

        int requestedPeerSize = envelop.getMessage().getPeersDiscovery().getSize();
        String category = envelop.getMessage().getManifest().getCategory();
        String cid = envelop.getMessage().getManifest().getCid().toString();

        LOGGER.log(Level.INFO, "Received get peers message of size {}", requestedPeerSize);
        Set<PeerAddress> activePeers = registery
                .getActivePeers()
                .keySet();

        int limit = Math.min(requestedPeerSize, activePeers.size());

        List<String> addresses = activePeers.stream()
                .limit(limit)
                .map(PeerAddress::id)
                .collect(Collectors.toList());

        NexusProtocol.GetPeersResponse getPeers
                = NexusProtocol.GetPeersResponse.newBuilder()
                        .addAllAddresses(addresses)
                        .build();

        GetPeersResponseMessage getPeersRequest = new GetPeersResponseMessage(
                NexusNetworkConfiguration.of(params.getNetwork()),
                getPeers,
                nodeServerId.getId()
        );

        ManifestRegistry.getInstance().put(category, cid);

        ctx.writeAndFlush(builder.build(getPeersRequest));
    }

}
