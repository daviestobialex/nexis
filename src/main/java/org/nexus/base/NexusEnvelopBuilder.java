/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.base;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public final class NexusEnvelopBuilder {

    private final NodeIdentity node;

    public NexusEnvelopBuilder(NodeIdentity node) {
        this.node = node;
    }

    public NexusProtocol.NexusEnvelop build(NexusMessage message) {
        try {
            NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                    .setChecksum(ByteString.copyFrom(message.checkSum()))
                    .setNodeId(ByteString.copyFrom(message.nodeId()))
                    .setMessageId(ByteString.copyFrom(message.messageId()))
                    .setMessage(message.message())
                    .setTimeStamp(now())
                    .setSignature(ByteString.copyFrom(sign(message.serialize())))
                    .build();
            return envelop;
        } catch (NoSuchAlgorithmException | NoSuchProviderException | InvalidKeyException | SignatureException ex) {
            Logger.getLogger(NexusEnvelopBuilder.class.getName()).log(Level.SEVERE, null, ex);
        }

        return null;
    }

    public byte[] sign(byte[] toSign) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {

        // Add the Bouncy Castle provider
        Security.addProvider(new BouncyCastleProvider());
        Signature sig = Signature.getInstance("Ed25519", "BC");
        sig.initSign(node.getKeyPair().getPrivate());
        sig.update(toSign);
        return sig.sign();
    }

    public static Timestamp now() {
        long millis = System.currentTimeMillis();
        return Timestamp.newBuilder()
                .setSeconds(millis / 1000)
                .setNanos((int) ((millis % 1000) * 1_000_000))
                .build();
    }

    public NodeIdentity getNode() {
        return node;
    }

}
