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
package org.nexis.base;

import org.nexis.core.NodeId;
import java.security.KeyPair;

/**
 * Interface for a generic Nexus-like node. See
 *
 * @author daviestobialex
 */
public interface Identity {

    /**
     * Unique identifier for this node (derived from the public key).
     *
     * @param pubkey
     * @return
     */
    NodeId getNodeId(byte[] pubkey);

    /**
     * The cryptographic keypair representing this node.
     *
     * @return
     */
    KeyPair getKeyPair();

    /**
     * Load keypair from disk (throws if not found or invalid).
     *
     * @param provider
     * @return
     */
    static Identity loadOrCreate(IdentityProvider provider) {
        return provider.loadOrCreateIdentity();
    }

}
