/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages;

import java.nio.ByteBuffer;
import org.nexis.core.AbstractNexusMessage;
import org.nexis.core.NexusMessage;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class PingRequestMessage extends AbstractNexusMessage {

    protected final NexusProtocol.Ping ping;

    public PingRequestMessage(
            NexusNetworkConfiguration params,
            NexusProtocol.Ping ping,
            byte[] nodeId) {
        super(params, nodeId);
        this.ping = ping;
    }

    @Override
    public NexusProtocol.NexusMessage getProtobufMessage() {
        return NexusProtocol.NexusMessage.newBuilder()
                .setPing(ping) // wrap Manifest into NexusMessage
                .build();
    }
}
