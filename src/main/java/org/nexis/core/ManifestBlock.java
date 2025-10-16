/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import com.google.protobuf.ByteString;
import java.time.Instant;
import java.util.List;
import org.nexis.utilities.Sha256Hash;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class ManifestBlock extends Block {

    protected ManifestBlock(
            long version,
            Sha256Hash prevBlockHash,
            Sha256Hash merkleRoot,
            Sha256Hash hash,
            Instant time,
            long nonce,
            List<Transaction> transactions) {
        super(version, prevBlockHash, merkleRoot, hash, time, nonce, transactions);
    }

    public static Block read(NexusProtocol.ManifestBlock block) {
        ByteString blockHash = block.getHeader().getBlockHash();
        ByteString prevHash = block.getHeader().getPrevHash();
        ByteString merkleRoot = block.getHeader().getMerkleRoot();
        Long timeStamp = block.getTimestamp();
        Long nonce = block.getNonce();
        long version = block.getVersion();
        return new ManifestBlock(
                version,
                Sha256Hash.of(prevHash.toByteArray()),
                Sha256Hash.of(merkleRoot.toByteArray()),
                Sha256Hash.of(blockHash.toByteArray()),
                Instant.ofEpochSecond(timeStamp),
                nonce,
                null);
    }
}
