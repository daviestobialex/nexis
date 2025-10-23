/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages.handlers;

import io.netty.channel.ChannelHandlerContext;
import org.nexis.core.Block;
import org.nexis.internal.MessageHandler;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class BlockMessageHandler  implements MessageHandler {

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasBlock();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        
        NexusProtocol.Block protoblock = envelop.getMessage().getBlock();
        
        Block block = Block.read(protoblock);
    }
    
}
