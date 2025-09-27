/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.net.handlers;

/**
 *
 * @author daviestobialex
 */
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

public class ConnectionHandler extends SimpleChannelInboundHandler<byte[]> {

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        System.out.println("New peer connected: " + ctx.channel().remoteAddress());
//        manager.broadcast("hello from " + ctx.channel().localAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, byte[] msg) {
        System.out.println("Message from peer: " + new String(msg));
        // TODO: parse your P2P protocol message types here
        // MessageHandler could it be blocking or non blocking here
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        System.out.println("Disconnected from peer: " + ctx.channel().remoteAddress());
    }
}
