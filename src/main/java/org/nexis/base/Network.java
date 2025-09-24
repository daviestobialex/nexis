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

/**
 * A {@code Network} represents a generic, addressable Nexus-like network
 * chain. Implementations of this interface provide the minimum metadata
 * needed to identify and inter-operate with a given network.
 * <p>
 * The contract defined here is intentionally minimal, but critical:
 * <ul>
 * <li>{@link #id()} provides a globally unique, dot-separated identifier for
 * the network, much like a Java package name (e.g.
 * {@code "org.nexus.mainnet"}). This allows software to distinguish between
 * production, test, staging, or private network instances.</li>
 *
 * <li>{@link #uriScheme()} returns the URI scheme used to encode addresses or
 * resources belonging to this network. For example, Bitcoin uses
 * {@code "bitcoin:"} URIs, while a Nexus network might use {@code "nexus:"}</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <p>
 * Implementations of this interface typically serve as singletons or enum-like
 * constants for known networks. For example:
 * <pre>{@code
 * public final class BitcoinMainnet implements Network {
 *     @Override
 *     public String id() {
 *         return "org.bitcoin.mainnet";
 *     }
 *
 *     @Override
 *     public String uriScheme() {
 *         return "bitcoin";
 *     }
 * }
 * }</pre> These implementations can then be injected or discovered at runtime
 * to configure network-specific behaviors like address parsing, transaction
 * relay, or peer-to-peer bootstrapping.
 *
 * <h3>Design Notes</h3>
 * <ul>
 * <li>This interface is deliberately simple to allow for extension. More
 * advanced metadata (network magic bytes, genesis block hash, supported
 * protocols) can be layered on in richer types that compose this base.</li>
 * <li>Interfaces rather than abstract classes were chosen here to maximize
 * flexibility and avoid inheritance lock-in.</li>
 * </ul>
 *
 * @author daviestobialex
 */
public interface Network {

    /**
     * The dot-seperated string id for this network. For example
     * {@code "org.bitcoin.production"}
     *
     * @return String ID for network
     */
    String id();

    /**
     * The URI scheme for this network. See {@link BitcoinNetwork#uriScheme()}.
     *
     * @return The URI scheme for this network
     */
    String uriScheme();

}
