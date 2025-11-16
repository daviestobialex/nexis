/*
 * Copyright by the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.nexis.base;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Progressive Bonded Stake (PBS) calculator for governance approvals.
 *
 * Improved formula with safety bounds and attack resistance: bond = baseBond +
 * min(variableBond, capPercentage * approverStake) where variableBond =
 * approverStake * (networkSize / k) * inflationFactor
 *
 * Key improvements: - Prevents bond exceeding approver's total stake (cap at
 * configurable %) - Square root dampening on inflation to prevent exponential
 * growth - Attack resistance against supply manipulation - Comprehensive
 * validation and edge case handling
 *
 * @author daviestobialex
 */
public final class BondPolicy {

    // --- Tunable network parameters ---
    private static final int PRECISION = 18;
    private static final int RESULT_SCALE = 8;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;
    private static final MathContext MC = new MathContext(PRECISION, ROUNDING_MODE);

    private final BigDecimal baseBond;         // minimum bond (NXI)
    private final BigDecimal scalingFactorK;   // > 0
    private final BigDecimal maxInflationFactor; // cap on inflation factor (>= 1)
    private final BigDecimal maxBondPercent;   // fraction in (0,1], e.g., 0.5 = 50%
    private final boolean allowPartialBond;    // if true, allow approving with partial bond

    /**
     * Constructor with default safety parameters.
     *
     * @param baseBond
     * @param scalingFactorK
     */
    public BondPolicy(BigDecimal baseBond, BigDecimal scalingFactorK) {
        this(baseBond, scalingFactorK,
                new BigDecimal("10"), // 75% max bond
                new BigDecimal("0.5"), // 50% max bond
                false); // 10x inflation cap
    }

    /**
     * Constructs a new calculator.
     *
     * @param baseBond minimum bond in NXI (>= 0)
     * @param scalingFactorK scaling parameter k (must be > 0)
     * @param maxInflationFactor cap for inflation factor (>= 1)
     * @param maxBondPercent maximum fraction of approver stake that may be
     * required (0 < maxBondPercent <= 1)
     * @param allowPartialBond whether to allow partial bonding if approver
     * cannot meet the required bond
     */
    public BondPolicy(
            BigDecimal baseBond,
            BigDecimal scalingFactorK,
            BigDecimal maxInflationFactor,
            BigDecimal maxBondPercent,
            boolean allowPartialBond) {

        Objects.requireNonNull(baseBond, "baseBond");
        Objects.requireNonNull(scalingFactorK, "scalingFactorK");
        Objects.requireNonNull(maxInflationFactor, "maxInflationFactor");
        Objects.requireNonNull(maxBondPercent, "maxBondPercent");

        if (baseBond.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("baseBond must be >= 0");
        }
        if (scalingFactorK.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("scalingFactorK must be > 0");
        }
        if (maxInflationFactor.compareTo(BigDecimal.ONE) < 0) {
            throw new IllegalArgumentException("maxInflationFactor must be >= 1");
        }
        if (maxBondPercent.compareTo(BigDecimal.ZERO) <= 0 || maxBondPercent.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("maxBondPercent must be in (0, 1]");
        }

        this.baseBond = baseBond;
        this.scalingFactorK = scalingFactorK;
        this.maxInflationFactor = maxInflationFactor;
        this.maxBondPercent = maxBondPercent;
        this.allowPartialBond = allowPartialBond;
    }

