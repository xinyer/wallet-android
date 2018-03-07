package com.mycelium.wallet.activity.send;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;

import com.google.common.base.Preconditions;
import com.mycelium.wallet.*;
import com.mycelium.wallet.event.SyncFailed;
import com.mycelium.wallet.event.SyncStopped;
import com.mycelium.wapi.wallet.WalletAccount;
import com.squareup.otto.Subscribe;

import java.util.UUID;

public class SendInitializationActivity extends Activity {

    private MbwManager mbwManager;
    private WalletAccount account;
    private BitcoinUri uri;
    private boolean isColdStorage;
    private Handler synchronizingHandler;
    private Handler slowNetworkHandler;
    private byte[] rawPr;

    public static void callMe(Activity currentActivity, UUID account, boolean isColdStorage) {
        Intent intent = new Intent(currentActivity, SendInitializationActivity.class)
                .putExtra("account", account)
                .putExtra("uri", new BitcoinUri(null, null, null))
                .putExtra("isColdStorage", isColdStorage)
                .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        currentActivity.startActivity(intent);
    }

    public static Intent getIntent(Activity currentActivity, UUID account, boolean isColdStorage) {
        return prepareSendingIntent(currentActivity, account, (BitcoinUri) null, isColdStorage);
    }

    public static void callMe(Activity currentActivity, UUID account, BitcoinUri uri, boolean isColdStorage) {
        Intent intent = prepareSendingIntent(currentActivity, account, uri, isColdStorage)
                .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        currentActivity.startActivity(intent);
    }

    public static void callMe(Activity currentActivity, UUID account, byte[] rawPaymentRequest, boolean isColdStorage) {
        Intent intent = prepareSendingIntent(currentActivity, account, rawPaymentRequest, isColdStorage)
                .addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        currentActivity.startActivity(intent);
    }

    private static Intent prepareSendingIntent(Activity currentActivity, UUID account, BitcoinUri uri, boolean isColdStorage) {
        return new Intent(currentActivity, SendInitializationActivity.class)
                .putExtra("account", account)
                .putExtra("uri", uri)
                .putExtra("isColdStorage", isColdStorage);
    }

    private static Intent prepareSendingIntent(Activity currentActivity, UUID account, byte[] rawPaymentRequest, boolean isColdStorage) {
        return new Intent(currentActivity, SendInitializationActivity.class)
                .putExtra("account", account)
                .putExtra("rawPr", rawPaymentRequest)
                .putExtra("isColdStorage", isColdStorage);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.send_initialization_activity);
        mbwManager = MbwManager.getInstance(getApplication());
        // Get intent parameters
        UUID accountId = Preconditions.checkNotNull((UUID) getIntent().getSerializableExtra("account"));

        uri = (BitcoinUri) getIntent().getSerializableExtra("uri");
        rawPr = getIntent().getByteArrayExtra("rawPr");
        isColdStorage = getIntent().getBooleanExtra("isColdStorage", true);
        account = mbwManager.getWalletManager().getAccount(accountId);
    }

    @Override
    protected void onResume() {
        mbwManager.getEventBus().register(this);

        // Show delayed messages so the user does not grow impatient
        synchronizingHandler = new Handler();
        synchronizingHandler.postDelayed(showSynchronizing, 2000);
        slowNetworkHandler = new Handler();
        slowNetworkHandler.postDelayed(showSlowNetwork, 6000);

        // If we are in cold storage spending mode we wish to synchronize the wallet
        if (isColdStorage) {
            mbwManager.getWalletManager().startSynchronization();
        } else {
            continueIfReady();
        }
        super.onResume();
    }

    @Override
    protected void onPause() {
        if (synchronizingHandler != null) {
            synchronizingHandler.removeCallbacks(showSynchronizing);
        }
        if (slowNetworkHandler != null) {
            slowNetworkHandler.removeCallbacks(showSlowNetwork);
        }
        mbwManager.getEventBus().unregister(this);
        super.onPause();
    }

    private Runnable showSynchronizing = new Runnable() {

        @Override
        public void run() {
            findViewById(R.id.tvSynchronizing).setVisibility(View.VISIBLE);
        }
    };

    private Runnable showSlowNetwork = new Runnable() {

        @Override
        public void run() {
            findViewById(R.id.tvSlowNetwork).setVisibility(View.VISIBLE);
        }
    };

    @Subscribe
    public void syncFailed(SyncFailed event) {
        Utils.toastConnectionError(this);
        // If we are in cold storage spending mode there is no point in continuing.
        // If we continued we would think that there were no funds on the private key
        if (isColdStorage) {
            finish();
        }
    }

    @Subscribe
    public void syncStopped(SyncStopped sync) {
        continueIfReady();
    }

    private void continueIfReady() {
        if (isFinishing()) {
            return;
        }
        if (account.getBalance().isSynchronizing) {
            // wait till its finished syncing
            return;
        }
        if (isColdStorage) {
            ColdStorageSummaryActivity.callMe(this, account.getId());
        } else {
            Intent intent;
            if (rawPr != null) {
                intent = SendMainActivity.getIntent(this, account.getId(), rawPr, false);
            } else {
                intent = SendMainActivity.getIntent(this, account.getId(), uri, false);
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_FORWARD_RESULT);
            this.startActivity(intent);
        }
        finish();
    }
}
