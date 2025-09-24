/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.base;

import io.netty.channel.ChannelHandlerContext;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public interface MessageHandler {

    boolean canHandle(NexusProtocol.NexusMessage message);

    void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) throws Exception;
}
