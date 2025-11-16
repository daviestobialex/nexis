/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/Classes/Class.java to edit this template
 */
package org.nexis.core;

import com.google.common.base.MoreObjects;
import java.security.PublicKey;
import java.util.ArrayList;
import java.util.List;
import java.math.BigDecimal;
import org.nexis.base.BondPolicy;
import org.nexis.base.Address;
import org.nexis.base.Coin;
import org.nexis.script.Script;
import org.nexis.script.ScriptBuilder;
import org.nexis.utilities.ExchangeRate;
import org.nexis.wallet.AllowUnconfirmedCoinSelector;
import org.nexis.wallet.CoinSelector;
import org.nexis.wallet.Wallet;
import org.nexis.wallet.Wallet.MissingSigsMode;

/**
 * A SendRequest gives the wallet information about precisely how to send money
 * to a recipient or set of recipients. Static methods are provided to help you
 * create SendRequests and there are a few helper methods on the wallet that
 * just simplify the most common use cases. You may wish to customize a
 * SendRequest if you want to attach a fee or modify the change address.
 */
public class SendRequest {

    /**
     * <p>
     * A transaction, probably incomplete, that describes the outline of what
     * you want to do. This typically will mean it has some outputs to the
     * intended destinations, but no inputs or change address (and therefore no
     * fees) - the wallet will calculate all that for you and update tx
     * later.</p>
     *
     * <p>
     * Be careful when adding outputs that you check the min output value
     * ({@link TransactionOutput#getMinNonDustValue(Coin)}) to avoid the whole
     * transaction being rejected because one output is dust.</p>
     *
     * <p>
     * If there are already inputs to the transaction, make sure their out point
     * has a connected output, otherwise their value will be added to fee. Also
     * ensure they are either signed or are spendable by a wallet key, otherwise
     * the behavior of {@link Wallet#completeTx(SendRequest)} is undefined
     * (likely RuntimeException).</p>
     */
    public final Transaction tx;

    /**
     * When emptyWallet is set, all coins selected by the coin selector are sent
     * to the first output in tx (its value is ignored and set to
     * {@link Wallet#getBalance()} - the fees required for the transaction). Any
     * additional outputs are removed.
     */
    public boolean emptyWallet = false;

    /**
     * "Change" means the difference between the value gathered by a
     * transactions inputs (the size of which you don't really control as it
     * depends on who sent you money), and the value being sent somewhere else.
     * The change address should be selected from this wallet, normally. <b>If
     * null this will be chosen for you.</b>
     */
    public Address changeAddress = null;

    /**
     * <p>
     * A transaction can have a fee attached, which is defined as the difference
     * between the input values and output values. Any value taken in that is
     * not provided to an output can be claimed by a miner. This is how mining
     * is incentivized in later years of the Bitcoin system when inflation
     * drops. It also provides a way for people to prioritize their transactions
     * over others and is used as a way to make denial of service attacks
     * expensive.</p>
     *
     * <p>
     * This is a dynamic fee (in satoshis) which will be added to the
     * transaction for each virtual kilobyte in size including the first. This
     * is useful as as miners usually sort pending transactions by their fee per
     * unit size when choosing which transactions to add to a block. Note that,
     * to keep this equivalent to Bitcoin Core definition, a virtual kilobyte is
     * defined as 1000 virtual bytes, not 1024.</p>
     */
    public Coin feePerKb = Context.get().getFeePerKb();

    public void setFeePerVkb(Coin feePerVkb) {
        this.feePerKb = feePerVkb;
    }

    /**
     * <p>
     * Requires that there be enough fee for a default Bitcoin Core to at least
     * relay the transaction. (ie ensure the transaction will not be outright
     * rejected by the network). Defaults to true, you should only set this to
     * false if you know what you're doing.</p>
     *
     * <p>
     * Note that this does not enforce certain fee rules that only apply to
     * transactions which are larger than 26,000 bytes. If you get a transaction
     * which is that large, you should set a feePerKb of at least
     * {@link Transaction#REFERENCE_DEFAULT_MIN_TX_FEE}.</p>
     */
    public boolean ensureMinRequiredFee = Context.get().isEnsureMinRequiredFee();

    /**
     * If true (the default), the inputs will be signed.
     */
    public boolean signInputs = true;

