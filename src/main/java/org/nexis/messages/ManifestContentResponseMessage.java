/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages;

import org.nexis.core.AbstractNexusMessage;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class ManifestContentResponseMessage extends AbstractNexusMessage {

    protected final NexusProtocol.ManifestContent manifestContent;

    public ManifestContentResponseMessage(
            NexusNetworkConfiguration params,
            NexusProtocol.ManifestContent manifestContent,
            byte[] nodeId) {
        super(params, nodeId);
        this.manifestContent = manifestContent;
    }

    @Override
    public NexusProtocol.NexusMessage getProtobufMessage() {
        return NexusProtocol.NexusMessage.newBuilder()
                .setManifestContent(manifestContent) // wrap Manifest into NexusMessage
                .build();
    }
}
