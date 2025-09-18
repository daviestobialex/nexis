/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.core;

import org.nexus.core.SimpleNodeIdentity;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.Security;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.nexus.base.NodeIdentity;
import org.nexus.base.NodeIdentityProvider;

/**
 *
 * @author daviestobialex
 */
public class Ed25519IdentityProvider implements NodeIdentityProvider {

    private final Path storageDir;

    public Ed25519IdentityProvider() {
        this.storageDir = Paths.get("src/main/resources/");// resource directory
    }

    public Ed25519IdentityProvider(Path storageDir) {
        this.storageDir = storageDir;
    }

    @Override
    public NodeIdentity loadOrCreateIdentity() {
        try {
            // Check if key files exist
            Path privateKeyFile = storageDir.resolve("node.key");
            Path publicKeyFile = storageDir.resolve("node.pub");

            if (Files.exists(privateKeyFile) && Files.exists(publicKeyFile)) {
                return new SimpleNodeIdentity(loadKeyPair(privateKeyFile, publicKeyFile));
            }

            // Otherwise, generate a new one
            KeyPair keyPair = generateKeys();
            writeKeysTofile(keyPair, privateKeyFile, publicKeyFile);
            return new SimpleNodeIdentity(keyPair);

        } catch (Exception e) {
            throw new RuntimeException("Failed to load or create NodeIdentity", e);
        }
    }

    private void writeKeysTofile(KeyPair keyPair, Path privFile, Path pubFile) throws IOException {
        Files.createDirectories(privFile.getParent());
        Files.write(privFile, keyPair.getPrivate().getEncoded());
        Files.write(pubFile, keyPair.getPublic().getEncoded());
    }

    /**
     * generates a public and private keys by using Elliptic Curve Cryptography
     * (ECC) with the Ed25519 algorithm.
     *
     * @return
     * @throws NoSuchAlgorithmException
     * @throws NoSuchProviderException
     */
    private KeyPair generateKeys() throws NoSuchAlgorithmException, NoSuchProviderException {
        // Add the Bouncy Castle provider
        Security.addProvider(new BouncyCastleProvider());

        // Use the strongest available SecureRandom instance
        SecureRandom random = SecureRandom.getInstanceStrong();

        // Get an Ed25519 key pair generator
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519", "BC");
        keyGen.initialize(256, random); // 256 is the standard key size for Ed25519

        // Generate the key pair
        return keyGen.generateKeyPair();
    }

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

        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");

        // PrivateKey is stored in PKCS#8 format
        PKCS8EncodedKeySpec privSpec = new PKCS8EncodedKeySpec(privateBytes);
        PrivateKey privateKey = keyFactory.generatePrivate(privSpec);

        // PublicKey is stored in X.509 format
        X509EncodedKeySpec pubSpec = new X509EncodedKeySpec(publicBytes);
        PublicKey publicKey = keyFactory.generatePublic(pubSpec);

        return new KeyPair(publicKey, privateKey);
    }
}
