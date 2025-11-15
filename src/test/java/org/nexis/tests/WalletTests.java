/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.tests;

import java.security.NoSuchAlgorithmException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.nexis.base.Address;
import org.nexis.base.Coin;
import org.nexis.base.Identity;
import org.nexis.base.IdentityProvider;
import org.nexis.base.NexusNetwork;
import org.nexis.core.Ed25519IdentityProvider;
import org.nexis.core.MemoryBlockUTXOProvider;
import org.nexis.core.StoredBlock;
import org.nexis.networks.NexusNetworkConfiguration;
import org.nexis.store.MemoryBlockStore;
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

        NexusNetworkConfiguration network = NexusNetworkConfiguration.of(NexusNetwork.TESTNET);

        MemoryBlockStore blockStore = new MemoryBlockStore(
                new StoredBlock(network.getGenesisBlock(), java.math.BigInteger.ONE, 0)
        );
        MemoryBlockUTXOProvider memoryBlockUTXOProvider = new MemoryBlockUTXOProvider(blockStore, network.getNetwork());

        // Mock network config    
        Wallet wallet = Wallet.of(identity, network, memoryBlockUTXOProvider);
        Address currentAddress = wallet.currentAddress();
        System.out.println("NODE ID LEN " + identity.getNodeId().getId().length);
        System.out.println("CURRENT ADDRESS " + currentAddress.toString());
        System.out.println("BASE 58 " + wallet.currentAddress().toStringBase58());
        Assertions.assertNotNull(currentAddress, "current address can not be null");
    }

    @Test
    public void genesisBalanceTest() throws NoSuchAlgorithmException {

        IdentityProvider identityProvider = new Ed25519IdentityProvider();
        Identity identity = identityProvider.loadOrCreateIdentity();

        NexusNetworkConfiguration network = NexusNetworkConfiguration.of(NexusNetwork.TESTNET);

        MemoryBlockStore blockStore = new MemoryBlockStore(
                new StoredBlock(network.getGenesisBlock(), java.math.BigInteger.ONE, 0)
        );
        MemoryBlockUTXOProvider memoryBlockUTXOProvider = new MemoryBlockUTXOProvider(blockStore, network.getNetwork());

        // Mock network config    
        Wallet wallet = Wallet.of(identity, network, memoryBlockUTXOProvider);
        Address currentAddress = wallet.currentAddress();
        System.out.println("NODE ID LEN " + identity.getNodeId().getId().length);
        System.out.println("CURRENT ADDRESS " + currentAddress.toString());
        System.out.println("BASE 58 " + wallet.currentAddress().toStringBase58());
        Assertions.assertNotNull(currentAddress, "current address can not be null");

        Coin balance = wallet.getBalance();
        System.out.println("BALANCE COIN " + balance.getValue());
        Assertions.assertTrue(balance.getValue() == 100000000000000L, "default balamnce must be zero");
    }
}
