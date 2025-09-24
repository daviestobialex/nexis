/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import static org.nexis.utilities.CryptographyUtils.ED25519_ALGO;
import static org.nexis.utilities.CryptographyUtils.loadKeyPair;
import org.nexis.base.IdentityProvider;
import org.nexis.base.Identity;

/**
 *
 * @author daviestobialex
 */
public class Ed25519IdentityProvider implements IdentityProvider {

    private final Path storageDir;

    public Ed25519IdentityProvider() {
        this.storageDir = Paths.get("src/main/resources/");// resource directory
    }

    public Ed25519IdentityProvider(Path storageDir) {
        this.storageDir = storageDir;
    }

    @Override
    public Identity loadOrCreateIdentity() {
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
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ED25519_ALGO, "BC");
        keyGen.initialize(256, random); // 256 is the standard key size for Ed25519

        // Generate the key pair
        return keyGen.generateKeyPair();
    }

}
