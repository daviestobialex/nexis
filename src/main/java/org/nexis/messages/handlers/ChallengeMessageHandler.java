/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages.handlers;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import org.nexis.internal.MessageHandler;
import org.nexis.base.NetworkConfiguration;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.core.NodeId;
import org.nexis.core.Peer;
import org.nexis.base.PeerAddress;
import org.nexis.core.PeerRegistry;
import org.nexis.messages.ChallengeResponseMessage;
import org.nexis.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class ChallengeMessageHandler implements MessageHandler {

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    private final PeerRegistry registery = PeerRegistry.getInstance();

    public ChallengeMessageHandler(NexusEnvelopBuilder builder, NetworkConfiguration params) {
        this.builder = builder;
        this.params = params;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasHandshake();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        NodeId nodeServerId = builder.getNode().getNodeId(builder.getNode().getKeyPair().getPublic().getEncoded());
        long nonce = envelop.getMessage().getHandshake().getNonce();
        byte[] nodeId = envelop.getNodeId().toByteArray();
        
        NexusProtocol.ChallengeResponse challenge
                = NexusProtocol.ChallengeResponse.newBuilder()
                        .setPublicKey(ByteString.copyFrom( builder.getNode().getKeyPair().getPublic().getEncoded()))
                        .setNonce(nonce)
                        .build();

        ChallengeResponseMessage challengeMessage = new ChallengeResponseMessage(
                NexusNetworkConfiguration.of(params.getNetwork()),
                challenge,
                nodeServerId.getId()
        );
        
        PeerAddress nodeById = registery.getNodeById(nodeId);
        // update peer registery with node id
        if (nodeById == null) {
            String remoteAddress = ctx.channel().remoteAddress().toString();
            registery.addPendingPeer(new Peer(remoteAddress, nodeId), ctx.channel());
        }

        ctx.writeAndFlush(builder.build(challengeMessage));
    }
}
