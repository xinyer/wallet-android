package com.mycelium.wallet.activity.send;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Html;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.mrd.bitlib.StandardTransactionBuilder.InsufficientFundsException;
import com.mrd.bitlib.StandardTransactionBuilder.OutputTooSmallException;
import com.mrd.bitlib.StandardTransactionBuilder.UnableToBuildTransactionException;
import com.mrd.bitlib.StandardTransactionBuilder.UnsignedTransaction;
import com.mrd.bitlib.crypto.HdKeyNode;
import com.mrd.bitlib.crypto.InMemoryPrivateKey;
import com.mrd.bitlib.model.Address;
import com.mrd.bitlib.model.OutputList;
import com.mrd.bitlib.model.Transaction;
import com.mrd.bitlib.model.UnspentTransactionOutput;
import com.mycelium.paymentrequest.PaymentRequestException;
import com.mycelium.paymentrequest.PaymentRequestInformation;
import com.mycelium.wallet.BitcoinUri;
import com.mycelium.wallet.BitcoinUriWithAddress;
import com.mycelium.wallet.Constants;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.MinerFee;
import com.mycelium.wallet.R;
import com.mycelium.wallet.ColuAssetUri;
import com.mycelium.wallet.StringHandleConfig;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.activity.GetAmountActivity;
import com.mycelium.wallet.activity.ScanActivity;
import com.mycelium.wallet.activity.StringHandlerActivity;
import com.mycelium.wallet.activity.send.adapter.FeeLvlViewAdapter;
import com.mycelium.wallet.activity.send.adapter.FeeViewAdapter;
import com.mycelium.wallet.activity.send.event.SelectListener;
import com.mycelium.wallet.activity.send.helper.FeeItemsBuilder;
import com.mycelium.wallet.activity.send.model.FeeItem;
import com.mycelium.wallet.activity.send.model.FeeLvlItem;
import com.mycelium.wallet.activity.send.view.SelectableRecyclerView;
import com.mycelium.wallet.activity.util.AnimationUtils;
import com.mycelium.wallet.event.ExchangeRatesRefreshed;
import com.mycelium.wallet.event.SelectedCurrencyChanged;
import com.mycelium.wallet.event.SyncFailed;
import com.mycelium.wallet.event.SyncStopped;
import com.mycelium.wallet.paymentrequest.PaymentRequestHandler;
import com.mycelium.wapi.api.lib.FeeEstimation;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.WalletManager;
import com.mycelium.wapi.wallet.bip44.Bip44AccountExternalSignature;
import com.mycelium.wapi.wallet.currency.BitcoinValue;
import com.mycelium.wapi.wallet.currency.CurrencyValue;
import com.mycelium.wapi.wallet.currency.ExactBitcoinValue;
import com.mycelium.wapi.wallet.currency.ExactCurrencyValue;
import com.mycelium.wapi.wallet.currency.ExchangeBasedBitcoinValue;
import com.squareup.otto.Subscribe;

import org.bitcoin.protocols.payments.PaymentACK;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.makeText;
import static com.mrd.bitlib.StandardTransactionBuilder.estimateTransactionSize;

public class SendMainActivity extends Activity {
    private static final String TAG = "SendMainActivity";

    private static final int GET_AMOUNT_RESULT_CODE = 1;
    private static final int SCAN_RESULT_CODE = 2;
    private static final int MANUAL_ENTRY_RESULT_CODE = 4;
    private static final int REQUEST_PICK_ACCOUNT = 5;
    protected static final int SIGN_TRANSACTION_REQUEST_CODE = 6;
    private static final int BROADCAST_REQUEST_CODE = 7;
    private static final int REQUEST_PAYMENT_HANDLER = 8;
    public static final String RAW_PAYMENT_REQUEST = "rawPaymentRequest";

    public static final String ACCOUNT = "account";
    private static final String AMOUNT = "amount";
    public static final String IS_COLD_STORAGE = "isColdStorage";
    public static final String RECEIVING_ADDRESS = "receivingAddress";
    public static final String HD_KEY = "hdKey";
    public static final String TRANSACTION_LABEL = "transactionLabel";
    public static final String BITCOIN_URI = "bitcoinUri";
    public static final String FEE_LVL = "feeLvl";
    public static final String PAYMENT_FETCHED = "paymentFetched";
    private static final String PAYMENT_REQUEST_HANDLER_ID = "paymentRequestHandlerId";
    private static final String SIGNED_TRANSACTION = "signedTransaction";
    private static final String RMC_URI = "rmcUri";
    private static final String FEE_PER_KB = "fee_per_kb";
    public static final String TRANSACTION_FIAT_VALUE = "transaction_fiat_value";


    private enum TransactionStatus {
        MissingArguments, OutputTooSmall, InsufficientFunds, InsufficientFundsForFee, OK
    }

