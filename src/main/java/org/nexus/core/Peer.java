/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.core;

import org.nexus.base.Network;
import org.nexus.base.PublicNodeProperties;

/**
 *
 * @author daviestobialex
 */
public class Peer implements PublicNodeProperties, Network {

    private final String networkId;

    public Peer(String networkId) {
        this.networkId = networkId;
    }

    @Override
    public String id() {
        return networkId;
    }

    @Override
    public String uriScheme() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

    @Override
    public byte[] getId() {
        return null;
    }

    @Override
    public byte[] getPublicKey() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

}
