/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages.handlers;

import io.netty.channel.ChannelHandlerContext;
import org.nexis.core.Block;
import org.nexis.core.ManifestBlock;
import org.nexis.internal.MessageHandler;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class ManifestBlockMessageHandler  implements MessageHandler {

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasManifestBlok();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        
        NexusProtocol.ManifestBlock manifestBlok = envelop.getMessage().getManifestBlok();
        
        Block block = ManifestBlock.read(manifestBlok);
    }
    
}
