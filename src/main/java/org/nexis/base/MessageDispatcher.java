/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.base;

import io.netty.channel.ChannelHandlerContext;
import java.util.ArrayList;
import java.util.List;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class MessageDispatcher {

    private final List<MessageHandler> handlers = new ArrayList<>();

    public void registerHandler(MessageHandler handler) {
        handlers.add(handler);
    }

    public void dispatch(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) throws Exception {
        for (MessageHandler handler : handlers) {
            if (handler.canHandle(envelop.getMessage())) {
                handler.handle(envelop, ctx);
                return;
            }
        }
        throw new UnsupportedOperationException("No handler found for message: " + envelop.getMessage());
    }
}
