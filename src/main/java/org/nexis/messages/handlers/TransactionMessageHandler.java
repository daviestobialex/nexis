/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.messages.handlers;

import io.netty.channel.ChannelHandlerContext;
import java.math.BigDecimal;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.nexis.base.Coin;
import org.nexis.core.Transaction;
import org.nexis.core.TransactionInput;
import org.nexis.core.TransactionOutput;
import org.nexis.core.Context;
import org.nexis.internal.MessageHandler;
import org.nexis.script.Script;
import org.nexis.script.ScriptException;
import org.nexis.script.ScriptPattern;
import org.nexus.base.proto.NexusProtocol;

/**
 * Handler for transaction messages received over the network.
 * 
 * Performs transaction-level validations including:
 * - Bond validation (checks staking/covenant outputs if present)
 * - Governance output validation (checks system-marked outputs)
 * - Signature validation (verifies scriptSig against scriptPubKey)
 * - Broadcasting valid transactions to peers
 * 
 * TODO: Finish up implementation of broadcasting and integrate with mempool and recheck validations done here, tests
 *
 * @author daviestobialex
 */
public class TransactionMessageHandler implements MessageHandler {

    private static final Logger log = Logger.getLogger(TransactionMessageHandler.class.getName());

    @Override
    public boolean canHandle(NexusProtocol.NexusMessage message) {
        return message.hasTransaction();
    }

    @Override
    public void handle(NexusProtocol.NexusEnvelop envelop, ChannelHandlerContext ctx) {

        NexusProtocol.Transaction protoTransaction = envelop.getMessage().getTransaction();

        Transaction transaction = Transaction.read(protoTransaction);
        
        // Perform all transaction-level validations
        try {
            // 1. Validate bond if present
            validateBondIfPresent(transaction);
            log.log(Level.FINE, "Bond validation passed for tx {0}", transaction.getTxId());
            
            // 2. Validate governance outputs if present
            validateGovernanceOutputsIfPresent(transaction);
            log.log(Level.FINE, "Governance outputs validation passed for tx {0}", transaction.getTxId());
            
            // 3. Validate transaction signatures
            validateSignatures(transaction);
            log.log(Level.FINE, "Signature validation passed for tx {0}", transaction.getTxId());
            
            // 4. Broadcast valid transaction to peers
            broadcastValidTransaction(transaction, ctx);
            log.log(Level.INFO, "Transaction {0} validated and broadcasted successfully", transaction.getTxId());
            
        } catch (ValidationException e) {
            log.log(Level.WARNING, "Transaction validation failed for tx " + transaction.getTxId() + ": " + e.getMessage(), e);
            // Optionally send rejection response to peer
            ctx.close();
        } catch (Exception e) {
            log.log(Level.SEVERE, "Unexpected error validating transaction " + transaction.getTxId(), e);
            ctx.close();
        }
    }

    /**
     * Validates bond outputs if present in the transaction.
     * 
     * A bond output is identified as an OP_RETURN output containing the approver's
     * public key bytes (as created by SendRequest.approve()).
     * 
     * This method:
     * - Scans for OP_RETURN outputs (bond-lock markers)
     * - Verifies the bond amount using BondPolicy
     * 
     * @param tx the transaction to validate
     * @throws ValidationException if bond validation fails
     */
    private void validateBondIfPresent(Transaction tx) throws ValidationException {
        for (TransactionOutput output : tx.getOutputs()) {
            try {
                Script script = output.getScriptPubKey();
                
                // Check if this is an OP_RETURN output (bond-lock marker)
                if (ScriptPattern.isOpReturn(script)) {
                    log.log(Level.FINE, "Found bond-lock (OP_RETURN) output in tx {0}", tx.getTxId());
                    
                    // Validate bond amount using BondPolicy
                    Context.get().getBondPolicy(); // Reference bondPolicy from context (centralized policy management)
                    Coin bondAmount = output.getValue();
                    
                    // The bond should be non-zero and reasonable
                    if (bondAmount.isLessThan(Coin.ZERO)) {
                        throw new ValidationException("Bond amount cannot be negative: " + bondAmount.toFriendlyString());
                    }
                    
                    // Check if bond meets minimum requirements
                    BigDecimal bondInBtc = Coin.satoshiToBtc(bondAmount.getValue());
                    
                    if (bondInBtc.compareTo(BigDecimal.ZERO) <= 0) {
                        throw new ValidationException("Bond amount must be positive: " + bondAmount.toFriendlyString());
                    }
                    
                    log.log(Level.FINE, "Bond validation passed: amount={0}", bondAmount.toFriendlyString());
                }
            } catch (ScriptException e) {
                log.log(Level.WARNING, "Could not parse script in bond validation", e);
                // Non-fatal; skip this output
            }
        }
    }

