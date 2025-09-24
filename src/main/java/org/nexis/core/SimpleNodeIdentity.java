/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.security.KeyPair;

import java.nio.charset.StandardCharsets;
import org.nexis.base.NodeIdentity;

/**
 *
 * @author daviestobialex
 */
public class SimpleNodeIdentity implements NodeIdentity {

    private final KeyPair keyPair;

    public SimpleNodeIdentity(KeyPair keyPair) {
        this.keyPair = keyPair;
    }

    /**
     * Returns the unique identifier for this node, typically derived as a hash
     * of the node's public key.
     * <p>
     * The {@code NodeId} is a cornerstone of the system for several reasons:
     *
     * <ul>
     * <li><b>Cryptographic Identity:</b> Because the NodeId is derived from the
     * public key, it can be used to verify that a peer really controls its
     * claimed identity via challenge-response signatures.</li>
     *
     * <li><b>Peer Authentication:</b> When nodes communicate, they use their
     * NodeId to establish secure, verifiable sessions, ensuring that only the
     * holder of the corresponding private key can sign messages for that
     * ID.</li>
     *
     * <li><b>Overlay Network Position:</b> In a Distributed Hash Table (DHT),
     * the NodeId determines the node's "coordinate" in the keyspace. This
     * enables the XOR-based distance metric used for efficient peer discovery,
     * routing, and lookup operations.</li>
     *
     * <li><b>Data Responsibility:</b> Content or resources (such as manifests)
     * are identified by their own hash-based keys. The nodes whose NodeIds are
     * closest to those keys in the XOR metric space become responsible for
     * storing and serving them.</li>
     *
     * <li><b>Reputation and Trust:</b> Since the NodeId is stable across
     * restarts (if keys are persisted), it can be used to build peer
     * reputations, apply blacklists/ whitelists, and audit network
     * activity.</li>
     * </ul>
     *
     * In short, the NodeId is not just an identifier but the anchor for
     * authentication, routing, and data placement in the peer-to-peer system.
     *
     * @param publicKey
     * @return a stable, unique identifier string for this node.
     */
    @Override
    public NodeId getNodeId(String publicKey) {
        return NodeId.fromPublicKey(publicKey.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public KeyPair getKeyPair() {
        return this.keyPair;
    }

}
