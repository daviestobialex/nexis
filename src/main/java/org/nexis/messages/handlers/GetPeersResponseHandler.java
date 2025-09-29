/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
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
