/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import java.io.IOException;
import java.util.logging.Logger;
import org.nexis.base.MessageDispatcher;
import org.nexis.base.NexusEnvelopBuilder;
import org.nexis.base.NodeIdentity;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.core.ValidationPipeline;
import org.nexis.handlers.message.ChallangeResponseHandler;
import org.nexis.handlers.message.HandshakeMessageHandler;
import org.nexis.handlers.message.ManifestMessageHandler;
import org.nexis.handlers.message.PingMessageHandler;
import org.nexis.validator.ChecksumValidator;
import org.nexis.validator.SignatureValidator;

/**
 *
 * @author daviestobialex
 */
public class ProtoConnectionHandler extends SimpleChannelInboundHandler<NexusProtocol.NexusEnvelop> {

    private static final Logger LOGGER = Logger.getLogger(ProtoConnectionHandler.class.getName());
    private final ValidationPipeline pipeline;
    private final MessageDispatcher dispatcher;

    public ProtoConnectionHandler(
            org.nexis.base.NetworkConfiguration params,
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
        LOGGER.info("MESSAGE RECIVED== \n"
                + "Handshake: " + msg.getMessage().hasHandshake()
                + "\nChallenge Response:  " + msg.getMessage().hasChallenge());

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
