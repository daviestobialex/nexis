/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.messages.handlers;

import io.netty.channel.ChannelHandlerContext;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.PeerAddress;
import org.nexis.base.PeerConnection;
import org.nexis.core.ManifestRegistry;
import org.nexis.core.NexusEnvelopBuilder;
import org.nexis.core.NodeId;
import org.nexis.core.PeerRegistry;
import org.nexis.internal.MessageHandler;
import org.nexis.messages.GetPeersResponseMessage;
import org.nexis.networks.NexusNetworkConfiguration;

import org.nexus.base.proto.NexusProtocol;

/**
 * {@code GetPeersMessageHandler} processes incoming "GetPeers" discovery
 * requests and responds with a list of available peers known to this node.
 * <p>
 * Peer discovery is a fundamental part of the Nexis P2P protocol. It allows
 * nodes to share knowledge of other peers in the network, ensuring that
 * participants can bootstrap connections and maintain a healthy, decentralized
 * topology.
 *
 * <h3>Responsibilities</h3>
 * <ul>
 * <li>Validates whether the incoming message is a peer discovery request.</li>
 * <li>Retrieves a list of active peers from the {@link PeerRegistry}.</li>
 * <li>Respects the requested size limit for peers.</li>
 * <li>Builds and sends back a {@link GetPeersResponseMessage} containing peer
 * addresses.</li>
 * <li>Updates the {@link ManifestRegistry} with category/CID metadata carried
 * in the request.</li>
 * </ul>
 *
 * <h3>Protocol Step</h3>
 * This handler corresponds to the "GetPeers → GetPeersResponse" exchange within
 * the peer discovery sequence.
 *
 * <pre>
 * Peer A → GetPeersRequest (asks for N peers)
 * Peer B → GetPeersResponse (returns up to N peers)
 * </pre>
 *
 * @author daviestobialex
 */
public class GetPeersMessageHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(GetManifestContentMessageHandler.class.getName());

    private final NexusEnvelopBuilder builder;
    private final NetworkConfiguration params;
    private final PeerRegistry registery;
    private final ManifestRegistry manifestRegistry;

    /**
     * Creates a handler for responding to "GetPeers" messages.
     *
     * @param builder builder used to construct signed protocol envelopes
     * @param params the network configuration for this node
     */
    public GetPeersMessageHandler(NexusEnvelopBuilder builder, NetworkConfiguration params) {
        this.builder = builder;
        this.params = params;
        this.registery = PeerRegistry.getInstance();
        this.manifestRegistry = ManifestRegistry.getInstance();
    }

    /**
     * Test constructor allowing injection of a custom {@link PeerRegistry}.
     *
     * @param builder envelope builder
     * @param params network configuration
     * @param registery peer registry (test double or singleton)
     * @param manifestRegistry
     */
    public GetPeersMessageHandler(
            NexusEnvelopBuilder builder,
            NetworkConfiguration params,
            PeerRegistry registery,
            ManifestRegistry manifestRegistry) {
        this.builder = builder;
        this.params = params;
        this.registery = registery;
        this.manifestRegistry = manifestRegistry;
    }

    /**
     * Determines whether this handler can process the given message.
     *
     * @param message the protocol message to inspect
     * @return {@code true} if it is a "peersDiscovery" message
     */
    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasPeersDiscovery();
    }

    /**
     * Handles a "GetPeers" request and responds with a limited list of known
     * peers.
     * <p>
     * The method:
     * <ol>
     * <li>Extracts the requested peer count and manifest info from the
     * message.</li>
     * <li>Collects up to {@code requestedPeerSize} peers from the active
     * registry.</li>
     * <li>Builds a {@link GetPeersResponseMessage} containing peer
     * addresses.</li>
     * <li>Updates the {@link ManifestRegistry} with category/CID for
     * bookkeeping.</li>
     * <li>Writes and flushes the response back to the requesting channel.</li>
     * </ol>
     *
     * @param envelop the incoming message envelope
     * @param ctx the Netty channel context for replying
     */
    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        System.out.println("Received Get Peers (peersDiscovery) step 4");
        NodeId nodeServerId = builder.getNode().getNodeId();

        int requestedPeerSize = envelop.getMessage().getPeersDiscovery().getSize();
        String category = envelop.getMessage().getPeersDiscovery().getCategory();
        byte[] cid = envelop.getMessage().getPeersDiscovery().getCid().toByteArray();

        LOGGER.log(Level.INFO, "Received get peers message of size {0}", requestedPeerSize);

        // Collect available active peers
        Iterable<PeerConnection> activePeers = registery.getActivePeers();
        int limit = Math.min(requestedPeerSize, registery.getActivePeerSize());

        InetSocketAddress local = (InetSocketAddress) ctx.channel().localAddress();

        List<String> addresses = StreamSupport.stream(activePeers.spliterator(), false)
                .limit(limit)
                .map(PeerConnection::peer) // extract PeerAddress
                .map(PeerAddress::id) // extract id() from PeerAddress
                .collect(Collectors.toList());

        // removing address of already connected peer in list of peers potentially
        addresses.removeIf(address -> local.getAddress().getHostAddress().equals(address)
                || local.getAddress().getHostAddress().contains(address));

        // Build protocol response
        NexusProtocol.GetPeersResponse getPeers
                = NexusProtocol.GetPeersResponse.newBuilder()
                        .addAllAddresses(addresses)
                        .build();

        GetPeersResponseMessage getPeersResponse = new GetPeersResponseMessage(
                NexusNetworkConfiguration.of(params.getNetwork()),
                getPeers,
                nodeServerId.getId()
        );

        // Update manifest registry with category/CID reference
        manifestRegistry.put(category, cid);

        // Send response back to requester
        ctx.writeAndFlush(builder.build(getPeersResponse));
    }
}
