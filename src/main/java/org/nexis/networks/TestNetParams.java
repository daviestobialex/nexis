/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.networks;

import java.time.Instant;
import org.nexis.base.NexusNetwork;
import org.nexis.utilities.Sha256Hash;

/**
 *
 * @author daviestobialex
 */
public class TestNetParams extends NexusNetworkConfiguration {

    private static final Instant GENESIS_TIME = Instant.ofEpochSecond(1759856131);
    private static final long GENESIS_NONCE = 2083236893;
    private static final Sha256Hash GENESIS_HASH = Sha256Hash.wrap("000000000019d6689c085ae165831e934ff763ae46a2a6c172b3f1b60a8ce26f");

    public TestNetParams() {
        super(NexusNetwork.TESTNET, 0xf9beb4d9);

        port = 9004;
        addressHeader = 0;

        // This contains (at a minimum) the blocks which are not BIP30 compliant. BIP30 changed how duplicate
        // transactions are handled. Duplicated transactions could occur in the case where a coinbase had the same
        // extraNonce and the same outputs but appeared at different heights, and greatly complicated re-org handling.
        // Having these here simplifies block connection logic considerably.
        checkpoints.put(91722, Sha256Hash.wrap("00000000000271a2dc26e7667f8419f2e15416dc6955e5a6c6cdf3f2574dd08e"));
        checkpoints.put(91812, Sha256Hash.wrap("00000000000af0aed4792b1acee3d966af36cf5def14935db8de83d6f9306f2f"));
        checkpoints.put(91842, Sha256Hash.wrap("00000000000a4d0a398161ffc163c503763b1f4360639393e0e4c8e300e0caec"));
        checkpoints.put(91880, Sha256Hash.wrap("00000000000743f190a18c5577a3c2d2a1f610ae9601ac046a38084ccb7cd721"));
        checkpoints.put(200000, Sha256Hash.wrap("000000000000034a7dedef4a161fa058a2d67a173a90155f3a2fe6fc132e0ebf"));

        dnsSeeds = new String[]{
            "18.170.223.254"
        };

        // These are in big-endian format, which is what the SeedPeers code expects.
        // Updated Apr. 11th 2019
        addrSeeds = new int[]{
            // 127.0.0.1
            0x117c7e18, 0x12641955, 0x1870652e, 0x1dfec3b9, 0x4a330834, 0x5b53382d, 0x77abaca3, 0x09e3d36c,
            0xa0a4e1d4, 0xa275d9c7, 0xa280bc4b, 0xa50d1b76, 0x0a5f84cb, 0xa86cd5bd, 0xb3f427ba, 0xc6fc4cd0,
            0xc73c19b9, 0xd905d85f, 0xd919f9ad, 0xda3fc312, 0xdc4ca5b9, 0xe38ef05b, 0xedce8e57, 0xf68ad23e,
            0xfb3b9c59};
    }

    private static TestNetParams instance;

    public static synchronized TestNetParams get() {
        if (instance == null) {
            instance = new TestNetParams();
        }
        return instance;
    }

}
