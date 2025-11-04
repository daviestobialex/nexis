/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

/**
 *
 * @author daviestobialex
 */
import java.math.BigInteger;
import java.util.HexFormat;
import org.nexis.base.PublicNodeProperties;
import org.nexis.base.Sha256Hash;

/**
 *
 * @author daviestobialex
 */
public final class NodeId implements PublicNodeProperties {

    private final Sha256Hash id;
    private final byte[] pubKey;

    public NodeId(byte[] pubKey) {
        this.id = stableNodeId(pubKey);
        this.pubKey = pubKey;
    }

    public static NodeId fromPublicKey(byte[] pubKey) {
        return new NodeId(pubKey);
    }

    public BigInteger toBigInt() {
        return new BigInteger(1, id.getBytes());
    }

    public int bitLength() {
        return id.getBytes().length * 8;
    }

    /**
     * XOR distance metric between two NodeIds.
     *
     * @param other
     * @return
     */
    public BigInteger distanceTo(NodeId other) {
        byte[] result = new byte[id.getBytes().length];
        for (int i = 0; i < id.getBytes().length; i++) {
            result[i] = (byte) (id.getBytes()[i] ^ other.id.getBytes()[i]);
        }
        return new BigInteger(1, result);
    }

    public String toHex() {
        return HexFormat.of().formatHex(id.getBytes());
    }

    /**
     * This creates an instance of {@code Sha256Hash}, which is a double hash of
     * the public key to produce a 32 byte length array
     *
     * @param publicKey public key bytes
     * @return
     */
    public static Sha256Hash stableNodeId(byte[] publicKey) {
        return Sha256Hash.twiceOf(publicKey);
    }

    @Override
    public byte[] getId() {
        return id.getBytes();
    }

    @Override
    public byte[] getPublicKey() {
        return pubKey;
    }

}
