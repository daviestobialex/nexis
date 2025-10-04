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

import io.netty.channel.ChannelHandlerContext;
import java.net.UnknownHostException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.PeerConnection;
import org.nexis.base.StreamConnection;
import org.nexis.core.PeerGroup;
import org.nexis.core.PeerRegistry;
import org.nexis.internal.MessageHandler;
import org.nexis.net.NioProducer;
import org.nexis.net.NioProtoServer;
import org.nexus.base.proto.NexusProtocol;

/**
 * Handles incoming peer discovery responses containing a list of peer
 * addresses.
 * <p>
 * When this handler receives a {@link NexusProtocol.GetPeersResponse} message,
 * it extracts the peer addresses from the response and attempts to establish
 * outbound connections to each peer in the list.
 * </p>
 *
 * <h2>Flow</h2>
 * <ol>
 * <li>The handler listens for messages of type {@code hasPeers()}.</li>
 * <li>When triggered, it retrieves the peer addresses from the response.</li>
 * <li>For each address, it creates a {@link PeerGroup} and initiates a
 * connection using {@link NioProducer} and {@link NioProtoServer}.</li>
 * <li>The handshake process is initiated for each newly connected peer.</li>
 * </ol>
 *
 * <h2>Usage</h2>
 * This handler is part of the peer discovery mechanism. It is typically invoked
 * after a node issues a "Get Peers" request and receives a response containing
 * potential peers in the network. By connecting to these peers, the node
 * expands its view of the network topology and improves resilience.
 *
 * @author daviestobialex
 */
public class GetPeersResponseHandler implements MessageHandler {

    private static final Logger LOGGER = Logger.getLogger(GetPeersResponseHandler.class.getName());

    private final StreamConnection connectionClient;
    private final NetworkConfiguration params;

    /**
     * Creates a new {@code GetPeersResponseHandler}.
     *
     * @param params network configuration for the current node
     * @param connection stream connection client
     */
    public GetPeersResponseHandler(
            NetworkConfiguration params,
            StreamConnection connection) {
        this.params = params;
        this.connectionClient = connection;
    }

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasPeers();
    }

    /**
     * Handles the incoming peer discovery response by connecting to all
     * provided peer addresses and initiating the handshake protocol.
     *
     * @param envelop the received network envelope containing the peers list
     * @param ctx the Netty channel context
     */
    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {
        LOGGER.info("RECIEVED PEER LIST AND CONNECTING");
        // trigger connection to peers functions
        envelop.getMessage().getPeers().getAddressesList().stream()
                .forEach(address -> {// TODO: might have to search with the collection here than creating a O(n^2)
                    PeerConnection connecedPeer = PeerRegistry.getInstance().getPeerByAddress(address);
                    // validate if address is not already connected (O(n))
                    try {
                        // attempt to filter out self propagting messages, network ip determinig issue
                        String localHost = java.net.InetAddress.getLocalHost().getHostAddress();
                        String canonicalHostName = java.net.InetAddress.getLocalHost().getCanonicalHostName();
                        LOGGER.log(Level.INFO, "connecting to {0} from {1}/{2}", new String[]{address, localHost, canonicalHostName});
                        if (connecedPeer == null || !address.contains(localHost)) {
                            connectionClient.connectionOpened(address, params.getPort());
                        }
                    } catch (UnknownHostException e) {
                        LOGGER.log(Level.SEVERE, "error reading host address while connecting to peer", e);
                    }
                });

    }

}
