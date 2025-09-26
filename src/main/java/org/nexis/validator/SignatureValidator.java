/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.validator;

import java.nio.ByteBuffer;
import java.security.Security;
import java.security.Signature;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.nexis.base.NetworkConfiguration;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.base.PeerAddress;
import org.nexis.core.PeerRegistry;
import org.nexis.utilities.ByteUtils;
import static org.nexis.utilities.CryptographyUtils.ED25519_ALGO;
import static org.nexis.utilities.CryptographyUtils.bytesToPublicKey;
import org.nexis.base.Validator;

/**
 *
 * @author daviestobialex
 */
public class SignatureValidator implements Validator {

    private final PeerRegistry peerRegistry;
    private final NetworkConfiguration params;

    public SignatureValidator(NetworkConfiguration params) {
        this.peerRegistry = PeerRegistry.getInstance();
        this.params = params;
    }

    @Override
    public boolean supports(NexusProtocol.NexusMessage message) {
        // Only messages that must be signed
        return message.hasHandshakeResponse()
                || message.hasManifest()
                || message.hasFunctionCall();
    }

    @Override
    public void validate(NexusProtocol.NexusEnvelop envelop) throws SecurityException {

        NexusProtocol.NexusMessage message = envelop.getMessage();
        byte[] payload = message.toByteArray();
        byte[] networkBytes = ByteUtils.writInt32BE(params.getPacketMagic());
        byte[] nodeId = envelop.getNodeId().toByteArray();
        byte[] pubKey;
        ByteBuffer buffer = ByteBuffer.allocate(payload.length + networkBytes.length + nodeId.length + envelop.getChecksum().toByteArray().length);
        buffer.put(networkBytes);
        buffer.put(payload);
        buffer.put(nodeId);
        buffer.put(envelop.getChecksum().toByteArray());

        if (envelop.getMessage().hasHandshakeResponse()) {
            pubKey = envelop.getMessage().getHandshakeResponse().getPublicKey().toByteArray();
        } else {
            PeerAddress nodeProps = peerRegistry.getNodeById(nodeId);

            if (nodeProps == null || nodeProps.getPublicKey() == null) {
                throw new SecurityException("Unknown node or missing public key");
            }
            pubKey = nodeProps.getPublicKey();
        }

        try {
            Security.addProvider(new BouncyCastleProvider());
            Signature sig = Signature.getInstance(ED25519_ALGO, "BC");
            sig.initVerify(bytesToPublicKey(pubKey, ED25519_ALGO));
            sig.update(buffer.array());

            if (!sig.verify(envelop.getSignature().toByteArray())) {
                throw new SecurityException("Invalid signature from node ID: " + envelop.getNodeId());
            }
        } catch (Exception e) {
            throw new SecurityException("Signature validation failed", e);
        }
    }
}
