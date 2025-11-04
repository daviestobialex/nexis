/*
 * Copyright 2014 Kosta Korenkov
 * Copyright 2019 Andreas Schildbach
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
package org.nexis.signers;

import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumSet;
import java.util.logging.Level;
import org.nexis.base.Coin;
import org.nexis.base.Identity;
import org.nexis.core.Transaction;
import org.nexis.core.TransactionInput;
import org.nexis.core.TransactionOutput;
import org.nexis.core.TransactionWitness;
import org.nexis.script.Script;
import org.nexis.script.Script.VerifyFlag;
import org.nexis.script.ScriptBuilder;
import org.nexis.script.ScriptException;
import org.nexis.script.ScriptPattern;
import org.nexis.wallet.RedeemData;

/**
 * <p>
 * {@link TransactionSigner} implementation for signing inputs using keys from
 * provided {@link KeyBag}.</p>
 * <p>
 * This signer doesn't create input scripts for tx inputs. Instead it expects
 * inputs to contain scripts with empty sigs and replaces one of the empty sigs
 * with calculated signature.
 * </p>
 * <p>
 * This signer is always implicitly added into every wallet and it is the first
 * signer to be executed during tx completion. As the first signer to create a
 * signature, it stores derivation path of the signing key in a given
 * {@link TransactionSigner.ProposedTransaction} object that will be also passed
 * then to the next signer in chain. This allows other signers to use correct
 * signing key for P2SH inputs, because all the keys involved in a single P2SH
 * address have the same derivation path.</p>
 * <p>
 * This signer always uses {@link Transaction.SigHash#ALL} signing mode.</p>
 */
public class LocalTransactionSigner implements TransactionSigner {

    private static final Logger log = LoggerFactory.getLogger(LocalTransactionSigner.class);

    /**
     * Verify flags that are safe to use when testing if an input is already
     * signed.
     */
    private static final EnumSet<Script.VerifyFlag> MINIMUM_VERIFY_FLAGS = EnumSet.of(VerifyFlag.P2SH,
            VerifyFlag.NULLDUMMY);

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean signInputs(ProposedTransaction propTx, Identity identity) {
        Transaction tx = propTx.partialTx;
        int numInputs = tx.getInputs().size();
        for (int i = 0; i < numInputs; i++) {
            TransactionInput txIn = tx.getInput(i);
            final TransactionOutput connectedOutput = txIn.getConnectedOutput();
            if (connectedOutput == null) {
                log.warn("Missing connected output, assuming input {} is already signed.", i);
                continue;
            }
            Script scriptPubKey = connectedOutput.getScriptPubKey();

            try {
                // We assume if its already signed, its hopefully got a SIGHASH type that will not invalidate when
                // we sign missing pieces (to check this would require either assuming any signatures are signing
                // standard output types or a way to get processed signatures out of script execution)
                txIn.getScriptSig().correctlySpends(tx, i, txIn.getWitness(), connectedOutput.getValue(),
                        connectedOutput.getScriptPubKey(), MINIMUM_VERIFY_FLAGS);
                log.warn("Input {} already correctly spends output, assuming SIGHASH type used will be safe and skipping signing.", i);
                continue;
            } catch (ScriptException e) {
                // Expected.
            } catch (NoSuchAlgorithmException | NoSuchProviderException | SignatureException | InvalidKeySpecException | InvalidKeyException ex) {
                java.util.logging.Logger.getLogger(LocalTransactionSigner.class.getName()).log(Level.SEVERE, null, ex);
            }

            RedeemData redeemData = txIn.getConnectedRedeemData(identity);

            // For P2SH inputs we need to share derivation path of the signing key with other signers, so that they
            // use correct key to calculate their signatures.
            // Married keys all have the same derivation path, so we can safely just take first one here.
            PublicKey pubKey = redeemData.key;

            propTx.keyPaths.put(scriptPubKey, pubKey);

            // script here would be either a standard CHECKSIG program for P2PKH or P2PK inputs or
            // a CHECKMULTISIG program for P2SH inputs
            byte[] script = redeemData.redeemScript.program();

            if (ScriptPattern.isP2WSH(scriptPubKey)) {

                Script inputScript = txIn.getScriptSig();
                Script scriptCode = ScriptBuilder.createP2WSHOutputScript(inputScript);
                Coin value = txIn.getValue();
                byte[] calculateWitnessSignature = tx.calculateWitnessSignature(i, identity.getKeyPair().getPrivate(), scriptCode, value,
                        Transaction.SigHash.ALL, false);
                txIn.setScriptSig(ScriptBuilder.createEmpty());
                txIn.setWitness(TransactionWitness.redeemP2WPKH(calculateWitnessSignature, pubKey));
            } else if (ScriptPattern.isP2WPKH(scriptPubKey)) {
                Script scriptCode = ScriptBuilder.createP2WPKHOutputScript(identity.getNodeId().getId());
                Coin value = txIn.getValue();
                byte[] calculateWitnessSignature = tx.calculateWitnessSignature(i, identity.getKeyPair().getPrivate(), scriptCode, value,
                        Transaction.SigHash.ALL, false);
                txIn.setScriptSig(ScriptBuilder.createEmpty());
                txIn.setWitness(TransactionWitness.redeemP2WPKH(calculateWitnessSignature, pubKey));
            } else {
                throw new IllegalStateException(script.toString());
            }

        }
        return true;
    }

}
