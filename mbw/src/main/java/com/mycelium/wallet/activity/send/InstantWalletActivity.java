package com.mycelium.wallet.activity.send;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;

import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.extsig.trezor.activity.InstantTrezorActivity;


public class InstantWalletActivity extends Activity {

    private static final int REQUEST_TREZOR = 1;

    public static void callMe(Activity currentActivity) {
        Intent intent = new Intent(currentActivity, InstantWalletActivity.class);
        currentActivity.startActivity(intent);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.instant_wallet_activity);


        findViewById(R.id.btTrezor).setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View arg0) {
                InstantTrezorActivity.callMe(InstantWalletActivity.this, REQUEST_TREZOR);
            }
        });

    }

    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == REQUEST_TREZOR) {
            if (resultCode == RESULT_OK) {
                finish();
            }
        } else {
            throw new IllegalStateException("unknown return codes after scanning... " + requestCode + " " + resultCode);
        }
    }

    @Override
    public void finish() {
        // drop and create a new TempWalletManager so that no sensitive data remains in memory
        MbwManager.getInstance(this).forgetColdStorageWalletManager();
        super.finish();
    }
}