    @BindView(R.id.tvAmount)
    TextView tvAmount;
    @BindView(R.id.tvError)
    TextView tvError;
    @BindView(R.id.tvAmountFiat)
    TextView tvAmountFiat;
    @BindView(R.id.tvAmountTitle)
    TextView tvAmountTitle;
    @BindView(R.id.tvUnconfirmedWarning)
    TextView tvUnconfirmedWarning;
    @BindView(R.id.tvReceiver)
    TextView tvReceiver;
    @BindView(R.id.tvRecipientTitle)
    TextView tvRecipientTitle;
    @BindView(R.id.tvReceiverLabel)
    TextView tvReceiverLabel;
    @BindView(R.id.tvReceiverAddress)
    TextView tvReceiverAddress;
    @BindView(R.id.tvTransactionLabelTitle)
    TextView tvTransactionLabelTitle;
    @BindView(R.id.tvTransactionLabel)
    TextView tvTransactionLabel;
    @BindView(R.id.tvSatFeeValue)
    TextView tvSatFeeValue;
    @BindView(R.id.btEnterAmount)
    ImageButton btEnterAmount;
    @BindView(R.id.btSend)
    Button btSend;
    @BindView(R.id.btManualEntry)
    Button btManualEntry;
    @BindView(R.id.btScan)
    Button btScan;
    @BindView(R.id.pbSend)
    ProgressBar pbSend;
    @BindView(R.id.llFee)
    LinearLayout llFee;
    @BindView(R.id.llEnterRecipient)
    View llEnterRecipient;
    @BindView(R.id.llRecipientAddress)
    LinearLayout llRecipientAddress;
    @BindView(R.id.tvFeeWarning)
    TextView tvFeeWarning;
    @BindView(R.id.feeLvlList)
    SelectableRecyclerView feeLvlList;
    @BindView(R.id.feeValueList)
    SelectableRecyclerView feeValueList;

    private MbwManager _mbwManager;

    private PaymentRequestHandler _paymentRequestHandler;
    private String _paymentRequestHandlerUuid;

    protected WalletAccount _account;
    private CurrencyValue _amountToSend;
    private BitcoinValue _lastBitcoinAmountToSend = null;
    private Address _receivingAddress;
    protected String _transactionLabel;
    private BitcoinUri _bitcoinUri;
    private ColuAssetUri _coluAssetUri;
    protected boolean _isColdStorage;
    private TransactionStatus _transactionStatus;
    protected UnsignedTransaction _unsigned;
    private Transaction _signedTransaction;
    private MinerFee feeLvl;
    private long feePerKbValue;
    private ProgressDialog _progress;
    private UUID _receivingAcc;
    private boolean _xpubSyncing = false;
    private boolean _spendingUnconfirmed = false;
    private boolean _paymentFetched = false;
    private FeeEstimation feeEstimation;
    private SharedPreferences transactionFiatValuePref;
    private FeeItemsBuilder feeItemsBuilder;

    int feeFirstItemWidth;

    public static Intent getIntent(Activity currentActivity, UUID account, boolean isColdStorage) {
        return new Intent(currentActivity, SendMainActivity.class)
                .putExtra(ACCOUNT, account)
                .putExtra(IS_COLD_STORAGE, isColdStorage);
    }

    public static Intent getIntent(Activity currentActivity, UUID account,
                                   Long amountToSend, Address receivingAddress, boolean isColdStorage) {
        return getIntent(currentActivity, account, isColdStorage)
                .putExtra(AMOUNT, ExactBitcoinValue.from(amountToSend))
                .putExtra(RECEIVING_ADDRESS, receivingAddress);
    }

    public static Intent getIntent(Activity currentActivity, UUID account, HdKeyNode hdKey) {
        return getIntent(currentActivity, account, false)
                .putExtra(HD_KEY, hdKey);
    }

    public static Intent getIntent(Activity currentActivity, UUID account, BitcoinUri uri, boolean isColdStorage) {
        return getIntent(currentActivity, account, uri.amount, uri.address, isColdStorage)
                .putExtra(TRANSACTION_LABEL, uri.label)
                .putExtra(BITCOIN_URI, uri);
    }

    public static Intent getIntent(Activity currentActivity, UUID account, byte[] rawPaymentRequest, boolean isColdStorage) {
        return getIntent(currentActivity, account, isColdStorage)
                .putExtra(RAW_PAYMENT_REQUEST, rawPaymentRequest);
    }

