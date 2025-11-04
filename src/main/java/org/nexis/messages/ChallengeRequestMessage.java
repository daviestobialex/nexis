/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages;

import org.nexis.core.AbstractNexusMessage;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class ChallengeRequestMessage extends AbstractNexusMessage {

    protected final NexusProtocol.Challenge handshake;

    public ChallengeRequestMessage(
            NexusNetworkConfiguration params,
            NexusProtocol.Challenge handshake,
            byte[] nodeId) {
        super(params, nodeId);
        this.handshake = handshake;
    }

    @Override
    public NexusProtocol.NexusMessage getProtobufMessage() {
        return NexusProtocol.NexusMessage.newBuilder()
                .setHandshake(handshake) // wrap Manifest into NexusMessage
                .build();
    }
}
