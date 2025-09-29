/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.tests;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.nexis.base.Manifest;
import org.nexis.utilities.ByteUtils;

/**
 *
 * @author daviestobialex
 */
public class CompressionTests {

    private final static Logger LOGGER = Logger.getLogger(CompressionTests.class.getName());

    @Test
    public void manifest_compressionTest() throws FileNotFoundException, IOException {

        Manifest manifest = Manifest.resolve("manifest.json");
        String category = manifest.getCategory();

        String raw = manifest.getRaw();

        int rawLength = raw.getBytes().length;

        byte[] compress = ByteUtils.compress(raw.getBytes());

        int compressedLength = compress.length;
        
        LOGGER.log(Level.INFO, "category {0}", category);
        LOGGER.log(Level.INFO, "rawLength {0}", rawLength);
        LOGGER.log(Level.INFO, "compressedLength {0}", compressedLength);
        
        Assertions.assertEquals("payments", category);
        Assertions.assertTrue(compressedLength < rawLength);

    }
}
