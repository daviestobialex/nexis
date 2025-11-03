/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package org.nexis.base;

/**
 * TODO: need to seeif network actually needs to be here based on the design but
 * I want to get the crypto part done first then I decide based on the
 * architecture I am going for
 *
 * @author daviestobialex
 */
public interface PeerAddress extends PublicNodeProperties {

    public String id();
}
