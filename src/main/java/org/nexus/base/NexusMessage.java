/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.base;

import com.google.protobuf.GeneratedMessage;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.UUID;
import org.nexus.base.proto.NexusProtocol;
import org.nexus.internal.ByteUtils;

/**
 * This represents properties of a nexus message
 *
 * @author daviestobialex
 */
public interface NexusMessage {

    byte[] messageId();     // for correlation

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

    public default byte[] getByteConcatenatedPayload(ByteBuffer buffer, int magicBytes) {
        buffer.put(ByteUtils.writInt32BE(magicBytes));
        buffer.put(message().toByteArray());
        buffer.put(nodeId());
        buffer.put(messageId());
        return buffer.array();
    }
}
