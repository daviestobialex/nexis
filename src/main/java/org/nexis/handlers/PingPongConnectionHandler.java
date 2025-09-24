/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 *
 * @author daviestobialex
 */
public class PingPongConnectionHandler extends SimpleChannelInboundHandler<String> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, String msg) throws Exception {
        System.out.println("Client received: " + msg);

        Thread.startVirtualThread(() -> {
            String response = "PING".equalsIgnoreCase(msg) ? "PONG" : null;
            if (response != null) {
                ctx.writeAndFlush(response);
            }
        });
    }

}
