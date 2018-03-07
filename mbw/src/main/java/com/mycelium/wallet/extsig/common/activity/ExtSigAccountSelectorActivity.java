package com.mycelium.wallet.extsig.common.activity;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.support.annotation.NonNull;
import android.view.*;
import android.widget.*;
import com.google.common.base.Strings;
import com.mycelium.wallet.*;
import com.mycelium.wallet.activity.HdAccountSelectorActivity;
import com.mycelium.wallet.activity.util.Pin;
import com.mycelium.wallet.extsig.common.ExternalSignatureDeviceManager;
import com.mycelium.wapi.wallet.AccountScanManager;
import com.mycelium.wallet.activity.util.MasterSeedPasswordSetter;
import com.squareup.otto.Subscribe;

public abstract class ExtSigAccountSelectorActivity extends HdAccountSelectorActivity implements MasterSeedPasswordSetter {

   @Override
   protected void onStart() {
      super.onStart();
      updateUi();
   }

   abstract protected AdapterView.OnItemClickListener accountClickListener();
   abstract protected void setView();


   @Override
   public void finish() {
      super.finish();
      masterSeedScanManager.stopBackgroundAccountScan();
   }

   @SuppressLint("DefaultLocale") // It's only for display.
   @Override
   protected void updateUi() {

      if (masterSeedScanManager.currentState == ExternalSignatureDeviceManager.Status.readyToScan) {
         findViewById(R.id.tvWaitForExtSig).setVisibility(View.GONE);
         findViewById(R.id.ivConnectExtSig).setVisibility(View.GONE);
         txtStatus.setText(getString(R.string.ext_sig_scanning_status));
      }else{
         super.updateUi();
      }

      if (masterSeedScanManager.currentAccountState == ExternalSignatureDeviceManager.AccountStatus.scanning) {
         findViewById(R.id.llStatus).setVisibility(View.VISIBLE);
         if (accounts.size()>0) {
            super.updateUi();
         }else{
            txtStatus.setText(getString(R.string.ext_sig_scanning_status));
         }

      }else if (masterSeedScanManager.currentAccountState == AccountScanManager.AccountStatus.done) {
         // DONE
         findViewById(R.id.llStatus).setVisibility(View.GONE);
         findViewById(R.id.llSelectAccount).setVisibility(View.VISIBLE);
         if (accounts.size()==0) {
            // no accounts found
            findViewById(R.id.tvNoAccounts).setVisibility(View.VISIBLE);
            findViewById(R.id.lvAccounts).setVisibility(View.GONE);
         } else {
            findViewById(R.id.tvNoAccounts).setVisibility(View.GONE);
            findViewById(R.id.lvAccounts).setVisibility(View.VISIBLE);
         }

         // Show the label and version of the connected Trezor
         findViewById(R.id.llExtSigInfo).setVisibility(View.VISIBLE);
         final ExternalSignatureDeviceManager extSigDevice = (ExternalSignatureDeviceManager) masterSeedScanManager;

         if (extSigDevice.getFeatures() != null && !Strings.isNullOrEmpty(extSigDevice.getFeatures().getLabel())) {
            ((TextView) findViewById(R.id.tvExtSigName)).setText(extSigDevice.getFeatures().getLabel());
         }else {
            ((TextView) findViewById(R.id.tvExtSigName)).setText(getString(R.string.ext_sig_unnamed));
         }

         String version;
         TextView tvTrezorSerial = findViewById(R.id.tvExtSigSerial);
         if (extSigDevice.isMostRecentVersion()) {
            if (extSigDevice.getFeatures() != null) {
               version = String.format("%s, V%d.%d.%d",
                     extSigDevice.getFeatures().getDeviceId(),
                     extSigDevice.getFeatures().getMajorVersion(),
                     extSigDevice.getFeatures().getMinorVersion(),
                     extSigDevice.getFeatures().getPatchVersion());
            } else {
               version = "";
            }
         }else{
            version = getString(R.string.ext_sig_new_firmware);
            tvTrezorSerial.setTextColor(getResources().getColor(R.color.semidarkgreen));
            tvTrezorSerial.setOnClickListener(new View.OnClickListener() {
               @Override
               public void onClick(View v) {
                  if (extSigDevice.hasExternalConfigurationTool()) {
                     extSigDevice.openExternalConfigurationTool(ExtSigAccountSelectorActivity.this, getString(R.string.external_app_needed),  null);
                  } else {
                     Utils.showSimpleMessageDialog(ExtSigAccountSelectorActivity.this, getFirmwareUpdateDescription());
                  }
               }
            });
         }
         tvTrezorSerial.setText(version);
      }

      accountsAdapter.notifyDataSetChanged();
   }

   @NonNull
   abstract protected String getFirmwareUpdateDescription();

   @Override
   public void setPassphrase(String passphrase){
      masterSeedScanManager.setPassphrase(passphrase);
      if (passphrase == null){
         // user choose cancel -> leave this activity
         finish();
      } else {
         // close the dialog fragment
         Fragment fragPassphrase = getFragmentManager().findFragmentByTag(PASSPHRASE_FRAGMENT_TAG);
         if (fragPassphrase != null) {
            getFragmentManager().beginTransaction().remove(fragPassphrase).commit();
         }
      }
   }


   @Subscribe
   public void onPinMatrixRequest(ExternalSignatureDeviceManager.OnPinMatrixRequest event){
      TrezorPinDialog pin = new TrezorPinDialog(ExtSigAccountSelectorActivity.this, true);
      pin.setOnPinValid(new PinDialog.OnPinEntered() {
         @Override
         public void pinEntered(PinDialog dialog, Pin pin) {
            ((ExternalSignatureDeviceManager) masterSeedScanManager).enterPin(pin.getPin());
            dialog.dismiss();
         }
      });
      pin.show();

      // update the UI, as the state might have changed
      updateUi();
   }


   @Subscribe
   public void onScanError(final AccountScanManager.OnScanError event){
      ExternalSignatureDeviceManager extSigDevice = (ExternalSignatureDeviceManager) masterSeedScanManager;
      // see if we know how to init that device
      if (event.errorType == AccountScanManager.OnScanError.ErrorType.NOT_INITIALIZED &&
              extSigDevice.hasExternalConfigurationTool()){
         extSigDevice.openExternalConfigurationTool(this, getString(R.string.ext_sig_device_not_initialized), new Runnable() {
            @Override
            public void run() {
               // close this activity and let the user restart it after the tool ran
               ExtSigAccountSelectorActivity.this.finish();
            }
         });
      } else {
         super.onScanError(event);
      }
   }

   @Subscribe
   public void onStatusChanged(AccountScanManager.OnStatusChanged event){
      super.onStatusChanged(event);
   }

   @Subscribe
   public void onAccountFound(AccountScanManager.OnAccountFound event){
      super.onAccountFound(event);
   }

   @Subscribe
   public void onPassphraseRequest(AccountScanManager.OnPassphraseRequest event){
      super.onPassphraseRequest(event);
   }

}


