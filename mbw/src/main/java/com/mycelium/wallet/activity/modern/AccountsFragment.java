package com.mycelium.wallet.activity.modern;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.support.v7.view.ActionMode.Callback;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.mrd.bitlib.model.Address;
import com.mycelium.wallet.ExchangeRateManager;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.activity.AddAccountActivity;
import com.mycelium.wallet.activity.AddAdvancedAccountActivity;
import com.mycelium.wallet.activity.modern.adapter.AccountListAdapter;
import com.mycelium.wallet.activity.modern.adapter.DividerItemDecoration;
import com.mycelium.wallet.activity.util.EnterAddressLabelUtil;
import com.mycelium.wallet.event.AccountChanged;
import com.mycelium.wallet.event.BalanceChanged;
import com.mycelium.wallet.event.ExtraAccountsChanged;
import com.mycelium.wallet.event.ReceivingAddressChanged;
import com.mycelium.wallet.event.SyncStarted;
import com.mycelium.wallet.event.SyncStopped;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wapi.model.Balance;
import com.mycelium.wapi.wallet.AesKeyCipher;
import com.mycelium.wapi.wallet.ExportableAccount;
import com.mycelium.wapi.wallet.KeyCipher;
import com.mycelium.wapi.wallet.SyncMode;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.WalletManager;
import com.mycelium.wapi.wallet.bip44.Bip44Account;
import com.mycelium.wapi.wallet.currency.CurrencyBasedBalance;
import com.mycelium.wapi.wallet.currency.CurrencyValue;
import com.mycelium.wapi.wallet.single.SingleAddressAccount;
import com.squareup.otto.Subscribe;

import java.util.List;
import java.util.UUID;

public class AccountsFragment extends Fragment {
    public static final int ADD_RECORD_RESULT_CODE = 0;

    public static final String TAG = "AccountsFragment";

    private WalletManager walletManager;

    private MetadataStorage _storage;
    private MbwManager _mbwManager;
    private Toaster _toaster;
    private ProgressDialog _progress;
    private RecyclerView rvRecords;
    private AccountListAdapter accountListAdapter;

