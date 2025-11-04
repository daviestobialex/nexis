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

import org.nexis.utilities.Sha256Hash;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import org.nexis.core.Block;

/**
 * {@code NetworkConfiguration} encapsulates the essential parameters required
 * to describe and interact with a particular instantiation of a Nexus-like
 * blockchain network.
 *
 * <p>
 * This abstraction defines the "network constants" needed by peer-to-peer
 * components, block validation logic, and higher-level application code to
 * correctly interoperate with a given chain (e.g., mainnet vs. testnet vs.
 * regtest). Concrete implementations for specific networks are provided in the
 * {@code org.nexis.networks} package.
 * </p>
 *
 * <h3>Core Responsibilities</h3>
 * <ul>
 * <li><b>Identify the network:</b> via a {@link NexusNetwork} reference, which
 * provides a stable ID and URI scheme.</li>
 * <li><b>Define wire protocol parameters:</b> such as the TCP port,
 * {@code packetMagic} (network message prefix), and block interval.</li>
 * <li><b>Bootstrap peers:</b> using DNS seeds and hardcoded address seeds.</li>
 * <li><b>Validate checkpoints:</b> through a block height → hash mapping,
 * enabling fast synchronization and integrity checks.</li>
 * <li><b>Defines Economic Policy:</b> Dynamically computes the economic values
 * of the network and controls economic unit production.</li>
 * </ul>
 *
 * <h3>Design Notes</h3>
 * <ul>
 * <li>This is an <b>abstract class</b> rather than an interface to allow shared
 * logic (e.g., checkpoint validation) while enforcing controlled construction
 * of network instances.</li>
 * <li>Fields are <b>protected</b> to be set by subclasses, but exposed publicly
 * through <b>immutable getters</b>. This balances configurability with API
 * stability.</li>
 * <li>{@code equals()} and {@code hashCode()} are based solely on the
 * {@link NexusNetwork#id()}, ensuring uniqueness is tied to the canonical
 * network identity rather than configuration details.</li>
 * </ul>
 *
 * <h3>Example Usage</h3>
 * <pre>{@code
 * public final class NexusMainnet extends NetworkConfiguration {
 *     public NexusMainnet() {
 *         super(NexusNetwork.MAINNET, 0xD9B4BEF9); // packetMagic
 *         this.port = 8333;
 *         this.interval = 600; // seconds per block
 *         this.addressHeader = 0;
 *         this.dnsSeeds = new String[] {"seed1.nexus.org", "seed2.nexus.org"};
 *         this.checkpoints.put(100000, Sha256Hash.wrap("..."));
 *     }
 * }
 * }</pre>
 *
 * @author daviestobialex
 */
public abstract class NetworkConfiguration {

    /**
     * The logical network this configuration belongs to (mainnet, testnet,
     * etc.).
     */
    protected final NexusNetwork network;

    /**
     * The depth of blocks required for a coinbase transaction to be spendable.
     */
    protected int spendableCoinbaseDepth;

    /**
     * Default peer-to-peer TCP port used by this network.
     */
    protected int port;

    /**
     * Expected block interval (in seconds).
     */
    protected int interval;

    /**
     * Magic number that prefixes every P2P message on this network.
     * <p>
     * Used both to disambiguate messages across networks and to help peers
     * resynchronize when stream state is lost.
     */
    protected int packetMagic;

    /**
     * Version/address header prefix used when encoding addresses.
     */
    protected int addressHeader;

    /**
     * DNS seed hostnames for peer discovery.
     */
    protected String[] dnsSeeds;

    /**
     * Hardcoded integer IP seeds for peer bootstrapping.
     */
    protected int[] addrSeeds;

    /**
     * <p>
     * Genesis block for this chain.</p>
     *
     * <p>
     * The first block in every chain is a well known constant shared between
     * all Bitcoin implementations. For a block to be valid, it must be
     * eventually possible to work backwards to the genesis block by following
     * the prevBlockHash pointers in the block headers.</p>
     *
     * <p>
     * The genesis blocks for both test and main networks contain the timestamp
     * of when they were created, and a message in the coinbase transaction. It
     * says, <i>"The Times 03/Jan/2009 Chancellor on brink of second bailout for
     * banks"</i>.</p>
     *
     * @return genesis block
     */
    public abstract Block getGenesisBlock();

    /**
     * Block height → block hash mapping for checkpoints.
     * <p>
     * Used to prevent deep reorganizations and accelerate initial block
     * download.
     */
    protected Map<Integer, Sha256Hash> checkpoints = new HashMap<>();

    /**
     * Creates a new {@code NetworkConfiguration} for a given network.
     *
     * @param network the logical network identity
     * @param messagePrefix the packet magic / network message prefix
     */
    protected NetworkConfiguration(NexusNetwork network, int messagePrefix) {
        this.network = network;
        this.packetMagic = messagePrefix;
    }

    public NexusNetwork getNetwork() {
        return network;
    }

    public int getPort() {
        return port;
    }

    public int getInterval() {
        return interval;
    }

    public int getPacketMagic() {
        return packetMagic;
    }

    public int getAddressHeader() {
        return addressHeader;
    }

    public String[] getDnsSeeds() {
        return dnsSeeds;
    }

    public int[] getAddrSeeds() {
        return addrSeeds;
    }

    public Map<Integer, Sha256Hash> getCheckpoints() {
        return checkpoints;
    }

    /**
     * Validates a block hash at the given height against known checkpoints.
     *
     * @param height the block height being checked
     * @param hash the block hash at that height
     * @return {@code true} if either no checkpoint is defined at this height,
     * or if the provided hash matches the expected checkpoint hash
     */
    public boolean passesCheckpoint(int height, Sha256Hash hash) {
        Sha256Hash checkpointHash = checkpoints.get(height);
        return checkpointHash == null || checkpointHash.equals(hash);
    }

    /**
     * Determines if the given block height is a known checkpoint.
     *
     * @param height block height
     * @return {@code true} if this height has a checkpoint entry
     */
    public boolean isCheckpoint(int height) {
        return checkpoints.containsKey(height);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof NetworkConfiguration)) {
            return false;
        }
        NetworkConfiguration that = (NetworkConfiguration) o;
        return network.id().equals(that.network.id());
    }

    @Override
    public int hashCode() {
        return Objects.hash(network.id());
    }

    public int getSpendableCoinbaseDepth() {
        return spendableCoinbaseDepth;
    }
}
