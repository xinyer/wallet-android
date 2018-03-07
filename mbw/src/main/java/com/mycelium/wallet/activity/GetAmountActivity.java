package com.mycelium.wallet.activity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Preconditions;
import com.megiontechnologies.Bitcoins;
import com.mrd.bitlib.StandardTransactionBuilder;
import com.mrd.bitlib.StandardTransactionBuilder.InsufficientFundsException;
import com.mrd.bitlib.StandardTransactionBuilder.OutputTooSmallException;
import com.mrd.bitlib.model.Address;
import com.mrd.bitlib.util.CoinUtil;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.NumberEntry;
import com.mycelium.wallet.NumberEntry.NumberEntryListener;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.currency.CurrencyValue;
import com.mycelium.wapi.wallet.currency.ExactBitcoinValue;
import com.mycelium.wapi.wallet.currency.ExactCurrencyValue;

import java.math.BigDecimal;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;


public class GetAmountActivity extends Activity implements NumberEntryListener {
    public static final String AMOUNT = "amount";
    public static final String ENTERED_AMOUNT = "enteredamount";
    public static final String ACCOUNT = "account";
    public static final String KB_MINER_FEE = "kbMinerFee";
    public static final String IS_COLD_STORAGE = "isColdStorage";

    @BindView(R.id.btOk)
    Button btOk;
    @BindView(R.id.tvMaxAmount)
    TextView tvMaxAmount;
    @BindView(R.id.tvAmount)
    TextView tvAmount;

    private MbwManager mbwManager;
    private WalletAccount account;
    private NumberEntry numberEntry;
    private CurrencyValue amount;
    private ExactCurrencyValue maxSpendableAmount;
    private long kbMinerFee;

    /**
     * Get Amount for spending
     */
    public static void callMeToSend(Activity currentActivity, int requestCode, UUID account, CurrencyValue amountToSend, Long kbMinerFee, boolean isColdStorage) {
        Intent intent = new Intent(currentActivity, GetAmountActivity.class)
                .putExtra(ACCOUNT, account)
                .putExtra(ENTERED_AMOUNT, amountToSend)
                .putExtra(KB_MINER_FEE, kbMinerFee)
                .putExtra(IS_COLD_STORAGE, isColdStorage);
        currentActivity.startActivityForResult(intent, requestCode);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.get_amount_activity);
        ButterKnife.bind(this);

