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
public class GetPeersRequestMessage extends AbstractNexusMessage {

    protected final NexusProtocol.GetPeers getPeers;

    public GetPeersRequestMessage(
            NexusNetworkConfiguration params,
            NexusProtocol.GetPeers getPeers,
            byte[] nodeId) {
        super(params, nodeId);
        this.getPeers = getPeers;
    }

    @Override
    public NexusProtocol.NexusMessage getProtobufMessage() {
        return NexusProtocol.NexusMessage.newBuilder()
                .setPeersDiscovery(getPeers) // wrap Manifest into NexusMessage
                .build();
    }
}
