/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import org.nexis.base.Coin;
import org.nexis.base.GovernanceMetadata;

/**
 *
 * @author daviestobialex
 */
public class GovernanceOutput extends TransactionOutput {

    private GovernanceMetadata governanceMetadata;

    public GovernanceOutput(Transaction parent, Coin value, byte[] scriptBytes) {
        super(parent, value, scriptBytes);
    }

}
