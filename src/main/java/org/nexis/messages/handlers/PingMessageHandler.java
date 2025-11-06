/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages.handlers;

import io.netty.channel.ChannelHandlerContext;
import org.nexis.internal.MessageHandler;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class PingMessageHandler implements MessageHandler {

    private int pingValue;
    private ChannelHandlerContext ctx;

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasPing();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        pingValue = envelop.getMessage().getPing().getPing();
        this.ctx = ctx;
    }

    @Override
    public void sendMessage() {
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
