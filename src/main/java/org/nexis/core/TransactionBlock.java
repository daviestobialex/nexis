/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import java.time.Instant;
import java.util.List;
import org.nexis.utilities.Sha256Hash;

/**
 *
 * @author daviestobialex
 */
public class TransactionBlock extends Block {

    protected TransactionBlock(long version, Sha256Hash prevBlockHash, Sha256Hash merkleRoot,
            Sha256Hash hash, Instant time, long nonce, List<Transaction> transactions) {
        super(version, prevBlockHash, merkleRoot, hash, time, nonce, transactions);
    }

}
