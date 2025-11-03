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
package org.nexis.core;

import io.netty.channel.Channel;
import java.util.Objects;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.logging.Logger;
import org.nexis.base.NexusNetwork;
import org.nexis.base.StreamConnection;
import org.nexus.base.proto.NexusProtocol;
import static org.nexis.core.PeerRegistry.DEFAULT_MAX_CONNECTIONS;
import org.nexis.messages.ChallengeRequestMessage;
import org.nexis.networks.NexusNetworkConfiguration;

/**
 * {@code PeerGroup} manages this node’s active and pending peer connections
 * within the Nexis P2P network.
 * <p>
 * Its responsibilities include:
 * <ul>
 * <li>Seeding initial connections from DNS bootstrap addresses.</li>
 * <li>Tracking peers via the {@link PeerRegistry} (active and pending).</li>
 * <li>Initiating the handshake process with peers to authenticate and establish
 * trust.</li>
 * <li>Respecting the configured maximum connection limit to prevent resource
 * exhaustion.</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>{@code
 * PeerGroup peerGroup = new PeerGroup(
 *      NexusNetwork.TESTNET,
 *      eventLoopGroup,
 *      channelInitializer,
 *      streamConnection
 * );
 *
 * peerGroup.seed(); // connect to DNS seeds
 * peerGroup.initiateHandshakeWithPeers(builder); // send handshake to peers
 * }</pre>
 *
 * <h3>Thread-safety</h3>
 * This class delegates peer state management to {@link PeerRegistry}, which
 * uses thread-safe structures like {@link ConcurrentMap}.
 *
 * @author daviestobialex
 */
public class PeerGroup {

    private final PeerRegistry peerRegistry = PeerRegistry.getInstance();

    private final NexusNetworkConfiguration params;
    private static final Logger log = Logger.getLogger(PeerGroup.class.getName());
    private int maxConnections;
    private final StreamConnection connection;

    /**
     * Creates a {@code PeerGroup} for the given network using the default
     * maximum connection limit.
     * <p>
     * This constructor is useful for exploring the network when block
     * synchronization is not required.
     *
     * @param network the P2P network to connect to (e.g. MAINNET, TESTNET)
     * @param connection the abstraction used to open new stream connections
     */
    public PeerGroup(
            NexusNetwork network,
            StreamConnection connection) {
        this(NexusNetworkConfiguration.of(Objects.requireNonNull(network)), connection);
    }

    /**
     * Creates a {@code PeerGroup} with a specified connection limit.
     *
     * @param network the P2P network to connect to
     * @param maxConnections the maximum number of peers to maintain
     * @param connection the abstraction for opening connections
     */
    public PeerGroup(
            NexusNetwork network,
            int maxConnections,
            StreamConnection connection) {
        this(NexusNetworkConfiguration.of(Objects.requireNonNull(network)),
                maxConnections, connection);
    }

    protected PeerGroup(
            NexusNetworkConfiguration params,
            StreamConnection connection) {
        this(params, DEFAULT_MAX_CONNECTIONS, connection);
    }

    protected PeerGroup(
            NexusNetworkConfiguration params,
            int maxConnections,
            StreamConnection connection) {

        this.params = params;
        this.maxConnections = maxConnections;
        this.connection = connection;
    }

    /**
     * Connects to the network’s predefined DNS seeds.
     * <p>
     * Seeds act as bootstrap peers that allow the node to discover and connect
     * to the wider network. Connections are only opened if the current active
     * peer count is below {@code maxConnections}.
     */
    public void seed() {

        String[] dnsSeeds = params.getDnsSeeds();

        for (String address : dnsSeeds) {
            if (peerRegistry.getActivePeerSize() > maxConnections) {
                break;
            }
            connection.connectionOpened(address, params.getPort());
        }
    }

    /**
     * Initiates the handshake process with all currently known peers (both
     * active and pending).
     * <p>
     * For each peer, this method:
     * <ol>
     * <li>Generates a random {@code nonce}.</li>
     * <li>Tracks the nonce in the {@link PeerRegistry} for replay
     * protection.</li>
     * <li>Sends a {@link ChallengeRequestMessage} containing the nonce to the
     * peer via Netty’s channel pipeline.</li>
     * </ol>
     *
     * @param builder the envelope builder used to construct signed messages
     * @param activeChannel
     */
    public void initiateHandshakeWithPeers(NexusEnvelopBuilder builder, Channel activeChannel) {

        long nonce = ThreadLocalRandom.current().nextLong();
        peerRegistry.getNonceIndex().add(nonce);// track nonce
        doHandshake(nonce, activeChannel, builder);
    }

    /**
     * Sends a handshake message containing the given nonce to the specified
     * peer.
     *
     * @param nonce the random value used to challenge the peer
     * @param activeChannel the Netty channel associated with the peer
     * @param builder the envelope builder for constructing protocol messages
     */
    private void doHandshake(long nonce, Channel activeChannel, NexusEnvelopBuilder builder) {
        NodeId nodeId = builder.getNode().getNodeId();
        NexusProtocol.Challenge handshake = NexusProtocol.Challenge.newBuilder()
                .setNonce(nonce)
                //                .setGenesisHash(params.getGenesisBlock())// publishes its gensis block, there needs to be a genesis block validator
                .build();
        ChallengeRequestMessage handshakeMessage = new ChallengeRequestMessage(
                params, handshake, nodeId.getId());

        NexusProtocol.NexusEnvelop envelop = builder
                .build(handshakeMessage);

        activeChannel.writeAndFlush(envelop);
    }
    
    public PeerRegistry getPeerRegistry(){
        return peerRegistry;
    }
}
