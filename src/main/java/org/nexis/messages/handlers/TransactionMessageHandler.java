/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages.handlers;

import io.netty.channel.ChannelHandlerContext;
import org.nexis.core.Block;
import org.nexis.core.Transaction;
import org.nexis.internal.MessageHandler;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class TransactionMessageHandler implements MessageHandler {

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasTransaction();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {

        NexusProtocol.Transaction protoTransaction = envelop.getMessage().getTransaction();

        Transaction transaction = Transaction.read(protoTransaction);
    }

    @Override
    public void sendMessage() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

}