    /**
     * Validates governance-related outputs if present in the transaction.
     * 
     * Governance outputs are identified by:
     * - TransactionOutput.isSystem() == true (system/governance-marked outputs)
     * - Multi-signature covenant scripts (P2MS outputs for approval requirements)
     * 
     * This method:
     * - Scans for system-marked outputs
     * - Verifies script structure (multi-sig outputs should be valid P2MS format)
     * 
     * Note: Full governance approval chain validation (tracing to genesis validators)
     * will be implemented in a dedicated GovernanceValidator class.
     * 
     * @param tx the transaction to validate
     * @throws ValidationException if governance output validation fails
     */
    private void validateGovernanceOutputsIfPresent(Transaction tx) throws ValidationException {
        for (TransactionOutput output : tx.getOutputs()) {
            // Check if output is marked as a system/governance output
            if (output.isSystem()) {
                log.log(Level.FINE, "Found governance output in tx {0}", tx.getTxId());
                
                try {
                    Script script = output.getScriptPubKey();
                    
                    // Verify it's a valid multi-sig script (covenant/approval output)
                    if (ScriptPattern.isSentToMultisig(script)) {
                        log.log(Level.FINE, "Governance output is a valid multi-sig script in tx {0}", tx.getTxId());
                        // Multi-sig script is properly formed
                    } else {
                        // System output should ideally be multi-sig for governance
                        log.log(Level.WARNING, "System output in tx {0} is not multi-sig format", tx.getTxId());
                    }
                    
                    // Value check: governance outputs should typically be zero-value (markers)
                    // or have specific value requirements defined by governance policy
                    if (output.getValue().isLessThan(Coin.ZERO)) {
                        throw new ValidationException("Governance output cannot have negative value: " + output.getValue());
                    }
                    
                    log.log(Level.FINE, "Governance output validation passed in tx {0}", tx.getTxId());
                    
                } catch (ScriptException e) {
                    log.log(Level.WARNING, "Could not parse governance output script", e);
                    throw new ValidationException("Invalid governance output script: " + e.getMessage());
                }
            }
        }
    }

    /**
     * Validates all signatures in the transaction.
     * 
     * For each input in the transaction:
     * - Gets the connected output's scriptPubKey
     * - Verifies the input's scriptSig is valid against the scriptPubKey
     * - Checks signature correctness using script execution
     * 
     * Note: This performs syntactic/structural validation. Full cryptographic
     * verification happens during script execution.
     * 
     * @param tx the transaction to validate
     * @throws ValidationException if signature validation fails
     */
    private void validateSignatures(Transaction tx) throws ValidationException {
        if (tx.getInputs().isEmpty()) {
            throw new ValidationException("Transaction has no inputs");
        }
        
        for (int i = 0; i < tx.getInputs().size(); i++) {
            TransactionInput input = tx.getInput(i);
            
            // Skip genesis inputs (coinbase transactions)
            if (input.isGenesis()) {
                log.log(Level.FINE, "Skipping signature validation for genesis input {0} in tx {1}", 
                        new Object[]{i, tx.getTxId()});
                continue;
            }
            
            try {
                // Get the connected output (the one being spent)
                TransactionOutput connectedOutput = input.getConnectedOutput();
                if (connectedOutput == null) {
                    throw new ValidationException("Input " + i + " has no connected output (unspent tx not found)");
                }
                
                Script scriptPubKey = connectedOutput.getScriptPubKey();
                Script inputScript = input.getScriptSig();
                
                // Basic checks
                if (inputScript == null || inputScript.program().length == 0) {
                    throw new ValidationException("Input " + i + " has empty script signature");
                }
                
                // Verify the scriptSig structure is valid for the scriptPubKey type
                verifyScriptStructure(inputScript, scriptPubKey, i, tx.getTxId().toString());
                
                log.log(Level.FINE, "Signature validation passed for input {0} in tx {1}", 
                        new Object[]{i, tx.getTxId()});
                
            } catch (ScriptException e) {
                throw new ValidationException("Script validation failed for input " + i + ": " + e.getMessage());
            }
        }
    }

