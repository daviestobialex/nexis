package org.nexis.core;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.nexis.base.Identity;
import org.nexis.base.Network;
import org.nexis.base.Sha256Hash;
import org.nexis.script.Script;
import org.nexis.exceptions.BlockStoreException;
import org.nexis.exceptions.UTXOProviderException;

/**
 * A simple UTXO provider that scans a {@link org.nexis.store.BlockStore} (in
 * practice the in-memory test store) starting from the chain head and walks
 * backwards collecting outputs that pay to the provided public keys.
 *
 * This is intentionally minimal and intended for tests or single-node setups
 * (eg. to load genesis outputs into a wallet). It is not intended to replace a
 * full indexer for production use.
 */
public class MemoryBlockUTXOProvider implements UTXOProvider {

    private final org.nexis.store.BlockStore blockStore;
    private final Network network;

    public MemoryBlockUTXOProvider(org.nexis.store.BlockStore blockStore, Network network) {
        this.blockStore = blockStore;
        this.network = network;
    }

    @Override
    public List<UTXO> getOpenTransactionOutputs(List<PublicKey> keys) throws UTXOProviderException {
        try {
            // Precompute node-id style hashes for keys (NodeId.stableNodeId(publicKeyBytes)).
            Set<byte[]> keyIds = new HashSet<>();
            for (PublicKey k : keys) {
                keyIds.add(NodeId.toSegwit(k.getEncoded()));
            }

            List<UTXO> results = new ArrayList<>();

            StoredBlock head = blockStore.getChainHead();
            // Walk backwards until genesis or until there are no more stored blocks.
            while (head != null) {
                Block b = head.getHeader();
                if (b != null && b.transactions != null) {
                    int height = head.getHeight();
                    for (Transaction tx : b.transactions) {
                        Sha256Hash txHash = tx.getTxId();

                        for (int i = 0; i < tx.getOutputs().size(); i++) {
                            TransactionOutput out = tx.getOutputs().get(i);
                            try {
                                Script script = out.getScriptPubKey();
                                if (org.nexis.script.ScriptPattern.isP2WPKH(script)) {

                                    byte[] hash = org.nexis.script.ScriptPattern.extractHashFromP2WH(script);

                                    // Check if this output belongs to any provided key
                                    for (byte[] kid : keyIds) {
                                        if (java.util.Arrays.equals(kid, hash)) {
                                            // Create a UTXO record
                                            results.add(new UTXO(txHash, i, out.getValue(), height, script));
                                            break;
                                        }
                                    }
                                } else if (org.nexis.script.ScriptPattern.isP2WSH(script)) {
                                    System.out.println("IS isP2WSH ");
                                } else if (org.nexis.script.ScriptPattern.isP2WH(script)) {
                                    System.out.println("IS isP2WH ");
                                }
                            } catch (org.nexis.script.ScriptException se) {
                                // Ignore unparsable outputs.
                            }
                        }
                    }
                }
                if (head.getPrevBlockHash().equals(Sha256Hash.ZERO_HASH)) {
                    break; // reached genesis
                }
                try {
                    head = blockStore.get(head.getPrevBlockHash());
                } catch (BlockStoreException bse) {
                    // no previous block known in the store - stop scanning
                    break;
                }
            }
            return results;
        } catch (BlockStoreException e) {
            throw new UTXOProviderException("Block store error", e);
        }
    }

    @Override
    public int getChainHeadHeight() throws UTXOProviderException {
        try {
            StoredBlock head = blockStore.getChainHead();
            return head.getHeight();
        } catch (org.nexis.exceptions.BlockStoreException e) {
            throw new UTXOProviderException("Cannot read chain head", e);
        }
    }

    @Override
    public Network network() {
        return network;
    }

    public TransactionOutput getGovernanceOutput(Identity identity) {
        List<TransactionOutput> outputs = scanBlockchainForGovernanceOutputs()
                .stream()
                .filter(output -> output.isMine(identity))
                .collect(Collectors.toList());
        if (outputs.isEmpty() || outputs.size() > 1) {
            throw new RuntimeException("there must only be one governance output per peer");
        }

        return outputs.get(0);
    }

