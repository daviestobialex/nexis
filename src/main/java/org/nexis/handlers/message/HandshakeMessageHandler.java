/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.handlers.message;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import org.nexis.base.MessageHandler;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.NexusEnvelopBuilder;
import org.nexis.base.NodeIdentity;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.core.NodeId;
import org.nexis.core.Peer;
import org.nexis.core.PeerAddress;
import org.nexis.core.PeerRegistry;
import org.nexis.messages.ChallengeResponseMessage;
import org.nexis.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class HandshakeMessageHandler implements MessageHandler {

    private final NodeIdentity identity;
    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    private final PeerRegistry registery = PeerRegistry.getInstance();

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
        long nonce = envelop.getMessage().getHandshake().getNonce();
        byte[] nodeId = envelop.getNodeId().toByteArray();
        NexusProtocol.challengeResponse challenge
                = NexusProtocol.challengeResponse.newBuilder()
                        .setPublicKey(ByteString.copyFrom(identity.getKeyPair().getPublic().getEncoded()))
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
            System.out.println("ADDING TO PENDING PEER");
            registery.addPendingPeer(new Peer(params.getNetwork().id(), nodeId), ctx.channel());
        }else{
             System.out.println("ALREADY PENDING PEER " + nodeById.id() + " " + nodeById.toString());
        }

        ctx.writeAndFlush(builder.build(challengeMessage));
    }
}
