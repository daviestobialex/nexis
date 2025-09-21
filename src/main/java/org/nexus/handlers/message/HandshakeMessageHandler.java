/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.handlers.message;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import org.nexus.base.MessageHandler;
import org.nexus.base.NetworkConfiguration;
import org.nexus.base.NexusEnvelopBuilder;
import org.nexus.base.NodeIdentity;
import org.nexus.base.proto.NexusProtocol;
import org.nexus.core.NodeId;
import org.nexus.messages.ChallengeResponseMessage;
import org.nexus.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class HandshakeMessageHandler implements MessageHandler {
    private final NodeIdentity identity;
    private final NexusEnvelopBuilder builder;
     private final NetworkConfiguration params;

    public HandshakeMessageHandler(NodeIdentity identity, NexusEnvelopBuilder builder, NetworkConfiguration params) {
        this.identity = identity;
        this.builder = builder;
        this.params = params;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasHandshake();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) throws Exception {
        NodeId nodeServerId = builder.getNode().getNodeId(builder.getNode().getKeyPair().getPublic().toString());
        NexusProtocol.challengeResponse challenge =
            NexusProtocol.challengeResponse.newBuilder()
                .setPublicKey(ByteString.copyFrom(identity.getKeyPair().getPublic().getEncoded()))
                .build();

        ChallengeResponseMessage challengeMessage = new ChallengeResponseMessage(
                NexusNetworkConfiguration.of(params.getNetwork()),
                challenge,
                nodeServerId.getId()
        );

        ctx.writeAndFlush(builder.build(challengeMessage));
    }
}
