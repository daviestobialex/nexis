package org.nexis.tests;

import java.math.BigInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import org.nexis.base.Sha256Hash;
import org.nexis.core.Block;
import org.nexis.core.StoredBlock;
import org.nexis.exceptions.BlockStoreException;
import org.nexis.store.MemoryBlockStore;

/**
 * Unit tests for StoredBlock and MemoryBlockStore.
 * 
 * @author daviestobialex
 */
public class BlockStoreTest {

    private MemoryBlockStore blockStore;
    private Block genesisBlock;
    private StoredBlock genesisStored;

    @BeforeEach
    public void setUp() {
        // Create a simple genesis block for testing
        genesisBlock = Block.createGenesis(java.time.Instant.now(), 0L);
        genesisStored = new StoredBlock(genesisBlock, BigInteger.ONE, 0);
        blockStore = new MemoryBlockStore(genesisStored);
    }

    @Test
    public void testStoredBlockCreation() {
        assertEquals(0, genesisStored.getHeight());
        assertEquals(BigInteger.ONE, genesisStored.getChainWork());
        assertEquals(genesisBlock.getHash(), genesisStored.getHash());
    }

    @Test
    public void testMemoryBlockStoreGetChainHead() throws BlockStoreException {
        StoredBlock head = blockStore.getChainHead();
        assertNotNull(head);
        assertEquals(genesisStored.getHash(), head.getHash());
    }

    @Test
    public void testMemoryBlockStorePut() throws BlockStoreException {
        // Create a new block
        Block block2 = new Block(1L, genesisBlock.getHash(), genesisBlock.getMerkleRoot(),
                java.time.Instant.now(), 1L, null);
        StoredBlock stored2 = new StoredBlock(block2, BigInteger.valueOf(2), 1);

        blockStore.put(stored2);
        assertEquals(2, blockStore.getBlockCount());

        // Retrieve the block
        StoredBlock retrieved = blockStore.get(block2.getHash());
        assertEquals(stored2.getHash(), retrieved.getHash());
        assertEquals(1, retrieved.getHeight());
    }

    @Test
    public void testMemoryBlockStoreSetChainHead() throws BlockStoreException {
        // Create a new block
        Block block2 = new Block(1L, genesisBlock.getHash(), genesisBlock.getMerkleRoot(),
                java.time.Instant.now(), 1L, null);
        StoredBlock stored2 = new StoredBlock(block2, BigInteger.valueOf(2), 1);

        blockStore.put(stored2);
        blockStore.setChainHead(stored2);

        StoredBlock head = blockStore.getChainHead();
        assertEquals(stored2.getHash(), head.getHash());
        assertEquals(1, head.getHeight());
    }

    @Test
    public void testStoredBlockEquality() {
        StoredBlock copy = new StoredBlock(genesisBlock, BigInteger.ONE, 0);
        assertEquals(genesisStored, copy);
        assertEquals(genesisStored.hashCode(), copy.hashCode());
    }

    @Test
    public void testMemoryBlockStoreGetNonExistent() {
        Sha256Hash nonExistent = Sha256Hash.of(new byte[32]);
        assertThrows(BlockStoreException.class, () -> blockStore.get(nonExistent));
    }

    @Test
    public void testMemoryBlockStoreClear() throws BlockStoreException {
        blockStore.clear();
        assertEquals(0, blockStore.getBlockCount());
        assertThrows(BlockStoreException.class, () -> blockStore.getChainHead());
    }
}
