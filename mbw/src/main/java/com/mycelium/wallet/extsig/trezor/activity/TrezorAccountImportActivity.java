package com.mycelium.wallet.extsig.trezor.activity;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.widget.ImageView;
import android.widget.TextView;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.activity.util.AbstractAccountScanManager;
import com.mycelium.wallet.extsig.common.ExternalSignatureDeviceManager;
import com.mycelium.wallet.extsig.common.activity.ExtSigAccountImportActivity;
import com.mycelium.wapi.wallet.AccountScanManager;
import com.squareup.otto.Subscribe;


public class TrezorAccountImportActivity extends ExtSigAccountImportActivity {
   public static void callMe(Activity currentActivity, int requestCode) {
      Intent intent = new Intent(currentActivity, TrezorAccountImportActivity.class);
      currentActivity.startActivityForResult(intent, requestCode);
   }

   @Override
   protected AbstractAccountScanManager initMasterseedManager() {
      return MbwManager.getInstance(this).getTrezorManager();
   }

   @Override
   protected void setView() {
      super.setView();
      ((ImageView)findViewById(R.id.ivConnectExtSig)).setImageResource(R.drawable.connect_trezor);
      ((TextView)findViewById(R.id.tvDeviceType)).setText(R.string.trezor_name);
   }

   @NonNull
   @Override
   protected String getFirmwareUpdateDescription() {
      return getString(R.string.trezor_new_firmware_description);
   }


   // Otto.EventBus does not traverse class hierarchy to find subscribers
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
