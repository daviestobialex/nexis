/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.tests;

import com.google.protobuf.ByteString;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.net.SocketAddress;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.HashSet;
import java.util.Set;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.nexis.base.Identity;
import org.nexis.base.Manifest;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.NexusNetwork;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.NodeId;
import org.nexis.core.Peer;
import org.nexis.core.PeerRegistry;
import org.nexis.messages.handlers.ChallangeResponseHandler;
import static org.nexis.utilities.CryptographyUtils.ED25519_ALGO;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class ChallangeResponseHandlerTets {

    private NexusEnvelopBuilder builder;
    private NetworkConfiguration params;
    private PeerRegistry registry;
    private ChannelHandlerContext ctx;
    private Channel channel;
    private ChallangeResponseHandler handler;
    private Identity node;
    private Manifest manifest;
    private KeyPair keyPair;

    @BeforeEach
    void setUp() throws NoSuchAlgorithmException, NoSuchProviderException {

        // Mock registry (singleton replaced via static mocking)
        registry = mock(PeerRegistry.class);

        manifest = mock(Manifest.class);
        when(manifest.getCategory()).thenReturn("test-category");
        when(manifest.manifestIdBytes()).thenReturn("test-id".getBytes());

        // generate a keypair
        // Add the Bouncy Castle provider
        Security.addProvider(new BouncyCastleProvider());

        // Use the strongest available SecureRandom instance
        SecureRandom random = SecureRandom.getInstanceStrong();

        // Get an Ed25519 key pair generator
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ED25519_ALGO, "BC");
        keyGen.initialize(256, random); // 256 is the standard key size for Ed25519

        // Generate the key pair
        keyPair = keyGen.generateKeyPair();

        node = mock(Identity.class);
        when(node.getKeyPair()).thenReturn(keyPair);
        when(node.getNodeId(any(byte[].class))).thenAnswer(inv -> new NodeId((byte[]) inv.getArgument(0)));

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

        handler = new ChallangeResponseHandler(params, builder, manifest, registry);
    }

    @Test
    void testCanHandleHandshakeResponseMessage() {
        NexusProtocol.NexusMessage msg = NexusProtocol.NexusMessage.newBuilder()
                .setHandshakeResponse(NexusProtocol.ChallengeResponse.newBuilder()
                        .setNonce(42L)
                        .setPublicKey(ByteString.EMPTY).build())
                .build();

        Assertions.assertTrue(handler.canHandle(msg));
    }

    @Test
    void testHandle_withValidResponse_addsPeerAndSendsManifest() {
        try (MockedStatic<PeerRegistry> mocked = Mockito.mockStatic(PeerRegistry.class)) {
            mocked.when(PeerRegistry::getInstance).thenReturn(registry);
            long nonce = 12345L;
            Set<Long> nonceIndex = new HashSet<>();
            nonceIndex.add(nonce);
            when(registry.getNonceIndex()).thenReturn(nonceIndex);

            byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
            byte[] nodeIdBytes = NodeId.stableNodeId(publicKeyBytes);

            NexusProtocol.ChallengeResponse challengeResp = NexusProtocol.ChallengeResponse.newBuilder()
                    .setNonce(nonce)
                    .setPublicKey(ByteString.copyFrom(publicKeyBytes))
                    .build();

            NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                    .setNodeId(ByteString.copyFrom(nodeIdBytes))
                    .setMessage(NexusProtocol.NexusMessage.newBuilder()
                            .setHandshakeResponse(challengeResp)
                            .build())
                    .build();

            handler.handle(envelop, ctx);

            // Verify nonce removed
            Assertions.assertFalse(nonceIndex.contains(nonce));

            // Verify peer added
            verify(registry).addActivePeer(any(Peer.class), eq(channel));

            // Verify manifest sent
            verify(ctx).writeAndFlush(any(NexusProtocol.NexusEnvelop.class));
        }
    }

    @Test
    void testHandle_withBadNodeId_throwsSecurityException() {
        long nonce = 222L;
        when(registry.getNonceIndex()).thenReturn(new HashSet<>(Set.of(nonce)));

        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        byte[] wrongNodeIdBytes = "fakeNode".getBytes(); // mismatch

        NexusProtocol.ChallengeResponse challengeResp = NexusProtocol.ChallengeResponse.newBuilder()
                .setNonce(nonce)
                .setPublicKey(ByteString.copyFrom(publicKeyBytes))
                .build();

        NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                .setNodeId(ByteString.copyFrom(wrongNodeIdBytes))
                .setMessage(NexusProtocol.NexusMessage.newBuilder()
                        .setHandshakeResponse(challengeResp)
                        .build())
                .build();

        Assertions.assertThrows(SecurityException.class, () -> handler.handle(envelop, ctx));
    }

    @Test
    void testHandle_withInvalidNonce_throwsSecurityException() {
        // registry nonce set is empty
        when(registry.getNonceIndex()).thenReturn(new HashSet<>());

        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        byte[] nodeIdBytes = NodeId.stableNodeId(publicKeyBytes);

        NexusProtocol.ChallengeResponse challengeResp = NexusProtocol.ChallengeResponse.newBuilder()
                .setNonce(99999L)
                .setPublicKey(ByteString.copyFrom(publicKeyBytes))
                .build();

        NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                .setNodeId(ByteString.copyFrom(nodeIdBytes))
                .setMessage(NexusProtocol.NexusMessage.newBuilder()
                        .setHandshakeResponse(challengeResp)
                        .build())
                .build();

        Assertions.assertThrows(SecurityException.class, () -> handler.handle(envelop, ctx));
    }

    @Test
    void testSendSignedManifest_writesManifestMessage() throws Exception {
        long nonce = 12345L;
        Set<Long> nonceIndex = new HashSet<>(Set.of(nonce));
        when(registry.getNonceIndex()).thenReturn(nonceIndex);

        byte[] publicKeyBytes = keyPair.getPublic().getEncoded();
        byte[] nodeIdBytes = NodeId.stableNodeId(publicKeyBytes);

        NexusProtocol.ChallengeResponse challengeResp = NexusProtocol.ChallengeResponse.newBuilder()
                .setNonce(nonce)
                .setPublicKey(ByteString.copyFrom(publicKeyBytes))
                .build();

        NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                .setNodeId(ByteString.copyFrom(nodeIdBytes))
                .setMessage(NexusProtocol.NexusMessage.newBuilder()
                        .setHandshakeResponse(challengeResp)
                        .build())
                .build();

        handler.handle(envelop, ctx);

        ArgumentCaptor<NexusProtocol.NexusEnvelop> captor
                = ArgumentCaptor.forClass(NexusProtocol.NexusEnvelop.class);

        verify(ctx).writeAndFlush(captor.capture());

        // We can at least assert it is non-null and contains a Manifest
        Assertions.assertNotNull(captor.getValue());
    }
}
