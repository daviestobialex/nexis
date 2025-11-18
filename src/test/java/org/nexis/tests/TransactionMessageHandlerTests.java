/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.tests;

import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import java.net.InetSocketAddress;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.nexis.core.Context;
import org.nexis.core.Transaction;
import org.nexis.core.TransactionConfidence;
import org.nexis.core.TransactionOutput;
import org.nexis.core.TxConfidenceTable;
import org.nexis.messages.handlers.TransactionMessageHandler;
import org.nexus.base.proto.NexusProtocol;
import java.security.Security;
import java.util.logging.Logger;
import static org.nexis.core.Block.genesisTxInputScriptBytes;
import static org.nexis.core.Block.genesisTxScriptPubKeyBytes;
import org.nexis.core.MonetaryPolicy;

/**
 * Unit tests for TransactionMessageHandler.
 *
 * Covers transaction validation pipeline: - Item 0: Duplicate checking
 * (confidence table) - Item 1: Bond validation - Item 2: Governance output
 * validation - Item 3: Signature validation - Item 4: Mempool (confidence
 * table) integration
 *
 * @author daviestobialex
 */
public class TransactionMessageHandlerTests {

    
    private final static Logger log = Logger.getLogger(TransactionMessageHandlerTests.class.getName());
    private TransactionMessageHandler handler;
    private ChannelHandlerContext ctx;
    private Channel channel;
    private Context context;
    private TxConfidenceTable confidenceTable;

    @BeforeEach
    void setUp() throws Exception {
        // Setup crypto and context
        Security.addProvider(new BouncyCastleProvider());

        // Create a real context for testing
        context = new Context();
        Context.propagate(context);

        // Mock Netty channel
        channel = mock(Channel.class);
        ctx = mock(ChannelHandlerContext.class);
        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 9004));

        // Create handler
        handler = new TransactionMessageHandler();

        // Get the confidence table from context
        confidenceTable = context.getConfidenceTable();
    }

    /**
     * Test that canHandle() returns true for transaction messages.
     */
    @Test
    void testCanHandleTransactionMessage() {
        NexusProtocol.NexusMessage msg = NexusProtocol.NexusMessage.newBuilder()
                .setTransaction(NexusProtocol.Transaction.newBuilder().build())
                .build();

        assertTrue(handler.canHandle(msg));
    }

    /**
     * Test that canHandle() returns false for non-transaction messages.
     */
    @Test
    void testCanHandleNonTransactionMessage() {
        NexusProtocol.NexusMessage msg = NexusProtocol.NexusMessage.newBuilder()
                .setPeersDiscovery(NexusProtocol.GetPeers.newBuilder().build())
                .build();

        assertFalse(handler.canHandle(msg));
    }

    /**
     * Test Item 0: Handler skips processing if transaction is already in
     * confidence table.
     */
    @Test
    void testItem0_SkipsDuplicateTransaction() {
        // Create a simple transaction
        Transaction tx = createGenesisTransaction();
        NexusProtocol.Transaction protoTx = tx.toProto();
        NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                .setMessage(NexusProtocol.NexusMessage.newBuilder().setTransaction(protoTx))
                .build();

        // Add transaction to confidence table as PENDING (already known)
        confidenceTable.getOrCreate(tx.getTxId())
                .setConfidenceType(TransactionConfidence.ConfidenceType.PENDING);

        // Handle the envelope
        handler.handle(envelop, ctx);

        // Verify ctx.close() was not called (transaction was skipped peacefully)
        verify(ctx, never()).close();

        // Verify transaction is still PENDING (not re-processed)
        assertEquals(TransactionConfidence.ConfidenceType.PENDING,
                confidenceTable.getOrCreate(tx.getTxId()).getConfidenceType());
    }

    /**
     * Test Item 4: Handler adds new transaction to confidence table as PENDING.
     */
    @Test
    void testItem4_AddsTransactionToMempool() {
        // Create a simple transaction
        Transaction tx = createGenesisTransaction();
        NexusProtocol.Transaction protoTx = tx.toProto();
        NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                .setMessage(NexusProtocol.NexusMessage.newBuilder().setTransaction(protoTx))
                .build();

        // Verify transaction is initially unknown
        assertEquals(TransactionConfidence.ConfidenceType.UNKNOWN,
                confidenceTable.getOrCreate(tx.getTxId()).getConfidenceType());

        // Handle the envelope
        handler.handle(envelop, ctx);

        // Verify transaction is now PENDING
        TransactionConfidence confidence = confidenceTable.getOrCreate(tx.getTxId());
        assertEquals(TransactionConfidence.ConfidenceType.PENDING, confidence.getConfidenceType());

        // Verify peer was recorded
        assertTrue(confidence.numBroadcastPeers() > 0, "Transaction should have at least one broadcast peer");
    }

    /**
     * Test full happy path: valid transaction passes all validations.
     */
    @Test
    void testHappyPath_ValidTransactionPassesAllValidations() {
        // Create a valid transaction
        Transaction tx = createGenesisTransaction();
        NexusProtocol.Transaction protoTx = tx.toProto();
        NexusProtocol.NexusEnvelop envelop = NexusProtocol.NexusEnvelop.newBuilder()
                .setMessage(NexusProtocol.NexusMessage.newBuilder().setTransaction(protoTx))
                .build();

        // Handle the envelope
        handler.handle(envelop, ctx);

        // Verify:
        // 1. Context was not closed (no validation error)
        verify(ctx, never()).close();

        // 2. Transaction is marked as PENDING
        TransactionConfidence confidence = confidenceTable.getOrCreate(tx.getTxId());
        assertEquals(TransactionConfidence.ConfidenceType.PENDING, confidence.getConfidenceType());

        // 3. Peer was recorded
        assertTrue(confidence.numBroadcastPeers() > 0);
    }

    /**
     * Test sendMessage() method (stub for future use).
     */
    @Test
    void testSendMessage_IsStubForFutureUse() {
        // Should not throw any exceptions
        handler.setTransaction(createGenesisTransaction());
        assertDoesNotThrow(() -> handler.sendMessage());
    }

    // ====== Helper methods ======
    /**
     * Creates a simple valid transaction with a genesis input and regular
     * output.
     */
    private Transaction createGenesisTransaction() {
        Transaction tx = Transaction.genesis(genesisTxInputScriptBytes);
        tx.addOutput(new TransactionOutput(tx, MonetaryPolicy.getStartCoinsAsCoin(), genesisTxScriptPubKeyBytes));
        
        return tx;
    }
}
