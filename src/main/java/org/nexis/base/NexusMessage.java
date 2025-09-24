/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.base;

import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.internal.ByteUtils;

/**
 * This represents properties of a nexus message
 *
 * @author daviestobialex
 */
public interface NexusMessage {

    byte[] nodeId();

    NexusProtocol.NexusMessage message();

    byte[] serialize();

    byte[] checkSum();

    static byte[] computeChecksum(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] firstHash = digest.digest(data);
            byte[] secondHash = digest.digest(firstHash);
            return Arrays.copyOfRange(secondHash, 0, 4); // first 4 bytes as checksum
        } catch (Exception e) {
            throw new RuntimeException("Unable to compute checksum", e);
        }
    }

    public default byte[] getByteConcatenatedPayload(int magicBytes) {
        byte[] magic = ByteUtils.writInt32BE(magicBytes);
        ByteBuffer buffer = ByteBuffer.allocate(magic.length + message().toByteArray().length + nodeId().length);
        buffer.put(ByteUtils.writInt32BE(magicBytes));
        buffer.put(message().toByteArray());
        buffer.put(nodeId());
        return buffer.array();
    }
}
