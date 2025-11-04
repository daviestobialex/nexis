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

import org.nexis.base.Identity;
import org.nexis.core.TransactionInput;
import org.nexis.core.TransactionWitness;
import org.nexis.script.Script;
import org.nexis.script.ScriptPattern;
import org.nexis.wallet.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This transaction signer resolves missing signatures in accordance with the
 * given {@link Wallet.MissingSigsMode}. If missingSigsMode is USE_OP_ZERO this
 * signer does nothing assuming missing signatures are already presented in
 * scriptSigs as OP_0. In MissingSigsMode.THROW mode this signer will throw an
 * exception. It would be MissingSignatureException for P2SH or
 * MissingPrivateKeyException for other transaction types.
 */
public class MissingSigResolutionSigner implements TransactionSigner {

    private static final Logger log = LoggerFactory.getLogger(MissingSigResolutionSigner.class);

    private final Wallet.MissingSigsMode missingSigsMode;

    public MissingSigResolutionSigner() {
        this(Wallet.MissingSigsMode.USE_DUMMY_SIG);
    }

    public MissingSigResolutionSigner(Wallet.MissingSigsMode missingSigsMode) {
        this.missingSigsMode = missingSigsMode;
    }

    @Override
    public boolean isReady() {
        return true;
    }

    @Override
    public boolean signInputs(ProposedTransaction propTx, Identity identity) {
        if (missingSigsMode == Wallet.MissingSigsMode.USE_OP_ZERO) {
            return true;
        }

        int numInputs = propTx.partialTx.getInputs().size();
//        byte[] dummySig = new byte[]{};;
        for (int i = 0; i < numInputs; i++) {
            TransactionInput txIn = propTx.partialTx.getInput(i);
            if (txIn.getConnectedOutput() == null) {
                log.warn("Missing connected output, assuming input {} is already signed.", i);
                continue;
            }

            Script scriptPubKey = txIn.getConnectedOutput().getScriptPubKey();
//            Script inputScript = txIn.getScriptSig();
            if (ScriptPattern.isP2WPKH(scriptPubKey) || ScriptPattern.isP2WSH(scriptPubKey)) {
                if (txIn.getWitness() == null || txIn.getWitness().equals(TransactionWitness.EMPTY)
                        || txIn.getWitness().getPush(0).length == 0) {
                    if (missingSigsMode == Wallet.MissingSigsMode.THROW) {
                        throw new RuntimeException("missing private key");
                    } else if (missingSigsMode == Wallet.MissingSigsMode.USE_DUMMY_SIG) {

                        byte[] dummySignature = new byte[]{};// TODO: dummy sig hash
                        txIn.setWitness(TransactionWitness.redeemP2WPKH(dummySignature,
                                identity.getKeyPair().getPublic()));
                    }
                }
            } else {
                throw new IllegalStateException("cannot handle: " + scriptPubKey);
            }
        }
        return true;
    }
}
