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
import com.google.protobuf.ProtocolStringList;
import io.netty.channel.ChannelHandlerContext;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.logging.Logger;
import org.nexis.base.Manifest;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.SignedManifest;
import org.nexis.core.ManifestRegistry;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.NodeId;
import org.nexis.core.Peer;
import org.nexis.core.PeerRegistry;
import org.nexis.internal.MessageHandler;
import org.nexis.messages.GetPeersRequestMessage;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class ManifestMessageHandler implements MessageHandler {

    private static final Logger log = Logger.getLogger(GetManifestContentMessageHandler.class.getName());

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    private final Manifest manifest;
    public static final int NUMBER_OF_PEERS_TO_GET = 10;

    private final PeerRegistry registery;
    private final ManifestRegistry manifestRegistry;

    private NodeId nodeServerId;
    private NexusProtocol.NexusEnvelop envelop;

    private ChannelHandlerContext ctx;

    public ManifestMessageHandler(
            NexusEnvelopBuilder builder,
            NetworkConfiguration params,
            Manifest manifest) {
        this.builder = builder;
        this.params = params;
        this.manifest = manifest;
        this.registery = PeerRegistry.getInstance();
        this.manifestRegistry = ManifestRegistry.getInstance();
    }

    // for test purposes
    public ManifestMessageHandler(
            NexusEnvelopBuilder builder,
            NetworkConfiguration params,
            Manifest manifest,
            PeerRegistry registry,
            ManifestRegistry manifestRegistry) {
        this.builder = builder;
        this.params = params;
        this.manifest = manifest;
        this.registery = registry;
        this.manifestRegistry = manifestRegistry;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasManifest();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        log.info("Received manifest message step 3");
        this.envelop = envelop;
        this.ctx = ctx;
        nodeServerId = builder.getNode().getNodeId();
        byte[] nodeId = envelop.getNodeId().toByteArray();

        // persist manifest CID to category against CID(IPFS) manifest registry
        ProtocolStringList categories = envelop.getMessage().getManifest().getCategoryList();
        byte[] cid = envelop.getMessage().getManifest().getCid().toByteArray();
        byte[] publicKey = envelop.getMessage().getManifest().getPublicKey().toByteArray();
        categories.forEach(category -> manifestRegistry.put(category, cid));

        // save public key
        String remoteAddress = ctx.channel().remoteAddress().toString();
        registery.removePendingPeer(new Peer(remoteAddress, nodeId));
        registery.addActivePeer(new Peer(remoteAddress, nodeId, publicKey), ctx.channel());
    }

    @Override
    public void sendMessage() {
        try {
            SignedManifest signedManifest = new SignedManifest(manifest, builder.getNode());

            //send out get peers request
            NexusProtocol.GetPeers getPeers
                    = NexusProtocol.GetPeers.newBuilder()
                            .setSize(NUMBER_OF_PEERS_TO_GET)
                            .setCid(ByteString.copyFrom(signedManifest.getSignature()))
                            .addAllCategory(manifest.getCategories())
                            .build();

            GetPeersRequestMessage getPeersRequest = new GetPeersRequestMessage(
                    NexusNetworkConfiguration.of(params.getNetwork()),
                    getPeers,
                    nodeServerId.getId()
            );

            ctx.writeAndFlush(builder.build(getPeersRequest));
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | SignatureException ex) {
            throw new RuntimeException("unable to sign manifest", ex);
        }
    }
}
