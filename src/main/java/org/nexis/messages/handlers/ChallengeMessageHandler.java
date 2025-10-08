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
package org.nexis.messages.handlers;

import com.google.protobuf.ByteString;
import io.netty.channel.ChannelHandlerContext;
import org.nexis.internal.MessageHandler;
import org.nexis.base.NetworkConfiguration;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.core.NodeId;
import org.nexis.core.Peer;
import org.nexis.base.PeerAddress;
import org.nexis.core.PeerRegistry;
import org.nexis.exceptions.DropMessageException;
import org.nexis.messages.ChallengeResponseMessage;
import org.nexis.networks.NexusNetworkConfiguration;

/**
 * Handles incoming {@code Challenge} messages during the handshake phase of the
 * Nexis peer-to-peer protocol.
 * <p>
 * In the challenge/response flow:
 * <ul>
 * <li>The remote peer (Peer A) sends a {@code Challenge} containing a random
 * nonce.</li>
 * <li>This handler (Peer B) responds by echoing back the received nonce and
 * attaching its own public key, thus proving ownership of the KeyPair
 * associated with the node.</li>
 * <li>The remote peer verifies the response against the expected public key and
 * nonce to complete the authentication step.</li>
 * </ul>
 *
 * <h3>Responsibilities:</h3>
 * <ul>
 * <li>Extracts the nonce from the incoming handshake message.</li>
 * <li>Builds a {@link NexusProtocol.ChallengeResponse} message containing the
 * nonce and this node’s public key.</li>
 * <li>Wraps the challenge response in a {@link ChallengeResponseMessage} and
 * sends it back.</li>
 * <li>Updates or registers the peer in the {@link PeerRegistry} if not already
 * present.</li>
 * </ul>
 *
 * <h3>Thread-safety:</h3>
 * Instances of this class are intended to be used by Netty’s event loop. Access
 * to {@link PeerRegistry} is centralized through its singleton instance.
 *
 * @author daviestobialex
 */
public class ChallengeMessageHandler implements MessageHandler {

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    private final PeerRegistry registery;

    /**
     * Constructs a new {@code ChallengeMessageHandler}.
     *
     * @param builder the envelope builder used to construct signed protocol
     * messages
     * @param params the active network configuration (e.g. MainNet, TestNet)
     */
    public ChallengeMessageHandler(NexusEnvelopBuilder builder, NetworkConfiguration params) {
        this.builder = builder;
        this.params = params;
        this.registery = PeerRegistry.getInstance();
    }

    /**
     * Constructs a new {@code ChallengeMessageHandler} with an explicit
     * {@link PeerRegistry}. Primarily used for testing purposes.
     *
     * @param builder the envelope builder used to construct signed protocol
     * messages
     * @param params the active network configuration
     * @param registery the peer registry instance (mock or custom in tests)
     */
    // for testing
    public ChallengeMessageHandler(NexusEnvelopBuilder builder, NetworkConfiguration params, PeerRegistry registery) {
        this.builder = builder;
        this.params = params;
        this.registery = registery;
    }

    /**
     * Determines if this handler should process the given message.
     *
     * @param message the incoming message to check
     * @return {@code true} if the message contains a handshake, {@code false}
     * otherwise
     */
    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasHandshake();
    }

    /**
     * Processes an incoming handshake message, responds with a
     * {@code ChallengeResponse}, and registers the peer if necessary.
     *
     * @param envelop the incoming message envelope containing the handshake and
     * nonce
     * @param ctx the Netty channel handler context for sending responses
     */
    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        NodeId nodeServerId = builder.getNode().getNodeId();
        long nonce = envelop.getMessage().getHandshake().getNonce();
        byte[] nodeId = envelop.getNodeId().toByteArray();

        if (!registery.getNonceIndex().contains(nonce)) {
            NexusProtocol.ChallengeResponse challenge
                    = NexusProtocol.ChallengeResponse.newBuilder()
                            .setPublicKey(ByteString.copyFrom(builder.getNode().getKeyPair().getPublic().getEncoded()))
                            .setNonce(nonce)
                            .build();

            ChallengeResponseMessage challengeMessage = new ChallengeResponseMessage(
                    NexusNetworkConfiguration.of(params.getNetwork()),
                    challenge,
                    nodeServerId.getId()
            );

            PeerAddress nodeById = registery.getPeerById(nodeId);
            // update peer registery with node id
            if (nodeById == null) {
                String remoteAddress = ctx.channel().remoteAddress().toString();
                nodeById = new Peer(remoteAddress, nodeId);
                registery.addPendingPeer(nodeById, ctx.channel());
            }

            ctx.writeAndFlush(builder.build(challengeMessage));
        } else {
            throw new DropMessageException("nonce is found in index, peer is communicating with self");
        }
    }
}
