package com.mycelium.wallet.activity.send;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.mrd.bitlib.model.Address;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wapi.model.Balance;
import com.mycelium.wapi.wallet.WalletAccount;

import java.util.UUID;

public class ColdStorageSummaryActivity extends Activity {

    private static final int SEND_MAIN_REQUEST_CODE = 1;
    private MbwManager mbwManager;
    private WalletAccount account;

    public static void callMe(Activity currentActivity, UUID account) {
        Intent intent = new Intent(currentActivity, ColdStorageSummaryActivity.class);
        intent.putExtra("account", account);
        intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        currentActivity.startActivity(intent);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.cold_storage_summary_activity);
        mbwManager = MbwManager.getInstance(getApplication());

        // Get intent parameters
        UUID accountId = Preconditions.checkNotNull((UUID) getIntent().getSerializableExtra("account"));
        if (mbwManager.getWalletManager().getAccountIds().contains(accountId)) {
            account = mbwManager.getWalletManager().getAccount(accountId);
        } else {
            finish();
        }
    }

    @Override
    protected void onResume() {
        updateUi();
        super.onResume();
    }

    private void updateUi() {
        Balance balance = account.getBalance();
        // Description
        if (account.canSpend()) {
            ((TextView) findViewById(R.id.tvDescription)).setText(R.string.cs_private_key_description);
        } else {
            ((TextView) findViewById(R.id.tvDescription)).setText(R.string.cs_address_description);
        }

        // Address
        Optional<Address> receivingAddress = account.getReceivingAddress();
        ((TextView) findViewById(R.id.tvAddress)).setText(receivingAddress.isPresent() ? receivingAddress.get().toMultiLineString() : "");

        // Balance
        ((TextView) findViewById(R.id.tvBalance)).setText(mbwManager.getBtcValueString(balance.getSpendableBalance()));

        // Send Button
        Button btSend = findViewById(R.id.btSend);
        if (account.canSpend()) {
            if (balance.getSpendableBalance() > 0) {
                btSend.setEnabled(true);
                btSend.setOnClickListener(new OnClickListener() {
                    @Override
                    public void onClick(View arg0) {
                        Intent intent = SendMainActivity.getIntent(ColdStorageSummaryActivity.this, account.getId(), true);
                        ColdStorageSummaryActivity.this.startActivityForResult(intent, SEND_MAIN_REQUEST_CODE);
                        finish();
                    }
                });
            } else {
                btSend.setEnabled(false);
            }
        } else {
            btSend.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SEND_MAIN_REQUEST_CODE) {
            mbwManager.forgetColdStorageWalletManager();
            setResult(resultCode, data);
            finish();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }
}