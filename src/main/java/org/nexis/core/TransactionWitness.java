/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.nexis.base.VarInt;
import org.nexis.internal.Buffers;
import static org.nexis.internal.Preconditions.check;
import static org.nexis.internal.StreamUtils.MAX_INITIAL_ARRAY_LENGTH;

/**
 *
 * This structure contains data required to check transaction validity but not
 * required to determine transaction effects. It is described as a number of
 * byte vectors called "pushes". Those vectors are pushed to the script stack
 * before script execution when validating a transaction.
 * <p>
 * For example, for inputs spending a P2WPKH output the witness consists of a
 * signature and a public key – the same data that would have been pushed to the
 * stack via a scriptSig for P2PKH.
 * <p>
 * Instances of this class are immutable.
 *
 * @see
 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0141.mediawiki">BIP
 * 141</a>
 * @see
 * <a href="https://github.com/bitcoin/bips/blob/master/bip-0143.mediawiki">BIP
 * 143</a>
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

    /**
     * Deserialize this transaction witness from a given payload.
     *
     * @param payload payload to deserialize from
     * @return read message
     * @throws BufferUnderflowException if the read message extends beyond the
     * remaining bytes of the payload
     */
    public static TransactionWitness read(ByteBuffer payload) throws BufferUnderflowException {
        VarInt pushCountVarInt = VarInt.read(payload);
        check(pushCountVarInt.fitsInt(), BufferUnderflowException::new);
        int pushCount = pushCountVarInt.intValue();
        List<byte[]> pushes = new ArrayList<>(Math.min(pushCount, MAX_INITIAL_ARRAY_LENGTH));
        for (int y = 0; y < pushCount; y++) {
            pushes.add(Buffers.readLengthPrefixedBytes(payload));
        }
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
     * Write this transaction witness into the given buffer.
     *
     * @param buf buffer to write into
     * @return the buffer
     * @throws BufferOverflowException if the serialized data doesn't fit the
     * remaining buffer
     */
    public ByteBuffer write(ByteBuffer buf) throws BufferOverflowException {
        VarInt.of(pushes.size()).write(buf);
        for (byte[] push : pushes) {
            Buffers.writeLengthPrefixedBytes(buf, push);
        }
        return buf;
    }

    /**
     * Allocates a byte array and writes this transaction witness into it.
     *
     * @return byte array containing the transaction witness
     */
    public byte[] serialize() {
        return write(ByteBuffer.allocate(messageSize())).array();
    }

    /**
     * Return the size of the serialized message. Note that if the message was
     * deserialized from a payload, this size can differ from the size of the
     * original payload.
     *
     * @return size of the serialized message in bytes
     */
    public int messageSize() {
        return VarInt.sizeOf(pushes.size())
                + pushes.stream()
                        .mapToInt(Buffers::lengthPrefixedBytesSize)
                        .sum();
    }

    // Convert to proto Witness (use when serializing to wire)
    public org.nexus.base.proto.NexusProtocol.Witness toProto() {
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
     * Creates the stack pushes necessary to redeem a P2WPKH output.If given
     * signature is null, an empty push will be used as a placeholder.
     *
     * @param signature
     * @param pubKey
     * @return
     */
    public static TransactionWitness redeemP2WPKH(byte[] signature, PublicKey pubKey) {
//        checkArgument(pubKey.isCompressed(), ()
//                -> "only compressed keys allowed");// TODO: No comoression, using raw public key but you need to check again here
        List<byte[]> pushes = new ArrayList<>(2);
        pushes.add(signature != null ? signature : new byte[0]); // signature
        pushes.add(pubKey.getEncoded()); // pubkey
        return TransactionWitness.of(pushes);
    }

}
