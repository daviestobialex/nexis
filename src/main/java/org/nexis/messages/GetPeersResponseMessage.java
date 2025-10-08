/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages;

import org.nexis.base.AbstractNexusMessage;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class GetPeersResponseMessage extends AbstractNexusMessage {

    protected final NexusProtocol.GetPeersResponse peers;

    public GetPeersResponseMessage(
            NexusNetworkConfiguration params,
            NexusProtocol.GetPeersResponse getPeers,
            byte[] nodeId) {
        super(params, nodeId);
        this.peers = getPeers;
    }

    @Override
    public NexusProtocol.NexusMessage getProtobufMessage() {
        return NexusProtocol.NexusMessage.newBuilder()
                .setPeers(peers) // wrap Manifest into NexusMessage
                .build();
    }
}
