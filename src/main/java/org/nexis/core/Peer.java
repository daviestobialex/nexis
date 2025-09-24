/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

/**
 *
 * @author daviestobialex
 */
public class Peer implements PeerAddress {

    private final String networkId;
    private byte[] nodeId;
    private byte[] pubKey;

    public Peer(String networkId) {
        this.networkId = networkId;
    }

    public Peer(String networkId, byte[] nodeId) {
        this.networkId = networkId;
        this.nodeId = nodeId;
    }

    public Peer(String networkId, byte[] nodeId, byte[] pubKey) {
        this.networkId = networkId;
        this.nodeId = nodeId;
        this.pubKey = pubKey;
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
        return nodeId;
    }

    @Override
    public byte[] getPublicKey() {
        return pubKey;
    }

}