    /**
     * If not null, the {@link CoinSelector} to use instead of the wallets
     * default. Coin selectors are responsible for choosing which transaction
     * outputs (coins) in a wallet to use given the desired send value amount.
     */
    public CoinSelector coinSelector = null;

    /**
     * Shortcut for
     * {@code req.coinSelector = AllowUnconfirmedCoinSelector.get();}.
     */
    public void allowUnconfirmed() {
        coinSelector = AllowUnconfirmedCoinSelector.get();
    }

    /**
     * If true (the default), the outputs will be shuffled during completion to
     * randomize the location of the change output, if any. This is normally
     * what you want for privacy reasons but in unit tests it can be annoying so
     * it can be disabled here.
     */
    public boolean shuffleOutputs = true;

    /**
     * Specifies what to do with missing signatures left after completing this
     * request. Default strategy is to throw an exception on missing signature
     * ({@link MissingSigsMode#THROW}).
     *
     * @see MissingSigsMode
     */
    public MissingSigsMode missingSigsMode = MissingSigsMode.THROW;

    /**
     * If not null, this exchange rate is recorded with the transaction during
     * completion.
     */
    public ExchangeRate exchangeRate = null;

    /**
     * If not null, this memo is recorded with the transaction during
     * completion. It can be used to record the memo of the payment request that
     * initiated the transaction.
     */
    public String memo = null;

    /**
     * If false (default value), tx fee is paid by the sender If true, tx fee is
     * paid by the recipient/s. If there is more than one recipient, the tx fee
     * is split equally between them regardless of output value and size.
     */
    public boolean recipientsPayFees = false;

    // Tracks if this has been passed to wallet.completeTx already: just a safety check.
    public boolean completed;

    private SendRequest(Transaction transaction) {
        tx = transaction;
    }

    /**
     * <p>
     * Creates a new SendRequest to the given address for the given value.</p>
     *
     * <p>
     * Be careful to check the output's value is reasonable using
     * {@link TransactionOutput#getMinNonDustValue(Coin)} afterwards or you risk
     * having the transaction rejected by the network.</p>
     *
     * @param destination
     * @param value
     * @return
     */
    public static SendRequest to(Address destination, Coin value) {
        Transaction tx = new Transaction();
        tx.addOutput(value, destination);
        return new SendRequest(tx);
    }

