/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.base;

/**
 *
 * @author daviestobialex
 */
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author daviestobialex
 */
public final class NodeId {

    private final byte[] id;
    private final static Logger log = Logger.getLogger(NodeId.class.getName());

    public NodeId(byte[] id) {
        this.id = id;
    }

    public static NodeId fromPublicKey(byte[] pubKey) {
        return new NodeId(stableNodeId(pubKey));
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

    private static byte[] stableNodeId(byte[] input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input);
            // Take the first 8 bytes of the hash
            return ByteBuffer.wrap(hash).array();
        } catch (NoSuchAlgorithmException ex) {
            log.log(Level.SEVERE, "Node can not start without node id generation", ex);
            return new byte[]{};
        }
    }

    public byte[] getId() {
        return id;
    }

}
