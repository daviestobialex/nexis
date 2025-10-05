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
package org.nexis.validator;

import java.util.Arrays;
import org.nexis.base.Identity;
import org.nexis.base.Validator;
import org.nexis.exceptions.DropMessageException;
import org.nexus.base.proto.NexusProtocol;

/**
 * {@code IsSelfValidator} is responsible for preventing a node from processing
 * messages that originate from itself.
 * <p>
 * This validator is a part of the inbound message validation pipeline and
 * ensures that self-connections or self-originated messages are ignored,
 * avoiding unnecessary message handling or event loops.
 * </p>
 *
 * <h2>How It Works</h2>
 * <ul>
 * <li>Each node in the network has a unique {@link Identity} that includes a
 * {@code NodeId}.</li>
 * <li>When an incoming {@link NexusProtocol.NexusEnvelop} is received, this
 * validator compares the message’s {@code NodeId} to the server’s
 * {@code NodeId}.</li>
 * <li>If they are identical, it throws a {@link DropMessageException},
 * signaling the pipeline to discard the message.</li>
 * </ul>
 *
 * <h3>Example Usage</h3>
 * This validator should be registered as part of a {@code ValidationPipeline}:
 * <pre>{@code
 * pipeline.addValidator(new IsSelfValidator(localIdentity));
 * }</pre>
 *
 * During message validation:
 * <pre>{@code
 * try {
 *     pipeline.validate(envelop);
 * } catch (DropMessageException ignored) {
 *     // Message dropped silently — self-originated.
 * }
 * }</pre>
 *
 * @author Davies Tobi Alex
 * @since 1.0
 */
public class IsSelfValidator implements Validator {

    /**
     * The identity of the local node running this instance.
     */
    private final Identity serverIdentity;

    /**
     * Constructs a new {@code IsSelfValidator}.
     *
     * @param serverIdentity the local node’s identity used for self-comparison
     */
    public IsSelfValidator(Identity serverIdentity) {
        this.serverIdentity = serverIdentity;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This validator applies to all message types; hence it always returns
     * {@code true}.
     * </p>
     */
    @Override
    public boolean supports(NexusProtocol.NexusMessage message) {
        return true;
    }

    /**
     * Validates that the incoming message was not sent by this same node.
     *
     * @param envelop the received message envelope
     * @throws DropMessageException if the message originates from this same
     * node
     * @throws SecurityException if an unexpected security violation occurs
     */
    @Override
    public void validate(NexusProtocol.NexusEnvelop envelop) throws SecurityException {
        byte[] localNodeId = serverIdentity.getNodeId().getId();
        byte[] messageNodeId = envelop.getNodeId().toByteArray();

        // If this node is the sender, drop the message immediately
        if (Arrays.equals(localNodeId, messageNodeId)) {
            throw new DropMessageException("Dropping self-originated message from node "
                    + serverIdentity.getNodeId());
        }
    }
}
