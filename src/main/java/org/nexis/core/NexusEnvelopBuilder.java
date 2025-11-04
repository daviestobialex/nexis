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
package org.nexis.core;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.Identity;
import org.nexis.utilities.CryptographyUtils;
import org.nexus.base.proto.NexusProtocol;

/**
 * {@code NexusEnvelopBuilder} is responsible for constructing signed,
 * checksummed {@link NexusProtocol.NexusEnvelop} messages ready for
 * transmission over the Nexus P2P network.
 *
 * <p>
 * It encapsulates the process of taking a domain-level {@link NexusMessage},
 * attaching required metadata (checksum, node ID, timestamp), and producing a
 * cryptographically signed envelope that can be verified by peers.
 * </p>
 *
 * <h3>Responsibilities</h3>
 * <ul>
 * <li>Compute and embed a {@code checksum} for integrity validation.</li>
 * <li>Attach the sending node’s unique {@code nodeId}.</li>
 * <li>Include the serialized {@link NexusMessage} payload.</li>
 * <li>Embed a network timestamp ({@link Timestamp}).</li>
 * <li>Sign the serialized message with the node’s private key to ensure
 * authenticity and non-repudiation.</li>
 * </ul>
 *
 * <h3>Design Notes</h3>
 * <ul>
 * <li>This class is {@code final} to guarantee immutability and to prevent
 * sub-classing that might compromise signature generation.</li>
 * <li>All cryptographic operations use the Ed25519 algorithm via the Bouncy
 * Castle provider ({@code "BC"}).</li>
 * <li>Exception handling currently logs errors and returns {@code null};
 * callers are expected to handle {@code null} envelopes gracefully.</li>
 * </ul>
 *
 * <h3>Example</h3>
 * <pre>{@code
 * Identity identity = ...; // contains key pair
 * NexusEnvelopBuilder builder = new NexusEnvelopBuilder(identity);
 *
 * NexusMessage msg = NexusMessage.ping(...);
 * NexusProtocol.NexusEnvelop envelop = builder.build(msg);
 *
 * // Send over network via Netty channel
 * channel.writeAndFlush(envelop);
 * }</pre>
 *
 * @author daviestobialex
 */
public final class NexusEnvelopBuilder {

    /**
     * The local node’s identity, including key pair and ID.
     */
    private final Identity node;

    /**
     * Constructs a builder bound to a specific node identity.
     *
     * @param node the identity of the node building envelopes
     */
    public NexusEnvelopBuilder(Identity node) {
        this.node = node;
    }

    /**
     * Builds a signed {@link NexusProtocol.NexusEnvelop} from the given
     * {@link NexusMessage}.
     *
     * <p>
     * Steps performed:
     * <ol>
     * <li>Compute the checksum from the message payload.</li>
     * <li>Embed the sending node ID.</li>
     * <li>Attach the serialized {@link NexusMessage} itself.</li>
     * <li>Set the current wall-clock timestamp.</li>
     * <li>Generate an Ed25519 signature over the serialized message.</li>
     * </ol>
     * </p>
     *
     * @param message the domain-level message to wrap
     * @return a fully-formed, signed envelope, or {@code null} if signing fails
     */
    public NexusProtocol.NexusEnvelop build(NexusMessage message) {
        try {
            byte[] signature = CryptographyUtils.sign(message.serialize(), node.getKeyPair().getPrivate());

            NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                    .setChecksum(ByteString.copyFrom(message.checkSum()))
                    .setNodeId(ByteString.copyFrom(message.getNodeId()))
                    .setMessage(message.message())
                    .setTimeStamp(now())
                    .setSignature(ByteString.copyFrom(signature))
                    .build();
            return envelop;
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | SignatureException ex) {
            Logger.getLogger(NexusEnvelopBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    /**
     * Returns the current timestamp in protobuf format.
     *
     * @return a {@link Timestamp} representing system wall-clock time
     */
    public static Timestamp now() {
        long millis = System.currentTimeMillis();
        return Timestamp.newBuilder()
                .setSeconds(millis / 1000)
                .setNanos((int) ((millis % 1000) * 1_000_000))
                .build();
    }

    /**
     * @return the identity of the node building envelopes
     */
    public Identity getNode() {
        return node;
    }

}
