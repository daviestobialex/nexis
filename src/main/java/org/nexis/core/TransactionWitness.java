/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.nexis.base.VarInt;

/**
 *
 * @author daviestobialex
 */
public class TransactionWitness {

    public static final TransactionWitness EMPTY = TransactionWitness.of(Collections.emptyList());

    /**
     * Construct a transaction witness from a given list of arbitrary pushes.
     *
     * @param pushes list of pushes
     * @return constructed transaction witness
     */
    public static TransactionWitness of(List<byte[]> pushes) {
        return new TransactionWitness(pushes);
    }

    private final List<byte[]> pushes;

    private TransactionWitness() {
        this.pushes = new ArrayList<>();
    }

    private TransactionWitness(List<byte[]> pushes) {
        for (byte[] push : pushes) {
            Objects.requireNonNull(push);
        }
        this.pushes = pushes;
    }

    public byte[] getPush(int i) {
        return pushes.get(i);
    }

    public int getPushCount() {
        return pushes.size();
    }

    /**
     * Allocates a byte array and writes into it.
     *
     * @return byte array containing the transaction input
     */
    public byte[] serialize() {
        throw new UnsupportedOperationException("no supported yet");
    }

    // Convert to proto Witness (use when serializing to wire)
    public org.nexus.base.proto.NexusProtocol.Witness toProtoWitness() {
        org.nexus.base.proto.NexusProtocol.Witness.Builder wb = org.nexus.base.proto.NexusProtocol.Witness.newBuilder();
        for (byte[] element : pushes) {
            wb.addStack(com.google.protobuf.ByteString.copyFrom(element));
        }
        return wb.build();
    }

    // Build from proto Witness
    public static TransactionWitness fromProtoWitness(org.nexus.base.proto.NexusProtocol.Witness pw) {
        TransactionWitness w = new TransactionWitness();
        for (com.google.protobuf.ByteString bs : pw.getStackList()) {
            w.push(bs.toByteArray());
        }
        return w;
    }

    private void push(byte[] toByteArray) {
        pushes.add(toByteArray);
    }

    /**
     * Return the size of the serialized message. Note that if the message was
     * deserialized from a payload, this size can differ from the size of the
     * original payload.
     *
     * @return size of the serialized message in bytes
     */
    public int messageSize() {
        int size = VarInt.sizeOf(pushes.size());
        for (byte[] push : pushes) {
            size += VarInt.sizeOf(push.length) + push.length;
        }
        return size;
    }

}
