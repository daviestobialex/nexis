/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS"
 * BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.base;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.util.Arrays;
import org.nexis.utilities.CryptographyUtils;
import org.nexis.utilities.HexFormat;

/**
 *
 * @author daviestobialex
 */
public final class SignedManifest {

    /**
     * The original manifest instance.
     */
    private final Manifest manifest;

    /**
     * Cryptographic signature over the manifest ID (SHA-256 digest). to be used
     * as CID for IPFS
     */
    private final byte[] signature;
    private final Sha256Hash signedHash;// this hash is a signed hash and is used to compare against the gensis hash

    /**
     * Creates a new {@code SignedManifest} by signing the given
     * {@link Manifest} with the private key of the provided node identity.
     *
     * @param manifest the manifest to sign
     * @param node the node identity containing the private key for signing
     * @throws NoSuchAlgorithmException if the signing algorithm is not
     * available
     * @throws NoSuchProviderException if the security provider is not available
     * @throws InvalidKeyException if the provided private key is invalid
     * @throws SignatureException if signing fails
     */
    public SignedManifest(Manifest manifest, Identity node)
            throws NoSuchAlgorithmException, NoSuchProviderException, InvalidKeyException, SignatureException {
        this.manifest = manifest;
        this.signature = CryptographyUtils.sign(manifest.manifestIdBytes(), node.getKeyPair().getPrivate());
        this.signedHash = Sha256Hash.of(signature);
    }

    /**
     * Returns a defensive copy of the raw signature bytes.
     *
     * @return a copy of the signature
     */
    public byte[] getSignature() {
        return Arrays.copyOf(signature, signature.length);
    }

    /**
     * return signature in Hex format
     *
     * @return
     */
    public String getHexSignature() {
        return HexFormat.bytesToHex(signature);
    }

    public Manifest getManifest() {
        return manifest;
    }

    @Override
    public String toString() {
        return "SignedManifest{"
                + "manifestId=" + manifest.manifestId()
                + ", signature=" + getHexSignature()
                + '}';
    }
}
