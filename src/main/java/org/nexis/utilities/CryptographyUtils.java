/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.utilities;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.InvalidKeyException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.Signature;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.bouncycastle.crypto.digests.RIPEMD160Digest;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

/**
 *
 * @author daviestobialex
 */
public class CryptographyUtils {

    public static final String ED25519_ALGO = "Ed25519";

    /**
     * Loads an Ed25519 KeyPair from the given file paths.
     *
     * @param privateKeyPath path to the private key file (PKCS#8 encoded)
     * @param publicKeyPath path to the public key file (X.509 encoded)
     * @return reconstructed KeyPair
     * @throws java.io.IOException
     * @throws java.security.NoSuchAlgorithmException
     * @throws java.security.spec.InvalidKeySpecException
     */
    public static KeyPair loadKeyPair(Path privateKeyPath, Path publicKeyPath)
            throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        byte[] privateBytes = Files.readAllBytes(privateKeyPath);
        byte[] publicBytes = Files.readAllBytes(publicKeyPath);

        KeyFactory keyFactory = KeyFactory.getInstance(ED25519_ALGO);

        // PrivateKey is stored in PKCS#8 format
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privateBytes);
        PrivateKey privateKey = keyFactory.generatePrivate(privSpec);

        // PublicKey is stored in X.509 format
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(publicBytes);
        PublicKey publicKey = keyFactory.generatePublic(pubSpec);

        return new KeyPair(publicKey, privateKey);
    }

    public static PublicKey bytesToPublicKey(byte[] pubKeyBytes, String algorithm) throws Exception {
        // Algorithm examples: "Ed25519", "EC", "RSA"
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(pubKeyBytes);
        KeyFactory keyFactory = KeyFactory.getInstance(algorithm, "BC");
        return keyFactory.generatePublic(keySpec);
    }

    /**
     * Signs a serialized message with the provided private key using Ed25519.
     *
     * @param toSign the serialized message bytes
     * @param privateKey the private key to sign with
     * @return a raw signature byte array
     * @throws NoSuchAlgorithmException if Ed25519 is unavailable
     * @throws NoSuchProviderException if Bouncy Castle provider is not
     * available
     * @throws InvalidKeyException if the key is not valid for signing
     * @throws SignatureException if the signing operation fails
     */
    public static byte[] sign(byte[] toSign, PrivateKey privateKey) throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {

        // Add the Bouncy Castle provider
        Security.addProvider(new BouncyCastleProvider());
        Signature sig = Signature.getInstance(ED25519_ALGO, "BC");
        sig.initSign(privateKey);
        sig.update(toSign);
        return sig.sign();
    }

    /**
     * Calculate RIPEMD160(SHA256(input)). This is used in Address calculations.
     *
     * @param input bytes to hash
     * @return RIPEMD160(SHA256(input))
     */
    public static byte[] sha256hash160(byte[] input) {
        byte[] sha256 = Sha256Hash.hash(input);
        return digestRipeMd160(sha256);
    }

    /**
     * Calculate RIPEMD160(input).
     *
     * @param input bytes to hash
     * @return RIPEMD160(input)
     */
    public static byte[] digestRipeMd160(byte[] input) {
        RIPEMD160Digest digest = new RIPEMD160Digest();
        digest.update(input, 0, input.length);
        byte[] ripmemdHash = new byte[20];
        digest.doFinal(ripmemdHash, 0);
        return ripmemdHash;
    }
}
