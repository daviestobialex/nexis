/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.base;

import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyPair;

/**
 *
 * @author daviestobialex
 */
public interface NodeIdentityProvider {

    /**
     * Load an existing identity, or generate and persist a new one if none
     * exists.
     *
     * @return
     */
    NodeIdentity loadOrCreateIdentity();

}
