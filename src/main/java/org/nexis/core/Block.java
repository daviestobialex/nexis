/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.nexis.utilities.Sha256Hash;
import org.nexus.base.proto.NexusProtocol;

/**
 *
 * @author daviestobialex
 */
public class Block {

    /**
     * How many bytes are required to represent a block header
     */
    public static final int HEADER_SIZE = 80;

    static final Duration ALLOWED_TIME_DRIFT = Duration.ofHours(2); // Same value as Bitcoin Core.

    /**
     * A constant shared by the entire network: how large in bytes a block is
     * allowed to be. One day we may have to upgrade everyone to change this, so
     * Bitcoin can continue to grow. For now it exists as an anti-DoS measure to
     * avoid somebody creating a titanically huge but valid block and forcing
     * everyone to download/store it forever.
     */
    public static final int MAX_BLOCK_SIZE = 1_000_000;

    /**
     * Value to use if the block height is unknown
     */
    public static final int BLOCK_HEIGHT_UNKNOWN = -1;
    /**
     * Height of the first block
     */
    public static final int BLOCK_HEIGHT_GENESIS = 0;

    public static final long BLOCK_VERSION_GENESIS = 1;

    // Fields defined as part of the protocol format.
    private final long version;
    private Sha256Hash prevBlockHash;
    private Sha256Hash merkleRoot, witnessRoot;
    private Instant time;
    private long nonce;// used in minning but this may not be required

    /**
     * Stores the hash of the block. If null, getHash() will recalculate it.
     */
    private Sha256Hash hash;

    List<Transaction> transactions;

    /**
     * Construct a block initialized with all the given fields.
     *
     * @param version This should usually be set to 1 or 2, depending on if the
     * height is in the base input.
     * @param prevBlockHash Reference to previous block in the chain or
     * {@link Sha256Hash#ZERO_HASH} if genesis.
     * @param merkleRoot The root of the merkle tree formed by the transactions.
     * @param hash
     * @param time time when the block was mined.
     * @param nonce Arbitrary number to make the block hash lower than the
     * target.
     * @param transactions List of transactions including the coinbase, or
     * {@code null} for header-only blocks
     */
    protected Block(
            long version,
            Sha256Hash prevBlockHash,
            Sha256Hash merkleRoot,
            Sha256Hash hash,
            Instant time,
            long nonce, List<Transaction> transactions) {
        super();
        this.version = version;
        this.prevBlockHash = prevBlockHash;
        this.merkleRoot = merkleRoot;
        this.hash = hash;
        this.time = time;
        this.nonce = nonce;
        this.transactions = transactions != null
                ? new ArrayList<>(transactions)
                : null;
    }

    /**
     * Deserialize this message from a given payload.
     *
     * @param payload payload to deserialize from
     * @return read message
     */
    public static Block read(NexusProtocol.Block payload) {

        long nonce = payload.getNonce();
        long version = payload.getVersion();
        Sha256Hash prevHash = Sha256Hash.of(payload.getHeader().getPrevHash().toByteArray());
        Sha256Hash merkelRoot = Sha256Hash.of(payload.getHeader().getMerkleRoot().toByteArray());
        Sha256Hash hash = Sha256Hash.of(payload.getHeader().getBlockHash().toByteArray());
        long timestamp = payload.getTimestamp();
        List<Transaction> transactions = payload.getTransactionsList()
                .stream().map(proto -> Transaction.read(proto))
                .collect(Collectors.toList());
         
        return new Block(version,
                prevHash,
                merkelRoot, hash,
                Instant.ofEpochSecond(timestamp),
                nonce,
                transactions);
    }
}
