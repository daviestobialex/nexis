/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.net.handlers;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import java.io.IOException;
import java.util.logging.Logger;
import org.nexis.internal.MessageDispatcher;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.core.ValidationPipeline;
import org.nexis.exceptions.DropMessageException;
import org.nexis.utilities.VirtualThreadExecutor;
import org.nexis.exceptions.ProtocolException;

/**
 * {@code ProtoConnectionHandler} is the primary inbound handler for processing
 * protobuf-based messages over a Netty channel.
 *
 * <p>
 * It represents the "entry point" for peer-to-peer communication once a
 * connection has been established with another node in the Nexis network.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 * <li><b>Validation:</b> Ensures that every incoming message passes through a
 * {@link ValidationPipeline}, which applies a chain of validators such as
 * checksums and cryptographic signatures.</li>
 * <li><b>Dispatching:</b> Forwards validated messages to the
 * {@link MessageDispatcher}, which routes them to the appropriate message
 * handler (e.g., Manifest exchange, Handshake challenge, Ping/Pong, etc.).</li>
 * <li><b>Lifecycle Logging:</b> Logs when peers connect or disconnect, which is
 * useful for monitoring and debugging the peer network.</li>
 * </ul>
 *
 * <h2>Why This Class Exists</h2>
 * The Nexis protocol is message-driven and event-based. By leveraging Netty’s
 * {@link SimpleChannelInboundHandler}, this class isolates protobuf message
 * handling from the rest of the application logic. It centralizes:
 * <ul>
 * <li>Peer lifecycle events (connection, disconnection).</li>
 * <li>Validation enforcement before any processing occurs.</li>
 * <li>Decoupling message handling via the {@link MessageDispatcher}.</li>
 * </ul>
 *
 * <h2>Typical Flow</h2>
 * <ol>
 * <li>A new peer connects → {@link #channelActive(ChannelHandlerContext)} logs
 * the connection.</li>
 * <li>The peer sends a protobuf envelope →
 * {@link #channelRead0(ChannelHandlerContext, NexusProtocol.NexusEnvelop)}
 * validates it using the {@link ValidationPipeline}.</li>
 * <li>If valid, the message is dispatched to a registered
 * {@link org.nexis.handlers.message.MessageHandler} implementation.</li>
 * <li>If invalid, a {@link SecurityException} is thrown and the channel may be
 * closed depending on policy.</li>
 * <li>When the peer disconnects →
 * {@link #channelInactive(ChannelHandlerContext)} logs the removal of the
 * peer.</li>
 * </ol>
 *
 * <h2>Extensibility</h2>
 * - To add new message types, implement a new handler and register it with the
 * {@link MessageDispatcher}. - To add new validation rules, extend the
 * {@link ValidationPipeline} by adding another validator.
 *
 * <h2>Design Considerations</h2>
 * - This class enforces <b>fail-fast validation</b>: no message is ever
 * dispatched without passing through the pipeline first. - Keeps networking
 * concerns (Netty channel lifecycle) separate from business concerns (protocol
 * semantics). - Uses composition (pipeline + dispatcher) instead of
 * inheritance, encouraging modularity.
 *
 * @author daviestobialex
 */
public class ProtoConnectionHandler extends SimpleChannelInboundHandler<NexusProtocol.NexusEnvelop> {

    private static final Logger LOGGER = Logger.getLogger(ProtoConnectionHandler.class.getName());
    private final ValidationPipeline pipeline;
    private final MessageDispatcher dispatcher;

    /**
     * Constructs a new {@code ProtoConnectionHandler} for a peer connection.
     *
     * @param pipeline The validation pipeline responsible for enforcing
     * security and integrity checks.
     * @param dispatcher The message dispatcher responsible for routing
     * validated messages to the correct handler.
     * @param group
     */
    public ProtoConnectionHandler(
            ValidationPipeline pipeline,
            MessageDispatcher dispatcher,
            EventLoopGroup group) {
        this.pipeline = pipeline;
        this.dispatcher = dispatcher;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        LOGGER.info("New proto peer connected: " + ctx.channel().remoteAddress());
    }

    /**
     * Core message-processing loop.
     *
     * <p>
     * Steps:</p>
     * <ol>
     * <li>Validate the message with the pipeline (checksum, signature,
     * etc.).</li>
     * <li>Dispatch it to the appropriate handler (manifest, ping, handshake,
     * etc.).</li>
     * </ol>
     *
     * @param ctx Netty channel context
     * @param msg The inbound protobuf envelope received from the peer
     * @throws IOException if message parsing fails
     * @throws Exception if validation or dispatching encounters an error
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, NexusProtocol.NexusEnvelop msg) {

        // Run validation and dispatch in lightweight virtual threads
        VirtualThreadExecutor.chain()
                .run(() -> pipeline.validate(msg))// Always validate before dispatch
                .thenRun(() -> dispatcher.dispatch(msg, ctx)) //  Dispatch to correct handler

                // note that the handlers should not send the messages out themselves, the handlers can build
                // the outgoing messages and the message is sent out
                .thenRun(() -> dispatcher.respond())// send message out
                .onError(e -> {
                    if (e instanceof DropMessageException) {
                        return; // Silently skip
                    }
                    // Handle other validation or dispatch exceptions
                    e.printStackTrace();
                })
                .execute();
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        LOGGER.info("Disconnected from proto peer: " + ctx.channel().remoteAddress());
    }
}
