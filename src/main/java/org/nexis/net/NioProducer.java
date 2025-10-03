/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import org.nexis.base.StreamConnection;
import org.nexis.core.Peer;
import org.nexis.listeners.PeerConnectListener;

/**
 *
 * @author daviestobialex
 */
public class NioProducer implements StreamConnection {

    private final EventLoopGroup group;
    private String host;
    private int port;
    private final Bootstrap b;

    public NioProducer(ChannelInitializer channelInitializer, EventLoopGroup group, String host, int port) {
        this.group = group;
        this.host = host;
        this.port = port;
        b = new Bootstrap();

        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(channelInitializer);
    }

    public NioProducer(ChannelInitializer channelInitializer, EventLoopGroup group) {
        this.group = group;
        b = new Bootstrap();

        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(channelInitializer);
    }

    @Override
    public void connectionClosed() {
        group.close();
    }

    @Override
    public StreamConnection connectionOpened() {
        return connectToNetwork();
    }

    private StreamConnection connectToNetwork() {
        System.out.println("===connectToNetwork default====");
        EventLoop eventLoop = group.next();

        InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);
        b.connect(inetSocketAddress)
                .addListener(new PeerConnectListener(
                        inetSocketAddress,
                        b,
                        eventLoop,
                        10,
                        10,
                        new Peer(host)));
        return this;

    }

    @Override
    public StreamConnection connectionOpened(String host, int port) {

        EventLoop eventLoop = group.next();

        InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);
        b.connect(inetSocketAddress)
                .addListener(new PeerConnectListener(
                        inetSocketAddress,
                        b,
                        eventLoop,
                        10,
                        10,
                        new Peer(host)));
        return this;
    }
}
