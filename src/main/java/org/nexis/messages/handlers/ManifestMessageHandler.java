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

import io.netty.channel.ChannelHandlerContext;
import java.util.logging.Logger;
import org.nexis.base.NetworkConfiguration;
import org.nexis.core.ManifestRegistry;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.NodeId;
import org.nexis.internal.MessageHandler;
import org.nexis.messages.GetPeersRequestMessage;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class ManifestMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(ManifestMessageHandler.class.getName());

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    public static final int NUMBER_OF_PEERS_TO_GET = 10;

    public ManifestMessageHandler(NexusEnvelopBuilder builder, NetworkConfiguration params) {
        this.builder = builder;
        this.params = params;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasManifest();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        LOGGER.info("Received manifest message");

        NodeId nodeServerId = builder.getNode().getNodeId(builder.getNode().getKeyPair().getPublic().getEncoded());

        // persist manifest CID to category against CID(IPFS) manifest registry
        String category = envelop.getMessage().getManifest().getCategory();
        String cid = envelop.getMessage().getManifest().getCid().toString();
        ManifestRegistry.getInstance().put(category, cid);

        //send out get peers request
        NexusProtocol.GetPeers getPeers
                = NexusProtocol.GetPeers.newBuilder()
                        .setSize(NUMBER_OF_PEERS_TO_GET)
                        .build();

        GetPeersRequestMessage getPeersRequest = new GetPeersRequestMessage(
                NexusNetworkConfiguration.of(params.getNetwork()),
                getPeers,
                nodeServerId.getId()
        );

        ctx.writeAndFlush(builder.build(getPeersRequest));
    }
}
