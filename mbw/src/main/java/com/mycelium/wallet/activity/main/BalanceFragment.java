package com.mycelium.wallet.activity.main;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.mrd.bitlib.model.Address;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.StringHandleConfig;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.activity.ScanActivity;
import com.mycelium.wallet.activity.modern.ModernMain;
import com.mycelium.wallet.activity.receive.ReceiveCoinsActivity;
import com.mycelium.wallet.activity.send.SendInitializationActivity;
import com.mycelium.wallet.event.AccountChanged;
import com.mycelium.wallet.event.BalanceChanged;
import com.mycelium.wallet.event.SelectedAccountChanged;
import com.mycelium.wallet.event.SyncStopped;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.currency.CurrencyBasedBalance;
import com.squareup.otto.Subscribe;

import java.math.BigDecimal;

import butterknife.ButterKnife;
import butterknife.OnClick;

public class BalanceFragment extends Fragment {

    private MbwManager mbwManager;
    private View root;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        root = Preconditions.checkNotNull(inflater.inflate(R.layout.main_balance_view, container, false));
        final View balanceArea = Preconditions.checkNotNull(root.findViewById(R.id.llBalance));
        balanceArea.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                mbwManager.getWalletManager().startSynchronization();
            }
        });
        ButterKnife.bind(this, root);
        return root;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(Activity activity) {
        mbwManager = MbwManager.getInstance(getActivity());
        super.onAttach(activity);
    }

    @Override
    public void onResume() {
        mbwManager.getEventBus().register(this);
        updateUi();
        super.onResume();
    }

    @OnClick(R.id.btSend)
    void onClickSend() {
        SendInitializationActivity.callMe(BalanceFragment.this.getActivity(), mbwManager.getSelectedAccount().getId(), false);
    }

    @OnClick(R.id.btReceive)
    void onClickReceive() {
        Optional<Address> receivingAddress = mbwManager.getSelectedAccount().getReceivingAddress();
        if (receivingAddress.isPresent()) {
            ReceiveCoinsActivity.callMe(getActivity(), receivingAddress.get(),
                    mbwManager.getSelectedAccount().canSpend(), true);
        }
    }

    @OnClick(R.id.btScan)
    void onClickScan() {
        //perform a generic scan, act based upon what we find in the QR code
        StringHandleConfig config = StringHandleConfig.genericScanRequest();
        ScanActivity.callMe(BalanceFragment.this.getActivity(), ModernMain.GENERIC_SCAN_REQUEST, config);
    }

    @Override
    public void onPause() {
        mbwManager.getEventBus().unregister(this);
        super.onPause();
    }

    private void updateUi() {
        if (!isAdded()) {
            return;
        }

        WalletAccount account = Preconditions.checkNotNull(mbwManager.getSelectedAccount());
        CurrencyBasedBalance balance;
        try {
            balance = Preconditions.checkNotNull(account.getCurrencyBasedBalance());
        } catch (IllegalArgumentException ex) {
            mbwManager.reportIgnoredException(ex);
            balance = CurrencyBasedBalance.ZERO_BITCOIN_BALANCE;
        }
        updateUiKnownBalance(balance);
    }

    private void updateUiKnownBalance(CurrencyBasedBalance balance) {
        // Set Balance
        String valueString = Utils.getFormattedValueWithUnit(balance.confirmed, mbwManager.getBitcoinDenomination());
        ((TextView) root.findViewById(R.id.tvBalance)).setText(valueString);
        root.findViewById(R.id.pbProgress).setVisibility(balance.isSynchronizing ? View.VISIBLE : View.GONE);

        // Show/Hide Receiving
        if (balance.receiving.getValue().compareTo(BigDecimal.ZERO) > 0) {
            String receivingString = Utils.getFormattedValueWithUnit(balance.receiving, mbwManager.getBitcoinDenomination());
            String receivingText = getResources().getString(R.string.receiving, receivingString);
            TextView tvReceiving = root.findViewById(R.id.tvReceiving);
            tvReceiving.setText(receivingText);
            tvReceiving.setVisibility(View.VISIBLE);
        } else {
            root.findViewById(R.id.tvReceiving).setVisibility(View.GONE);
        }

        // Show/Hide Sending
        if (balance.sending.getValue().compareTo(BigDecimal.ZERO) > 0) {
            String sendingString = Utils.getFormattedValueWithUnit(balance.sending, mbwManager.getBitcoinDenomination());
            String sendingText = getResources().getString(R.string.sending, sendingString);
            TextView tvSending = root.findViewById(R.id.tvSending);
            tvSending.setText(sendingText);
            tvSending.setVisibility(View.VISIBLE);
        } else {
            root.findViewById(R.id.tvSending).setVisibility(View.GONE);
        }
    }

    @Subscribe
    public void selectedAccountChanged(SelectedAccountChanged event) {
        updateUi();
    }

    @Subscribe
    public void balanceChanged(BalanceChanged event) {
        updateUi();
    }

    @Subscribe
    public void accountChanged(AccountChanged event) {
        updateUi();
    }

    @Subscribe
    public void syncStopped(SyncStopped event) {
        updateUi();
    }

}
