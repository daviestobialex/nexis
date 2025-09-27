/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.net;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.protobuf.ProtobufDecoder;
import io.netty.handler.codec.protobuf.ProtobufEncoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32FrameDecoder;
import io.netty.handler.codec.protobuf.ProtobufVarint32LengthFieldPrepender;
import org.nexis.internal.MessageDispatcher;
import org.nexis.base.NetworkConfiguration;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.net.handlers.ProtoConnectionHandler;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.core.ValidationPipeline;
import org.nexis.base.Identity;
import org.nexis.base.Manifest;

/**
 * {@code NioProtoServer} wires together the Netty channel pipeline for nodes in
 * the Nexis network. It is responsible for configuring how raw TCP streams are
 * transformed into strongly-typed protobuf messages and vice versa.
 *
 * <h2>Responsibilities</h2>
 * <ul>
 * <li><b>Protocol Framing:</b> Adds Protobuf-specific encoders/decoders so that
 * messages are correctly framed with varint length prefixes and
 * serialized/deserialized into {@link NexusProtocol.NexusEnvelop}
 * instances.</li>
 * <li><b>Message Handling:</b> Installs a {@link ProtoConnectionHandler} at the
 * end of the pipeline to enforce validation and dispatch inbound messages to
 * appropriate handlers.</li>
 * <li><b>Node Context:</b> Associates each channel with the current node’s
 * {@link Identity} and {@link Manifest}, which define its cryptographic
 * identity and organizational metadata.</li>
 * </ul>
 *
 * <h2>Why This Class Exists</h2>
 * Netty requires a {@link ChannelInitializer} to define how each new connection
 * should be set up. By encapsulating protobuf codec setup and protocol-specific
 * handler configuration here, Nexis ensures that all connections conform to the
 * same message pipeline, reducing duplication and errors.
 *
 * <h2>Pipeline Layout</h2>
 * For each {@link SocketChannel}, the pipeline is built as:
 * <pre>
 * [ProtobufVarint32FrameDecoder]
 *     → [ProtobufDecoder(NexusEnvelop)]
 *     → [ProtobufVarint32LengthFieldPrepender]
 *     → [ProtobufEncoder]
 *     → [ProtoConnectionHandler]
 * </pre> This guarantees that:
 * <ul>
 * <li>Inbound bytes are framed and decoded into {@code NexusEnvelop}
 * objects.</li>
 * <li>Outbound messages are serialized with the correct protobuf encoding.</li>
 * <li>Application logic (validation, dispatching, peer events) is isolated
 * inside {@link ProtoConnectionHandler}.</li>
 * </ul>
 *
 * <h2>Extensibility</h2>
 * - To support additional codecs (e.g., compression, encryption), they can be
 * inserted before the {@link ProtoConnectionHandler}. - To extend validation or
 * message handling, add validators/handlers in {@link ProtoConnectionHandler}
 * or supply a custom dispatcher.
 *
 * <h2>Design Considerations</h2>
 * - This server is lightweight and stateless; it wires per-channel handlers and
 * leaves peer state management to higher-level classes. - Ensures all
 * connections use the node’s current {@link Identity} and {@link Manifest},
 * maintaining consistency across sessions.
 *
 * @author daviestobialex
 */
public class NioProtoServer extends ChannelInitializer<SocketChannel> {

    private final NetworkConfiguration params;
    /**
     * node credential identity of running node server
     */
    private final Identity identity;
    private final Manifest manifest;

    public NioProtoServer(NetworkConfiguration params, Identity identity, Manifest manifest) {
        this.params = params;
        this.identity = identity;
        this.manifest = manifest;
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
                        new MessageDispatcher(),
                        manifest)
        );
    }

}