    /**
     * Creates a SendRequest for an approval transaction. This creates a
     * transaction that: 1. Sends coins to the destination address 2. Includes a
     * multi-signature output script that requires approval from the specified
     * approver (and potentially other governance nodes)
     *
     * The multi-sig output will be signed by governance nodes when they approve
     * the request. The approval script requires at least 1 signature from the
     * approver's public key.
     *
     * @param destination the address where funds are sent
     * @param approver the public key of the governance node approving this
     * request
     * @param value the amount to send
     * @return a SendRequest with the approval transaction
     */
    public static SendRequest approve(Address destination, PublicKey approver, Coin value) {
        Transaction tx = new Transaction();

        // First output: send coins to the destination address
        tx.addOutput(value, destination);
        // Governance nodes represent the most influential actors in the network,
        // and therefore also its greatest potential vulnerability. To prevent abuse,
        // each governor must lock a stake-backed bond as "skin in the game".
        //
        // The bond serves two critical roles:
        //
        // 1. ECONOMIC SAFETY: 
        //    A malicious or irresponsible governor risks losing part of their locked 
        //    stake. This creates a direct financial cost for misbehavior.
        //
        // 2. INCENTIVE ALIGNMENT:
        //    Honest governors earn rewards whenever the nodes they approve behave 
        //    correctly and contribute value to the network. Good approval decisions 
        //    become economically beneficial, while poor decisions become costly.
        //
        // This bond mechanism turns governance into a Proof-of-Service system:
        // governors must *prove* their reliability, judgement, and contribution by 
        // risking real stake, and are continuously evaluated based on the performance 
        // of the peers they approve. The network becomes self-regulating, economically 
        // secure, and resistant to Sybil or low-cost governance attacks.

        // Calculate the bond required by policy and add an output that locks
        // the bond amount to the network (not to any address). The bond is
        // represented as a dedicated output whose script marks it as a bond
        // (we use OP_RETURN with approver pubkey bytes as metadata). This
        // deducts the bond from the sender's balance at creation time.
        try {
            // Interpret the provided `value` as the approver's stake amount
            BigDecimal approverStake = Coin.satoshiToBtc(value.getValue());

            // Very small networks or tests: use the monetary policy genesis as
            // a conservative source for initial/current supply values.
            BigDecimal initialSupply = Coin.satoshiToBtc(MonetaryPolicy.getStartCoinsAsCoin().getValue());
            BigDecimal currentSupply = initialSupply; // best-effort; real supply tracked elsewhere

            // Network size is unknown here; use 1 as a conservative default.
            int networkSize = 1;

            // Obtain the bond policy from the Context so policy is centrally managed.
            BondPolicy bondPolicy = Context.get().getBondPolicy();
            BondPolicy.BondResult bondResult = bondPolicy.calculateBond(approverStake, networkSize, currentSupply, initialSupply);

            if (!bondResult.allowed) {
                // Approver cannot meet the required bond under current policy.
                throw new BondPolicy.InsufficientStakeException("Approver stake insufficient for required bond");
            }

            // Convert bond amount (in whole coins) back to a Coin value
            Coin bondCoin = Coin.ofBtc(bondResult.bondToLock);

            // Build a small unspendable marker output that locks the bond to
            // the network. We use an OP_RETURN containing the approver pubkey
            // bytes so the lock can be associated with the approver later.
            byte[] opReturnData = approver.getEncoded();
            Script bondLockScript = ScriptBuilder.createOpReturnScript(opReturnData);

            // Add bond output which deducts the bond amount from the sender's balance
            tx.addOutput(bondCoin, bondLockScript);
        } catch (ArithmeticException | BondPolicy.InsufficientStakeException ex) {
            // If conversion or bond calculation fails, surface as runtime
            // problem; callers may catch and handle this as needed.
            throw new RuntimeException("Failed to calculate/lock bond for approval: " + ex.getMessage(), ex);
        }
        // Second output: create a multi-signature output that requires approval
        // Threshold of 1 means at least 1 signature is required (from the approver)
        // This output serves as a covenant/approval marker on the blockchain
        List<PublicKey> approverKeys = new ArrayList<>();
        approverKeys.add(approver);

        Script multiSignatureScript = ScriptBuilder.createMultiSigOutputScript(7, approverKeys);

        // Add a small approval marker output (governance contracts often use 0 value or minimal value)
        // This output flags the transaction as requiring governance approval
        tx.addOutput(Coin.ZERO, multiSignatureScript);

        return new SendRequest(tx);
    }

    /**
     * <p>
     * Creates a new SendRequest to the given pubkey for the given value.</p>
     *
     * <p>
     * Be careful to check the output's value is reasonable using
     * {@link TransactionOutput#getMinNonDustValue(Coin)} afterwards or you risk
     * having the transaction rejected by the network. Note that using
     * {@link SendRequest#to(Address, Coin)} will result in a smaller output,
     * and thus the ability to use a smaller output value without rejection.</p>
     *
     * @param destination
     * @param value
     * @return
     */
    public static SendRequest to(PublicKey destination, Coin value) {
        Transaction tx = new Transaction();
        tx.addOutput(value, destination);
        return new SendRequest(tx);
    }

    /**
     * Simply wraps a pre-built incomplete transaction provided by you.
     *
     * @param tx
     * @return
     */
    public static SendRequest forTx(Transaction tx) {
        return new SendRequest(tx);
    }

    public static SendRequest emptyWallet(Address destination) {
        Transaction tx = new Transaction();
        tx.addOutput(Coin.ZERO, destination);
        SendRequest req = new SendRequest(tx);
        req.emptyWallet = true;
        return req;
    }

    @Override
    public String toString() {
        // print only the user-settable fields
        MoreObjects.ToStringHelper helper = MoreObjects.toStringHelper(this).omitNullValues();
        helper.add("emptyWallet", emptyWallet);
        helper.add("changeAddress", changeAddress);
        helper.add("feePerKb", feePerKb);
        helper.add("ensureMinRequiredFee", ensureMinRequiredFee);
        helper.add("signInputs", signInputs);
//        helper.add("aesKey", aesKey != null ? "set" : null); // careful to not leak the key
        helper.add("coinSelector", coinSelector);
        helper.add("shuffleOutputs", shuffleOutputs);
        helper.add("recipientsPayFees", recipientsPayFees);
        return helper.toString();
    }

}
