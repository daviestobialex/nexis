/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.util.Map;
import org.nexis.base.Identity;
import org.nexis.base.Sha256Hash;
import org.nexis.wallet.WalletTransaction;

/**
 * links transactions and wallet
 *
 * @author daviestobialex
 */
public interface WalletTransactionAdapter {

    public Identity getIdentity();

    /**
     * Returns transactions from a specific pool.
     *
     * @param pool
     * @return
     */
    Map<Sha256Hash, Transaction> getTransactionPool(WalletTransaction.Pool pool);
}
