/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages;

import java.nio.ByteBuffer;
import java.util.UUID;
import org.nexis.base.NexusMessage;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class GetPeersRequestMessage implements NexusMessage {

    protected final UUID id;
    protected final NexusProtocol.GetPeers getPeers;
    protected final byte[] nodeId;
    protected final NexusNetworkConfiguration params;

    public GetPeersRequestMessage(
            NexusNetworkConfiguration params,
            NexusProtocol.GetPeers getPeers,
            byte[] nodeId) {
        id = UUID.randomUUID();
        this.getPeers = getPeers;
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
                .setPeerDiscovery(getPeers) // wrap Manifest into NexusMessage
                .build();
    }

    @Override
    public byte[] serialize() {
        // payload(magicBytes + message + nodeId + msgId) + checksum

        byte[] payload = getByteConcatenatedPayload(params.getPacketMagic());
        byte[] checksum = NexusMessage.computeChecksum(payload);
        System.out.println("PAYOAD  " + java.util.Base64.getEncoder().encodeToString(payload));
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