    /**
     * Calculate required bond. This returns a BondResult which indicates if the
     * computed bond fits the approver's stake or not.
     *
     * @param approverStake amount of NXI approver has staked (must be > 0)
     * @param networkSize current number of governance nodes (>= 0)
     * @param currentSupply current NXI total supply (must be > 0)
     * @param initialSupply genesis supply (must be > 0)
     * @return BondResult containing final bond and flags
     */
    public BondResult calculateBond(
            BigDecimal approverStake,
            int networkSize,
            BigDecimal currentSupply,
            BigDecimal initialSupply) {

        Objects.requireNonNull(approverStake, "approverStake");
        Objects.requireNonNull(currentSupply, "currentSupply");
        Objects.requireNonNull(initialSupply, "initialSupply");

        if (approverStake.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("approverStake must be > 0");
        }
        if (initialSupply.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("initialSupply must be > 0");
        }
        if (currentSupply.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("currentSupply must be > 0");
        }
        if (networkSize < 0) {
            throw new IllegalArgumentException("networkSize must be >= 0");
        }

        // 1) dampedInflation = currentSupply / initialSupply
        BigDecimal inflationFactor = currentSupply.divide(initialSupply, MC);

        // 2) cap inflation factor and apply sqrt dampening
        BigDecimal cappedInflation = inflationFactor.min(maxInflationFactor);
        BigDecimal dampedInflation = sqrt(cappedInflation, MC);

        // 3) network growth factor = networkSize / k
        BigDecimal networkGrowthFactor = BigDecimal.valueOf(networkSize).divide(scalingFactorK, MC);

        // 4) variableBond = approverStake * networkGrowthFactor * dampedInflation
        BigDecimal variableBond = approverStake.multiply(networkGrowthFactor, MC).multiply(dampedInflation, MC);
        if (variableBond.signum() < 0) {
            variableBond = BigDecimal.ZERO;
        }

        // 5) totalBond = baseBond + variableBond
        BigDecimal totalBond = baseBond.add(variableBond, MC);

        // 6) cap to allowed percentage of approverStake
        BigDecimal maxAllowedBond = approverStake.multiply(maxBondPercent, MC);
        BigDecimal finalBond = totalBond.min(maxAllowedBond);

        // scale to RESULT_SCALE for on-chain use
        finalBond = finalBond.setScale(RESULT_SCALE, ROUNDING_MODE);

        boolean fitsStake = totalBond.compareTo(maxAllowedBond) <= 0;
        boolean wasCapped = !fitsStake;

        // If not fits AND partial bonds not allowed -> return result indicating not affordable
        if (!fitsStake && !allowPartialBond) {
            return new BondResult(dampedInflation, finalBond, totalBond.setScale(RESULT_SCALE, ROUNDING_MODE), wasCapped, false);
        }

        // allowPartialBond==true: we return capped finalBond but mark as partial
        return new BondResult(dampedInflation, finalBond, totalBond.setScale(RESULT_SCALE, ROUNDING_MODE), wasCapped, true);
    }

    public BigDecimal getBondToLock(
            BigDecimal approverStake,
            int networkSize,
            BigDecimal currentSupply,
            BigDecimal initialSupply) {
        return calculateBond(approverStake, networkSize, currentSupply, initialSupply).bondToLock;
    }

    /**
     * Result payload for calculateBond.
     */
    public static final class BondResult {

        public final BigDecimal dampedInflation;
        public final BigDecimal bondToLock; // what should be locked (<= approver stake * maxBondPercent)
        public final BigDecimal nominalBond; // computed (uncapped) bond
        public final boolean capped; // whether we capped to maxBondPercent
        public final boolean allowed; // if false -> approver cannot meet required policy (and partial not allowed)

        private BondResult(BigDecimal dampedInflation, BigDecimal bondToLock, BigDecimal nominalBond, boolean capped, boolean allowed) {
            this.dampedInflation = dampedInflation;
            this.bondToLock = bondToLock;
            this.nominalBond = nominalBond;
            this.capped = capped;
            this.allowed = allowed;
        }
    }

    // Newton-Raphson sqrt for BigDecimal
    private static BigDecimal sqrt(BigDecimal x, MathContext mc) {
        if (x.compareTo(BigDecimal.ZERO) < 0) {
            throw new ArithmeticException("sqrt of negative");
        }
        if (x.equals(BigDecimal.ZERO)) {
            return BigDecimal.ZERO;
        }
        BigDecimal guess = BigDecimal.valueOf(Math.sqrt(x.doubleValue()));
        BigDecimal TWO = BigDecimal.valueOf(2);
        for (int i = 0; i < 50; i++) {
            guess = guess.add(x.divide(guess, mc)).divide(TWO, mc);
        }
        return guess;
    }

    /**
     * Exception thrown when approver doesn't have sufficient stake.
     */
    public static class InsufficientStakeException extends RuntimeException {

        public InsufficientStakeException(String message) {
            super(message);
        }
    }
}