        mbwManager = MbwManager.getInstance(getApplication());
        initNumberEntry(savedInstanceState);
        initSendMode();
        updateUI();
        checkEntry();
    }

    private void initSendMode() {
        //we need to have an account, fee, etc to be able to calculate sending related stuff
        boolean isColdStorage = getIntent().getBooleanExtra(IS_COLD_STORAGE, false);
        UUID accountId = Preconditions.checkNotNull((UUID) getIntent().getSerializableExtra(ACCOUNT));
        account = mbwManager.getWalletManager().getAccount(accountId);

        // Calculate the maximum amount that can be spent where we send everything we got to another address
        kbMinerFee = Preconditions.checkNotNull((Long) getIntent().getSerializableExtra(KB_MINER_FEE));
        maxSpendableAmount = account.calculateMaxSpendableAmount(kbMinerFee);
        showMaxAmount();

        // if no amount is set, create an null amount with the correct currency
        if (amount == null || amount.getValue() == null) {
            amount = ExactCurrencyValue.from(null, account.getAccountDefaultCurrency());
            updateUI();
        }

        // Max Button
        tvMaxAmount.setVisibility(View.VISIBLE);
    }

    private void initNumberEntry(Bundle savedInstanceState) {
        amount = (CurrencyValue) getIntent().getSerializableExtra(ENTERED_AMOUNT);
        // Load saved state
        if (savedInstanceState != null) {
            amount = (CurrencyValue) savedInstanceState.getSerializable(ENTERED_AMOUNT);
        }

        // Init the number pad
        String amountString;
        if (!CurrencyValue.isNullOrZero(amount)) {
            amountString = Utils.getFormattedValue(amount, mbwManager.getBitcoinDenomination());
        } else {
            amountString = "";
        }
        numberEntry = new NumberEntry(mbwManager.getBitcoinDenomination().getDecimalPlaces(), this, this, amountString);
    }

    @OnClick(R.id.btOk)
    void onOkClick() {
        if (CurrencyValue.isNullOrZero(amount)) {
            return;
        }

        // Return the entered value and set a positive result code
        Intent result = new Intent();
        result.putExtra(AMOUNT, amount);
        setResult(RESULT_OK, result);
        GetAmountActivity.this.finish();
    }

    private void updateUI() {
        // Show maximum spendable amount
        showMaxAmount();
        if (amount != null) {
            //update amount
            int showDecimalPlaces;
            BigDecimal newAmount = null;
            //just good ol bitcoins
            showDecimalPlaces = mbwManager.getBitcoinDenomination().getDecimalPlaces();
            if (amount.getValue() != null) {
                int btcToTargetUnit = CoinUtil.Denomination.BTC.getDecimalPlaces() - mbwManager.getBitcoinDenomination().getDecimalPlaces();
                newAmount = amount.getValue().multiply(BigDecimal.TEN.pow(btcToTargetUnit));
            }
            numberEntry.setEntry(newAmount, showDecimalPlaces);
        } else {
            tvAmount.setText("");
        }

    }

    private void showMaxAmount() {
        CurrencyValue maxSpendable = CurrencyValue.fromValue(maxSpendableAmount);
        String maxBalanceString = getResources().getString(R.string.max_btc,
                Utils.getFormattedValueWithUnit(maxSpendable, mbwManager.getBitcoinDenomination()));
        tvMaxAmount.setText(maxBalanceString);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putSerializable(ENTERED_AMOUNT, amount);
    }

    @Override
    protected void onResume() {
        mbwManager.getEventBus().register(this);
        super.onResume();
    }

    @Override
    protected void onPause() {
        mbwManager.getEventBus().unregister(this);
        super.onPause();
    }

    @Override
    public void onEntryChanged(String entry, boolean wasSet) {
        if (!wasSet) {
            // if it was change by the user pressing buttons (show it unformatted)
            BigDecimal value = numberEntry.getEntryAsBigDecimal();
            setEnteredAmount(value);
        }
        tvAmount.setText(entry);
        checkEntry();
    }

    private void setEnteredAmount(BigDecimal value) {
        // handle denomination
        int decimals = mbwManager.getBitcoinDenomination().getDecimalPlaces();
        Long satoshis = value.movePointRight(decimals).longValue();
        if (satoshis >= Bitcoins.MAX_VALUE) {
            // entered value is equal or larger then total amount of bitcoins ever existing
            return;
        }
        amount = ExactBitcoinValue.from(satoshis);
    }

    private void checkEntry() {
        if (CurrencyValue.isNullOrZero(amount)) {
            // Nothing entered
            tvAmount.setTextColor(getResources().getColor(R.color.white));
            btOk.setEnabled(false);
            return;
        }
        if (!CurrencyValue.isNullOrZero(amount)) {
            AmountValidation result = checkTransaction();
            btOk.setEnabled(result == AmountValidation.Ok && !amount.isZero());
        } else {
            btOk.setEnabled(true);
        }
    }

    /**
     * Check that the amount is large enough for the network to accept it, and
     * that we have enough funds to send it.
     */
    private AmountValidation checkSendAmount(Bitcoins satoshis) {
        if (satoshis == null) {
            return AmountValidation.Ok; //entering a fiat value + exchange is not availible
        }
        try {
            WalletAccount.Receiver receiver = new WalletAccount.Receiver(Address.getNullAddress(mbwManager.getNetwork()), satoshis);
            account.checkAmount(receiver, kbMinerFee, amount);
        } catch (OutputTooSmallException e1) {
            return AmountValidation.ValueTooSmall;
        } catch (InsufficientFundsException e) {
            return AmountValidation.NotEnoughFunds;
        } catch (StandardTransactionBuilder.UnableToBuildTransactionException e) {
            // under certain conditions the max-miner-fee check fails - report it back to the server, so we can better
            // debug it
            mbwManager.reportIgnoredException("MinerFeeException", e);
            return AmountValidation.Invalid;
        }
        return AmountValidation.Ok;
    }

    private enum AmountValidation {
        Ok, ValueTooSmall, Invalid, NotEnoughFunds
    }

    private AmountValidation checkTransaction() {
        AmountValidation result;
        Bitcoins satoshis = null;

        try {
            satoshis = amount.getAsBitcoin();
        } catch (IllegalArgumentException ex) {
            // something failed while calculating the bitcoin amount
            return AmountValidation.Invalid;
        }
        // Check whether we have sufficient funds, and whether the output is too small
        result = checkSendAmount(satoshis);

        if (result == AmountValidation.Ok) {
            tvAmount.setTextColor(getResources().getColor(R.color.white));
        } else {
            tvAmount.setTextColor(getResources().getColor(R.color.red));
            if (result == AmountValidation.NotEnoughFunds) {
                // We do not have enough funds
                if (satoshis == null || account.getBalance().getSpendableBalance() < satoshis.getLongValue()) {
                    // We do not have enough funds for sending the requested amount
                    String msg = getResources().getString(R.string.insufficient_funds);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                } else {
                    // We do have enough funds for sending the requested amount, but
                    // not for the required fee
                    String msg = getResources().getString(R.string.insufficient_funds_for_fee);
                    Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                }
            }
            // else {
            // The amount we want to send is not large enough for the network to
            // accept it. Don't Toast about it, it's just annoying
            // }
        }
        return result;
    }
}
