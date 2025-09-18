///*
// * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
// * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
// */
//package org.nexus.base.net;
//
//import io.netty.channel.Channel;
//import io.netty.channel.ChannelInitializer;
//import io.netty.channel.ChannelPipeline;
//import io.netty.channel.socket.SocketChannel;
//import java.util.Set;
//
///**
// * not tested to fullest yet
// * @author daviestobialex
// */
//public class NioProtocolTypeManager extends ChannelInitializer<SocketChannel> {
//
//    final Set<Channel> activePeers;
//
//    public NioProtocolTypeManager(Set<Channel> activePeers) {
//        this.activePeers = activePeers;
//    }
//
//    @Override
//    protected void initChannel(SocketChannel ch) {
//        ChannelPipeline p = ch.pipeline();
//        p.addLast(new NioProtoServer(activePeers));
//        p.addLast(new NioServer(activePeers));
//    }
//}