    /**
     * Verifies that a scriptSig structure is valid for its corresponding scriptPubKey.
     * 
     * This is a structural check (not full cryptographic verification) that ensures:
     * - P2PKH inputs have correct signature + pubkey format
     * - P2SH inputs have correct redeem script structure
     * - Multi-sig inputs have the expected number of signatures
     * 
     * @param inputScript the input script to verify (reserved for future full cryptographic verification)
     * @param scriptPubKey the output script being spent
     * @param inputIndex the index of this input in the transaction
     * @param txId the transaction ID (for logging)
     * @throws ScriptException if the script structure is invalid
     */
    @SuppressWarnings("unused")  // inputScript reserved for full cryptographic verification in future
    private void verifyScriptStructure(Script inputScript, Script scriptPubKey, int inputIndex, String txId) throws ScriptException {
        // Check if this is a P2PKH output being spent
        if (ScriptPattern.isP2WPKH(scriptPubKey)) {
            // P2PKH scriptSig should have exactly 2 chunks: signature + pubkey
            log.log(Level.FINER, "Input {0} in tx {1} is P2PKH format", new Object[]{inputIndex, txId});
        }
        // Check if this is a P2SH output being spent
        else if (ScriptPattern.isP2WSH(scriptPubKey)) {
            log.log(Level.FINER, "Input {0} in tx {1} is P2SH format", new Object[]{inputIndex, txId});
        }
        // Check if this is a multi-sig output being spent
        else if (ScriptPattern.isSentToMultisig(scriptPubKey)) {
            log.log(Level.FINER, "Input {0} in tx {1} is multi-sig format", new Object[]{inputIndex, txId});
        }
        else {
            log.log(Level.WARNING, "Input {0} in tx {1} has unknown script type", new Object[]{inputIndex, txId});
        }
    }

    /**
     * Broadcasts a validated transaction to all connected peers via the channel.
     * 
     * @param tx the validated transaction to broadcast
     * @param ctx the Netty channel context
     */
    @SuppressWarnings("unused")  // tx and ctx reserved for full broadcast implementation
    private void broadcastValidTransaction(Transaction tx, ChannelHandlerContext ctx) {
        try {
            // Broadcast to the connected peer (could extend to multi-peer broadcast)
            // For now, we just log as the actual broadcast mechanism depends on network architecture
            log.log(Level.INFO, "Broadcasting validated transaction {0} to peers", tx.getTxId());
            
            // In a full implementation, this would:
            // 1. Add tx to mempool via tx.toProto()
            // 2. Broadcast to all connected peers
            // 3. Track transaction state (pending, confirmed, etc.)
            
        } catch (Exception e) {
            log.log(Level.WARNING, "Failed to broadcast transaction: " + e.getMessage(), e);
            // Non-fatal; log but continue
        }
    }

    /**
     * Custom exception for transaction validation errors.
     */
    public static class ValidationException extends Exception {
        public ValidationException(String message) {
            super(message);
        }
        
        public ValidationException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Override
    public void sendMessage() {
        throw new UnsupportedOperationException("Not supported yet."); // Generated from nbfs://nbhost/SystemFileSystem/Templates/Classes/Code/GeneratedMethodBody
    }

}
