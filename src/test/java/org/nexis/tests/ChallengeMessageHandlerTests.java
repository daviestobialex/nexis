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
package org.nexis.tests;

import com.google.protobuf.ByteString;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.net.SocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.PeerAddress;
import org.nexis.core.*;
import org.nexus.base.proto.NexusProtocol;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Assertions;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.nexis.base.Identity;
import org.nexis.base.NexusNetwork;

import org.nexis.messages.handlers.ChallengeMessageHandler;

/**
 *
 * @author daviestobialex
 */
public class ChallengeMessageHandlerTests {

    private NexusEnvelopBuilder builder;
    private NetworkConfiguration params;
    private PeerRegistry registry;
    private ChannelHandlerContext ctx;
    private Channel channel;
    private ChallengeMessageHandler handler;
    private Identity node;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException {
        // Mock registry (singleton replaced via static mocking)
        registry = mock(PeerRegistry.class);

        doNothing().when(registry).addPendingPeer(any(), any());
        // Build keypair + node
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        KeyPair keyPair = keyGen.generateKeyPair();
        node = mock(Identity.class);
        when(node.getKeyPair()).thenReturn(keyPair);
        when(node.getNodeId()).thenReturn(new NodeId("test-node-id".getBytes()));

        // Mock builder
        builder = mock(NexusEnvelopBuilder.class);
        when(builder.getNode()).thenReturn(node);
        when(builder.build(any())).thenReturn(mock(NexusProtocol.NexusEnvelop.class));

        // Mock network config
        params = mock(NetworkConfiguration.class);
        when(params.getNetwork()).thenReturn(NexusNetwork.LOCALHOSTTEST);

        // Mock channel + ctx
        channel = mock(Channel.class);
        ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel);
        SocketAddress mockedSocket = mock(SocketAddress.class);
        when(channel.remoteAddress()).thenReturn(mockedSocket);
        when(mockedSocket.toString()).thenReturn("127.0.0.1:1334");

        handler = new ChallengeMessageHandler(builder, params, registry);
    }

    @Test
    void testCanHandleHandshakeMessage() {
        NexusProtocol.NexusMessage msg = NexusProtocol.NexusMessage.newBuilder()
                .setHandshake(NexusProtocol.Challenge.newBuilder().setNonce(42L).build())
                .build();

        Assertions.assertTrue(handler.canHandle(msg));
    }

    @Test
    void testHandleWithNewPeer_addsToRegistryAndSendsResponse() {
        try (MockedStatic<PeerRegistry> mocked = Mockito.mockStatic(PeerRegistry.class)) {
            mocked.when(PeerRegistry::getInstance).thenReturn(registry);

            // Arrange
            NexusProtocol.Challenge handshake = NexusProtocol.Challenge.newBuilder()
                    .setNonce(99L)
                    .build();

            byte[] nodeIdBytes = "peer123".getBytes();
            NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                    .setMessage(NexusProtocol.NexusMessage.newBuilder().setHandshake(handshake).build())
                    .setNodeId(ByteString.copyFrom(nodeIdBytes))
                    .build();

            // Act
            handler.handle(envelop, ctx);

            // Assert that pending peer was added
            verify(registry).addPendingPeer(any(Peer.class), eq(channel));

            // Assert response was sent
            verify(ctx).writeAndFlush(any(NexusProtocol.NexusEnvelop.class));
        }
    }

    @Test
    void testHandleWithExistingPeer_doesNotAddToRegistryButSendsResponse() {
        // Arrange
        NexusProtocol.Challenge handshake = NexusProtocol.Challenge.newBuilder()
                .setNonce(77L)
                .build();

        byte[] nodeIdBytes = "existingPeer".getBytes();
        NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                .setMessage(NexusProtocol.NexusMessage.newBuilder().setHandshake(handshake).build())
                .setNodeId(ByteString.copyFrom(nodeIdBytes))
                .build();

        when(registry.getNodeById(nodeIdBytes)).thenReturn(mock(PeerAddress.class));

        handler.handle(envelop, ctx);

        // Assert: should NOT call addPendingPeer
        verify(registry, never()).addPendingPeer(any(), any());

        // But still sends response
        verify(ctx).writeAndFlush(any(NexusProtocol.NexusEnvelop.class));
    }
}
