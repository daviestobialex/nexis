/*
 * Copyright (c) 2025 Nexis Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * This package provides base-level abstractions for node identity and public
 * properties within the Nexis peer-to-peer network. These abstractions define
 * how node information, such as public keys and identifiers, is exposed for
 * network communication and cryptographic verification.
 */
package org.nexis.base;

/**
 * {@code PublicNodeProperties} represents the publicly visible attributes of a
 * node in the Nexis network.
 * <p>
 * Implementations of this interface expose:
 * <ul>
 *   <li>A unique node identifier, derived from cryptographic keys or assigned IDs.</li>
 *   <li>The node's public key, used for message signing verification and secure peer communication.</li>
 * </ul>
 * <p>
 * This interface is used during handshake, peer discovery, and message validation
 * to ensure nodes can authenticate and interact securely within the network.
 * </p>
 *
 * Example usage:
 * <pre>{@code
 * PublicNodeProperties node = peerRegistry.getNodeById(peerId);
 * byte[] publicKey = node.getPublicKey();
 * }</pre>
 *
 * <p><b>Suggested alternative names:</b></p>
 * <ul>
 *   <li>{@code NodeInfo} – simpler, conveys node metadata.</li>
 *   <li>{@code PeerIdentity} – emphasizes peer identity for cryptographic and network purposes.</li>
 *   <li>{@code NodeProfile} – conveys a structured view of a node’s public attributes.</li>
 * </ul>
 * 
 * @author 
 *   daviestobialex
 */
public interface PublicNodeProperties {

    /**
     * Returns the unique identifier of this node.
     * <p>
     * This ID may be derived from the node's public key or assigned by the network.
     * </p>
     *
     * @return a byte array representing the node ID
     */
    byte[] getId();

    /**
     * Returns the public key associated with this node.
     * <p>
     * This key is used for verifying signatures, establishing secure connections,
     * and validating protocol messages.
     * </p>
     *
     * @return a byte array containing the node's public key
     */
    byte[] getPublicKey();
}
