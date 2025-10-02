/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.networks;

import java.time.Instant;
import org.nexis.base.Network;
import org.nexis.base.NetworkConfiguration;
import org.nexis.base.NexusNetwork;
import org.nexis.utilities.Sha256Hash;

/**
 *
 * @author daviestobialex
 */
public class NexusNetworkConfiguration extends NetworkConfiguration {

    private static final Sha256Hash GENESIS_HASH = Sha256Hash.wrap("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f");
    private static final Instant GENESIS_TIME = Instant.ofEpochSecond(1231006505);
    private static final long GENESIS_NONCE = 2083236893;

    public NexusNetworkConfiguration(NexusNetwork network, int packetMagic) {
        super(network, packetMagic);
    }

    /**
     * Return network parameters for a network id
     *
     * @param id the network id
     * @return the network parameters for the given string ID or NULL if not
     * recognized
     */
    public static NexusNetworkConfiguration fromID(String id) {
        if (id.equals(NexusNetwork.ID_TESTNET)) {
            return TestNetParams.get();
        } else {
            return null;
        }
    }

    /**
     * Return network parameters for a {@link BitcoinNetwork} enum
     *
     * @param network the network
     * @return the network parameters for the given string ID
     * @throws IllegalArgumentException if unknown network
     */
    public static NexusNetworkConfiguration of(NexusNetwork network) {
        switch (network) {
            case LOCALHOSTTEST -> {
                return TestNetParams.get();
            }
            default ->
                throw new IllegalArgumentException("Unknown network");
        }
    }
}
