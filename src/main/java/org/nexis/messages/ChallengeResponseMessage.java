/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages;

import java.nio.ByteBuffer;
import org.nexis.base.NexusMessage;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class ChallengeResponseMessage implements NexusMessage {

    protected final NexusProtocol.ChallengeResponse challenge;
    protected final byte[] nodeId;
    protected final NexusNetworkConfiguration params;

    public ChallengeResponseMessage(
            NexusNetworkConfiguration params,
            NexusProtocol.ChallengeResponse challenge,
            byte[] nodeId) {
        this.challenge = challenge;
        this.nodeId = nodeId;
        this.params = params;
    }

    @Override
    public byte[] nodeId() {
        return nodeId;
    }

    @Override
    public NexusProtocol.NexusMessage message() {
        return NexusProtocol.NexusMessage.newBuilder()
                .setHandshakeResponse(challenge) // wrap challenge into NexusMessage
                .build();
    }

    @Override
    public byte[] serialize() {
        // payload(magicBytes + message + nodeId) + checksum

        byte[] payload = getByteConcatenatedPayload(params.getPacketMagic());
        byte[] checksum = NexusMessage.computeChecksum(payload);
        System.out.println("PAYOAD LEN " + payload.length + " checksum " + checksum.length);
        ByteBuffer buffer = ByteBuffer.allocate(payload.length + checksum.length);
        buffer.put(payload);
        buffer.put(checksum);

        return buffer.array();
    }

    @Override
    public byte[] checkSum() {
        byte[] payload = getByteConcatenatedPayload(params.getPacketMagic());
        return NexusMessage.computeChecksum(payload);
    }

}
