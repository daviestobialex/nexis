/*
 * Copyright (c) 2025 Nexis Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This package provides the base-level abstractions for node identity management
 * in the Nexis peer-to-peer network. Identity management is fundamental to secure
 * communication, cryptographic signing, and peer authentication. Interfaces in
 * this package define contracts for loading, generating, and persisting node
 * identities that uniquely represent participants in the Nexis network.
 */
package org.nexis.base;

/**
 * {@code IdentityProvider} defines the contract for managing cryptographic
 * node identities within the Nexis peer-to-peer system.
 * <p>
 * Implementations of this interface are responsible for loading an existing
 * identity from secure storage, or generating and persisting a new one if no
 * identity exists. This ensures that each Nexis node has a stable, verifiable
 * identity used for:
 * <ul>
 * <li>Signing and verifying protocol messages</li>
 * <li>Establishing secure peer-to-peer connections</li>
 * <li>Supporting replay-attack prevention and trust establishment</li>
 * </ul>
 * </p>
 *
 * Example usage:
 * <pre>{@code
 IdentityProvider provider = new FileSystemIdentityProvider();
 Identity identity = provider.loadOrCreateIdentity();
 }</pre>
 *
 * @author daviestobialex
 */
public interface IdentityProvider {

    /**
     * Loads an existing {@link Identity} from persistent storage, or
     * generates and securely persists a new one if none exists.
     * <p>
     * This method guarantees that a valid identity will always be returned,
     * ensuring that the node can participate in the Nexis network with a unique
     * and consistent cryptographic identity.
     * </p>
     *
     * @return a valid {@link Identity} associated with this node
     */
    Identity loadOrCreateIdentity();

}
