/*
 * Small helper message wrapper for sending GetHeaders (getHeader) requests.
 */
package org.nexis.messages;

import org.nexis.core.AbstractNexusMessage;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.networks.NexusNetworkConfiguration;

/**
 * Wraps a protobuf Header message into a NexusMessage.getHeader field.
 */
public class GetHeadersRequestMessage extends AbstractNexusMessage {

    protected final NexusProtocol.Header header;

    public GetHeadersRequestMessage(NexusNetworkConfiguration params, NexusProtocol.Header headers, byte[] nodeId) {
        super(params, nodeId);
        this.header = headers;
    }

    @Override
    protected NexusProtocol.NexusMessage getProtobufMessage() {
        return NexusProtocol.NexusMessage.newBuilder()
                .setGetHeader(header)
                .build();
    }
}
