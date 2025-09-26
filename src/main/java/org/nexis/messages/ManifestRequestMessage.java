/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages;

import java.nio.ByteBuffer;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.NexusMessage;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class ManifestRequestMessage implements NexusMessage {

    protected final NexusProtocol.Manifest manifest;
    protected final byte[] nodeId;
    protected final NetworkConfiguration params;

    public ManifestRequestMessage(
            NexusNetworkConfiguration params,
            NexusProtocol.Manifest manifest,
            byte[] nodeId) {
        this.manifest = manifest;
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
                .setManifest(manifest) // wrap Manifest into NexusMessage
                .build();
    }

    @Override
    public byte[] serialize() {
        // payload(magicBytes + message + nodeId + msgId) + checksum

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
