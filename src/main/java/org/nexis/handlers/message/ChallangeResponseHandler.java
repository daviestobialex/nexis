/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.handlers.message;

import io.netty.channel.ChannelHandlerContext;
import java.util.Arrays;
import java.util.logging.Logger;
import org.nexis.base.NetworkConfiguration;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.internal.MessageHandler;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.core.NodeId;
import org.nexis.core.Peer;
import org.nexis.core.PeerRegistry;
import org.nexis.messages.ManifestRequestMessage;
import org.nexis.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class ChallangeResponseHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(ChallangeResponseHandler.class.getName());
    private final NexusEnvelopBuilder builder;
    protected final NetworkConfiguration params;

    public ChallangeResponseHandler(
            NetworkConfiguration params,
            NexusEnvelopBuilder builder) {
        this.builder = builder;
        this.params = params;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasHandshakeResponse();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        NodeId nodeServerId = builder.getNode().getNodeId(builder.getNode().getKeyPair().getPublic().getEncoded());
        PeerRegistry registery = PeerRegistry.getInstance();
        LOGGER.info("challenge response received");
        // validate node id 
        byte[] nodeId = envelop.getNodeId().toByteArray();
        byte[] publicKey = envelop.getMessage().getHandshakeResponse().getPublicKey().toByteArray();
        long nonce = envelop.getMessage().getHandshakeResponse().getNonce();

        byte[] computedNodeId = NodeId.stableNodeId(publicKey);

        if (!Arrays.equals(computedNodeId, nodeId)) {
            throw new SecurityException("bad node actor detected");// TODO: maybe update network with bad node actor id?
        }
        // get nonce in registery
        // ensure nonce matches
        if (!registery.getNonceIndex().contains(nonce)) {
            throw new SecurityException("invalid nonce");
        }

        // remove nonce from registory
        registery.getNonceIndex().remove(nonce);
        // make peer active from pending peers list if pass
        String remoteAddress = ctx.channel().remoteAddress().toString();
        registery.addActivePeer(new Peer(remoteAddress, nodeId, publicKey), ctx.channel());
        // load and parse manifest and populate manifest fields
        NexusProtocol.Manifest manifest = NexusProtocol.Manifest.newBuilder()
                .setOrgName("Fxbud Limited")
//                .setCatalog(builderForValue)
                .setOrgUrl("https://fxbud.com/")
                .setProtocolVersion(1)
                .build();

        ManifestRequestMessage manifestMessage
                = new ManifestRequestMessage(
                        NexusNetworkConfiguration.of(params.getNetwork()),
                        manifest, nodeServerId.getId());

        ctx.writeAndFlush(builder.build(manifestMessage));
    }
}
