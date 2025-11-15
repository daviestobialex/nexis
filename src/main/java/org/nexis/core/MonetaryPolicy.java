/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Calendar;
import java.util.Objects;
import org.nexis.base.Coin;

/**
 * Immutable-ish holder of monetary policy parameters and helper checks. Use
 * smallest units (e.g. "microunits") to avoid floating point issues.
 *
 * @author daviestobialex
 */
public final class MonetaryPolicy {

    /**
     * The initial available number of coins generated
     */
    private static final long START_COINS = 1_000_000;

    private static final long START_EPOCH_LIMIT = 1_000;// this can grow as well
    // smallest atomic unit (like satoshi). All amounts are BigInteger in these units.
    private final BigInteger totalSupplyCap;       // e.g., 1_000_000 will grow but starts at a relatively large value
    /**
     * This represents the total number of coins currently in existence — i.e.,
     * how many tokens have been minted minus how many have been burned.
     */
    private BigInteger currentSupply;              // tracked in chain state, updated when mint/burn happen

    /**
     * max to mint per epoch (safety guard) will be adjusted according to
     * network growth
     *
     */
    private final BigInteger epochMintLimit;
    /**
     * private BigInteger currentSupply; // tracked in chain state, updated when
     * mint/burn happen private final BigInteger epochMintLimit; // max to mint
     * per epoch (safety guard) private final int maxTxPercentOfStake; // e.g.,
     * 10 = 10% of bonded stake allowed per tx
     */
    private final int maxTxPercentOfStake;         // e.g., 10 = 10% of bonded stake allowed per tx
    private final Instant policyEffective;        // since when

    public MonetaryPolicy() {
        this.totalSupplyCap = BigInteger.valueOf(START_COINS);
        this.currentSupply = BigInteger.ZERO;// zero temporarily until it reads data from the last block hash sent to it by the peers
        this.epochMintLimit = BigInteger.valueOf(START_EPOCH_LIMIT);
        this.maxTxPercentOfStake = 50;// 50% for default
        this.policyEffective = Calendar.getInstance().toInstant();
    }

    /**
     * Returns the configured genesis/start amount as a {@link Coin} instance.
     *
     * <p>This is a convenience accessor so callers (eg. Wallet) can use the
     * monetary policy configured genesis coins in the correct units. The value
     * here is multiplied by {@link Coin#COIN} to convert from whole-coins into
     * atomic satoshi units.</p>
     *
     * @return Coin representing the start coins configured by policy
     */
    public static Coin getStartCoinsAsCoin() {
        // START_COINS is expressed in whole coins (policy units). Convert to satoshis.
        long satoshis = Math.multiplyExact(START_COINS, Coin.COIN.getValue());
        return Coin.valueOf(satoshis);
    }

}
