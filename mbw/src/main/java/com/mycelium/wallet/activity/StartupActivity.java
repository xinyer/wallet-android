package com.mycelium.wallet.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.Window;

import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.activity.modern.ModernMain;
import com.mycelium.wallet.extsig.trezor.activity.TrezorAccountImportActivity;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.bip44.Bip44Account;

import java.util.UUID;

public class StartupActivity extends Activity {
    private static final int MINIMUM_SPLASH_TIME = 500;
    private static final int TREZOR_RESULT_CODE = 1;

    private MbwManager _mbwManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.startup_activity);
        new Handler().postDelayed(delayedInitialization, 200);
    }

    private Runnable delayedInitialization = new Runnable() {
        @Override
        public void run() {
            long startTime = System.currentTimeMillis();
            _mbwManager = MbwManager.getInstance(StartupActivity.this.getApplication());

            if (_mbwManager.getSelectedAccount() == null) {
                initAccount();
                return;
            }

            // Calculate how much time we spent initializing, and do a delayed
            // finish so we display the splash a minimum amount of time
            long timeSpent = System.currentTimeMillis() - startTime;
            long remainingTime = MINIMUM_SPLASH_TIME - timeSpent;
            if (remainingTime < 0) {
                remainingTime = 0;
            }
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    normalStartup();
                }
            }, remainingTime);
        }
    };

    private void initAccount() {
        new AlertDialog
                .Builder(this)
                .setCancelable(false)
                .setTitle(R.string.init_account_title)
                .setMessage(getString(R.string.init_account_content))
                .setPositiveButton(R.string.init_account_import_button, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface arg0, int arg1) {
                        TrezorAccountImportActivity.callMe(StartupActivity.this, TREZOR_RESULT_CODE);
                    }
                }).show();
    }

    private void normalStartup() {
        // Normal startup, show the selected account in the BalanceActivity
        startActivity(new Intent(StartupActivity.this, ModernMain.class));
        finish();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == TREZOR_RESULT_CODE && resultCode == RESULT_OK) {
            UUID accountId = (UUID) data.getSerializableExtra("account");
            WalletAccount account = _mbwManager.getWalletManager().getAccount(accountId);
            if (account.isActive()) {
                _mbwManager.setSelectedAccount(accountId);
            }
            String defaultName = getString(R.string.account) + " " + (((Bip44Account) account).getAccountIndex() + 1);
            _mbwManager.getMetadataStorage().storeAccountLabel(accountId, defaultName);
            normalStartup();
        }
    }
}
