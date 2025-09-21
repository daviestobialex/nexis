/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.io.IOException;
import java.util.logging.Logger;
import org.nexus.base.MessageDispatcher;
import org.nexus.base.NexusEnvelopBuilder;
import org.nexus.base.NodeIdentity;
import org.nexus.base.proto.NexusProtocol;
import org.nexus.core.ValidationPipeline;
import org.nexus.handlers.message.ChallangeResponseHandler;
import org.nexus.handlers.message.HandshakeMessageHandler;
import org.nexus.handlers.message.ManifestMessageHandler;
import org.nexus.handlers.message.PingMessageHandler;
import org.nexus.validator.ChecksumValidator;
import org.nexus.validator.SignatureValidator;

/**
 *
 * @author daviestobialex
 */
public class ProtoConnectionHandler extends SimpleChannelInboundHandler<NexusProtocol.NexusEnvelop> {

    private static final Logger LOGGER = Logger.getLogger(ProtoConnectionHandler.class.getName());
    private final ValidationPipeline pipeline;
    private final MessageDispatcher dispatcher;

    public ProtoConnectionHandler(
            org.nexus.base.NetworkConfiguration params,
            NodeIdentity identity,
            NexusEnvelopBuilder builder,
            ValidationPipeline pipeline,
            MessageDispatcher dispatcher) {
        this.pipeline = pipeline;
        this.dispatcher = dispatcher;

        // add pipeline validators
        pipeline.addValidator(new ChecksumValidator(params));
        pipeline.addValidator(new SignatureValidator(params));

        // add dispatchers
        dispatcher.registerHandler(new ManifestMessageHandler());
        dispatcher.registerHandler(new HandshakeMessageHandler(identity, builder, params));
        dispatcher.registerHandler(new PingMessageHandler());
        dispatcher.registerHandler(new ChallangeResponseHandler(identity, builder, params));
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        LOGGER.info("New proto peer connected: " + ctx.channel().remoteAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NexusProtocol.NexusEnvelop msg) throws IOException, Exception {
        // Always validate before dispatch
        pipeline.validate(msg);

        //  Dispatch to correct handler
        dispatcher.dispatch(msg, ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOGGER.info("Disconnected from proto peer: " + ctx.channel().remoteAddress());
    }
}
