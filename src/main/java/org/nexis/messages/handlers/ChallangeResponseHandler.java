/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages.handlers;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.Manifest;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.SignedManifest;
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
    private final NetworkConfiguration params;
    private final Manifest manifest;

    public ChallangeResponseHandler(
            NetworkConfiguration params,
            NexusEnvelopBuilder builder,
            Manifest manifest) {
        this.builder = builder;
        this.params = params;
        this.manifest = manifest;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasHandshakeResponse();
    }

    /**
     * Manifest exchange begins here
     *
     * @param envelop
     * @param ctx
     */
    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        NodeId nodeServerId = builder.getNode().getNodeId(builder.getNode().getKeyPair().getPublic().getEncoded());
        PeerRegistry registery = PeerRegistry.getInstance();
        LOGGER.info("challenge/handshake response received");
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

        sendSignedManifest(envelop, ctx, nodeServerId);
    }

    /**
     * This function would eventually evolve to manage and maintain the manifest
     * using a Markel DAG, where files can be chunked and sent over the wire,
     * nodes can hold chunks and then can rebuild file based on distributed
     * chunks on the network
     *
     * @param envelop
     * @param ctx
     * @param nodeServerId
     */
    protected void sendSignedManifest(NexusProtocol.NexusEnvelop envelop,
            ChannelHandlerContext ctx,
            NodeId nodeServerId) {

        try {
            SignedManifest signedManifest = new SignedManifest(manifest, builder.getNode());
            
            NexusProtocol.Manifest manifestRequest = NexusProtocol.Manifest.newBuilder()
                    .setCategory(manifest.getCategory())
                    .setCid(ByteString.copyFrom(signedManifest.getSignature()))
                    .build();

            ManifestRequestMessage manifestMessage
                    = new ManifestRequestMessage(
                            NexusNetworkConfiguration.of(params.getNetwork()),
                            manifestRequest, nodeServerId.getId());

            ctx.writeAndFlush(builder.build(manifestMessage));
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | SignatureException ex) {
            Logger.getLogger(ChallangeResponseHandler.class.getName()).log(Level.SEVERE, null, ex);
        }

    }
}
