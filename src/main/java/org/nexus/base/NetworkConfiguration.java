/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexus.base;

import org.nexus.internal.Sha256Hash;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 *
 * <p>
 * NetworkConfiguration contains the data needed for working with an instantiation
 * of a Nexus network chain.
 * </p>
 *
 * <p>
 * This is an abstract class, concrete instantiations can be found in the
 * networks package.
 * </p>
 *
 * @author daviestobialex
 */
public abstract class NetworkConfiguration {

    protected final NexusNetwork network;

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
    protected int port;
    protected int interval;
    protected int packetMagic;  // Indicates message origin network and is used to seek to the next message when stream state is unknown.
    protected int addressHeader;
    protected String[] dnsSeeds;
    protected int[] addrSeeds;
    protected Map<Integer, Sha256Hash> checkpoints = new HashMap<>();

    protected NetworkConfiguration(NexusNetwork network, int messagePrefix) {
        this.network = network;
        this.packetMagic = messagePrefix;
    }

    /**
     * Validate the hash for a given block height against checkpoints
     *
     * @param height block height
     * @param hash hash for {@code height}
     * @return true if the block height is either not a checkpoint, or is a
     * checkpoint and the hash matches
     */
    public boolean passesCheckpoint(int height, Sha256Hash hash) {
        Sha256Hash checkpointHash = checkpoints.get(height);
        return checkpointHash == null || checkpointHash.equals(hash);
    }

    /**
     * Is height a checkpoint
     *
     * @param height block height
     * @return true if the given height has a recorded checkpoint
     */
    public boolean isCheckpoint(int height) {
        Sha256Hash checkpointHash = checkpoints.get(height);
        return checkpointHash != null;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        return network.id().equals(((NetworkConfiguration) o).network.id());
    }

    @Override
    public int hashCode() {
        return Objects.hash(network.id());
    }

}
