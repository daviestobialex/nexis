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
import java.util.logging.Logger;
import org.nexis.base.PublicNodeProperties;
import org.nexis.utilities.Sha256Hash;

/**
 *
 * @author daviestobialex
 */
public final class NodeId implements PublicNodeProperties {

    private final byte[] id;
    private final byte[] pubKey;
    private final static Logger log = Logger.getLogger(NodeId.class.getName());

    public NodeId(byte[] pubKey) {
        this.id = stableNodeId(pubKey);
        this.pubKey = pubKey;
    }

    public static NodeId fromPublicKey(byte[] pubKey) {
        return new NodeId(pubKey);
    }

    public BigInteger toBigInt() {
        return new BigInteger(1, id);
    }

    public int bitLength() {
        return id.length * 8;
    }

    /**
     * XOR distance metric between two NodeIds.
     *
     * @param other
     * @return
     */
    public BigInteger distanceTo(NodeId other) {
        byte[] result = new byte[id.length];
        for (int i = 0; i < id.length; i++) {
            result[i] = (byte) (id[i] ^ other.id[i]);
        }
        return new BigInteger(1, result);
    }

    public String toHex() {
        return HexFormat.of().formatHex(id);
    }

    public static byte[] stableNodeId(byte[] input) {
        return Sha256Hash.hash(input);
    }

    @Override
    public byte[] getId() {
        return id;
    }

    @Override
    public byte[] getPublicKey() {
        return pubKey;
    }

}
