package com.mycelium.wallet.activity;

import android.annotation.SuppressLint;
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
import com.mycelium.wallet.CurrencySwitcher;
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
    public static final String SEND_MODE = "sendmode";

    @BindView(R.id.btOk)
    Button btOk;
    @BindView(R.id.tvMaxAmount)
    TextView tvMaxAmount;
    @BindView(R.id.tvAmount)
    TextView tvAmount;

    private boolean isSendMode;

    private WalletAccount _account;
    private NumberEntry _numberEntry;
    private CurrencyValue _amount;
    private MbwManager _mbwManager;
    private ExactCurrencyValue _maxSpendableAmount;
    private long _kbMinerFee;

    /**
     * Get Amount for spending
     */
    public static void callMeToSend(Activity currentActivity, int requestCode, UUID account, CurrencyValue amountToSend, Long kbMinerFee, boolean isColdStorage) {
        Intent intent = new Intent(currentActivity, GetAmountActivity.class)
                .putExtra(ACCOUNT, account)
                .putExtra(ENTERED_AMOUNT, amountToSend)
                .putExtra(KB_MINER_FEE, kbMinerFee)
                .putExtra(IS_COLD_STORAGE, isColdStorage)
                .putExtra(SEND_MODE, true);
        currentActivity.startActivityForResult(intent, requestCode);
    }

    @SuppressLint("ShowToast")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.get_amount_activity);
        ButterKnife.bind(this);

        _mbwManager = MbwManager.getInstance(getApplication());
        initNumberEntry(savedInstanceState);

        isSendMode = getIntent().getBooleanExtra(SEND_MODE, false);
        if (isSendMode) {
            initSendMode();
        }

        updateUI();
        checkEntry();
    }

    private void initSendMode() {
        //we need to have an account, fee, etc to be able to calculate sending related stuff
        boolean isColdStorage = getIntent().getBooleanExtra(IS_COLD_STORAGE, false);
        UUID accountId = Preconditions.checkNotNull((UUID) getIntent().getSerializableExtra(ACCOUNT));
        _account = _mbwManager.getWalletManager().getAccount(accountId);

        // Calculate the maximum amount that can be spent where we send everything we got to another address
        _kbMinerFee = Preconditions.checkNotNull((Long) getIntent().getSerializableExtra(KB_MINER_FEE));
        _maxSpendableAmount = _account.calculateMaxSpendableAmount(_kbMinerFee);
        showMaxAmount();

        // if no amount is set, create an null amount with the correct currency
        if (_amount == null || _amount.getValue() == null) {
            _amount = ExactCurrencyValue.from(null, _account.getAccountDefaultCurrency());
            updateUI();
        }

        // Max Button
        tvMaxAmount.setVisibility(View.VISIBLE);
    }

    private void initNumberEntry(Bundle savedInstanceState) {
        _amount = (CurrencyValue) getIntent().getSerializableExtra(ENTERED_AMOUNT);
        // Load saved state
        if (savedInstanceState != null) {
            _amount = (CurrencyValue) savedInstanceState.getSerializable(ENTERED_AMOUNT);
        }

        // Init the number pad
        String amountString;
        if (!CurrencyValue.isNullOrZero(_amount)) {
            amountString = Utils.getFormattedValue(_amount, _mbwManager.getBitcoinDenomination());
            _mbwManager.getCurrencySwitcher().setCurrency(_amount.getCurrency());
        } else {
            if (_amount != null && _amount.getCurrency() != null) {
                _mbwManager.getCurrencySwitcher().setCurrency(_amount.getCurrency());
            } else {
                _mbwManager.getCurrencySwitcher().setCurrency(_account.getAccountDefaultCurrency());
            }
            amountString = "";
        }
        _numberEntry = new NumberEntry(_mbwManager.getBitcoinDenomination().getDecimalPlaces(), this, this, amountString);
    }

    @OnClick(R.id.btOk)
    void onOkClick() {
        if (CurrencyValue.isNullOrZero(_amount)) {
            return;
        }

        // Return the entered value and set a positive result code
        Intent result = new Intent();
        result.putExtra(AMOUNT, _amount);
        setResult(RESULT_OK, result);
        GetAmountActivity.this.finish();
    }

    private void updateUI() {
        // Show maximum spendable amount
        if (isSendMode) {
            showMaxAmount();
        }

        if (_amount != null) {
            //update amount
            int showDecimalPlaces;
            BigDecimal newAmount = null;
            if (_mbwManager.getCurrencySwitcher().getCurrentCurrency().equals(CurrencyValue.BTC)) {
                //just good ol bitcoins
                showDecimalPlaces = _mbwManager.getBitcoinDenomination().getDecimalPlaces();
                if (_amount.getValue() != null) {
                    int btcToTargetUnit = CoinUtil.Denomination.BTC.getDecimalPlaces() - _mbwManager.getBitcoinDenomination().getDecimalPlaces();
                    newAmount = _amount.getValue().multiply(BigDecimal.TEN.pow(btcToTargetUnit));
                }
            } else {
                //take what was typed in
                showDecimalPlaces = 2;
                newAmount = _amount.getValue();
            }
            _numberEntry.setEntry(newAmount, showDecimalPlaces);
        } else {
            tvAmount.setText("");
        }

    }

    private void showMaxAmount() {
        CurrencyValue maxSpendable = CurrencyValue.fromValue(_maxSpendableAmount,
                _amount.getCurrency(), _mbwManager.getExchangeRateManager());
        String maxBalanceString = getResources().getString(R.string.max_btc,
                Utils.getFormattedValueWithUnit(maxSpendable, _mbwManager.getBitcoinDenomination()));
        tvMaxAmount.setText(maxBalanceString);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putSerializable(ENTERED_AMOUNT, _amount);
    }

    @Override
    protected void onResume() {
        _mbwManager.getEventBus().register(this);
        _mbwManager.getExchangeRateManager().requestOptionalRefresh();
        super.onResume();
    }

    @Override
    protected void onPause() {
        _mbwManager.getEventBus().unregister(this);
        CurrencySwitcher currencySwitcher = _mbwManager.getCurrencySwitcher();
        currencySwitcher.setCurrency(currencySwitcher.getCurrentFiatCurrency());
        super.onPause();
    }

    @Override
    public void onEntryChanged(String entry, boolean wasSet) {
        if (!wasSet) {
            // if it was change by the user pressing buttons (show it unformatted)
            BigDecimal value = _numberEntry.getEntryAsBigDecimal();
            setEnteredAmount(value);
        }
        tvAmount.setText(entry);
        checkEntry();
    }

    private void setEnteredAmount(BigDecimal value) {
        // handle denomination
        String currentCurrency = _mbwManager.getCurrencySwitcher().getCurrentCurrency();

        if (currentCurrency.equals(CurrencyValue.BTC)) {
            Long satoshis;
            int decimals = _mbwManager.getBitcoinDenomination().getDecimalPlaces();
            satoshis = value.movePointRight(decimals).longValue();
            if (satoshis >= Bitcoins.MAX_VALUE) {
                // entered value is equal or larger then total amount of bitcoins ever existing
                return;
            }

            _amount = ExactBitcoinValue.from(satoshis);
        } else {
            _amount = ExactCurrencyValue.from(value, currentCurrency);
        }
    }

    private void checkEntry() {
        if (CurrencyValue.isNullOrZero(_amount)) {
            // Nothing entered
            tvAmount.setTextColor(getResources().getColor(R.color.white));
            btOk.setEnabled(false);
            return;
        }
        if (isSendMode && !CurrencyValue.isNullOrZero(_amount) /*|| !_mbwManager.getColuManager().isColuAsset(_amount.getCurrency())*/) {
            AmountValidation result = checkTransaction();
            // Enable/disable Ok button
            btOk.setEnabled(result == AmountValidation.Ok && !_amount.isZero());
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
            WalletAccount.Receiver receiver = new WalletAccount.Receiver(Address.getNullAddress(_mbwManager.getNetwork()), satoshis);
            _account.checkAmount(receiver, _kbMinerFee, _amount);
        } catch (OutputTooSmallException e1) {
            return AmountValidation.ValueTooSmall;
        } catch (InsufficientFundsException e) {
            return AmountValidation.NotEnoughFunds;
        } catch (StandardTransactionBuilder.UnableToBuildTransactionException e) {
            // under certain conditions the max-miner-fee check fails - report it back to the server, so we can better
            // debug it
            _mbwManager.reportIgnoredException("MinerFeeException", e);
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
            satoshis = _amount.getAsBitcoin(_mbwManager.getExchangeRateManager());
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
                if (satoshis == null || _account.getBalance().getSpendableBalance() < satoshis.getLongValue()) {
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
