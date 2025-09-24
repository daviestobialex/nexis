/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.validator;

import java.nio.ByteBuffer;
import java.security.Security;
import java.security.Signature;
import java.util.logging.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.nexis.base.MessageValidator;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.PublicNodeProperties;
import org.nexus.base.proto.NexusProtocol;
import org.nexis.core.PeerAddress;
import org.nexis.core.PeerRegistry;
import org.nexis.handlers.message.ChallangeResponseHandler;
import org.nexis.internal.ByteUtils;
import static org.nexis.internal.CryptographyUtils.ED25519_ALGO;
import static org.nexis.internal.CryptographyUtils.bytesToPublicKey;

/**
 *
 * @author daviestobialex
 */
public class SignatureValidator implements MessageValidator {
    
    private final PeerRegistry peerRegistry;
    private final NetworkConfiguration params;
    private static final Logger LOGGER = Logger.getLogger(ChallangeResponseHandler.class.getName());
    
    public SignatureValidator(NetworkConfiguration params) {
        this.peerRegistry = PeerRegistry.getInstance();
        this.params = params;
    }
    
    @Override
    public boolean supports(NexusProtocol.NexusMessage message) {
        // Only messages that must be signed
        return message.hasChallenge()
                || message.hasManifest()
                || message.hasCatalog()
                || message.hasFunctionCall();
    }
    
    @Override
    public void validate(NexusProtocol.NexusEnvelop envelop) throws SecurityException {
        
        NexusProtocol.NexusMessage message = envelop.getMessage();
        byte[] payload = message.toByteArray();
        byte[] networkBytes = ByteUtils.writInt32BE(params.getPacketMagic());
        byte[] nodeId = envelop.getNodeId().toByteArray();
        byte[] pubKey;
        ByteBuffer buffer = ByteBuffer.allocate(payload.length + networkBytes.length +  nodeId.length);
        buffer.put(networkBytes);
        buffer.put(payload);
        buffer.put(nodeId);
        
        if (envelop.getMessage().hasChallenge()) {
            pubKey = envelop.getMessage().getChallenge().getPublicKey().toByteArray();
        } else {
            PeerAddress nodeProps = peerRegistry.getNodeById(nodeId);
            
            if (nodeProps == null || nodeProps.getPublicKey() == null) {
                throw new SecurityException("Unknown node or missing public key");
            }
            pubKey = nodeProps.getPublicKey();
        }
        LOGGER.info("PUB KEY LEN " + pubKey.length + " BUF ARRA = " + buffer.array().length);
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