    /**
     * Called when the activity is first created.
     */
    @SuppressWarnings("deprecation")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.records_activity, container, false);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        rvRecords = view.findViewById(R.id.rvRecords);
        rvRecords.setLayoutManager(new LinearLayoutManager(getActivity(), LinearLayoutManager.VERTICAL, false));
        accountListAdapter = new AccountListAdapter(getActivity(), _mbwManager);
        rvRecords.setAdapter(accountListAdapter);
        rvRecords.addItemDecoration(new DividerItemDecoration(getResources().getDrawable(R.drawable.divider_account_list)));
        rvRecords.setHasFixedSize(true);
        accountListAdapter.setItemClickListener(recordAddressClickListener);
    }

    @Override
    public void onAttach(Activity activity) {
        _mbwManager = MbwManager.getInstance(activity);
        walletManager = _mbwManager.getWalletManager();
        _storage = _mbwManager.getMetadataStorage();
        _toaster = new Toaster(this);
        super.onAttach(activity);
    }

    @Override
    public void onResume() {
        _mbwManager.getEventBus().register(this);
        update();
        _progress = new ProgressDialog(getActivity());
        super.onResume();
    }

    @Override
    public void onPause() {
        _progress.dismiss();
        _mbwManager.getEventBus().unregister(this);
        super.onPause();
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (!isVisibleToUser) {
            finishCurrentActionMode();
        }
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        ActivityCompat.invalidateOptionsMenu(getActivity());
        if (requestCode == ADD_RECORD_RESULT_CODE && resultCode == Activity.RESULT_OK) {
            UUID accountid = (UUID) intent.getSerializableExtra(AddAccountActivity.RESULT_KEY);
            //check whether the account is active - we might have scanned the priv key for an archived watchonly
            WalletAccount account = _mbwManager.getWalletManager().getAccount(accountid);
            if (account.isActive()) {
                _mbwManager.setSelectedAccount(accountid);
            }
            accountListAdapter.setFocusedAccount(account);
            update();
        } else if (requestCode == ADD_RECORD_RESULT_CODE && resultCode == AddAdvancedAccountActivity.RESULT_MSG) {
            new AlertDialog.Builder(getActivity())
                    .setMessage(intent.getStringExtra(AddAccountActivity.RESULT_MSG))
                    .setPositiveButton(R.string.button_ok, null)
                    .create()
                    .show();
        } else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    private void deleteAccount(final WalletAccount accountToDelete) {
        Preconditions.checkNotNull(accountToDelete);

        final View checkBoxView = View.inflate(getActivity(), R.layout.delkey_checkbox, null);
        final CheckBox keepAddrCheckbox = checkBoxView.findViewById(R.id.checkbox);
        keepAddrCheckbox.setText(getString(R.string.keep_account_address));
        keepAddrCheckbox.setChecked(false);

        final AlertDialog.Builder deleteDialog = new AlertDialog.Builder(getActivity());
        deleteDialog.setTitle(R.string.delete_account_title);
        final WalletAccount account = _mbwManager.getSelectedAccount();
        deleteDialog.setMessage(R.string.delete_account_message);

        // add checkbox only for SingleAddressAccounts and only if a private key is present
        final boolean hasPrivateData = (accountToDelete instanceof ExportableAccount
                && ((ExportableAccount) accountToDelete).getExportData(AesKeyCipher.defaultKeyCipher()).privateData.isPresent());

        if (accountToDelete instanceof SingleAddressAccount && hasPrivateData) {
            deleteDialog.setView(checkBoxView);
        }

        if (accountToDelete.canSpend()) {
            Log.d(TAG, "Preparing to delete a colu account.");
            deleteDialog.setView(checkBoxView);
        }

        deleteDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface arg0, int arg1) {
                Log.d(TAG, "Entering onClick delete");
                if (hasPrivateData) {
                    Long satoshis = getPotentialBalance(accountToDelete);
                    AlertDialog.Builder confirmDeleteDialog = new AlertDialog.Builder(getActivity());
                    confirmDeleteDialog.setTitle(R.string.confirm_delete_pk_title);

                    // Set the message. There are four combinations, with and without label, with and without BTC amount.
                    String label = _mbwManager.getMetadataStorage().getLabelByAccount(accountToDelete.getId());
                    int labelCount = 1;
                    String message;

                    // For active accounts we check whether there is money on them before deleting. we don't know if there
                    // is money on archived accounts
                    Optional<Address> receivingAddress = accountToDelete.getReceivingAddress();
                    if (accountToDelete.isActive() && satoshis != null && satoshis > 0) {
                        if (label != null && label.length() != 0) {
                            String address;
                            if (receivingAddress.isPresent()) {
                                address = receivingAddress.get().toMultiLineString();
                            } else {
                                address = "";
                            }
                            message = getString(R.string.confirm_delete_pk_with_balance_with_label
                                    , getResources().getQuantityString(R.plurals.account_label, labelCount, label)
                                    , address, _mbwManager.getBtcValueString(satoshis)
                            );
                        } else {
                            message = getString(
                                    R.string.confirm_delete_pk_with_balance,
                                    receivingAddress.isPresent() ? receivingAddress.get().toMultiLineString() : "",
                                    _mbwManager.getBtcValueString(satoshis)

                            );
                        }
                    } else {
                        if (label != null && label.length() != 0) {
                            message = getString(R.string.confirm_delete_pk_without_balance_with_label
                                    , getResources().getQuantityString(R.plurals.account_label, labelCount, label)
                                    , receivingAddress.isPresent() ? receivingAddress.get().toMultiLineString() : ""
                            );
                        } else {
                            message = getString(
                                    R.string.confirm_delete_pk_without_balance,
                                    receivingAddress.isPresent() ? receivingAddress.get().toMultiLineString() : ""
                            );
                        }
                    }
                    confirmDeleteDialog.setMessage(message);

                    confirmDeleteDialog.setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface arg0, int arg1) {
                            Log.d(TAG, "In deleteFragment onClick");
                            if (keepAddrCheckbox.isChecked() && accountToDelete instanceof SingleAddressAccount) {
                                try {
                                    //Check if this SingleAddress account is related with ColuAccount
                                    ((SingleAddressAccount) accountToDelete).forgetPrivateKey(AesKeyCipher.defaultKeyCipher());
                                    _toaster.toast(R.string.private_key_deleted, false);
                                } catch (KeyCipher.InvalidKeyCipher e) {
                                    throw new RuntimeException(e);
                                }
                            } else {
                                //Check if this SingleAddress account is related with ColuAccount
                                try {
                                    walletManager.deleteUnrelatedAccount(accountToDelete.getId(), AesKeyCipher.defaultKeyCipher());
                                    _storage.deleteAccountMetadata(accountToDelete.getId());
                                } catch (KeyCipher.InvalidKeyCipher e) {
                                    throw new RuntimeException(e);
                                }
                                _mbwManager.setSelectedAccount(_mbwManager.getWalletManager().getActiveAccounts().get(0).getId());
                                _toaster.toast(R.string.account_deleted, false);
                                _mbwManager.getEventBus().post(new ExtraAccountsChanged());
                            }

                            finishCurrentActionMode();
                            _mbwManager.getEventBus().post(new AccountChanged(accountToDelete.getId()));
                        }
                    });
                    confirmDeleteDialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

                        public void onClick(DialogInterface arg0, int arg1) {
                        }
                    });
                    confirmDeleteDialog.show();
                } else {
                    try {
                        walletManager.deleteUnrelatedAccount(accountToDelete.getId(), AesKeyCipher.defaultKeyCipher());
                        _storage.deleteAccountMetadata(accountToDelete.getId());
                    } catch (KeyCipher.InvalidKeyCipher e) {
                        throw new RuntimeException(e);
                    }

                    finishCurrentActionMode();
                    _mbwManager.getEventBus().post(new AccountChanged(accountToDelete.getId()));
                    _mbwManager.getEventBus().post(new ExtraAccountsChanged());
                    _toaster.toast(R.string.account_deleted, false);
                }
            }

            private CurrencyValue getPotentialBalanceColu(WalletAccount account) {
                if (account.isArchived()) {
                    return null;
                } else {
                    CurrencyBasedBalance balance = account.getCurrencyBasedBalance();
                    ExchangeRateManager exchanger = _mbwManager.getExchangeRateManager();
                    return balance.confirmed;
                }
            }

            private Long getPotentialBalance(WalletAccount account) {
                if (account.isArchived()) {
                    return null;
                } else {
                    Balance balance = account.getBalance();
                    return balance.confirmed + balance.pendingChange + balance.pendingReceiving;
                }
            }

        });
        deleteDialog.setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {

            public void onClick(DialogInterface arg0, int arg1) {
            }
        });
        deleteDialog.show();

    }

    private void finishCurrentActionMode() {
        if (currentActionMode != null) {
            currentActionMode.finish();
        }
    }

    private void update() {
        if (!isAdded()) {
            return;
        }
        rvRecords.post(new Runnable() {
            @Override
            public void run() {
                accountListAdapter.updateData();
            }
        });

    }

    private ActionMode currentActionMode;

    private AccountListAdapter.ItemClickListener recordAddressClickListener = new AccountListAdapter.ItemClickListener() {
        @Override
        public void onItemClick(WalletAccount account) {
            // Check whether a new account was selected
            if (!_mbwManager.getSelectedAccount().equals(account) && account.isActive()) {
                _mbwManager.setSelectedAccount(account.getId());
            }
            toastSelectedAccountChanged(account);
            updateIncludingMenus();
        }
    };

    private void updateIncludingMenus() {
        WalletAccount account = accountListAdapter.getFocusedAccount();
        final List<Integer> menus = Lists.newArrayList();
        menus.add(R.menu.record_options_menu);

        ActionBarActivity parent = (ActionBarActivity) getActivity();
        Callback actionMode = new Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                for (Integer res : menus) {
                    actionMode.getMenuInflater().inflate(res, menu);
                }
                return true;
            }

            @Override
            public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                return true;
            }

            @Override
            public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                // If we are synchronizing, show "Synchronizing, please wait..." to avoid blocking behavior
                if (_mbwManager.getWalletManager().getState() == WalletManager.State.SYNCHRONIZING) {
                    _toaster.toast(R.string.synchronizing_please_wait, false);
                    return true;
                }
                int id = menuItem.getItemId();
                if (id == R.id.miSetLabel) {
                    setLabelOnAccount(accountListAdapter.getFocusedAccount(), "");
                    return true;
                } else if (id == R.id.miDeleteRecord) {
                    deleteSelected();
                    return true;
                } else if (id == R.id.miRescan) {
                    rescan();
                    return true;
                }
                return false;
            }

            @Override
            public void onDestroyActionMode(ActionMode actionMode) {
                currentActionMode = null;
                // Loose focus
                if (accountListAdapter.getFocusedAccount() != null) {
                    accountListAdapter.setFocusedAccount(null);
                    update();
                }
            }
        };
        currentActionMode = parent.startSupportActionMode(actionMode);
        accountListAdapter.setFocusedAccount(account);
        update();
    }

    /**
     * Show a message to the user explaining what it means to select a different
     * address.
     */
    private void toastSelectedAccountChanged(WalletAccount account) {
        if (account instanceof Bip44Account) {
            _toaster.toast(getString(R.string.selected_hd_info), true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // If we are synchronizing, show "Synchronizing, please wait..." to avoid blocking behavior
        if (_mbwManager.getWalletManager().getState() == WalletManager.State.SYNCHRONIZING) {
            _toaster.toast(R.string.synchronizing_please_wait, false);
            return true;
        }

        if (!isAdded()) {
            return true;
        }
        if (item.getItemId() == R.id.miAddRecord) {
            AddAccountActivity.callMe(this, ADD_RECORD_RESULT_CODE);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void setLabelOnAccount(final WalletAccount account, final String defaultName) {
        if (!AccountsFragment.this.isAdded()) {
            return;
        }
        EnterAddressLabelUtil.enterAccountLabel(getActivity(), account.getId(), defaultName, _storage);
    }

    private void deleteSelected() {
        if (!AccountsFragment.this.isAdded()) {
            return;
        }
        final WalletAccount _focusedAccount = accountListAdapter.getFocusedAccount();
        if (_focusedAccount.isActive() && _mbwManager.getWalletManager().getActiveAccounts().size() < 2) {
            _toaster.toast(R.string.keep_one_active, false);
            return;
        }
        if (!AccountsFragment.this.isAdded()) {
            return;
        }
        deleteAccount(_focusedAccount);
    }

    private void rescan() {
        if (!isAdded()) {
            return;
        }
        accountListAdapter.getFocusedAccount().dropCachedData();
        _mbwManager.getWalletManager().startSynchronization(SyncMode.FULL_SYNC_CURRENT_ACCOUNT_FORCED);
    }

    @Subscribe()
    public void onExtraAccountsChanged(ExtraAccountsChanged event) {
        update();
    }

    @Subscribe
    public void addressChanged(ReceivingAddressChanged event) {
        update();
    }

    @Subscribe
    public void balanceChanged(BalanceChanged event) {
        update();
    }

    @Subscribe
    public void syncStarted(SyncStarted event) {
    }

    @Subscribe
    public void syncStopped(SyncStopped event) {
        update();
    }

    @Subscribe
    public void accountChanged(AccountChanged event) {
        update();
    }

}
