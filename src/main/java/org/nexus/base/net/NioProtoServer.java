/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.base.net;

import io.netty.channel.Channel;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import java.util.Set;
import org.nexus.base.handlers.ProtoConnectionHandler;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class NioProtoServer extends ChannelInitializer<SocketChannel> {

    @Override
    protected void initChannel(SocketChannel ch) {

        ch.pipeline().addLast(
                new ProtobufVarint32FrameDecoder(), // handles protobuf's varint length prefix
                new ProtobufDecoder(NexusProtocol.NexusMessage.getDefaultInstance()), // incoming -> POJO
                new ProtobufVarint32LengthFieldPrepender(),
                new ProtobufEncoder(),
                new ProtoConnectionHandler()
        );
    }

}