    @SuppressLint("ShowToast")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        // TODO: profile. slow!
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.send_main_activity);
        ButterKnife.bind(this);
        _mbwManager = MbwManager.getInstance(getApplication());

        // Get intent parameters
        UUID accountId = Preconditions.checkNotNull((UUID) getIntent().getSerializableExtra(ACCOUNT));

        // May be null
        setAmountToSend((CurrencyValue) getIntent().getSerializableExtra(AMOUNT));
        // May be null
        _receivingAddress = (Address) getIntent().getSerializableExtra(RECEIVING_ADDRESS);
        //May be null
        _transactionLabel = getIntent().getStringExtra(TRANSACTION_LABEL);
        //May be null
        _bitcoinUri = (BitcoinUri) getIntent().getSerializableExtra(BITCOIN_URI);
        //May be null
        _coluAssetUri = (ColuAssetUri) getIntent().getSerializableExtra(RMC_URI);

        // did we get a raw payment request
        byte[] _rawPr = getIntent().getByteArrayExtra(RAW_PAYMENT_REQUEST);

        _isColdStorage = getIntent().getBooleanExtra(IS_COLD_STORAGE, false);
        _account = _mbwManager.getWalletManager().getAccount(accountId);
        feeLvl = _mbwManager.getMinerFee();
        feeEstimation = _mbwManager.getWalletManager().getLastFeeEstimations();
        feePerKbValue = _mbwManager.getMinerFee().getFeePerKb(feeEstimation).getLongValue();

        // Load saved state, overwriting amount and address
        if (savedInstanceState != null) {
            setAmountToSend((CurrencyValue) savedInstanceState.getSerializable(AMOUNT));
            _receivingAddress = (Address) savedInstanceState.getSerializable(RECEIVING_ADDRESS);
            _transactionLabel = savedInstanceState.getString(TRANSACTION_LABEL);
            feeLvl = (MinerFee) savedInstanceState.getSerializable(FEE_LVL);
            feePerKbValue = savedInstanceState.getLong(FEE_PER_KB);
            _bitcoinUri = (BitcoinUri) savedInstanceState.getSerializable(BITCOIN_URI);
            _coluAssetUri = (ColuAssetUri) savedInstanceState.getSerializable(RMC_URI);
            _paymentFetched = savedInstanceState.getBoolean(PAYMENT_FETCHED);
            _signedTransaction = (Transaction) savedInstanceState.getSerializable(SIGNED_TRANSACTION);

            // get the payment request handler from the BackgroundObject cache - if the application
            // has restarted since it was cached, the user gets queried again
            _paymentRequestHandlerUuid = savedInstanceState.getString(PAYMENT_REQUEST_HANDLER_ID);
            if (_paymentRequestHandlerUuid != null) {
                _paymentRequestHandler = (PaymentRequestHandler) _mbwManager.getBackgroundObjectsCache()
                        .getIfPresent(_paymentRequestHandlerUuid);
            }
        }

        //if we do not have a stored receiving address, and got a keynode, we need to figure out the address
        if (_receivingAddress == null) {
            HdKeyNode hdKey = (HdKeyNode) getIntent().getSerializableExtra(HD_KEY);
            if (hdKey != null) {
                setReceivingAddressFromKeynode(hdKey);
            }
        }

        // check whether the account can spend, if not, ask user to select one
        if (_account.canSpend()) {
            // See if we can create the transaction with what we have
            _transactionStatus = tryCreateUnsignedTransaction();
        } else {
            //we need the user to pick a spending account - the activity will then init sendmain correctly
            BitcoinUri uri;
            if (_bitcoinUri == null) {
                uri = BitcoinUri.from(_receivingAddress, getBitcoinValueToSend() == null ? null : getBitcoinValueToSend().getLongValue(), _transactionLabel, null);
            } else {
                uri = _bitcoinUri;
            }

            if (_rawPr != null) {
                GetSpendingRecordActivity.callMeWithResult(this, _rawPr, REQUEST_PICK_ACCOUNT);
            } else {
                GetSpendingRecordActivity.callMeWithResult(this, uri, REQUEST_PICK_ACCOUNT);
            }

            //no matter whether the user did successfully send or tapped back - we do not want to stay here with a wrong account selected
            finish();
            return;
        }

        // lets see if we got a raw Payment request (probably by downloading a file with MIME application/bitcoin-paymentrequest)
        if (_rawPr != null && _paymentRequestHandler == null) {
            verifyPaymentRequest(_rawPr);
        }

        // lets check whether we got a payment request uri and need to fetch payment data
        if (_bitcoinUri != null && !Strings.isNullOrEmpty(_bitcoinUri.callbackURL) && _paymentRequestHandler == null) {
            verifyPaymentRequest(_bitcoinUri);
        }

        checkHaveSpendAccount();

        // Amount Hint
        tvAmount.setHint(getResources().getString(R.string.amount_hint_denomination,
                _mbwManager.getBitcoinDenomination().toString()));

        int senderFinalWidth = getWindowManager().getDefaultDisplay().getWidth();
        feeFirstItemWidth = (senderFinalWidth - getResources().getDimensionPixelSize(R.dimen.item_dob_width)) / 2;

        initFeeView();
        initFeeLvlView();

        transactionFiatValuePref = getSharedPreferences(TRANSACTION_FIAT_VALUE, MODE_PRIVATE);

    }

    private FeeViewAdapter feeViewAdapter;
    private boolean showSendBtn = true;

    private void initFeeView() {
        feeValueList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));

        feeViewAdapter = new FeeViewAdapter(feeFirstItemWidth);
        feeItemsBuilder = new FeeItemsBuilder(_mbwManager);
        feeValueList.setSelectListener(new SelectListener() {
            @Override
            public void onSelect(RecyclerView.Adapter adapter, int position) {
                FeeItem item = ((FeeViewAdapter) adapter).getItem(position);
                feePerKbValue = item.feePerKb;
                updateRecipient();
                checkHaveSpendAccount();
                updateAmount();
                updateFeeText();
                updateError();
                btSend.setEnabled(_transactionStatus == TransactionStatus.OK);
                ScrollView scrollView = (ScrollView) findViewById(R.id.root);

                if (showSendBtn && scrollView.getMaxScrollAmount() - scrollView.getScaleY() > 0) {
                    scrollView.smoothScrollBy(0, scrollView.getMaxScrollAmount());
                    showSendBtn = false;
                }

            }
        });
        feeValueList.setAdapter(feeViewAdapter);
        feeValueList.setHasFixedSize(true);
    }

    private void initFeeLvlView() {
        feeLvlList.setLayoutManager(new LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false));
        List<MinerFee> fees = Arrays.asList(MinerFee.values());
        List<FeeLvlItem> feeLvlItems = new ArrayList<>();
        feeLvlItems.add(new FeeLvlItem(null, null, SelectableRecyclerView.Adapter.VIEW_TYPE_PADDING));
        for (MinerFee fee : fees) {
            String duration = Utils.formatBlockcountAsApproxDuration(this, fee.getNBlocks());
            feeLvlItems.add(new FeeLvlItem(fee, "~" + duration, SelectableRecyclerView.Adapter.VIEW_TYPE_ITEM));
        }
        feeLvlItems.add(new FeeLvlItem(null, null, SelectableRecyclerView.Adapter.VIEW_TYPE_PADDING));

        final FeeLvlViewAdapter feeLvlViewAdapter = new FeeLvlViewAdapter(feeLvlItems, feeFirstItemWidth);

        feeLvlList.setSelectListener(new SelectListener() {
            @Override
            public void onSelect(RecyclerView.Adapter adapter, int position) {
                FeeLvlItem item = ((FeeLvlViewAdapter) adapter).getItem(position);
                feeLvl = item.minerFee;
                feePerKbValue = feeLvl.getFeePerKb(feeEstimation).getLongValue();
                List<FeeItem> feeItems = feeItemsBuilder.getFeeItemList(feeLvl, estimateTxSize());
                feeViewAdapter.setDataset(feeItems);
                feeValueList.setSelectedItem(new FeeItem(feePerKbValue, null, null, FeeViewAdapter.VIEW_TYPE_ITEM));
            }
        });

        feeLvlList.setAdapter(feeLvlViewAdapter);

        int selectedIndex = -1;
        for (int i = 0; i < feeLvlItems.size(); i++) {
            FeeLvlItem feeLvlItem = feeLvlItems.get(i);
            if (feeLvlItem.minerFee == _mbwManager.getMinerFee()) {
                selectedIndex = i;
                break;
            }
        }

        feeLvlList.setSelectedItem(selectedIndex);
        feeLvlList.setHasFixedSize(true);
    }

    private int estimateTxSize() {
        int inCount = _unsigned != null ? _unsigned.getFundingOutputs().length : 1;
        int outCount = _unsigned != null ? _unsigned.getOutputs().length : 2;
        return estimateTransactionSize(inCount, outCount);
    }

    //TODO: fee from other bitcoin account if colu
    private TransactionStatus checkHaveSpendAccount() {
        return _transactionStatus;
    }

    // returns the amcountToSend in Bitcoin - it tries to get it from the entered amount and
    // only uses the ExchangeRate-Manager if we dont have it already converted
    private BitcoinValue getBitcoinValueToSend() {
        if (CurrencyValue.isNullOrZero(_amountToSend)) {
            return null;
        } else if (_amountToSend.getExactValueIfPossible().isBtc()) {
            return (BitcoinValue) _amountToSend.getExactValueIfPossible();
        } else if (_amountToSend.isBtc()) {
            return (BitcoinValue) _amountToSend;
        } else {
            if (_lastBitcoinAmountToSend == null) {
                // only convert once and keep that fx rate for further calls - the cache gets invalidated in setAmountToSend
                _lastBitcoinAmountToSend = (BitcoinValue) ExchangeBasedBitcoinValue.fromValue(_amountToSend, _mbwManager.getExchangeRateManager());
            }
            return _lastBitcoinAmountToSend;
        }
    }

    private void setAmountToSend(CurrencyValue toSend) {
        _amountToSend = toSend;
        _lastBitcoinAmountToSend = null;
    }

    private void verifyPaymentRequest(BitcoinUri uri) {
        Intent intent = VerifyPaymentRequestActivity.getIntent(this, uri);
        startActivityForResult(intent, REQUEST_PAYMENT_HANDLER);
    }

    private void verifyPaymentRequest(byte[] rawPr) {
        Intent intent = VerifyPaymentRequestActivity.getIntent(this, rawPr);
        startActivityForResult(intent, REQUEST_PAYMENT_HANDLER);
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putSerializable(AMOUNT, _amountToSend);
        savedInstanceState.putSerializable(RECEIVING_ADDRESS, _receivingAddress);
        savedInstanceState.putString(TRANSACTION_LABEL, _transactionLabel);
        savedInstanceState.putSerializable(FEE_LVL, feeLvl);
        savedInstanceState.putLong(FEE_PER_KB, feePerKbValue);
        savedInstanceState.putBoolean(PAYMENT_FETCHED, _paymentFetched);
        savedInstanceState.putSerializable(BITCOIN_URI, _bitcoinUri);
        savedInstanceState.putSerializable(RMC_URI, _coluAssetUri);
        savedInstanceState.putSerializable(PAYMENT_REQUEST_HANDLER_ID, _paymentRequestHandlerUuid);
        savedInstanceState.putSerializable(SIGNED_TRANSACTION, _signedTransaction);
    }

    @OnClick(R.id.btScan)
    void onClickScan() {
        StringHandleConfig config = StringHandleConfig.returnKeyOrAddressOrUriOrKeynode();
        ScanActivity.callMe(this, SCAN_RESULT_CODE, config);
    }

    @OnClick(R.id.btManualEntry)
    void onClickManualEntry() {
        Intent intent = new Intent(this, ManualAddressEntry.class);
        startActivityForResult(intent, MANUAL_ENTRY_RESULT_CODE);
    }

    @OnClick(R.id.btEnterAmount)
    void onClickAmount() {
        CurrencyValue presetAmount = _amountToSend;
        if (CurrencyValue.isNullOrZero(presetAmount)) {
            presetAmount = ExactCurrencyValue.from(null, _account.getAccountDefaultCurrency());
        }
        GetAmountActivity.callMeToSend(this, GET_AMOUNT_RESULT_CODE, _account.getId(), presetAmount, feePerKbValue, _isColdStorage);
    }

    @OnClick(R.id.btSend)
    void onClickSend() {
        if (_isColdStorage || _account instanceof Bip44AccountExternalSignature) {
            // We do not ask for pin when the key is from cold storage or from a external device (trezor,...)
            signTransaction();
        }
    }

    @OnClick(R.id.tvUnconfirmedWarning)
    void onClickUnconfirmedWarning() {
        new AlertDialog.Builder(this)
                .setTitle(getString(R.string.spending_unconfirmed_title))
                .setMessage(getString(R.string.spending_unconfirmed_description))
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        // continue
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_alert)
                .show();
    }

    private TransactionStatus tryCreateUnsignedTransaction() {
        Log.d(TAG, "tryCreateUnsignedTransaction");
        return tryCreateUnsignedTransactionFromWallet();
    }

    private TransactionStatus tryCreateUnsignedTransactionFromWallet() {
        _unsigned = null;

        BitcoinValue toSend = getBitcoinValueToSend();
        boolean hasAddressData = toSend != null && toSend.getAsBitcoin() != null && _receivingAddress != null;
        boolean hasRequestData = _paymentRequestHandler != null && _paymentRequestHandler.hasValidPaymentRequest();

        // Create the unsigned transaction
        try {
            if (hasRequestData) {
                PaymentRequestInformation paymentRequestInformation = _paymentRequestHandler.getPaymentRequestInformation();
                OutputList outputs = paymentRequestInformation.getOutputs();

                // has the payment request an amount set?
                if (paymentRequestInformation.hasAmount()) {
                    setAmountToSend(ExactBitcoinValue.from(paymentRequestInformation.getOutputs().getTotalAmount()));
                } else {
                    if (CurrencyValue.isNullOrZero(_amountToSend)) {
                        return TransactionStatus.MissingArguments;
                    }

                    // build new output list with user specified amount
                    outputs = outputs.newOutputsWithTotalAmount(toSend.getLongValue());
                }
                _unsigned = _account.createUnsignedTransaction(outputs, feePerKbValue);
                _receivingAddress = null;
                _transactionLabel = paymentRequestInformation.getPaymentDetails().memo;
                return TransactionStatus.OK;
            } else if (hasAddressData) {
                WalletAccount.Receiver receiver = new WalletAccount.Receiver(_receivingAddress, toSend.getLongValue());
                _unsigned = _account.createUnsignedTransaction(Collections.singletonList(receiver), feePerKbValue);
                checkSpendingUnconfirmed();
                return TransactionStatus.OK;
            } else {
                return TransactionStatus.MissingArguments;
            }
        } catch (InsufficientFundsException e) {
            if (_transactionStatus != TransactionStatus.InsufficientFunds) {
                makeText(this, getResources().getString(R.string.insufficient_funds), LENGTH_LONG).show();
            }
            return TransactionStatus.InsufficientFunds;
        } catch (OutputTooSmallException e1) {
            if (_transactionStatus != TransactionStatus.OutputTooSmall) {
                makeText(this, getResources().getString(R.string.amount_too_small), LENGTH_LONG).show();
            }
            return TransactionStatus.OutputTooSmall;
        } catch (UnableToBuildTransactionException e) {
            if (_transactionStatus != TransactionStatus.MissingArguments) {
                makeText(this, getResources().getString(R.string.unable_to_build_tx), LENGTH_LONG).show();
            }
            // under certain conditions the max-miner-fee check fails - report it back to the server, so we can better
            // debug it
            _mbwManager.reportIgnoredException("MinerFeeException", e);
            return TransactionStatus.MissingArguments;
        }
    }

    private void checkSpendingUnconfirmed() {
        for (UnspentTransactionOutput out : _unsigned.getFundingOutputs()) {
            Address address = out.script.getAddress(_mbwManager.getNetwork());
            if (out.height == -1 && _account.isOwnExternalAddress(address)) {
                // this is an unconfirmed output from an external address -> we want to warn the user
                // we allow unconfirmed spending of internal (=change addresses) without warning
                _spendingUnconfirmed = true;
                return;
            }
        }
        //no unconfirmed outputs are used as inputs, we are fine
        _spendingUnconfirmed = false;
    }

    private void updateUi() {
        // TODO: profile. slow!
        updateRecipient();
        checkHaveSpendAccount();
        updateAmount();
        updateFeeText();
        updateError();

        // Enable/disable send button
        btSend.setEnabled(_transactionStatus == TransactionStatus.OK);
        findViewById(R.id.root).invalidate();

        List<FeeItem> feeItems = feeItemsBuilder.getFeeItemList(feeLvl, estimateTxSize());
        feeViewAdapter.setDataset(feeItems);
        feeValueList.setSelectedItem(new FeeItem(feePerKbValue, null, null, FeeViewAdapter.VIEW_TYPE_ITEM));
    }

    private void updateRecipient() {
        boolean hasPaymentRequest = _paymentRequestHandler != null && _paymentRequestHandler.hasValidPaymentRequest();
        if (_receivingAddress == null && !hasPaymentRequest) {
            // Hide address, show "Enter"
            tvRecipientTitle.setText(R.string.enter_recipient_title);
            llEnterRecipient.setVisibility(View.VISIBLE);
            llRecipientAddress.setVisibility(View.GONE);
            return;
        }
        // Hide "Enter", show address
        tvRecipientTitle.setText(R.string.recipient_title);
        llRecipientAddress.setVisibility(View.VISIBLE);
        llEnterRecipient.setVisibility(View.GONE);

        // See if the address is in the address book or one of our accounts
        String label = null;
        if (_receivingAddress != null) {
            label = getAddressLabel(_receivingAddress);
        }
        if (label == null || label.length() == 0) {
            // Hide label
            tvReceiverLabel.setVisibility(GONE);
        } else {
            // Show label
            tvReceiverLabel.setText(label);
            tvReceiverLabel.setVisibility(VISIBLE);
        }

        // Set Address
        if (!hasPaymentRequest) {
            String choppedAddress = _receivingAddress.toMultiLineString();
            tvReceiver.setText(choppedAddress);
        }

        if (hasPaymentRequest) {
            PaymentRequestInformation paymentRequestInformation = _paymentRequestHandler.getPaymentRequestInformation();
            if (paymentRequestInformation.hasValidSignature()) {
                tvReceiver.setText(paymentRequestInformation.getPkiVerificationData().displayName);
            } else {
                tvReceiver.setText(getString(R.string.label_unverified_recipient));
            }
        }

        // show address (if available - some PRs might have more than one address or a not decodeable input)
        if (hasPaymentRequest && _receivingAddress != null) {
            tvReceiverAddress.setText(_receivingAddress.toDoubleLineString());
            tvReceiverAddress.setVisibility(VISIBLE);
        } else {
            tvReceiverAddress.setVisibility(GONE);
        }

        //if present, show transaction label
        if (_transactionLabel != null) {
            tvTransactionLabelTitle.setVisibility(VISIBLE);
            tvTransactionLabel.setVisibility(VISIBLE);
            tvTransactionLabel.setText(_transactionLabel);
        } else {
            tvTransactionLabelTitle.setVisibility(GONE);
            tvTransactionLabel.setVisibility(GONE);
        }
    }

    private String getAddressLabel(Address address) {
        Optional<UUID> accountId = _mbwManager.getAccountId(address, null);
        if (!accountId.isPresent()) {
            // We don't have it in our accounts, look in address book, returns empty string by default
            return _mbwManager.getMetadataStorage().getLabelByAddress(address);
        }
        // Get the name of the account
        return _mbwManager.getMetadataStorage().getLabelByAccount(accountId.get());
    }

    private void updateAmount() {
        // Update Amount
        if (_amountToSend == null) {
            // No amount to show
            tvAmountTitle.setText(R.string.enter_amount_title);
            tvAmount.setText("");
            tvAmountFiat.setVisibility(GONE);
        } else {
            tvAmountTitle.setText(R.string.amount_title);
            switch (_transactionStatus) {
                case OutputTooSmall:
                    // Amount too small
                    tvAmount.setText(_mbwManager.getBtcValueString(getBitcoinValueToSend().getLongValue()));
                    tvAmountFiat.setVisibility(GONE);
                    break;
                case InsufficientFunds:
                    // Insufficient funds
                    tvAmount.setText(
                            Utils.getFormattedValueWithUnit(_amountToSend, _mbwManager.getBitcoinDenomination())
                    );
                    break;
                default:
                    // Set Amount
                    if (!CurrencyValue.isNullOrZero(_amountToSend)) {
                        // show the user entered value as primary amount
                        CurrencyValue primaryAmount = _amountToSend;
                        CurrencyValue alternativeAmount;
                        if (primaryAmount.getCurrency().equals(_account.getAccountDefaultCurrency())) {
                            if (primaryAmount.isBtc()) {
                                // if the accounts default currency is BTC and the user entered BTC, use the current
                                // selected fiat as alternative currency
                                alternativeAmount = CurrencyValue.fromValue(
                                        primaryAmount, _mbwManager.getFiatCurrency(), _mbwManager.getExchangeRateManager()
                                );
                            } else {
                                // if the accounts default currency isn't BTC, use BTC as alternative
                                alternativeAmount = ExchangeBasedBitcoinValue.fromValue(
                                        primaryAmount, _mbwManager.getExchangeRateManager()
                                );
                            }
                        } else {
                            // use the accounts default currency as alternative
                            alternativeAmount = CurrencyValue.fromValue(
                                    primaryAmount, _account.getAccountDefaultCurrency(), _mbwManager.getExchangeRateManager()
                            );
                        }
                        String sendAmount = Utils.getFormattedValueWithUnit(primaryAmount, _mbwManager.getBitcoinDenomination());
                        if (!primaryAmount.isBtc()) {
                            // if the amount is not in BTC, show a ~ to inform the user, its only approximate and depends
                            // on a FX rate
                            sendAmount = "~ " + sendAmount;
                        }
                        tvAmount.setText(sendAmount);
                        if (CurrencyValue.isNullOrZero(alternativeAmount)) {
                            tvAmountFiat.setVisibility(GONE);
                        } else {
                            // show the alternative amount
                            String alternativeAmountString =
                                    Utils.getFormattedValueWithUnit(alternativeAmount, _mbwManager.getBitcoinDenomination());

                            if (!alternativeAmount.isBtc()) {
                                // if the amount is not in BTC, show a ~ to inform the user, its only approximate and depends
                                // on a FX rate
                                alternativeAmountString = "~ " + alternativeAmountString;
                            }

                            tvAmountFiat.setText(alternativeAmountString);
                            tvAmountFiat.setVisibility(VISIBLE);
                        }
                    } else {
                        tvAmount.setText("");
                        tvAmountFiat.setText("");
                    }
                    break;
            }
        }

        // Disable Amount button if we have a payment request with valid amount
        if (_paymentRequestHandler != null && _paymentRequestHandler.getPaymentRequestInformation().hasAmount()) {
            btEnterAmount.setEnabled(false);
        }
    }

    void updateError() {
        boolean tvErrorShow;
        switch (_transactionStatus) {
            case OutputTooSmall:
                // Amount too small
                tvError.setText(R.string.amount_too_small_short);
                tvErrorShow = true;
                break;
            case InsufficientFunds:
                tvError.setText(R.string.insufficient_funds);
                tvErrorShow = true;
                break;
            case InsufficientFundsForFee:
                tvError.setText(R.string.requires_btc_amount);
                tvErrorShow = true;
                break;
            default:
                tvErrorShow = false;
                //check if we need to warn the user about unconfirmed funds
                tvUnconfirmedWarning.setVisibility(_spendingUnconfirmed ? VISIBLE : GONE);
                break;
        }

        if (tvErrorShow && tvError.getVisibility() != VISIBLE) {
            AnimationUtils.expand(tvError, null);
        } else if (!tvErrorShow && tvError.getVisibility() == VISIBLE) {
            AnimationUtils.collapse(tvError, null);
        }
    }


    private void updateFeeText() {
        // Update Fee-Display
        _transactionStatus = tryCreateUnsignedTransaction();
        String feeWarning = null;
        tvFeeWarning.setOnClickListener(null);
        if (feePerKbValue == 0) {
            feeWarning = getString(R.string.fee_is_zero);
        }
        if (_unsigned == null) {
            // Only show button for fee lvl, cannot calculate fee yet
        } else {
            int inCount = _unsigned.getFundingOutputs().length;
            int outCount = _unsigned.getOutputs().length;
            int size = estimateTransactionSize(inCount, outCount);

            tvSatFeeValue.setText(inCount + " In- / " + outCount + " Outputs, ~" + size + " bytes");

            long fee = _unsigned.calculateFee();
            if (fee != size * feePerKbValue / 1000) {
                CurrencyValue value = ExactBitcoinValue.from(fee);
                CurrencyValue fiatValue = CurrencyValue.fromValue(value, _mbwManager.getFiatCurrency(), _mbwManager.getExchangeRateManager());
                String fiat = Utils.getFormattedValueWithUnit(fiatValue, _mbwManager.getBitcoinDenomination());
                fiat = fiat.isEmpty() ? "" : "(" + fiat + ")";
                feeWarning = getString(R.string.fee_change_warning
                        , Utils.getFormattedValueWithUnit(value, _mbwManager.getBitcoinDenomination())
                        , fiat);
                tvFeeWarning.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        new AlertDialog.Builder(SendMainActivity.this)
                                .setMessage(R.string.fee_change_description)
                                .setPositiveButton(R.string.button_ok, null).create()
                                .show();
                    }
                });
            }
        }
        tvFeeWarning.setVisibility(feeWarning != null ? View.VISIBLE : View.GONE);
        tvFeeWarning.setText(feeWarning != null ? Html.fromHtml(feeWarning) : null);
    }

    @Override
    protected void onResume() {
        _mbwManager.getEventBus().register(this);

        // If we don't have a fresh exchange rate, now is a good time to request one, as we will need it in a minute
        if (!_mbwManager.getCurrencySwitcher().isFiatExchangeRateAvailable()) {
            _mbwManager.getExchangeRateManager().requestRefresh();
        }

        pbSend.setVisibility(GONE);

        updateUi();
        super.onResume();
    }

    @Override
    protected void onPause() {
        _mbwManager.getEventBus().unregister(this);
        super.onPause();
    }

    protected void signTransaction() {
        // if we have a payment request, check if it is expired
        if (_paymentRequestHandler != null) {
            if (_paymentRequestHandler.getPaymentRequestInformation().isExpired()) {
                makeText(this, getString(R.string.payment_request_not_sent_expired), LENGTH_LONG).show();
                return;
            }
        }

        disableButtons();
        SignTransactionActivity.callMe(this, _account.getId(), _isColdStorage, _unsigned, SIGN_TRANSACTION_REQUEST_CODE);
    }

    protected void disableButtons() {
        pbSend.setVisibility(VISIBLE);
        btSend.setEnabled(false);
        btManualEntry.setEnabled(false);
        btScan.setEnabled(false);
        btEnterAmount.setEnabled(false);
    }

    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        Log.d(TAG, "onActivityResult: requestCode=" + requestCode + " and resultCode=" + resultCode);
        if (requestCode == SCAN_RESULT_CODE) {
            if (resultCode != RESULT_OK) {
                if (intent != null) {
                    String error = intent.getStringExtra(StringHandlerActivity.RESULT_ERROR);
                    if (error != null) {
                        makeText(this, error, LENGTH_LONG).show();
                    }
                }
            } else {
                StringHandlerActivity.ResultType type = (StringHandlerActivity.ResultType) intent.getSerializableExtra(StringHandlerActivity.RESULT_TYPE_KEY);
                if (type == StringHandlerActivity.ResultType.PRIVATE_KEY) {
                    InMemoryPrivateKey key = StringHandlerActivity.getPrivateKey(intent);
                    _receivingAddress = key.getPublicKey().toAddress(_mbwManager.getNetwork());
                } else if (type == StringHandlerActivity.ResultType.ADDRESS) {
                    _receivingAddress = StringHandlerActivity.getAddress(intent);
                } else if (type == StringHandlerActivity.ResultType.URI_WITH_ADDRESS) {
                    BitcoinUriWithAddress uri = StringHandlerActivity.getUriWithAddress(intent);
                    if (uri.callbackURL != null) {
                        //we contact the merchant server instead of using the params
                        _bitcoinUri = uri;
                        _paymentFetched = false;
                        verifyPaymentRequest(_bitcoinUri);
                        return;
                    }
                    _receivingAddress = uri.address;
                    _transactionLabel = uri.label;
                    if (uri.amount != null && uri.amount > 0) {
                        //we set the amount to the one contained in the qr code, even if another one was entered previously
                        if (!CurrencyValue.isNullOrZero(_amountToSend)) {
                            makeText(this, R.string.amount_changed, LENGTH_LONG).show();
                        }
                        setAmountToSend(ExactBitcoinValue.from(uri.amount));
                    }
                } else if (type == StringHandlerActivity.ResultType.URI) {
                    //todo: maybe merge with BitcoinUriWithAddress ?
                    BitcoinUri uri = StringHandlerActivity.getUri(intent);
                    if (uri.callbackURL != null) {
                        //we contact the merchant server instead of using the params
                        _bitcoinUri = uri;
                        _paymentFetched = false;
                        verifyPaymentRequest(_bitcoinUri);
                        return;
                    }
                } else if (type == StringHandlerActivity.ResultType.HD_NODE) {
                    setReceivingAddressFromKeynode(StringHandlerActivity.getHdKeyNode(intent));
                } else {
                    throw new IllegalStateException("Unexpected result type from scan: " + type.toString());
                }

            }

            _transactionStatus = tryCreateUnsignedTransaction();
            updateUi();
        } else if (requestCode == MANUAL_ENTRY_RESULT_CODE && resultCode == RESULT_OK) {
            _receivingAddress = Preconditions.checkNotNull((Address) intent
                    .getSerializableExtra(ManualAddressEntry.ADDRESS_RESULT_NAME));

            _transactionStatus = tryCreateUnsignedTransaction();
            updateUi();
        } else if (requestCode == GET_AMOUNT_RESULT_CODE && resultCode == RESULT_OK) {
            // Get result from AmountEntry
            CurrencyValue enteredAmount = (CurrencyValue) intent.getSerializableExtra(GetAmountActivity.AMOUNT);
            setAmountToSend(enteredAmount);
            if (!CurrencyValue.isNullOrZero(_amountToSend)) {
                _transactionStatus = tryCreateUnsignedTransaction();
            }
            updateUi();
        } else if (requestCode == SIGN_TRANSACTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                _signedTransaction = (Transaction) Preconditions.checkNotNull(intent.getSerializableExtra("signedTx"));

                // if we have a payment request with a payment_url, handle the send differently:
                if (_paymentRequestHandler != null
                        && _paymentRequestHandler.getPaymentRequestInformation().hasPaymentCallbackUrl()) {

                    // check again if the payment request isn't expired, as signing might have taken some time
                    // (e.g. with external signature provider)
                    if (!_paymentRequestHandler.getPaymentRequestInformation().isExpired()) {
                        // first send signed tx directly to the Merchant, and broadcast
                        // it only if we get a ACK from him (in paymentRequestAck)
                        _paymentRequestHandler.sendResponse(_signedTransaction, _account.getReceivingAddress().get());
                    } else {
                        makeText(this, getString(R.string.payment_request_not_sent_expired), LENGTH_LONG).show();

                    }
                } else {
                    BroadcastTransactionActivity.callMe(this, _account.getId(), _isColdStorage, _signedTransaction, _transactionLabel, getFiatValue(), BROADCAST_REQUEST_CODE);
                }
            }
        } else if (requestCode == BROADCAST_REQUEST_CODE) {
            // return result from broadcast
            if (resultCode == RESULT_OK) {
                transactionFiatValuePref.edit().putString(intent.getStringExtra(Constants.TRANSACTION_HASH_INTENT_KEY)
                        , intent.getStringExtra(Constants.TRANSACTION_FIAT_VALUE_KEY)).apply();
            }
            this.setResult(resultCode, intent);
            finish();
        } else if (requestCode == REQUEST_PAYMENT_HANDLER) {
            if (resultCode == RESULT_OK) {
                _paymentRequestHandlerUuid = Preconditions.checkNotNull(intent.getStringExtra("REQUEST_PAYMENT_HANDLER_ID"));
                if (_paymentRequestHandlerUuid != null) {
                    _paymentRequestHandler = (PaymentRequestHandler) _mbwManager.getBackgroundObjectsCache()
                            .getIfPresent(_paymentRequestHandlerUuid);
                } else {
                    _paymentRequestHandler = null;
                }
                _transactionStatus = tryCreateUnsignedTransaction();
                updateUi();
            } else {
                // user canceled - also leave this activity
                setResult(RESULT_CANCELED);
                finish();
            }
        } else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    private String getFiatValue() {
        long value = _amountToSend.getAsBitcoin(_mbwManager.getExchangeRateManager()).getLongValue() + _unsigned.calculateFee();
        return _mbwManager.getCurrencySwitcher().getFormattedFiatValue(ExactBitcoinValue.from(value), true);
    }

    private void setReceivingAddressFromKeynode(HdKeyNode hdKeyNode) {
        _progress = ProgressDialog.show(this, "", getString(R.string.retrieving_pubkey_address), true);
        _receivingAcc = _mbwManager.getWalletManager().createUnrelatedBip44Account(hdKeyNode);
        _xpubSyncing = true;
        _mbwManager.getWalletManager().startSynchronization(_receivingAcc);
    }

    @Subscribe
    public void paymentRequestException(PaymentRequestException ex) {
        //todo: maybe hint the user, that the merchant might broadcast the transaction later anyhow
        // and we should move funds to a new address to circumvent it
        Utils.showSimpleMessageDialog(this,
                String.format(getString(R.string.payment_request_error_while_getting_ack), ex.getMessage()));
    }

    @Subscribe
    public void paymentRequestAck(PaymentACK paymentACK) {
        if (paymentACK != null) {
            BroadcastTransactionActivity.callMe(this, _account.getId(), _isColdStorage, _signedTransaction
                    , _transactionLabel, getFiatValue(), BROADCAST_REQUEST_CODE);
        }
    }

    @Subscribe
    public void exchangeRatesRefreshed(ExchangeRatesRefreshed event) {
        updateUi();
    }

    @Subscribe
    public void selectedCurrencyChanged(SelectedCurrencyChanged event) {
        updateUi();
    }

    @Subscribe
    public void syncFinished(SyncStopped event) {
        if (_xpubSyncing) {
            _xpubSyncing = false;
            _receivingAddress = _mbwManager.getWalletManager().getAccount(_receivingAcc).getReceivingAddress().get();
            if (_progress != null) {
                _progress.dismiss();
            }
            _transactionStatus = tryCreateUnsignedTransaction();
            updateUi();
        }
    }

    @Subscribe
    public void syncFailed(SyncFailed event) {
        if (_progress != null) {
            _progress.dismiss();
        }
        makeText(this, R.string.warning_sync_failed_reusing_first, LENGTH_LONG).show();
    }
}
