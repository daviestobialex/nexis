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
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import java.util.logging.Logger;
import org.nexis.base.Manifest;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.NexusNetwork;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.PeerGroup;
import org.nexis.internal.MessageHandler;
import org.nexis.net.NioProtoServer;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class GetPeersResponseHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(ManifestMessageHandler.class.getName());

    private final EventLoopGroup group;
    private final ChannelInitializer connectionServer;

    public GetPeersResponseHandler(
            NexusEnvelopBuilder builder,
            NetworkConfiguration params,
            EventLoopGroup group,
            Manifest manifest) {
        this.group = group;
        this.connectionServer = new NioProtoServer(params, builder.getNode(), manifest);
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasPeers();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {

        // trigger connection to peers functions
        envelop.getMessage().getPeers().getAddressesList().stream()
                .forEach(address -> {
                    PeerGroup peer = new PeerGroup(
                            NexusNetwork.fromIdString(address).get(),
                            group, connectionServer);
                });

    }

}
