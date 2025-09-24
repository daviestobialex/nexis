/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.handlers.message;

import io.netty.channel.ChannelHandlerContext;
import org.nexis.base.MessageHandler;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class PingMessageHandler implements MessageHandler {

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasPing();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        int pingValue = envelop.getMessage().getPing().getPing();
        NexusProtocol.Ping pong = NexusProtocol.Ping.newBuilder()
                .setPing(pingValue + 1)
                .build();

        ctx.writeAndFlush(
                NexusProtocol.NexusEnvelop.newBuilder()
                        .setMessage(NexusProtocol.NexusMessage.newBuilder().setPing(pong))
                        .build()
        );
    }
}
