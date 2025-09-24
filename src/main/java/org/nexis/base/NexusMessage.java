/*
 * Copyright by the original author.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.base;

import java.nio.ByteBuffer;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.utilities.ByteUtils;
import org.nexis.utilities.Sha256Hash;

/**
 * Represents the abstract contract for a Nexus protocol message.
 * <p>
 * This interface defines the fundamental properties and operations
 * expected from any message transmitted across the Nexus network.
 * By standardizing how messages expose their identity, serialization,
 * and validation mechanisms, we ensure that all message types can be
 * uniformly dispatched, verified, and reconstructed.
 * </p>
 *
 * <h2>Design Intent</h2>
 * <ul>
 *   <li><b>Identity:</b> Each message carries the {@code nodeId()} that
 *       identifies the originating node.</li>
 *   <li><b>Structure:</b> The {@link NexusProtocol.NexusMessage} is the
 *       raw protobuf payload representing the message body.</li>
 *   <li><b>Serialization:</b> The {@link #serialize()} method ensures
 *       messages can be flattened into a stable byte representation for
 *       transmission or signing.</li>
 *   <li><b>Integrity:</b> The {@link #checkSum()} method guarantees
 *       tamper detection via double SHA-256 hashing (similar to Bitcoin’s
 *       block and transaction integrity checks).</li>
 *   <li><b>Transport Safety:</b> {@link #getByteConcatenatedPayload(int)}
 *       prepends magic bytes and concatenates critical fields into a
 *       canonical payload, ensuring proper framing and network separation.</li>
 * </ul>
 *
 * <p>
 * Implementations should be <b>immutable</b> wherever possible to prevent
 * accidental modification of message state after construction.
 * </p>
 *
 * @author
 *   daviestobialex
 */
public interface NexusMessage {

    /**
     * Returns the unique identifier of the node that originated this message.
     * <p>
     * This ID is typically derived from the node’s cryptographic identity and
     * is essential for routing, trust evaluation, and accountability within
     * the Nexus network.
     * </p>
     *
     * @return the raw {@code byte[]} representing the node ID
     */
    byte[] nodeId();

    /**
     * Returns the protobuf representation of this message’s content.
     * <p>
     * The {@link NexusProtocol.NexusMessage} object encapsulates the
     * structured data carried by this message. It is the canonical form
     * used in serialization, signing, and interpretation by handlers.
     * </p>
     *
     * @return the protobuf payload of this message
     */
    NexusProtocol.NexusMessage message();

    /**
     * Serializes the message into a deterministic byte array.
     * <p>
     * This representation must be stable across nodes and JVMs to ensure
     * consistent hashing and signature verification. Typically includes
     * both {@link #message()} and {@link #nodeId()} data.
     * </p>
     *
     * @return the serialized byte representation of this message
     */
    byte[] serialize();

    /**
     * Computes and returns the integrity checksum of this message.
     * <p>
     * The checksum is calculated by performing a double SHA-256 hash
     * over the serialized message. This mirrors Bitcoin’s approach to
     * collision resistance and tamper detection.
     * </p>
     *
     * @return a 32-byte checksum of the message
     */
    byte[] checkSum();

    /**
     * Computes a checksum for any arbitrary byte input using double SHA-256.
     *
     * @param input the data to hash
     * @return the resulting checksum
     */
    static byte[] computeChecksum(byte[] input) {
        return Sha256Hash.hashTwice(input);
    }

    /**
     * Constructs a network-ready byte payload containing the magic bytes,
     * the protobuf message, and the node ID in concatenated form.
     * <p>
     * The "magic bytes" act as a network discriminator or protocol
     * version marker, preventing accidental message crossover between
     * incompatible networks (e.g., testnet vs mainnet).
     * </p>
     *
     * <h3>Payload Layout:</h3>
     * <pre>
     * +------------+----------------------+------------------+
     * | magic (4B) | message payload (N) | nodeId (M bytes) |
     * +------------+----------------------+------------------+
     * </pre>
     *
     * @param magicBytes the 4-byte magic constant representing the network
     * @return a concatenated byte array representing the transport payload
     */
    default byte[] getByteConcatenatedPayload(int magicBytes) {
        byte[] magic = ByteUtils.writInt32BE(magicBytes);
        ByteBuffer buffer = ByteBuffer.allocate(
                magic.length + message().toByteArray().length + nodeId().length
        );
        buffer.put(magic);
        buffer.put(message().toByteArray());
        buffer.put(nodeId());
        return buffer.array();
    }
}
