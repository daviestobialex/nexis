/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.base.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class ProtoConnectionHandler extends SimpleChannelInboundHandler<NexusProtocol.NexusMessage> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("New peer connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NexusProtocol.NexusMessage msg) {
        // TODO: parse your P2P protocol message types here
        if (msg.hasPing() && msg.getPing().getPing() == 1) {

            Thread.startVirtualThread(() -> {
                NexusProtocol.Ping pong
                        = NexusProtocol.Ping.newBuilder()
                                .setPing(msg.getPing().getPing() + 1)
                                .build();
                System.out.println("PONG");
                ctx.writeAndFlush(pong);

            });
        }

        // MessageHandler could it be blocking or non blocking here
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("Disconnected from peer: " + ctx.channel().remoteAddress());
//        activePeers.remove(ctx.channel());// remove from list
    }
}
