/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.base.messages;

import java.nio.ByteBuffer;
import java.util.UUID;
import org.nexus.base.NexusMessage;
import org.nexus.base.proto.NexusProtocol;
import org.nexus.internal.ByteUtils;
import org.nexus.networks.NexusNetworkConfiguration;

/**
 *
 * @author daviestobialex
 */
public class ManifestRequestMessage implements NexusMessage {

    protected final UUID id;
    protected final NexusProtocol.Manifest manifest;
    protected final byte[] nodeId;
    protected final NexusNetworkConfiguration params;

    public ManifestRequestMessage(
            NexusNetworkConfiguration params,
            NexusProtocol.Manifest manifest,
            byte[] nodeId) {
        id = UUID.randomUUID();
        this.manifest = manifest;
        this.nodeId = nodeId;
        this.params = params;
    }

    @Override
    public byte[] messageId() {
        return ByteUtils.uuidToBytes(id);
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
        ByteBuffer buffer = ByteBuffer.allocate(256);

        byte[] payload = getByteConcatenatedPayload(buffer, params.getPacketMagic());
        byte[] checksum = NexusMessage.computeChecksum(payload);

        buffer = ByteBuffer.allocate(256);
        buffer.put(payload);
        buffer.put(checksum);

        return buffer.array();
    }

    @Override
    public byte[] checkSum() {
        ByteBuffer buffer = ByteBuffer.allocate(256);

        byte[] payload = getByteConcatenatedPayload(buffer, params.getPacketMagic());
        return NexusMessage.computeChecksum(payload);
    }

}
