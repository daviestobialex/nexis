/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.tests;

import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.junit.jupiter.api.Assertions.*;
import org.nexis.base.BondPolicy;
import static org.nexis.base.Coin.COIN;
import org.nexis.core.MonetaryPolicy;

/**
 * Comprehensive test suite for BondCalculator. Tests cover edge cases, attack
 * vectors, and security vulnerabilities.
 */
class BondPolicyTest {

    private static final BigDecimal BASE = COIN.toNxs();// 1 coin as the base
    private static final BigDecimal NETWORK_SCALING_FACTOR = new BigDecimal("500");
    private static final BigDecimal MAX_INFLATION = new BigDecimal("10"); // 10x
    private static final BigDecimal MAX_BOND_PERCENTAGE = new BigDecimal("0.5"); // 50%

    @Test
    public void normalScenario() {
        BondPolicy calc = new BondPolicy(BASE, NETWORK_SCALING_FACTOR, MAX_INFLATION, MAX_BOND_PERCENTAGE, false);

        BigDecimal approverStake = new BigDecimal("100"); // 100 NXI
        int networkSize = 200;
        BigDecimal currentSupply = new BigDecimal("120000000");
        BigDecimal initialSupply = new BigDecimal(MonetaryPolicy.getStartCoinsAsCoin().value);

        BondPolicy.BondResult r = calc.calculateBond(approverStake, networkSize, currentSupply, initialSupply);
        assertTrue(r.allowed);
        assertNotNull(r.bondToLock);
        assertTrue(r.bondToLock.compareTo(BigDecimal.ZERO) >= 0);
        // bond shouldn't exceed 50% of stake (50)
        assertTrue(r.bondToLock.compareTo(new BigDecimal("50")) <= 0);
    }

    @Test
    public void normalScenarioVariableNetworkScalingFactorSize() {

        int maxNetworkNodes = 1000000;
        BigDecimal testFactorSize = NETWORK_SCALING_FACTOR.multiply(BigDecimal.TWO);
        int networkSize = 200;
        while (testFactorSize.compareTo(BigDecimal.valueOf(maxNetworkNodes)) <= 0) {
            BondPolicy calc = new BondPolicy(BASE, testFactorSize, MAX_INFLATION, MAX_BOND_PERCENTAGE, false);

            BigDecimal approverStake = new BigDecimal("100"); // 100 NXI
            networkSize *= 3;// scaling network size progressively
            BigDecimal currentSupply = new BigDecimal("120000000");
            BigDecimal initialSupply = new BigDecimal(MonetaryPolicy.getStartCoinsAsCoin().value);

            BondPolicy.BondResult r = calc.calculateBond(approverStake, networkSize, currentSupply, initialSupply);
            assertTrue(r.allowed);
            assertNotNull(r.bondToLock);
            assertTrue(r.bondToLock.compareTo(BigDecimal.ZERO) >= 0);
            // bond shouldn't exceed 50% of stake (50)
            assertTrue(r.bondToLock.compareTo(new BigDecimal("50")) <= 0);
            testFactorSize = testFactorSize.multiply(BigDecimal.TWO);
        }
    }

    @Test
    public void bondNeverExceedsMaxPercentageUnderExtremeNetworkGrowth() {

        BigDecimal approverStake = new BigDecimal("100"); // 100 NXI
        BigDecimal initialSupply = new BigDecimal(MonetaryPolicy.getStartCoinsAsCoin().value);
        BigDecimal currentSupply = initialSupply; // no inflation in this test

        int networkSize = 200;
        BigDecimal factor = NETWORK_SCALING_FACTOR;

        for (int i = 0; i < 15; i++) { // simulate exponential network growth, but 15 iterations is the most before int exceeds its size
            BondPolicy policy = new BondPolicy(
                    BASE,
                    factor,
                    MAX_INFLATION,
                    MAX_BOND_PERCENTAGE,
                    false
            );

            BondPolicy.BondResult result = policy.calculateBond(
                    approverStake,
                    networkSize,
                    currentSupply,
                    initialSupply
            );

            assertNotNull(result.bondToLock);

            // Bond must be >= 0
            assertTrue(result.bondToLock.compareTo(BigDecimal.ZERO) >= 0);

            // Must never exceed 50% of stake
            BigDecimal maxAllowedBond = approverStake.multiply(MAX_BOND_PERCENTAGE);
            assertTrue(result.bondToLock.compareTo(maxAllowedBond) <= 0);

            // Exponentially scale network
            networkSize *= 3;
            factor = factor.multiply(BigDecimal.TWO);
        }
    }

    @Test
    public void inflationCannotCauseHyperBondRequirement() {

        BigDecimal approverStake = new BigDecimal("100");
        BigDecimal initialSupply = new BigDecimal("1000000");
        BigDecimal currentSupply = initialSupply.multiply(new BigDecimal("1000"));
        // 1000x inflation attack

        BondPolicy policy = new BondPolicy(
                BASE,
                NETWORK_SCALING_FACTOR,
                MAX_INFLATION, // this should guard against abuse
                MAX_BOND_PERCENTAGE,
                false
        );

        BondPolicy.BondResult result = policy.calculateBond(
                approverStake,
                5000,
                currentSupply,
                initialSupply
        );

        // Should not exceed MAX_BOND_PERCENTAGE of stake
        assertTrue(result.bondToLock.compareTo(approverStake.multiply(MAX_BOND_PERCENTAGE)) <= 0);

        // Inflation dampening should never exceed MAX_INFLATION
        assertTrue(result.dampedInflation.compareTo(MAX_INFLATION) <= 0);
    }

