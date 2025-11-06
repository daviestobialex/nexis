/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages.handlers;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.List;
import org.nexis.base.Sha256Hash;
import org.nexis.internal.MessageHandler;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class GetHeaderMessageHandler implements MessageHandler {

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasGetHeader();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        NexusProtocol.Header headers = envelop.getMessage().getGetHeader();
        List<ByteString> hashes = headers.getHashList();

        List<Sha256Hash> hashList = new ArrayList<>();
        for (int i = 0; i < hashes.size(); i++) {
            hashList.add(Sha256Hash.of(hashes.get(i).toByteArray()));
        }
        Sha256Hash stopHash = Sha256Hash.of(hashes.get(hashes.size() - 1).toByteArray());
    }

    @Override
    public void sendMessage() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

}
