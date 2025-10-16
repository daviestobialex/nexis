/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Interface.java to edit this template
 */
package org.nexis.signers;

/**
 *
 * <p>
 * Implementations of this interface are intended to sign inputs of the given
 * transaction. Given transaction may already be partially signed or somehow
 * altered by other signers.</p>
 * <p>
 * To make use of the signer, you need to add it into the wallet by calling
 * {@link Wallet#addTransactionSigner(TransactionSigner)}. Signer will be
 * serialized along with the wallet data. In order for a wallet to recreate
 * signer after deserialization, each signer should have no-args constructor</p>
 *
 * @author daviestobialex
 */
public interface TransactionSigner {

}
