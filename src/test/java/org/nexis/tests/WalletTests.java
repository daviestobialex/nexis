/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.tests;

import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.nexis.base.Address;
import org.nexis.base.Identity;
import org.nexis.base.IdentityProvider;
import org.nexis.base.NexusNetwork;
import org.nexis.core.Ed25519IdentityProvider;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexis.wallet.Wallet;

/**
 *
 * @author daviestobialex
 */
public class WalletTests {
    
    @Test
    public void currentAddressTest() throws NoSuchAlgorithmException {
        
        IdentityProvider identityProvider = new Ed25519IdentityProvider();
        Identity identity = identityProvider.loadOrCreateIdentity();

        // Mock network config    
        Wallet wallet = Wallet.of(identity, NexusNetworkConfiguration.of(NexusNetwork.TESTNET));
        Address currentAddress = wallet.currentAddress();
        System.out.println("NODE ID LEN " + identity.getNodeId().getId().length);
        System.out.println("CURRENT ADDRESS " + currentAddress.toString());
        System.out.println("BASE 58 " + wallet.currentAddress().toStringBase58());
        Assertions.assertNotNull(currentAddress, "current address can not be null");
    }
}
