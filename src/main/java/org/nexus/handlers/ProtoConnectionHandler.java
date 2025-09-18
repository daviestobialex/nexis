/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.util.logging.Logger;
import org.nexus.base.proto.NexusProtocol;
import org.nexus.listeners.PeerConnectListener;

/**
 *
 * @author daviestobialex
 */
public class ProtoConnectionHandler extends SimpleChannelInboundHandler<NexusProtocol.NexusEnvelop> {

    private static final Logger LOGGER = Logger.getLogger(PeerConnectListener.class.getName());

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("New peer connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NexusProtocol.NexusEnvelop msg) {
        // TODO: parse your P2P protocol message types here
        LOGGER.info("MESSAGE RECIVED==");
        if (msg.getMessage().hasPing() && msg.getMessage().getPing().getPing() == 1) {

            Thread.startVirtualThread(() -> {
                NexusProtocol.Ping pong
                        = NexusProtocol.Ping.newBuilder()
                                .setPing(msg.getMessage().getPing().getPing() + 1)
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
