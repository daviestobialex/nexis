/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.tests;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SecureRandom;
import java.security.Security;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.nexis.base.Identity;
import org.nexis.base.Manifest;
import org.nexis.core.NodeId;
import org.nexis.base.utils.ByteUtils;
import static org.nexis.utilities.CryptographyUtils.ED25519_ALGO;

/**
 *
 * @author daviestobialex
 */
public class CompressionTests {

    private final static Logger LOGGER = Logger.getLogger(CompressionTests.class.getName());

    private Identity node;

    @Test
    public void manifest_compression_and_decompress_Test() throws FileNotFoundException, IOException, NoSuchAlgorithmException, NoSuchProviderException {

        // generate a keypair
        // Add the Bouncy Castle provider
        Security.addProvider(new BouncyCastleProvider());

        // Use the strongest available SecureRandom instance
        SecureRandom random = SecureRandom.getInstanceStrong();

        // Get an Ed25519 key pair generator
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance(ED25519_ALGO, "BC");
        keyGen.initialize(256, random); // 256 is the standard key size for Ed25519

        // Generate the key pair
        KeyPair keyPair = keyGen.generateKeyPair();

        node = mock(Identity.class);
        when(node.getKeyPair()).thenReturn(keyPair);
        when(node.getNodeId()).thenAnswer(inv -> new NodeId((byte[]) inv.getArgument(0)));

        Manifest manifest = Manifest.resolve("manifest.json", node);
        List<String> category = manifest.getCategories();

        String raw = manifest.getRaw();

        int rawLength = raw.getBytes().length;

        byte[] compress = ByteUtils.compress(raw.getBytes());

        int compressedLength = compress.length;

        LOGGER.log(Level.INFO, "compressed category {0}", category);
        LOGGER.log(Level.INFO, "compressed rawLength {0}", rawLength);
        LOGGER.log(Level.INFO, "compressedLength {0}", compressedLength);

        Assertions.assertEquals("payments", category.get(0));
        Assertions.assertTrue(compressedLength < rawLength);

        byte[] decompress = ByteUtils.decompress(compress);

        LOGGER.log(Level.INFO, "decompressLength {0}", decompress.length);
        Assertions.assertTrue(rawLength == decompress.length);
    }
}
