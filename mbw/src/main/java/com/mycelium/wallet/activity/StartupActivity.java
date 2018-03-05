/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wallet.activity;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.Window;

import com.google.common.base.Preconditions;
import com.mrd.bitlib.crypto.Bip39;
import com.mrd.bitlib.crypto.InMemoryPrivateKey;
import com.mycelium.wallet.Constants;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.PinDialog;
import com.mycelium.wallet.R;
import com.mycelium.wallet.activity.modern.ModernMain;
import com.mycelium.wallet.activity.send.SendInitializationActivity;
import com.mycelium.wapi.wallet.AesKeyCipher;
import com.mycelium.wapi.wallet.KeyCipher;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.WalletManager;
import com.mycelium.wapi.wallet.bip44.Bip44Account;

import java.lang.ref.WeakReference;
import java.util.UUID;

public class StartupActivity extends Activity {
   private static final int MINIMUM_SPLASH_TIME = 500;
   private static final int REQUEST_FROM_URI = 2;
   private static final int IMPORT_WORDLIST = 0;

   private static final String URI_HOST_GLIDERA_REGISTRATION = "glideraRegistration";

   private MbwManager _mbwManager;
   private PinDialog _pinDialog;
   private ProgressDialog _progress;

   @Override
   protected void onCreate(Bundle savedInstanceState) {
      requestWindowFeature(Window.FEATURE_NO_TITLE);
      super.onCreate(savedInstanceState);
      _progress = new ProgressDialog(this);
      setContentView(R.layout.startup_activity);
      // Do slightly delayed initialization so we get a chance of displaying the
      // splash before doing heavy initialization
      new Handler().postDelayed(delayedInitialization, 200);
   }

   @Override
   public void onPause() {
      _progress.dismiss();
      if (_pinDialog != null) {
         _pinDialog.dismiss();
      }
      super.onPause();
   }

   private Runnable delayedInitialization = new Runnable() {
      @Override
      public void run() {
         long startTime = System.currentTimeMillis();
         _mbwManager = MbwManager.getInstance(StartupActivity.this.getApplication());

         //in case this is a fresh startup, import backup or create new seed
         if (!_mbwManager.getWalletManager(false).hasBip32MasterSeed()) {
            initMasterSeed();
            return;
         }

         // Calculate how much time we spent initializing, and do a delayed
         // finish so we display the splash a minimum amount of time
         long timeSpent = System.currentTimeMillis() - startTime;
         long remainingTime = MINIMUM_SPLASH_TIME - timeSpent;
         if (remainingTime < 0) {
            remainingTime = 0;
         }
         new Handler().postDelayed(delayedFinish, remainingTime);
      }
   };

   private void initMasterSeed() {
      new AlertDialog
              .Builder(this)
              .setCancelable(false)
              .setTitle(R.string.master_seed_configuration_title)
              .setMessage(getString(R.string.master_seed_configuration_description))
              .setNegativeButton(R.string.master_seed_restore_backup_button, new DialogInterface.OnClickListener() {
                 //import master seed from wordlist
                 public void onClick(DialogInterface arg0, int arg1) {
                    EnterWordListActivity.callMe(StartupActivity.this, IMPORT_WORDLIST);
                 }
              })
              .setPositiveButton(R.string.master_seed_create_new_button, new DialogInterface.OnClickListener() {
                 //configure new random seed
                 public void onClick(DialogInterface arg0, int arg1) {
                    startMasterSeedTask();
                 }
              })
              .show();
   }

   private void startMasterSeedTask() {
      _progress.setCancelable(false);
      _progress.setProgressStyle(ProgressDialog.STYLE_SPINNER);
      _progress.setMessage(getString(R.string.preparing_wallet_on_first_startup_info));
      _progress.show();
      new ConfigureSeedAsyncTask(new WeakReference<>(this)).execute();
   }

   private static class ConfigureSeedAsyncTask extends AsyncTask<Void, Integer, UUID> {
      private WeakReference<StartupActivity> startupActivity;

      ConfigureSeedAsyncTask(WeakReference<StartupActivity> startupActivity) {
         this.startupActivity = startupActivity;
      }

