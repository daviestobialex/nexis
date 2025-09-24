/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.base;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 *
 * @author daviestobialex
 */
public class Manifest {

    private final String raw;

    private Manifest(String raw) {
        this.raw = raw;
    }

    public static final String[] SEARCH_PATHS = {
        "src/main/nexus",
        "src/main/resources/nexus "
    };

    public static Manifest resolve(String filename) throws FileNotFoundException {
        for (String dir : SEARCH_PATHS) {
            Path candidate = Paths.get(dir, filename);
            if (Files.exists(candidate)) {
                return load(candidate);
            }
        }
        throw new FileNotFoundException("File not found in search paths: " + filename);
    }

    public static Manifest load(Path path) {
        try {
            String content = Files.readString(path);
            // TODO: parse & validate schema
            return new Manifest(content);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load manifest at " + path, e);
        }
    }

    @Override
    public String toString() {
        return raw;
    }
}
