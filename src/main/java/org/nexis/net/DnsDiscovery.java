/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.net;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import org.nexis.base.Identity;
import org.nexis.base.NexusNetwork;
import org.nexis.base.StreamConnection;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.PeerGroup;
import org.nexis.core.PeerRegistry;
import org.nexis.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class DnsDiscovery {

    private final NexusNetwork network;
    private final Identity identity;
    private final StreamConnection connection;

    public DnsDiscovery(
            NexusNetworkConfiguration network,
            EventLoopGroup group,
            StreamConnection connection,
            Identity identity) {
        this.network = network.getNetwork();
        this.identity = identity;
        this.connection = connection;
    }

    public void seedPeers(int maxConnections, boolean propagate) {

        // connect to peers and seed
        PeerGroup peer = new PeerGroup(
                network,
                maxConnections,
                connection);

        peer.seed();

        PeerRegistry.getInstance().onActivePeerConnected(connectedChannel -> {
            System.out.println("===Active Peer connected====");
            if (propagate) {
                peer.initiateHandshakeWithPeers(new NexusEnvelopBuilder(identity), connectedChannel);
            }
        });

    }
}
