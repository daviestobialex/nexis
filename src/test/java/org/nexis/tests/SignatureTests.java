/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.tests;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Test;
import static org.nexis.utilities.CryptographyUtils.ED25519_ALGO;
import org.nexis.utilities.HexFormat;

/**
 *
 * @author daviestobialex
 */
public class SignatureTests {

    @Test
    public void rawED25519SignatureTest() throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {

        KeyPairGenerator kpg = KeyPairGenerator.getInstance(ED25519_ALGO);
        KeyPair keyPair = kpg.generateKeyPair();
        PublicKey publicKey = keyPair.getPublic();
        PrivateKey privateKey = keyPair.getPrivate();

        // The message to be signed
        String message = "This is a test message for Ed25519.";
        byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

        // 2. Sign the message with the private key
        Security.addProvider(new BouncyCastleProvider());
        Signature sig = Signature.getInstance(ED25519_ALGO, "BC");
        sig.initSign(privateKey);
        sig.update(messageBytes);
        byte[] signatureBytes = sig.sign();;

        System.out.println("Signature (as hex): " + HexFormat.bytesToHex(signatureBytes));

        // 3. Verify the signature with the public key
        sig.initVerify(publicKey);
        sig.update(messageBytes);
        boolean isVerified = sig.verify(signatureBytes);

        System.out.println("Signature verification result: " + isVerified);
    }

    public void objectUsageED25519SignatureTest() {

    }
}
