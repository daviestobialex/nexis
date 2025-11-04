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
package org.nexis.wallet;

import com.google.common.base.MoreObjects;
import java.security.PublicKey;

import org.nexis.script.Script;
import org.nexis.script.ScriptPattern;
import static org.nexis.utilities.Preconditions.checkArgument;

/**
 * This class aggregates data required to spend transaction output.
 *
 * For P2PKH and P2PK transactions it will have only a single key and CHECKSIG
 * program as redeemScript. For multisignature transactions there will be
 * multiple keys one of which will be a full key and the rest are watch only,
 * redeem script will be a CHECKMULTISIG program. Keys will be sorted in the
 * same order they appear in a program (lexicographical order).
 */
public class RedeemData {

    public final Script redeemScript;
    public final PublicKey key;

    private RedeemData(PublicKey key, Script redeemScript) {
        this.redeemScript = redeemScript;
        this.key = key;
    }

    /**
     * Creates RedeemData for P2PKH, P2WPKH or P2PK input.Provided key is a
     * single private key needed to spend such inputs.
     *
     * @param key
     * @param redeemScript
     * @return
     */
    public static RedeemData of(PublicKey key, Script redeemScript) {
        checkArgument(ScriptPattern.isP2WPKH(redeemScript) || ScriptPattern.isP2WSH(redeemScript));
        return new RedeemData(key, redeemScript);
    }

    @Override
    public String toString() {
        final MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this).omitNullValues();
        helper.add("redeemScript", redeemScript);
        helper.add("key", key);
        return helper.toString();
    }
}
