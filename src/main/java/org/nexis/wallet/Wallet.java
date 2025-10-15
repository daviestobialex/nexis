/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.wallet;

import java.nio.ByteBuffer;
import java.util.Arrays;
import org.nexis.base.Base58;
import org.nexis.base.Identity;
import org.nexis.base.Manifest;
import org.nexis.base.NetworkConfiguration;
import org.nexis.utilities.ByteUtils;
import org.nexis.utilities.CryptographyUtils;
import org.nexis.utilities.Sha256Hash;

/**
 *
 *
 * @author daviestobialex
 */
public class Wallet {

    private final Identity identity;
    private final NetworkConfiguration networkParams;
    // The wallet version. This is an int that can be used to track breaking changes in the wallet format.
    // You can also use it to detect wallets that come from the future (ie they contain features you
    // do not know how to deal with).
    private int version;

    public static Wallet of(Identity identity, NetworkConfiguration networkParams) {

        return new Wallet(identity, networkParams);
    }

    private Wallet(Identity identity, NetworkConfiguration networkParams) {
        this.identity = identity;
        this.networkParams = networkParams;
        // check locally for saved wallet file, else create a new one
    }

    /**
     * generates wallet address in base58, so we can get a human readable
     * address, and be able to run initial checksum validation for an address
     * and generate address based on public key <br>
     * pubKeyHash = RIPEMD160(SHA256(pubKey))<br>
     * addressBytes = [versionByte (0x00)] + pubKeyHash <br>
     * checksum = first 4 bytes of SHA256(SHA256(addressBytes)) <br>
     * finalAddress = Base58Encode(addressBytes + checksum)
     *
     * @return
     */
    public String tobase58() {
        byte[] magic = ByteUtils.writInt32BE(networkParams.getPacketMagic());
        byte[] digestRipeMd160 = CryptographyUtils.digestRipeMd160(identity.getNodeId().getId());
        ByteBuffer buffer = ByteBuffer.allocate(magic.length + digestRipeMd160.length);
        buffer.put(magic);
        buffer.put(digestRipeMd160);
        byte[] addressBytes = buffer.array();
        byte[] checksum = Arrays.copyOfRange(Sha256Hash.hashTwice(addressBytes), 0, 4);
        buffer = ByteBuffer.allocate(addressBytes.length + checksum.length);
        buffer.put(addressBytes);
        buffer.put(checksum);
        return Base58.encode(buffer.array());
    }
}
