/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages;

import org.nexis.base.AbstractNexusMessage;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class ChallengeResponseMessage extends AbstractNexusMessage {

    protected final NexusProtocol.ChallengeResponse challenge;

    public ChallengeResponseMessage(
            NexusNetworkConfiguration params,
            NexusProtocol.ChallengeResponse challenge,
            byte[] nodeId) {
        super(params, nodeId);
        this.challenge = challenge;
    }

    @Override
    public NexusProtocol.NexusMessage getProtobufMessage() {
        return NexusProtocol.NexusMessage.newBuilder()
                .setHandshakeResponse(challenge) // wrap challenge into NexusMessage
                .build();
    }

}
