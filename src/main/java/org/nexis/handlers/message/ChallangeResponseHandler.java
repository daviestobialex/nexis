/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.handlers.message;

import io.netty.channel.ChannelHandlerContext;
import java.util.Arrays;
import java.util.logging.Logger;
import org.nexis.base.MessageHandler;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.NexusEnvelopBuilder;
import org.nexis.base.NodeIdentity;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.core.NodeId;
import org.nexis.core.PeerRegistry;

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
//        NodeId nodeServerId = builder.getNode().getNodeId(builder.getNode().getKeyPair().getPublic().toString());
        PeerRegistry registery = PeerRegistry.getInstance();
        LOGGER.info("challenge response received");
        // validate node id 
        byte[] nodeId = envelop.getNodeId().toByteArray();
        byte[] publicKey = envelop.getMessage().getChallenge().getPublicKey().toByteArray();
        long nonce = envelop.getMessage().getChallenge().getNonce();

        byte[] computedNodeId = NodeId.stableNodeId(publicKey);

        if (!Arrays.equals(computedNodeId, nodeId)) {
            throw new SecurityException("bad node actor detected");// TODO: maybe update network with bad node actor id?
        }

        long registeredPeerNonce = registery.getNonceById(nodeId);// get nonce in registery
        // ensure nonce matches
        if (nonce != registeredPeerNonce) {
            throw new SecurityException("invalid nonce");
        }

        // remove nonce from registory
        registery.removeNonceById(nodeId);
        // make peer active from pending peers list if pass
        // populate node with public key
//        ctx.writeAndFlush(builder.build(null));
    }
}
