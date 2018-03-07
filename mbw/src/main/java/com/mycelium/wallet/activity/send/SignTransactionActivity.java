package com.mycelium.wallet.activity.send;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Window;
import com.google.common.base.Preconditions;
import com.mrd.bitlib.StandardTransactionBuilder.UnsignedTransaction;
import com.mrd.bitlib.model.Transaction;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.extsig.trezor.activity.TrezorSignTransactionActivity;
import com.mycelium.wapi.wallet.AesKeyCipher;
import com.mycelium.wapi.wallet.KeyCipher;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.bip44.Bip44AccountContext;
import com.mycelium.wapi.wallet.bip44.Bip44AccountExternalSignature;

import java.util.UUID;

public class SignTransactionActivity extends Activity {
   protected MbwManager _mbwManager;
   protected WalletAccount _account;
   protected boolean _isColdStorage;
   protected UnsignedTransaction _unsigned;
   private Transaction _transaction;
   private AsyncTask<Void, Integer, Transaction> _signingTask;
   private AsyncTask<Void, Integer, Transaction> signingTask;

   public static void callMe(Activity currentActivity, UUID account, boolean isColdStorage, UnsignedTransaction unsigned, int requestCode) {
      currentActivity.startActivityForResult(getIntent(currentActivity, account, isColdStorage, unsigned), requestCode);
   }

   public static Intent getIntent(Activity currentActivity, UUID account, boolean isColdStorage, UnsignedTransaction unsigned) {
      WalletAccount walletAccount = MbwManager.getInstance(currentActivity).getWalletManager().getAccount(account);

      Class targetClass;
      if (walletAccount instanceof Bip44AccountExternalSignature) {
         final int bip44AccountType = ((Bip44AccountExternalSignature) walletAccount).getBIP44AccountType();
         switch (bip44AccountType) {
//            case (Bip44AccountContext.ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_LEDGER):
//               targetClass = LedgerSignTransactionActivity.class;
//               break;
//            case (Bip44AccountContext.ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_KEEPKEY):
//               targetClass = KeepKeySignTransactionActivity.class;
//               break;
            case (Bip44AccountContext.ACCOUNT_TYPE_UNRELATED_X_PUB_EXTERNAL_SIG_TREZOR):
               targetClass = TrezorSignTransactionActivity.class;
               break;
            default:
               throw new RuntimeException("Unknown ExtSig Account type " + bip44AccountType);
         }
      } else {
         targetClass = SignTransactionActivity.class;
      }
      Preconditions.checkNotNull(account);

      return new Intent(currentActivity, targetClass)
              .putExtra("account", account)
              .putExtra("isColdStorage", isColdStorage)
              .putExtra("unsigned", unsigned);
   }

   @Override
   public void onCreate(Bundle savedInstanceState) {
      this.requestWindowFeature(Window.FEATURE_NO_TITLE);
      super.onCreate(savedInstanceState);
      setView();
      _mbwManager = MbwManager.getInstance(getApplication());
      // Get intent parameters
      UUID accountId = Preconditions.checkNotNull((UUID) getIntent().getSerializableExtra("account"));
      _isColdStorage = getIntent().getBooleanExtra("isColdStorage", false);
      _account = Preconditions.checkNotNull(_mbwManager.getWalletManager().getAccount(accountId));
      _unsigned = Preconditions.checkNotNull((UnsignedTransaction) getIntent().getSerializableExtra("unsigned"));

      // Load state
      if (savedInstanceState != null) {
         // May be null
         _transaction = (Transaction) savedInstanceState.getSerializable("transaction");
      }
   }

   protected void setView() {
      setContentView(R.layout.sign_transaction_activity);
   }

   @Override
   protected void onSaveInstanceState(Bundle outState) {
      if (_transaction != null) {
         outState.putSerializable("transaction", _transaction);
      }
      super.onSaveInstanceState(outState);
   }

   @Override
   protected void onResume() {
      if (_signingTask == null) {
         _signingTask = startSigningTask();
      }
      super.onResume();
   }

   protected AsyncTask<Void, Integer, Transaction> startSigningTask() {
      cancelSigningTask();
      // Sign transaction in the background
      signingTask = new AsyncTask<Void, Integer, Transaction>() {
         @Override
         protected Transaction doInBackground(Void... args) {
            try {
               return _account.signTransaction(_unsigned, AesKeyCipher.defaultKeyCipher());
            } catch (KeyCipher.InvalidKeyCipher invalidKeyCipher) {
               throw new RuntimeException(invalidKeyCipher);
            }
         }

         @Override
         protected void onPostExecute(Transaction transaction) {
            if (transaction != null) {
               Intent ret = new Intent();
               ret.putExtra("signedTx", transaction);
               setResult(RESULT_OK, ret);
               SignTransactionActivity.this.finish();
            } else {
               setResult(RESULT_CANCELED);
            }
         }
      };
      signingTask.execute();
      return signingTask;
   }

   protected void cancelSigningTask(){
      if (signingTask != null){
         signingTask.cancel(true);
      }
   }

   @Override
   protected void onPause() {
      super.onPause();
   }

   @Override
   protected void onDestroy() {
      if (_signingTask != null){
         _signingTask.cancel(true);
      }
      super.onDestroy();
   }
}
