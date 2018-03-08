package com.mycelium.wallet.extsig.common.activity;

import android.app.Activity;
import android.app.Fragment;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import com.google.common.collect.Iterables;
import com.mrd.bitlib.crypto.HdKeyNode;
import com.mrd.bitlib.model.hdpath.HdKeyPath;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.extsig.MasterSeedPasswordSetter;
import com.mycelium.wallet.extsig.AbstractAccountScanManager;
import com.mycelium.wallet.widget.MasterseedPasswordDialog;
import com.mycelium.wapi.wallet.AccountScanManager;
import com.mycelium.wapi.model.Balance;
import com.mycelium.wapi.wallet.SyncMode;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.WalletManager;
import com.mycelium.wapi.wallet.bip44.Bip44Account;
import com.squareup.otto.Subscribe;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public abstract class HdAccountSelectorActivity extends Activity implements MasterSeedPasswordSetter {
    protected final static int REQUEST_SEND = 1;
    public static final String PASSPHRASE_FRAGMENT_TAG = "passphrase";
    protected ArrayList<HdAccountWrapper> accounts = new ArrayList<>();
    protected AccountsAdapter accountsAdapter;
    protected AbstractAccountScanManager masterSeedScanManager;

    private ListView lvAccounts;
    protected TextView txtStatus;

    protected abstract AbstractAccountScanManager initMasterseedManager();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setView();

        lvAccounts = findViewById(R.id.lvAccounts);
        txtStatus = findViewById(R.id.txtStatus);

        accountsAdapter = new AccountsAdapter(this, R.id.lvAccounts, accounts);
        lvAccounts.setAdapter(accountsAdapter);
        lvAccounts.setOnItemClickListener(accountClickListener());

        masterSeedScanManager = initMasterseedManager();

        startBackgroundScan();
        updateUi();
    }

    protected void startBackgroundScan() {
        masterSeedScanManager.startBackgroundAccountScan(new AccountScanManager.AccountCallback() {
            @Override
            public UUID checkForTransactions(AbstractAccountScanManager.HdKeyNodeWrapper account) {
                MbwManager mbwManager = MbwManager.getInstance(getApplicationContext());
                WalletManager walletManager = mbwManager.getWalletManager();

                UUID id = masterSeedScanManager.createOnTheFlyAccount(
                        account.accountRoot,
                        walletManager,
                        account.keyPath.getLastIndex());

                Bip44Account tempAccount = (Bip44Account) walletManager.getAccount(id);
                tempAccount.doSynchronization(SyncMode.NORMAL_WITHOUT_TX_LOOKUP);
                if (tempAccount.hasHadActivity()) {
                    return id;
                } else {
                    tempAccount.dropCachedData();
                    return null;
                }
            }
        });
    }

    abstract protected AdapterView.OnItemClickListener accountClickListener();

    abstract protected void setView();

    @Override
    protected void onResume() {
        super.onResume();
        MbwManager.getInstance(this).getEventBus().register(this);
    }

    @Override
    protected void onPause() {
        MbwManager.getInstance(getApplicationContext()).getEventBus().unregister(this);
        super.onPause();
    }

    @Override
    public void finish() {
        super.finish();
        masterSeedScanManager.stopBackgroundAccountScan();
    }

    protected void updateUi() {
        if (masterSeedScanManager.currentAccountState == AccountScanManager.AccountStatus.scanning) {
            findViewById(R.id.llStatus).setVisibility(View.VISIBLE);
            if (accounts.size() > 0) {
                txtStatus.setText(String.format(getString(R.string.account_found), Iterables.getLast(accounts).name));
                findViewById(R.id.llSelectAccount).setVisibility(View.VISIBLE);
            }
        } else if (masterSeedScanManager.currentAccountState == AccountScanManager.AccountStatus.done) {
            // DONE
            findViewById(R.id.llStatus).setVisibility(View.GONE);
            findViewById(R.id.llSelectAccount).setVisibility(View.VISIBLE);
            if (accounts.size() == 0) {
                // no accounts found
                findViewById(R.id.tvNoAccounts).setVisibility(View.VISIBLE);
                findViewById(R.id.lvAccounts).setVisibility(View.GONE);
            } else {
                findViewById(R.id.tvNoAccounts).setVisibility(View.GONE);
                findViewById(R.id.lvAccounts).setVisibility(View.VISIBLE);
            }
        }

        accountsAdapter.notifyDataSetChanged();
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        clearTempData();
    }

    protected void clearTempData() {
        // remove all account-data from the tempWalletManager, to improve privacy
        MbwManager.getInstance(this).forgetColdStorageWalletManager();
        masterSeedScanManager.forgetAccounts();
    }

    @Override
    public void setPassphrase(String passphrase) {
        masterSeedScanManager.setPassphrase(passphrase);

        if (passphrase == null) {
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

    protected class HdAccountWrapper implements Serializable {
        public UUID id;
        public HdKeyPath accountHdKeyPath;
        public HdKeyNode xPub;
        public String name;

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            HdAccountWrapper that = (HdAccountWrapper) o;

            if (id != null ? !id.equals(that.id) : that.id != null) return false;

            return true;
        }

        @Override
        public int hashCode() {
            return id != null ? id.hashCode() : 0;
        }
    }

    protected class AccountsAdapter extends ArrayAdapter<HdAccountWrapper> {

        private AccountsAdapter(Context context, int resource, List<HdAccountWrapper> objects) {
            super(context, resource, objects);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            View row;
            if (convertView == null) {
                row = inflater.inflate(R.layout.record_row, parent, false);
            } else {
                row = convertView;
            }

            HdAccountWrapper account = getItem(position);
            ((TextView) row.findViewById(R.id.tvLabel)).setText(account.name);
            WalletAccount walletAccount = MbwManager.getInstance(getContext()).getWalletManager().getAccount(account.id);
            Balance balance = walletAccount.getBalance();
            String balanceString = MbwManager.getInstance(getContext()).getBtcValueString(balance.confirmed + balance.pendingChange);

            if (balance.getSendingBalance() > 0) {
                balanceString += " " + String.format(getString(R.string.account_balance_sending_amount), MbwManager.getInstance(getContext()).getBtcValueString(balance.getSendingBalance()));
            }
            Drawable drawableForAccount = Utils.getDrawableForAccount(walletAccount, true, getResources());

            ((TextView) row.findViewById(R.id.tvBalance)).setText(balanceString);
            ((TextView) row.findViewById(R.id.tvAddress)).setVisibility(View.GONE);
            ((ImageView) row.findViewById(R.id.ivIcon)).setImageDrawable(drawableForAccount);

            ((TextView) row.findViewById(R.id.tvBackupMissingWarning)).setVisibility(View.GONE);

            return row;
        }
    }


    @Subscribe
    public void onScanError(AccountScanManager.OnScanError event) {
        Utils.showSimpleMessageDialog(this, event.errorMessage);
    }


    @Subscribe
    public void onStatusChanged(AccountScanManager.OnStatusChanged event) {
        updateUi();
    }

    @Subscribe
    public void onAccountFound(AccountScanManager.OnAccountFound event) {
        HdAccountWrapper acc = new HdAccountWrapper();
        acc.id = event.account.accountId;
        acc.accountHdKeyPath = event.account.keyPath;
        if (event.account.keyPath.equals(HdKeyPath.BIP32_ROOT)) {
            acc.name = getString(R.string.bip32_root_account);
        } else {
            acc.name = String.format(getString(R.string.account_number), event.account.keyPath.getLastIndex() + 1);
        }
        acc.xPub = event.account.accountRoot;
        if (!accounts.contains(acc)) {
            accountsAdapter.add(acc);
            updateUi();
        }
    }

    @Subscribe
    public void onPassphraseRequest(AccountScanManager.OnPassphraseRequest event) {
        MasterseedPasswordDialog pwd = new MasterseedPasswordDialog();
        pwd.show(getFragmentManager(), PASSPHRASE_FRAGMENT_TAG);
    }

}

