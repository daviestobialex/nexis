/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.validator;

import java.nio.ByteBuffer;
import java.security.Security;
import java.security.Signature;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.nexus.base.MessageValidator;
import org.nexus.base.NetworkConfiguration;
import org.nexus.base.PublicNodeProperties;
import org.nexus.base.proto.NexusProtocol;
import org.nexus.core.PeerRegistry;
import org.nexus.internal.ByteUtils;
import static org.nexus.internal.CryptographyUtils.ED25519_ALGO;
import static org.nexus.internal.CryptographyUtils.bytesToPublicKey;

/**
 *
 * @author daviestobialex
 */
public class SignatureValidator implements MessageValidator {

    private final PeerRegistry peerRegistry;
    private final NetworkConfiguration params;

    public SignatureValidator(NetworkConfiguration params) {
        this.peerRegistry = PeerRegistry.getInstance();
        this.params = params;
    }

    @Override
    public boolean supports(NexusProtocol.NexusMessage message) {
        // ✅ Only messages that must be signed
        return message.hasManifest()
                || message.hasCatalog()
                || message.hasFunctionCall();
    }

    @Override
    public void validate(NexusProtocol.NexusEnvelop envelop) throws SecurityException {
        
        NexusProtocol.NexusMessage message = envelop.getMessage();
        byte[] payload = message.toByteArray();
        byte[] networkBytes = ByteUtils.writInt32BE(params.getPacketMagic());
        byte[] nodeId = envelop.getNodeId().toByteArray();
        byte[] messageId = envelop.getMessageId().toByteArray();

        ByteBuffer buffer = ByteBuffer.allocate(payload.length + networkBytes.length + messageId.length + nodeId.length);
        buffer.put(networkBytes);
        buffer.put(payload);
        buffer.put(nodeId);
        buffer.put(messageId);
        PublicNodeProperties nodeProps = peerRegistry.getNodeById(nodeId);

        if (nodeProps == null || nodeProps.getPublicKey() == null) {
            throw new SecurityException("Unknown node or missing public key");
        }

        try {
            Security.addProvider(new BouncyCastleProvider());
            Signature sig = Signature.getInstance(ED25519_ALGO, "BC");
            sig.initVerify(bytesToPublicKey(nodeProps.getPublicKey(), ED25519_ALGO));
            sig.update(envelop.getMessage().toByteArray());

            if (!sig.verify(envelop.getSignature().toByteArray())) {
                throw new SecurityException("Invalid signature for message ID: " + envelop.getMessageId());
            }
        } catch (Exception e) {
            throw new SecurityException("Signature validation failed", e);
        }
    }
}
