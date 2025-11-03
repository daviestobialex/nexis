/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.tests;

import com.google.protobuf.ByteString;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.net.SocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.SecureRandom;
import java.security.Security;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.nexis.base.Identity;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.NexusNetwork;
import org.nexis.base.PeerConnection;
import org.nexis.core.ManifestRegistry;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.NodeId;
import org.nexis.core.Peer;
import org.nexis.core.PeerRegistry;
import org.nexis.messages.handlers.GetPeersMessageHandler;
import static org.nexis.utilities.CryptographyUtils.ED25519_ALGO;
import org.nexus.base.proto.NexusProtocol;

public class GetPeersMessageHandlerTests {

    private NexusEnvelopBuilder builder;
    private NetworkConfiguration params;
    private PeerRegistry registry;
    private ChannelHandlerContext ctx;
    private Channel channel;
    private Identity node;
    private KeyPair keyPair;
    private GetPeersMessageHandler handler;
    private ManifestRegistry manifestRegistry;

    @BeforeEach
    void setUp() throws Exception {
        // crypto identity
        // Use the strongest available SecureRandom instance
        Security.addProvider(new BouncyCastleProvider());
        SecureRandom random = SecureRandom.getInstanceStrong();

        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ED25519_ALGO, "BC");
        keyGen.initialize(256, random); // 256 is the standard key size for Ed25519

        // Generate the key pair
        keyPair = keyGen.generateKeyPair();

        node = mock(Identity.class);
        byte[] bytes = NodeId.stableNodeId(keyPair.getPublic().getEncoded()).getBytes();
        when(node.getNodeId()).thenReturn(new NodeId(bytes));

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

        registry = mock(PeerRegistry.class);
        manifestRegistry = mock(ManifestRegistry.class);

        handler = new GetPeersMessageHandler(builder, params, registry, manifestRegistry);
    }

    @Test
    void testCanHandlePeersDiscoveryMessage() {
        NexusProtocol.NexusMessage msg = NexusProtocol.NexusMessage.newBuilder()
                .setPeersDiscovery(NexusProtocol.GetPeers.newBuilder().setSize(2))
                .build();

        assertTrue(handler.canHandle(msg));
    }

    @Test
    void testHandle_withPeers_sendsResponseAndUpdatesManifest() {
        // prepare active peers
        ConcurrentLinkedQueue<PeerConnection> active = new ConcurrentLinkedQueue<>();
        active.add(new PeerConnection(new Peer("peer1"), channel));
        active.add(new PeerConnection(new Peer("peer2"), channel));

        when(registry.getActivePeers()).thenReturn(active);

        // incoming envelope
        NexusProtocol.Manifest manifestProto = NexusProtocol.Manifest.newBuilder()
                .setCategory("catA")
                .setCid(ByteString.copyFromUtf8("cid123"))
                .build();

        NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                .setMessage(NexusProtocol.NexusMessage.newBuilder()
                        .setManifest(manifestProto)
                        .setPeersDiscovery(NexusProtocol.GetPeers.newBuilder().setSize(5).build())
                        .build())
                .build();
        Set<String> mockedSet = new HashSet<>(Arrays.asList("a", "b"));

        when(manifestRegistry.getByCategory(any())).thenReturn(mockedSet);

        when(ctx.channel()).thenReturn(channel);
        InetSocketAddress local = mock(InetSocketAddress.class);

        when(channel.localAddress()).thenReturn(local);

        when(local.getHostName()).thenReturn("127.0.0.1");
        // act
        handler.handle(envelop, ctx);

        // Verify ManifestRegistry updated
        assertEquals(2, manifestRegistry.getByCategory("catA").size());

        // Verify a response is sent
        verify(ctx).writeAndFlush(any(NexusProtocol.NexusEnvelop.class));
    }

    @Test
    void testHandle_respectsRequestedPeerSizeLimit() {
        // prepare many active peers
        ConcurrentLinkedQueue<PeerConnection> active = new ConcurrentLinkedQueue<>();

        for (int i = 0; i < 10; i++) {
            active.add(new PeerConnection(new Peer("peer-" + i), channel));
        }
        when(registry.getActivePeers()).thenReturn(active);

        NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                .setMessage(NexusProtocol.NexusMessage.newBuilder()
                        .setPeersDiscovery(NexusProtocol.GetPeers.newBuilder()
                                .setCategory("catB")
                                .setCid(ByteString.copyFromUtf8("cid999"))
                                .setSize(3).build())
                        .build())
                .build();

        when(ctx.channel()).thenReturn(channel);
        InetSocketAddress local = mock(InetSocketAddress.class);

        when(channel.localAddress()).thenReturn(local);

        when(local.getHostName()).thenReturn("127.0.0.1");

        handler.handle(envelop, ctx);

        verify(ctx).writeAndFlush(any());
    }
}
