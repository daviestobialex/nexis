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
package org.nexis.net;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.socket.nio.NioSocketChannel;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.logging.Logger;
import org.nexis.base.StreamConnection;
import org.nexis.core.Peer;
import org.nexis.listeners.PeerClientConnectListener;

/**
 * {@code NioProducer} is a client-side network connection manager built on top
 * of <a href="https://netty.io/">Netty</a>.
 * <p>
 * It encapsulates the creation and management of a {@link Bootstrap} used to
 * establish outbound TCP connections to peers in the Nexus network.
 * </p>
 *
 * <h2>Responsibilities</h2>
 * <ul>
 * <li>Initialize and configure a Netty {@link Bootstrap} for NIO socket
 * channels.</li>
 * <li>Establish a connection to a specified host/port or use defaults provided
 * during instantiation.</li>
 * <li>Provide lifecycle methods from {@link StreamConnection} to open or close
 * connections.</li>
 * <li>Attach {@link PeerClientConnectListener} to each connection attempt for
 * handling retries, reconnections, and peer state tracking.</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * EventLoopGroup group = new NioEventLoopGroup();
 * ChannelInitializer<?> initializer = new MyChannelInitializer();
 *
 * // Create a producer with a target host/port
 * NioProducer producer = new NioProducer(initializer, group, "127.0.0.1", 8080);
 *
 * // Open the connection
 * producer.connectionOpened();
 * }</pre>
 *
 * This class is typically used by higher-level discovery or peer management
 * components (e.g., {@code DnsDiscovery}) to establish outbound links to other
 * nodes.
 *
 * @author daviestobialex
 */
public class NioProducer implements StreamConnection {

    private static final Logger LOGGER = Logger.getLogger(PeerClientConnectListener.class.getName());
    /**
     * Event loop group for managing channel events and threads.
     */
    private final EventLoopGroup group;

    /**
     * Remote peer host address.
     */
    private String host;

    /**
     * Remote peer port number.
     */
    private int port;

    /**
     * Netty bootstrap instance used to configure and establish connections.
     */
    private final Bootstrap b;

    /**
     * Constructs a new {@code NioProducer} that connects to a specific host and
     * port.
     *
     * @param channelInitializer Channel pipeline initializer for configuring
     * handlers.
     * @param group Event loop group used for channel I/O operations.
     * @param host Remote host address.
     * @param port Remote port number.
     */
    public NioProducer(ChannelInitializer channelInitializer, EventLoopGroup group, String host, int port) {
        this.group = group;
        this.host = host;
        this.port = port;
        b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(channelInitializer);
    }

    /**
     * Constructs a new {@code NioProducer} without a predefined host and port.
     * <p>
     * The host and port must later be provided when opening the connection.
     * </p>
     *
     * @param channelInitializer Channel pipeline initializer for configuring
     * handlers.
     * @param group Event loop group used for channel I/O operations.
     */
    public NioProducer(ChannelInitializer channelInitializer, EventLoopGroup group) {
        this.group = group;
        b = new Bootstrap();
        b.group(group)
                .channel(NioSocketChannel.class)
                .handler(channelInitializer);
    }

    /**
     * Closes the current connection and shuts down the event loop group.
     * <p>
     * This will release all associated resources and threads.
     * </p>
     */
    @Override
    public void connectionClosed() {
        group.close();
    }

    /**
     * Opens a connection to the predefined host and port (if available).
     *
     * @return this instance for chaining.
     */
    @Override
    public StreamConnection connectionOpened() {
        return connectToNetwork();
    }

    /**
     * Connects to the network using the stored {@code host} and {@code port}.
     *
     * @return this instance for chaining.
     */
    private StreamConnection connectToNetwork() {
        InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);
        if (!isSelfConnection(inetSocketAddress)) {
            LOGGER.info(" client connect to peer= " + host + " " + port);
            b.connect(inetSocketAddress)
                    .addListener(new PeerClientConnectListener(
                            inetSocketAddress,
                            b,
                            group.next(),
                            10,
                            10,
                            new Peer(host)));
        }
        return this;
    }

    /**
     * Opens a connection to a specific host and port.
     *
     * @param host Remote host address.
     * @param port Remote port number.
     * @return this instance for chaining.
     */
    @Override
    public StreamConnection connectionOpened(String host, int port) {
        InetSocketAddress inetSocketAddress = new InetSocketAddress(host, port);
        if (!isSelfConnection(inetSocketAddress)) {
            LOGGER.info(" client connect to peer: " + host + " " + port);
            b.connect(inetSocketAddress)
                    .addListener(new PeerClientConnectListener(
                            inetSocketAddress,
                            b,
                            group.next(),
                            10,
                            10,
                            new Peer(host)));
        }
        return this;
    }

    /**
     * Determines whether the target address represents a self-connection.
     * <p>
     * This prevents the node from connecting to itself during localhost testing
     * or when the seed host resolves to the same machine (e.g. 127.0.0.1, ::1,
     * or any of the node’s bound network interfaces).
     * </p>
     *
     * @param target the target {@link InetSocketAddress} to connect to
     * @return {@code true} if the connection is local/self; {@code false}
     * otherwise
     */
    private boolean isSelfConnection(InetSocketAddress target) {
        try {
            if (target == null || target.getAddress() == null) {
                LOGGER.info(() -> "Checking self-connection: target is NULL");
                return false;
            }

            // Normalize to textual IP
            String targetHost = target.getAddress().getHostAddress();
            int targetPort = target.getPort();

            // Local interfaces (loopback, local, etc.)
            var localAddresses = java.net.NetworkInterface.networkInterfaces()
                    .flatMap(iface -> iface.inetAddresses())
                    .map(java.net.InetAddress::getHostAddress)
                    .toList();

            // Log for debugging
            LOGGER.info(() -> "Checking self-connection: target=" + targetHost + ":" + targetPort
                    + " localAddrs=" + localAddresses);

            // Check if target is local (loopback or any local interface)
            return target.getAddress().isLoopbackAddress()
                    || localAddresses.contains(targetHost)
                    || targetHost.equals("127.0.0.1")
                    || targetHost.equalsIgnoreCase("::1");

            // Optional: also compare the node's listening port if known
            // (e.g., if this producer was created to connect to the same port it’s bound on)
//            boolean samePort = (this.port == targetPort);
//            return isLocalAddress && samePort;
        } catch (Exception e) {
            LOGGER.warning("Error checking self connection: " + e.getMessage());
            return false;
        }
    }

}
