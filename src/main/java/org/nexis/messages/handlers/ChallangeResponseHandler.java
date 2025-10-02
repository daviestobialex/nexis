/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.messages.handlers;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.Arrays;
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
    private final PeerRegistry registery;

    public ChallangeResponseHandler(
            NetworkConfiguration params,
            NexusEnvelopBuilder builder,
            Manifest manifest) {
        this.builder = builder;
        this.params = params;
        this.manifest = manifest;
        this.registery = PeerRegistry.getInstance();
    }

    // for test
    public ChallangeResponseHandler(
            NetworkConfiguration params,
            NexusEnvelopBuilder builder,
            Manifest manifest,
            PeerRegistry registery) {
        this.builder = builder;
        this.params = params;
        this.manifest = manifest;
        this.registery = registery;
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
        NodeId nodeServerId = builder.getNode().getNodeId();

        LOGGER.info("challenge/handshake response received step 2");
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

            System.out.println("pub key LEN" + builder.getNode().getKeyPair().getPublic().getEncoded().length);
            
            NexusProtocol.Manifest manifestRequest = NexusProtocol.Manifest.newBuilder()
                    .setCategory(manifest.getCategory())
                    .setCid(ByteString.copyFrom(signedManifest.getSignature()))
                    .setPublicKey(ByteString.copyFrom(builder.getNode().getKeyPair().getPublic().getEncoded()))
                    .build();

            ManifestRequestMessage manifestMessage
                    = new ManifestRequestMessage(
                            NexusNetworkConfiguration.of(params.getNetwork()),
                            manifestRequest, nodeServerId.getId());
            LOGGER.info("sending signed manifed");

            ctx.writeAndFlush(builder.build(manifestMessage));
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | SignatureException ex) {
            ex.printStackTrace();
            throw new RuntimeException("unable to sign manifest");
        }

    }
}