    @Test
    public void whenBondExceedsStake_itIsAutoReduced() {

        BigDecimal approverStake = new BigDecimal("1"); // VERY low stake
        BigDecimal initialSupply = new BigDecimal("1000000");
        BigDecimal currentSupply = initialSupply;

        BondPolicy policy = new BondPolicy(
                BASE,
                NETWORK_SCALING_FACTOR.multiply(new BigDecimal("1000")), // VERY high factor
                MAX_INFLATION,
                MAX_BOND_PERCENTAGE,
                false
        );

        BondPolicy.BondResult result = policy.calculateBond(
                approverStake,
                50000,
                currentSupply,
                initialSupply
        );

        // Should always be <= approverStake
        assertTrue(result.bondToLock.compareTo(approverStake) <= 0);

        // And >= 0
        assertTrue(result.bondToLock.compareTo(BigDecimal.ZERO) >= 0);
    }

    /**
     * Simulates a catastrophic, unrealistic hyperinflation attack where the
     * token supply is inflated to astronomically high values.
     *
     * The BondPolicy must: - Apply sqrt dampening to reduce the inflation
     * impact. - Enforce MAX_BOND_PERCENTAGE so the approver never locks >50% of
     * stake. - Mark the result as `capped = true`, indicating the raw bond
     * exceeded permitted limits and was force-limited for safety.
     *
     * Economically: This ensures the Nexis network is resistant to
     * inflation-based denial-of-service attacks where adversaries try to force
     * honest nodes to lock impossibly large bonds.
     */
    @Test
    public void superHyperInflationAttackIsDamped() {
        BondPolicy calc = new BondPolicy(BASE, NETWORK_SCALING_FACTOR, MAX_INFLATION, MAX_BOND_PERCENTAGE, false);

        BigDecimal approverStake = new BigDecimal("100"); // 100 NXI
        int networkSize = 200;
        // current supply massively inflated
        BigDecimal currentSupply = new BigDecimal("1000000000000000000000000"); // 1000x
        BigDecimal initialSupply = new BigDecimal(MonetaryPolicy.getStartCoinsAsCoin().value);

        BondPolicy.BondResult r = calc.calculateBond(approverStake, networkSize, currentSupply, initialSupply);

        // nominalBond may be big, but final bond is capped to 50% stake
        assertTrue(r.bondToLock.compareTo(new BigDecimal("50")) <= 0);
        assertTrue(r.capped); // capped because inflation was large
    }

    /**
     * Simulates a severe but still mathematically reasonable inflation
     * scenario, where the token supply grows by large multiples (e.g., 1000x)
     * due to organic network expansion or moderate manipulation.
     *
     * The BondPolicy must: - Apply sqrt dampening, preventing the bond from
     * exploding exponentially. - Keep required bond <= 50% of approver stake
     * due to MAX_BOND_PERCENTAGE. - Not mark the result as capped, because the
     * calculated bond was naturally within allowed limits (the cap was not
     * invoked).
     *
     * Economically: This ensures that normal or aggressive growth in token
     * supply does not penalize node operators or destabilize governance.
     * Inflation-related growth increases bond requirements smoothly and
     * predictably.
     */
    @Test
    public void hyperInflationAttackIsDamped() {
        BondPolicy calc = new BondPolicy(BASE, NETWORK_SCALING_FACTOR, MAX_INFLATION, MAX_BOND_PERCENTAGE, false);

        BigDecimal approverStake = new BigDecimal("100"); // 100 NXI
        int networkSize = 200;
        // current supply massively inflated
        BigDecimal currentSupply = new BigDecimal("100000000000"); // 1000x
        BigDecimal initialSupply = new BigDecimal(MonetaryPolicy.getStartCoinsAsCoin().value);

        BondPolicy.BondResult r = calc.calculateBond(approverStake, networkSize, currentSupply, initialSupply);

        // nominalBond may be big, but final bond is capped to 50% stake
        assertTrue(r.bondToLock.compareTo(new BigDecimal("50")) <= 0);
        assertFalse(r.capped); // capped because inflation was large
    }

    @Test
    public void impossibleBondRejectedWhenPartialNotAllowed() {
        BondPolicy calc = new BondPolicy(BASE, NETWORK_SCALING_FACTOR, MAX_INFLATION, MAX_BOND_PERCENTAGE, false);

        BigDecimal approverStake = new BigDecimal("1"); // very small stake
        int networkSize = 1000000; // huge network -> huge variableBond
        BigDecimal currentSupply = new BigDecimal("100000000");
        BigDecimal initialSupply = new BigDecimal(MonetaryPolicy.getStartCoinsAsCoin().value);

        BondPolicy.BondResult r = calc.calculateBond(approverStake, networkSize, currentSupply, initialSupply);

        // not allowed because required > maxBondPercent * stake and partial not allowed
        assertFalse(r.allowed);
    }

    @Test
    public void allowPartialBondWhenConfigured() {
        BondPolicy calc = new BondPolicy(BASE, NETWORK_SCALING_FACTOR, MAX_INFLATION, MAX_BOND_PERCENTAGE, true);

        BigDecimal approverStake = new BigDecimal("1");
        int networkSize = 1000000;
        BigDecimal currentSupply = new BigDecimal("100000000");
        BigDecimal initialSupply = new BigDecimal(MonetaryPolicy.getStartCoinsAsCoin().value);

        BondPolicy.BondResult r = calc.calculateBond(approverStake, networkSize, currentSupply, initialSupply);
        assertTrue(r.allowed);     // allowed because partial bonds permitted
        assertTrue(r.capped);      // but was capped
        assertTrue(r.bondToLock.compareTo(BigDecimal.ZERO) >= 0);
    }
}
