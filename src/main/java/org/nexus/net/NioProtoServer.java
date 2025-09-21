/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.net;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.nexus.base.MessageDispatcher;
import org.nexus.base.NetworkConfiguration;
import org.nexus.base.NexusEnvelopBuilder;
import org.nexus.base.NodeIdentity;
import org.nexus.handlers.ProtoConnectionHandler;
import org.nexus.base.proto.NexusProtocol;
import org.nexus.core.ValidationPipeline;

/**
 *
 * @author daviestobialex
 */
public class NioProtoServer extends ChannelInitializer<SocketChannel> {

    private final NetworkConfiguration params;
    private final NodeIdentity identity;

    public NioProtoServer(NetworkConfiguration params, NodeIdentity identity) {
        this.params = params;
        this.identity = identity;
    }

    @Override
    protected void initChannel(SocketChannel ch) {

        ch.pipeline().addLast(new ProtobufVarint32FrameDecoder(), // handles protobuf's varint length prefix
                new ProtobufDecoder(NexusProtocol.NexusEnvelop.getDefaultInstance()), // incoming -> POJO
                new ProtobufVarint32LengthFieldPrepender(),
                new ProtobufEncoder(),
                new ProtoConnectionHandler(
                        params,
                        identity,
                        new NexusEnvelopBuilder(identity),
                        new ValidationPipeline(),
                        new MessageDispatcher())
        );
    }

}
