/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

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
}
