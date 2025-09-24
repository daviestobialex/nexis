/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.base;

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
