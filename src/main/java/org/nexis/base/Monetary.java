/*
 * Copyright 2014 Andreas Schildbach
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

/**
 * Classes implementing this interface represent a monetary value, such as a
 * Bitcoin or fiat amount.
 */
public interface Monetary {

    /**
     * Returns the absolute value of exponent of the value of a "smallest unit"
     * in scientific notation.For Bitcoin, a satoshi is worth 1E-8 so this would
     * be 8.
     *
     * @return
     */
    int smallestUnitExponent();

    /**
     * Returns the number of "smallest units" of this monetary value.For
     * Bitcoin, this would be the number of satoshis.
     *
     * @return
     */
    long getValue();

    /**
     * Returns the sign of this monetary value as an integer.
     * <p>
     * This method indicates whether the amount represented by this
     * {@code Monetary} instance is positive, zero, or negative — similar to the
     * mathematical signum function. It helps identify whether the value
     * represents a credit (positive amount), no value (zero), or a debit
     * (negative amount).
     * </p>
     *
     * <ul>
     * <li>Returns {@code 1} if the value is positive.</li>
     * <li>Returns {@code 0} if the value is zero.</li>
     * <li>Returns {@code -1} if the value is negative.</li>
     * </ul>
     *
     * @return {@code -1}, {@code 0}, or {@code 1} indicating the sign of the
     * monetary value.
     */
    int signum();
}
