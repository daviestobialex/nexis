/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.tests;

import com.google.protobuf.ByteString;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.SocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import org.nexis.base.Identity;
import org.nexis.base.Manifest;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.NexusNetwork;
import org.nexis.core.ManifestRegistry;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.NodeId;
import org.nexis.core.Peer;
import org.nexis.core.PeerRegistry;
import org.nexis.messages.handlers.ManifestMessageHandler;
import static org.nexis.utilities.CryptographyUtils.ED25519_ALGO;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class ManifestMessageHandlerTests {

    private NexusEnvelopBuilder builder;
    private NetworkConfiguration params;
    private Manifest manifest;
    private PeerRegistry registry;
    private ChannelHandlerContext ctx;
    private Channel channel;
    private Identity node;
    private KeyPair keyPair;
    private ManifestMessageHandler handler;
    private ManifestRegistry manifestRegistry;

    @BeforeEach
    void setUp() throws Exception {
        manifest = mock(Manifest.class);
        when(manifest.getCategory()).thenReturn("category-x");
        when(manifest.manifestIdBytes()).thenReturn("manifext-x".getBytes());

        // Use the strongest available SecureRandom instance
        SecureRandom random = SecureRandom.getInstanceStrong();

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ED25519_ALGO, "BC");
        keyGen.initialize(256, random); // 256 is the standard key size for Ed25519

        // Generate the key pair
        keyPair = keyGen.generateKeyPair();
        node = mock(Identity.class);
        when(node.getKeyPair()).thenReturn(keyPair);
        when(node.getNodeId()).thenReturn(new NodeId(
                NodeId.stableNodeId(keyPair.getPublic().getEncoded()).getBytes()));

        builder = mock(NexusEnvelopBuilder.class);
        when(builder.getNode()).thenReturn(node);
        when(builder.build(any())).thenReturn(mock(NexusProtocol.NexusEnvelop.class));

        params = mock(NetworkConfiguration.class);
        when(params.getNetwork()).thenReturn(NexusNetwork.LOCALHOSTTEST);

        channel = mock(Channel.class);
        ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel);
        SocketAddress mockedSocket = mock(SocketAddress.class);
        when(channel.remoteAddress()).thenReturn(mockedSocket);
        when(mockedSocket.toString()).thenReturn("127.0.0.1:8080");

        registry = mock(PeerRegistry.class);

        manifestRegistry = mock(ManifestRegistry.class);

        when(registry.getPendingPeers()).thenReturn(new ConcurrentLinkedQueue<>());

        handler = new ManifestMessageHandler(builder, params, manifest, registry, manifestRegistry);
    }

    @Test
    void testCanHandleManifestMessage() {
        NexusProtocol.NexusMessage msg = NexusProtocol.NexusMessage.newBuilder()
                .setManifest(NexusProtocol.Manifest.newBuilder()
                        .setCategory("category-x")
                        .setCid(ByteString.copyFromUtf8("cid123"))
                        .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                        .build())
                .build();

        assertTrue(handler.canHandle(msg));
    }

    @Test
    void testHandle_validManifest_promotesPeerAndSendsGetPeers() {
        byte[] nodeId = NodeId.stableNodeId(keyPair.getPublic().getEncoded()).getBytes();

        NexusProtocol.Manifest manifestProto = NexusProtocol.Manifest.newBuilder()
                .setCategory("category-x")
                .setCid(ByteString.copyFromUtf8("cid123"))
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .build();

        NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                .setNodeId(ByteString.copyFrom(nodeId))
                .setMessage(NexusProtocol.NexusMessage.newBuilder()
                        .setManifest(manifestProto)
                        .build())
                .build();

        Set<byte[]> mockedSet = new HashSet<>(Arrays.asList("a".getBytes()));

        when(manifestRegistry.getByCategory(any())).thenReturn(mockedSet);

        // act
        handler.handle(envelop, ctx);

        // Assert ManifestRegistry updated
        assertEquals(1, manifestRegistry.getByCategory("category-x").size());

        // Verify Peer promotion
        verify(registry, atLeastOnce()).addActivePeer(any(Peer.class), eq(channel));
    }

    @Test
    void testHandle_invalidSignature_throwsRuntimeException() throws Exception {
        // Force SignedManifest to throw
        NexusEnvelopBuilder badBuilder = mock(NexusEnvelopBuilder.class);
        when(badBuilder.getNode()).thenThrow(new RuntimeException("bad node"));

        ManifestMessageHandler badHandler = new ManifestMessageHandler(badBuilder, params, manifest);

        byte[] nodeId = NodeId.stableNodeId(keyPair.getPublic().getEncoded()).getBytes();

        NexusProtocol.Manifest manifestProto = NexusProtocol.Manifest.newBuilder()
                .setCategory("cat")
                .setCid(ByteString.copyFromUtf8("cid999"))
                .setPublicKey(ByteString.copyFrom(keyPair.getPublic().getEncoded()))
                .build();

        NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                .setNodeId(ByteString.copyFrom(nodeId))
                .setMessage(NexusProtocol.NexusMessage.newBuilder()
                        .setManifest(manifestProto)
                        .build())
                .build();

        assertThrows(RuntimeException.class, () -> badHandler.handle(envelop, ctx));
    }
}
