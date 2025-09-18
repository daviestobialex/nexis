/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.base.core;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.EventLoop;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;
import org.nexus.base.NexusNetwork;
import org.nexus.base.listeners.PeerConnectListener;
import org.nexus.base.net.NioProtoServer;
import org.nexus.base.networks.NexusNetworkParams;

/**
 *
 * @author daviestobialex
 */
public class PeerGroup {

    // Currently active peers. This is an ordered list rather than a set to make unit tests predictable.
    private final CopyOnWriteArraySet<Channel> peers = new CopyOnWriteArraySet<>();

    /**
     * Creates a PeerGroup for the given network.No chain is provided so this
     * node will report its chain height as zero to other peers. This
     * constructor is useful if you just want to explore the network but aren't
     * interested in downloading block data.
     *
     * @param network the P2P network to connect to
     * @param group
     */
    public PeerGroup(NexusNetwork network, EventLoopGroup group) {
        this(NexusNetworkParams.of(Objects.requireNonNull(network)), group);

    }

    public PeerGroup(NexusNetwork network, EventLoopGroup group, int maxConnections) {
        this(NexusNetworkParams.of(Objects.requireNonNull(network)), group);
    }

    protected PeerGroup(NexusNetworkParams params, EventLoopGroup group) {

        String host = params.getNetwork().id();
        int port = params.getPort();

        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(new NioProtoServer());

        EventLoop eventLoop = group.next();

        b.connect(new InetSocketAddress(host, port))
                .addListener(new PeerConnectListener(
                        params,
                        b,
                        eventLoop,
                        peers,
                        10,
                        10));
    }
}
