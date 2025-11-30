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
public class FunctionCallResponseMessage extends AbstractNexusMessage {

    private final NexusProtocol.Result result;

    public FunctionCallResponseMessage(
            NexusNetworkConfiguration params,
            NexusProtocol.Result result,
            byte[] nodeId) {
        super(params, nodeId);
        this.result = result;
    }

    @Override
    protected NexusProtocol.NexusMessage getProtobufMessage() {
        return NexusProtocol.NexusMessage.newBuilder()
                .setFunctionResult(result)
                .build();

    }

}
