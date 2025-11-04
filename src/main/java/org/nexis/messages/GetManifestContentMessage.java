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
public class GetManifestContentMessage extends AbstractNexusMessage {

    protected final NexusProtocol.GetManifestContent getManifestContent;

    public GetManifestContentMessage(
            NexusNetworkConfiguration params,
            NexusProtocol.GetManifestContent manifest,
            byte[] nodeId) {
        super(params, nodeId);
        this.getManifestContent = manifest;
    }

    @Override
    public NexusProtocol.NexusMessage getProtobufMessage() {
        return NexusProtocol.NexusMessage.newBuilder()
                .setGetManifestContent(getManifestContent) // wrap Manifest into NexusMessage
                .build();
    }
}
