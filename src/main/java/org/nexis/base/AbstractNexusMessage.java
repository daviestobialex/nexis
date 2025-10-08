/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.base;

import java.nio.ByteBuffer;
import org.nexus.base.proto.NexusProtocol;

/**
 * Abstract base class for Nexus protocol message serialization.
 * <p>
 * This class provides common serialization and checksum computation logic
 * for all Nexus message types, eliminating code duplication across
 * concrete message implementations.
 * </p>
 *
 * <h2>Design Intent</h2>
 * <ul>
 *   <li><b>DRY Principle:</b> Centralizes serialization logic that was
 *       previously duplicated across message types.</li>
 *   <li><b>Consistency:</b> Ensures all messages follow the same
 *       serialization format: [payload][checksum]</li>
 *   <li><b>Extensibility:</b> Subclasses only need to implement
 *       {@link #getProtobufMessage()} to define their specific payload.</li>
 *   <li><b>Immutability:</b> Encourages immutable message design by
 *       providing final serialization methods.</li>
 * </ul>
 *
 * <h3>Serialization Format:</h3>
 * <pre>
 * +-----------------+----------------+
 * | Payload         | Checksum (32B) |
 * +-----------------+----------------+
 * </pre>
 *
 * <h3>Payload Structure:</h3>
 * <pre>
 * +------------+----------------------+------------------+
 * | magic (4B) | protobuf message (N) | nodeId (M bytes) |
 * +------------+----------------------+------------------+
 * </pre>
 *
 * @author daviestobialex
 */
public abstract class AbstractNexusMessage implements NexusMessage {
    
    protected final byte[] nodeId;
    protected final NetworkConfiguration params;
    
    /**
     * Constructs an abstract Nexus message with network parameters and node identity.
     *
     * @param params the network configuration containing magic bytes and other settings
     * @param nodeId the unique identifier of the originating node
     * @throws IllegalArgumentException if params or nodeId is null
     */
    protected AbstractNexusMessage(NetworkConfiguration params, byte[] nodeId) {
        if (params == null) {
            throw new IllegalArgumentException("Network configuration cannot be null");
        }
        if (nodeId == null || nodeId.length == 0) {
            throw new IllegalArgumentException("Node ID cannot be null or empty");
        }
        this.params = params;
        this.nodeId = nodeId;
    }
    
    /**
     * Returns the node ID of the message originator.
     *
     * @return the node identifier as a byte array
     */
    @Override
    public byte[] getNodeId() {
        return nodeId;
    }
    
    /**
     * Subclasses must implement this to return their specific protobuf message type
     * wrapped in the appropriate NexusMessage field.
     * <p>
     * Example implementations:
     * <pre>
     * // For GetManifestContent
     * return NexusProtocol.NexusMessage.newBuilder()
     *     .setGetManifestContent(manifest)
     *     .build();
     *
     * // For GetPeers
     * return NexusProtocol.NexusMessage.newBuilder()
     *     .setPeersDiscovery(getPeers)
     *     .build();
     * </pre>
     *
     * @return the protobuf NexusMessage containing the specific message payload
     */
    protected abstract NexusProtocol.NexusMessage getProtobufMessage();
    
    /**
     * Returns the protobuf representation of this message.
     * <p>
     * This delegates to {@link #getProtobufMessage()} which subclasses must implement.
     * </p>
     *
     * @return the protobuf NexusMessage
     */
    @Override
    public final NexusProtocol.NexusMessage message() {
        return getProtobufMessage();
    }
    
    /**
     * Serializes the message into a deterministic byte array.
     * <p>
     * The serialization format consists of:
     * <ol>
     *   <li>The concatenated payload (magic bytes + protobuf message + nodeId)</li>
     *   <li>A 32-byte double SHA-256 checksum of the payload</li>
     * </ol>
     * </p>
     *
     * <p>
     * This format ensures message integrity and allows receivers to validate
     * that the message hasn't been tampered with during transmission.
     * </p>
     *
     * @return the complete serialized message including checksum
     */
    @Override
    public final byte[] serialize() {
        // Get the base payload (magic + message + nodeId)
        byte[] payload = getByteConcatenatedPayload(params.getPacketMagic());
        
        // Compute checksum over the payload
        byte[] checksum = NexusMessage.computeChecksum(payload);
        
        // Optional: Add debug logging (remove in production)
        if (isDebugEnabled()) {
            logSerialization(payload, checksum);
        }
        
        // Allocate buffer for payload + checksum
        ByteBuffer buffer = ByteBuffer.allocate(payload.length + checksum.length);
        buffer.put(payload);
        buffer.put(checksum);
        
        return buffer.array();
    }
    
    /**
     * Computes and returns the integrity checksum of this message.
     * <p>
     * The checksum is calculated by performing a double SHA-256 hash
     * over the concatenated payload (magic bytes + protobuf message + nodeId).
     * </p>
     *
     * @return a 32-byte checksum of the message payload
     */
    @Override
    public final byte[] checkSum() {
        byte[] payload = getByteConcatenatedPayload(params.getPacketMagic());
        return NexusMessage.computeChecksum(payload);
    }
    
    /**
     * Hook for subclasses to enable debug logging.
     * Default implementation returns false.
     *
     * @return true if debug logging should be enabled
     */
    protected boolean isDebugEnabled() {
        return false;
    }
    
    /**
     * Logs serialization details for debugging purposes.
     * Subclasses can override to customize logging behavior.
     *
     * @param payload the payload bytes
     * @param checksum the checksum bytes
     */
    protected void logSerialization(byte[] payload, byte[] checksum) {
        System.out.println("PAYLOAD: " + java.util.Base64.getEncoder().encodeToString(payload));
        System.out.println("PAYLOAD LEN: " + payload.length + " CHECKSUM LEN: " + checksum.length);
    }
    
    /**
     * Returns the network configuration parameters.
     *
     * @return the network configuration
     */
    protected final NetworkConfiguration getParams() {
        return params;
    }
    
    /**
     * Validates the checksum of a received serialized message.
     * <p>
     * This static utility method can be used by message receivers to verify
     * message integrity before deserialization.
     * </p>
     *
     * @param serializedMessage the complete serialized message (payload + checksum)
     * @param expectedChecksumLength the expected checksum length (typically 32 bytes)
     * @return true if the checksum is valid, false otherwise
     * @throws IllegalArgumentException if the message is too short to contain a checksum
     */
    public static boolean validateChecksum(byte[] serializedMessage, int expectedChecksumLength) {
        if (serializedMessage == null || serializedMessage.length <= expectedChecksumLength) {
            throw new IllegalArgumentException(
                "Serialized message must be longer than checksum length");
        }
        
        // Split payload and checksum
        int payloadLength = serializedMessage.length - expectedChecksumLength;
        byte[] payload = new byte[payloadLength];
        byte[] receivedChecksum = new byte[expectedChecksumLength];
        
        System.arraycopy(serializedMessage, 0, payload, 0, payloadLength);
        System.arraycopy(serializedMessage, payloadLength, receivedChecksum, 0, expectedChecksumLength);
        
        // Compute expected checksum
        byte[] computedChecksum = NexusMessage.computeChecksum(payload);
        
        // Compare checksums using constant-time comparison to prevent timing attacks
        return java.security.MessageDigest.isEqual(receivedChecksum, computedChecksum);
    }
}