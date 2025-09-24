/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.validator;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.logging.Logger;
import org.nexis.base.MessageValidator;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.NexusMessage;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.internal.ByteUtils;

/**
 *
 * @author daviestobialex
 */
public class ChecksumValidator implements MessageValidator {

    private static final Logger LOGGER = Logger.getLogger(ChecksumValidator.class.getName());
    private final NetworkConfiguration params;

    public ChecksumValidator(NetworkConfiguration params) {
        this.params = params;
    }

    @Override
    public boolean supports(NexusProtocol.NexusMessage message) {
        return true; // applies to all messages
    }

    @Override
    public void validate(NexusProtocol.NexusEnvelop envelop) {
        NexusProtocol.NexusMessage message = envelop.getMessage();
        byte[] payload = message.toByteArray();
        byte[] networkBytes = ByteUtils.writInt32BE(params.getPacketMagic());
        byte[] nodeId = envelop.getNodeId().toByteArray();
        byte[] messageId = envelop.getMessageId().toByteArray();

        ByteBuffer buffer = ByteBuffer.allocate(payload.length + networkBytes.length + messageId.length + nodeId.length);
        buffer.put(networkBytes);
        buffer.put(payload);
        buffer.put(nodeId);
        buffer.put(messageId);
        byte[] computedChecksum = NexusMessage.computeChecksum(buffer.array());

        // verify computedChecksum
        if (!Arrays.equals(computedChecksum, envelop.getChecksum().toByteArray())) {
            throw new SecurityException("Payload checksum mismatch");
        }
    }
}
