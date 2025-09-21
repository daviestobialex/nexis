/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.net;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.LengthFieldPrepender;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;
import org.nexus.handlers.ConnectionHandler;
import org.nexus.handlers.PingPongConnectionHandler;

/**
 *
 * @author daviestobialex
 */
public class NioServer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel ch) {
        /**
         * a frame structure for reading the data
         * <br/>
         * size is at 1024 of the max frame length to be received from the tcp
         * stream layer
         * <br/>
         * initial bytes to strip out before other handlers 4
         */
        ch.pipeline().addLast(new LengthFieldBasedFrameDecoder(1024, 0, 4, 0, 4),
                new LengthFieldPrepender(4),//
                new StringDecoder(),
                new StringEncoder(),
                new ConnectionHandler(),
                new PingPongConnectionHandler()
        );
    }

}
