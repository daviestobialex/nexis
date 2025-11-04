/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import org.nexis.base.PeerAddress;

/**
 *
 * @author daviestobialex
 */
public class Peer implements PeerAddress {

    private final String networkId;
    private final byte[] nodeId;
    private byte[] pubKey;

    //TODO: need a way to determin the peer mode  created based on the constructor
    /**
     * a partial peer, which does not carry a node id network bytes become node
     * id bytes
     *
     * @param networkId
     */
    public Peer(String networkId) {
        this.networkId = networkId;
        this.nodeId = networkId.getBytes();
    }

    /**
     * semi partial peer
     *
     * @param networkId
     * @param nodeId
     */
    public Peer(String networkId, byte[] nodeId) {
        this.networkId = networkId;
        this.nodeId = nodeId;
    }

    /**
     * full peer
     *
     * @param networkId
     * @param nodeId
     * @param pubKey
     */
    public Peer(String networkId, byte[] nodeId, byte[] pubKey) {
        this.networkId = networkId;
        this.nodeId = nodeId;
        this.pubKey = pubKey;
    }

    @Override
    public byte[] getId() {
        return nodeId;
    }

    @Override
    public byte[] getPublicKey() {
        return pubKey;
    }

    @Override
    public String id() {
        return networkId;
    }

}
