package com.mycelium.wallet.extsig.common.activity;

import android.content.Intent;
import android.view.View;
import android.widget.AdapterView;
import android.widget.TextView;
import com.google.common.base.Optional;
import com.mrd.bitlib.crypto.HdKeyNode;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.extsig.common.ExternalSignatureDeviceManager;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wapi.wallet.AccountScanManager;
import com.squareup.otto.Subscribe;

import java.util.UUID;


public abstract class ExtSigAccountImportActivity extends ExtSigAccountSelectorActivity {
   @Override
   protected AdapterView.OnItemClickListener accountClickListener() {
      return new AdapterView.OnItemClickListener() {
         @Override
         public void onItemClick(AdapterView<?> adapterView, View view, int i, long l) {
            HdAccountWrapper item = (HdAccountWrapper) adapterView.getItemAtPosition(i);

            // create the new account and get the uuid of it
            MbwManager mbwManager = MbwManager.getInstance(ExtSigAccountImportActivity.this);

            UUID acc = mbwManager.getWalletManager()
                  .createExternalSignatureAccount(
                        item.xPub,
                        (ExternalSignatureDeviceManager) masterSeedScanManager,
                        item.accountHdKeyPath.getLastIndex()
                  );

            // Mark this account as backup warning ignored
            mbwManager.getMetadataStorage().setOtherAccountBackupState(acc, MetadataStorage.BackupState.IGNORED);

            Intent result = new Intent();
            result.putExtra("account", acc);
            setResult(RESULT_OK, result);
            finish();
         }
      };
   }

   @Override
   protected void updateUi() {
      super.updateUi();
      if (masterSeedScanManager.currentAccountState == AccountScanManager.AccountStatus.done) {
         findViewById(R.id.btNextAccount).setEnabled(true);
      } else {
         findViewById(R.id.btNextAccount).setEnabled(false);
      }
   }

   @Override
   protected void setView() {
      setContentView(R.layout.activity_instant_ext_sig);
      ((TextView) findViewById(R.id.tvCaption)).setText(getString(R.string.ext_sig_import_account_caption));
      ((TextView) findViewById(R.id.tvSelectAccount)).setText(getString(R.string.ext_sig_select_account_to_import));
      ((TextView) findViewById(R.id.btNextAccount)).setVisibility(View.VISIBLE);

      findViewById(R.id.btNextAccount).setOnClickListener(new View.OnClickListener() {
         @Override
         public void onClick(View view) {
            Utils.showSimpleMessageDialog(ExtSigAccountImportActivity.this, getString(R.string.ext_sig_next_unused_account_info), new Runnable() {
               @Override
               public void run() {

                  Optional<HdKeyNode> nextAccount = masterSeedScanManager.getNextUnusedAccount();

                  MbwManager mbwManager = MbwManager.getInstance(ExtSigAccountImportActivity.this);

                  if (nextAccount.isPresent()) {
                     UUID acc = mbwManager.getWalletManager()
                           .createExternalSignatureAccount(
                                 nextAccount.get(),
                                 (ExternalSignatureDeviceManager) masterSeedScanManager,
                                 nextAccount.get().getIndex()
                           );

                     mbwManager.getMetadataStorage().setOtherAccountBackupState(acc, MetadataStorage.BackupState.IGNORED);

                     Intent result = new Intent();
                     result.putExtra("account", acc);
                     setResult(RESULT_OK, result);
                     finish();
                  }

               }
            });
         }
      });
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
