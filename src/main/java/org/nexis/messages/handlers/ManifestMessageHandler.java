/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages.handlers;

import io.netty.channel.ChannelHandlerContext;
import java.util.logging.Logger;
import org.nexis.base.NetworkConfiguration;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.NodeId;
import org.nexis.internal.MessageHandler;
import org.nexis.messages.GetPeersRequestMessage;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class ManifestMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(ManifestMessageHandler.class.getName());

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    public static final int NUMBER_OF_PEERS_TO_GET = 10;

    public ManifestMessageHandler(NexusEnvelopBuilder builder, NetworkConfiguration params) {
        this.builder = builder;
        this.params = params;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasManifest();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        LOGGER.info("Received manifest message");

        NodeId nodeServerId = builder.getNode().getNodeId(builder.getNode().getKeyPair().getPublic().getEncoded());

        //handle received manifest 
        // persist manifest to category against CID(IPFS) manifest registry
        //send out get peers request
        NexusProtocol.GetPeers getPeers
                = NexusProtocol.GetPeers.newBuilder()
                        .setSize(NUMBER_OF_PEERS_TO_GET)
                        .build();

        GetPeersRequestMessage getPeersRequest = new GetPeersRequestMessage(
                NexusNetworkConfiguration.of(params.getNetwork()),
                getPeers,
                nodeServerId.getId()
        );

        ctx.writeAndFlush(builder.build(getPeersRequest));
    }
}
