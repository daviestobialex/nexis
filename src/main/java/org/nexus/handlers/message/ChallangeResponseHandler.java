/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.handlers.message;

import io.netty.channel.ChannelHandlerContext;
import java.util.logging.Logger;
import org.nexus.base.MessageHandler;
import org.nexus.base.NetworkConfiguration;
import org.nexus.base.NexusEnvelopBuilder;
import org.nexus.base.NodeIdentity;
import org.nexus.base.proto.NexusProtocol;
import org.nexus.core.NodeId;

/**
 *
 * @author daviestobialex
 */
public class ChallangeResponseHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(ChallangeResponseHandler.class.getName());

    private final NodeIdentity identity;
    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;

    public ChallangeResponseHandler(NodeIdentity identity, NexusEnvelopBuilder builder, NetworkConfiguration params) {
        this.identity = identity;
        this.builder = builder;
        this.params = params;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasChallenge();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) throws Exception {
        NodeId nodeServerId = builder.getNode().getNodeId(builder.getNode().getKeyPair().getPublic().toString());

        LOGGER.info("challenge response received");
        // validate node id and signature
        // make peer active from pending peers list if pass
        // populate node with public key
//        ctx.writeAndFlush(builder.build(null));
    }
}
