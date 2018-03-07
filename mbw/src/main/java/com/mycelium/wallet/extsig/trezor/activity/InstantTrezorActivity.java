package com.mycelium.wallet.extsig.trezor.activity;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.widget.ImageView;
import android.widget.TextView;

import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.extsig.AbstractAccountScanManager;
import com.mycelium.wallet.extsig.common.ExternalSignatureDeviceManager;
import com.mycelium.wallet.extsig.common.activity.InstantExtSigActivity;
import com.mycelium.wapi.wallet.AccountScanManager;
import com.squareup.otto.Subscribe;

public class InstantTrezorActivity extends InstantExtSigActivity {
    public static void callMe(Activity currentActivity, int requestCode) {
        Intent intent = new Intent(currentActivity, InstantTrezorActivity.class);
        currentActivity.startActivityForResult(intent, requestCode);
    }

    @Override
    protected AbstractAccountScanManager initMasterseedManager() {
        return MbwManager.getInstance(this).getTrezorManager();
    }

    @NonNull
    @Override
    protected String getFirmwareUpdateDescription() {
        return getString(R.string.trezor_new_firmware_description);
    }

    @Override
    protected void setView() {
        setContentView(R.layout.activity_instant_ext_sig);
        ((ImageView) findViewById(R.id.ivConnectExtSig)).setImageResource(R.drawable.connect_trezor);
        ((TextView) findViewById(R.id.tvCaption)).setText(R.string.trezor_cold_storage_header);
        ((TextView) findViewById(R.id.tvDeviceType)).setText(R.string.trezor_name);
    }

    @Subscribe
    public void onPinMatrixRequest(ExternalSignatureDeviceManager.OnPinMatrixRequest event) {
        super.onPinMatrixRequest(event);
    }

    @Subscribe
    public void onScanError(AccountScanManager.OnScanError event) {
        super.onScanError(event);
    }

    @Subscribe
    public void onStatusChanged(AccountScanManager.OnStatusChanged event) {
        super.onStatusChanged(event);
    }

    @Subscribe
    public void onAccountFound(AccountScanManager.OnAccountFound event) {
        super.onAccountFound(event);
    }

    @Subscribe
    public void onPassphraseRequest(AccountScanManager.OnPassphraseRequest event) {
        super.onPassphraseRequest(event);
    }
}
