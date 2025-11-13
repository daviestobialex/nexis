package org.nexis.core;

import java.security.PublicKey;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
}