    /**
     * Internal method to scan the blockchain for governance outputs matching a
     * predicate. Walks from chain head backwards to genesis, collecting
     * matching outputs.
     *
     * @param filter predicate to test each governance output
     * @return list of matching governance outputs
     */
    private List<TransactionOutput> scanBlockchainForGovernanceOutputs() {
        List<TransactionOutput> results = new ArrayList<>();

        try {
            StoredBlock head = blockStore.getChainHead();
            if (head == null) {
                return Collections.emptyList();
            }

            // Walk backwards from chain head to genesis
            StoredBlock current = head;
            while (current != null) {
                Block block = current.getHeader();
                if (block != null && block.transactions != null) {
                    // Iterate through transactions
                    for (Transaction tx : block.transactions) {
                        // Iterate through outputs
                        for (TransactionOutput output : tx.getOutputs()) {
                            // Check if it's a GovernanceOutput
                            if (output.getValue().isZero()) {
                                results.add(output);
                            }
                        }
                    }
                }

                // Move to previous block
                Sha256Hash prevHash = block.getPrevBlockHash();
                if (prevHash.equals(Sha256Hash.ZERO_HASH)) {
                    break; // Reached genesis
                }

                StoredBlock prev = blockStore.get(prevHash);
                if (prev == null) {
                    break; // No more blocks in store
                }
                current = prev;
            }
        } catch (Exception e) {
            // Log and continue; errors shouldn't crash governance lookups
            System.err.println("Error scanning blockchain for governance outputs: " + e.getMessage());
            e.printStackTrace();
        }

        return results;
    }

    /**
     * Traces a transaction output back through the blockchain to its genesis
     * (origin).
     *
     * Since every output has a corresponding input (except genesis outputs
     * which have no input), we can walk backwards through the transaction chain
     * by following each input's outpoint reference. The trace reaches genesis
     * when we encounter an output with no input (identified by hash ==
     * ZERO_HASH).
     *
     * This validates that an output exists on the same network fork by ensuring
     * an unbroken chain back to the genesis block.
     *
     * @param txHash the hash of the transaction containing the output
     * @param outputIndex the index of the output within the transaction
     * @return true if a valid trace to genesis is found; false if the chain is
     * broken or if the transaction/output doesn't exist in the block store
     */
    public boolean isFromGenesis(Sha256Hash txHash, long outputIndex) {
        if (txHash == null || txHash.equals(Sha256Hash.ZERO_HASH)) {
            // A genesis output has no input transaction
            return true;
        }

        // Track visited transactions to detect cycles (prevent infinite loops)
        Set<Sha256Hash> visited = new HashSet<>();
        Sha256Hash currentTxHash = txHash;
        long currentIndex = outputIndex;

        // Walk backwards through the transaction chain
        while (currentTxHash != null && !currentTxHash.equals(Sha256Hash.ZERO_HASH)) {
            // Detect cycles (should not happen in valid blockchain)
            if (visited.contains(currentTxHash)) {
                return false;
            }
            visited.add(currentTxHash);

            // Find the transaction in the blockchain
            Transaction tx = findTransactionInBlockchain(currentTxHash);
            if (tx == null) {
                // Transaction not found in blockchain - broken chain
                return false;
            }

            // Check if this transaction has inputs (non-genesis)
            List<TransactionInput> inputs = tx.getInputs();
            if (inputs == null || inputs.isEmpty()) {
                // Genesis transaction (no inputs) - valid terminus
                return true;
            }

            // Get the input at the specified index
            // The currentIndex should correspond to a valid input index
            if (currentIndex >= inputs.size() || currentIndex < 0) {
                // Invalid index
                return false;
            }

            TransactionInput input = inputs.get((int) currentIndex);
            if (input == null) {
                return false;
            }

            // Get the outpoint that this input references (the previous output it spends)
            TransactionOutPoint outpoint = input.getOutpoint();
            if (outpoint == null || outpoint.hash() == null) {
                return false;
            }

            // Check if this is a genesis input (no previous transaction)
            if (input.isGenesis()) {
                return true;
            }

            // Move to the previous transaction in the chain
            currentTxHash = outpoint.hash();
            currentIndex = outpoint.index();
        }

        // If currentTxHash is ZERO_HASH, we've reached genesis
        return true;
    }

    /**
     * Helper method to find a transaction in the blockchain by its hash. Scans
     * from the chain head backwards to genesis looking for the transaction.
     *
     * @param txHash the hash of the transaction to find
     * @return the Transaction object if found, or null if not found
     */
    private Transaction findTransactionInBlockchain(Sha256Hash txHash) {
        try {
            StoredBlock head = blockStore.getChainHead();
            if (head == null) {
                return null;
            }

            StoredBlock current = head;
            // Scan backwards through blocks until genesis or transaction is found
            for (;;) {
                Block block = current.getHeader();
                if (block != null && block.transactions != null) {
                    for (Transaction tx : block.transactions) {
                        if (tx.getTxId().equals(txHash)) {
                            return tx;
                        }
                    }
                }

                // Move to previous block
                if (block == null || block.getPrevBlockHash().equals(Sha256Hash.ZERO_HASH)) {
                    break; // Reached genesis
                }

                StoredBlock prev = blockStore.get(block.getPrevBlockHash());
                if (prev == null) {
                    break; // No more blocks in store
                }
                current = prev;
            }

            return null;
        } catch (BlockStoreException e) {
            System.err.println("Error finding transaction in blockchain: " + e.getMessage());
            return null;
        }
    }
}