      @Override
      protected UUID doInBackground(Void... params) {
         StartupActivity activity = this.startupActivity.get();
         if(activity == null) {
            return null;
         }
         Bip39.MasterSeed masterSeed = Bip39.createRandomMasterSeed(activity._mbwManager.getRandomSource());
         try {
            WalletManager walletManager = activity._mbwManager.getWalletManager(false);
            walletManager.configureBip32MasterSeed(masterSeed, AesKeyCipher.defaultKeyCipher());
            return walletManager.createAdditionalBip44Account(AesKeyCipher.defaultKeyCipher());
         } catch (KeyCipher.InvalidKeyCipher e) {
            throw new RuntimeException(e);
         }
      }

      @Override
      protected void onPostExecute(UUID accountid) {
         StartupActivity activity = this.startupActivity.get();
         if(accountid == null || activity == null) {
            return;
         }
         activity._progress.dismiss();
         //set default label for the created HD account
         WalletAccount account = activity._mbwManager.getWalletManager(false).getAccount(accountid);
         String defaultName = activity.getString(R.string.account) + " " + (((Bip44Account) account).getAccountIndex() + 1);
         activity._mbwManager.getMetadataStorage().storeAccountLabel(accountid, defaultName);
         //finish initialization
         activity.delayedFinish.run();
      }
   }

   private Runnable delayedFinish = new Runnable() {
      @Override
      public void run() {
         if (_mbwManager.isUnlockPinRequired()) {

            // set a click handler to the background, so that
            // if the PIN-Pad closes, you can reopen it by touching the background
            getWindow().getDecorView().findViewById(android.R.id.content).setOnClickListener(new View.OnClickListener() {
               @Override
               public void onClick(View view) {
                  delayedFinish.run();
               }
            });

            Runnable start = new Runnable() {
               @Override
               public void run() {
                  _mbwManager.setStartUpPinUnlocked(true);
                  start();
               }
            };

            // set the pin dialog to not cancelable
            _pinDialog = _mbwManager.runPinProtectedFunction(StartupActivity.this, start, false);
         } else {
            start();
         }
      }

      private void start() {
         normalStartup();
      }
   };

   private void normalStartup() {
      // Normal startup, show the selected account in the BalanceActivity
      startActivity(new Intent(StartupActivity.this, ModernMain.class));
      finish();
   }

   @Override
   protected void onActivityResult(int requestCode, int resultCode, Intent data) {
      //todo make sure delayed init has finished (ev mit countdownlatch)
      switch (requestCode) {
         case IMPORT_WORDLIST:
            if (resultCode != RESULT_OK) {
               //user cancelled the import, so just ask what he wants again
               initMasterSeed();
               return;
            }
            //we have restored a backup
            UUID accountid = (UUID) data.getSerializableExtra(AddAccountActivity.RESULT_KEY);
            //set default label for the created HD account
            WalletAccount account = _mbwManager.getWalletManager(false).getAccount(accountid);
            String defaultName = getString(R.string.account) + " " + (((Bip44Account) account).getAccountIndex() + 1);
            _mbwManager.getMetadataStorage().storeAccountLabel(accountid, defaultName);
            //finish initialization
            delayedFinish.run();
            return;
         case StringHandlerActivity.IMPORT_ENCRYPTED_BIP38_PRIVATE_KEY_CODE:
            String content = data.getStringExtra("base58Key");
            if (content != null) {
               InMemoryPrivateKey key = InMemoryPrivateKey.fromBase58String(content, _mbwManager.getNetwork()).get();
               UUID onTheFlyAccount = MbwManager.getInstance(this).createOnTheFlyAccount(key);
               SendInitializationActivity.callMe(this, onTheFlyAccount, true);
               finish();
               return;
            }
         case REQUEST_FROM_URI:
            // double-check result data, in case some downstream code messes up.
            if (resultCode == RESULT_OK) {
               Bundle extras = Preconditions.checkNotNull(data.getExtras());
               for(String key: extras.keySet()) {
                  // make sure we only share TRANSACTION_HASH_INTENT_KEY with external caller
                  if(!key.equals(Constants.TRANSACTION_HASH_INTENT_KEY)) {
                     data.removeExtra(key);
                  }
               }
               // return the tx hash to our external caller, if he cares...
               setResult(RESULT_OK, data);
            } else {
               setResult(RESULT_CANCELED);
            }
            break;
         default:
            setResult(RESULT_CANCELED);
      }
      finish();
   }
}
