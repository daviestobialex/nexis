/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.net;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import java.time.Duration;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.Identity;
import org.nexis.base.NexusNetwork;
import org.nexis.core.NexusBootstrap;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.PeerGroup;

/**
 *
 * @author daviestobialex
 */
public class DnsDiscovery {

    private final NexusNetwork network;
    private final ChannelInitializer connectionServer;
    private final EventLoopGroup group;
    private final Identity identity;

    public DnsDiscovery(
            NexusNetwork network,
            EventLoopGroup group,
            ChannelInitializer connectionServer,
            Identity identity) {
        this.network = network;
        this.group = group;
        this.connectionServer = connectionServer;
        this.identity = identity;
    }

    public void seedPeers(int maxConnections, boolean propagate) {

        // connect to peers and seed
        PeerGroup peer = new PeerGroup(
                network, group, maxConnections, connectionServer);
        peer.seed();
        try {
            // begin message propagagtions to active peers, a class would handle this
            Thread.sleep(Duration.ofSeconds(10));
        } catch (InterruptedException ex) {
            Logger.getLogger(NexusBootstrap.class.getName()).log(Level.SEVERE, null, ex);
        }

        if (propagate) {
            peer.initiateHandshakeWithPeers(new NexusEnvelopBuilder(identity));
        }
    }
}
