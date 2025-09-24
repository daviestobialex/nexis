/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.handlers.message;

import io.netty.channel.ChannelHandlerContext;
import java.util.logging.Logger;
import org.nexis.base.MessageHandler;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class ManifestMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(ManifestMessageHandler.class.getName());

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasManifest();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        LOGGER.info("Received manifest from org=" + envelop.getMessage().getManifest().getOrgName());
    }
}
