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
import java.io.IOException;
import java.math.BigInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.NetworkConfiguration;
import org.nexis.core.ManifestRegistry;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.NodeId;
import org.nexis.core.PeerRegistry;
import org.nexis.internal.MessageHandler;
import org.nexis.messages.GetManifestContentRequest;
import org.nexis.messages.ManifestContentResponseMessage;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexis.store.Storage;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class GetManifestContentMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(GetManifestContentMessageHandler.class.getName());

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    private final Storage manifestStore;

    public GetManifestContentMessageHandler(
            NexusEnvelopBuilder builder,
            NetworkConfiguration params) {
        this.builder = builder;
        this.params = params;
        this.manifestStore = ManifestRegistry.getInstance().getStore();
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasGetManifestContent();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        NodeId nodeServerId = builder.getNode().getNodeId();

        // check manifest index for cid
        BigInteger cid = new BigInteger(envelop.getMessage().getGetManifestContent().getCid().toByteArray());
        try {
            byte[] rawJson = manifestStore.get(cid);
            // if found return manifest
            if (rawJson != null) {
                NexusProtocol.ManifestContent manifestContent = NexusProtocol.ManifestContent.newBuilder()
                        .setCid(envelop.getMessage().getGetManifestContent().getCid())
                        .setRaw(ByteString.copyFrom(rawJson))
                        .build();

                ManifestContentResponseMessage manifestContentResponseMessage
                        = new ManifestContentResponseMessage(
                                NexusNetworkConfiguration.of(params.getNetwork()),
                                manifestContent,
                                nodeServerId.getId());

                ctx.writeAndFlush(builder.build(manifestContentResponseMessage));
            } else {
                // else forward to all active peers
                PeerRegistry.getInstance()
                        .getActivePeers()
                        .forEach(peerConnection -> {
                    NexusProtocol.GetManifestContent getManifestContent = NexusProtocol.GetManifestContent.newBuilder()
                            .setCid(envelop.getMessage().getGetManifestContent().getCid())
                            .build();

                    GetManifestContentRequest getManifestContentRequest = new GetManifestContentRequest(
                            NexusNetworkConfiguration.of(params.getNetwork()),
                            getManifestContent,
                            nodeServerId.getId());

                    peerConnection.channel().writeAndFlush(builder.build(getManifestContentRequest));
                });
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "error fetching manifest from store");
        }
    }
}
