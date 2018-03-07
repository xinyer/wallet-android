package com.mycelium.wallet.activity.accounts;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import com.mycelium.wallet.R;
import com.mycelium.wallet.extsig.trezor.activity.TrezorAccountImportActivity;

import java.util.UUID;

public class AddAdvancedAccountActivity extends Activity {

    public static void callMe(Activity activity, int requestCode) {
        Intent intent = new Intent(activity, AddAdvancedAccountActivity.class);
        activity.startActivityForResult(intent, requestCode);
    }

    private static final int TREZOR_RESULT_CODE = 2;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_advanced_account_activity);
        final Activity activity = AddAdvancedAccountActivity.this;

        findViewById(R.id.btTrezor).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TrezorAccountImportActivity.callMe(activity, TREZOR_RESULT_CODE);
            }
        });

    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {

        if (requestCode == TREZOR_RESULT_CODE && resultCode == Activity.RESULT_OK) {
            // already added to the WalletManager - just return the new account
            finishOk((UUID) intent.getSerializableExtra("account"));
        } else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    private void finishOk(UUID account) {
        Intent result = new Intent();
        result.putExtra(AddAccountActivity.RESULT_KEY, account);
        setResult(RESULT_OK, result);
        finish();
    }
}
