package com.mycelium.wallet.activity.main;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.support.v7.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.commonsware.cwac.endless.EndlessAdapter;
import com.google.common.base.Preconditions;
import com.mrd.bitlib.StandardTransactionBuilder.InsufficientFundsException;
import com.mrd.bitlib.StandardTransactionBuilder.UnableToBuildTransactionException;
import com.mrd.bitlib.StandardTransactionBuilder.UnsignedTransaction;
import com.mrd.bitlib.model.Address;
import com.mrd.bitlib.model.Transaction;
import com.mrd.bitlib.util.HexUtils;
import com.mrd.bitlib.util.Sha256Hash;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.MinerFee;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.activity.TransactionDetailsActivity;
import com.mycelium.wallet.activity.modern.Toaster;
import com.mycelium.wallet.activity.send.BroadcastTransactionActivity;
import com.mycelium.wallet.activity.send.SignTransactionActivity;
import com.mycelium.wallet.activity.util.EnterAddressLabelUtil;
import com.mycelium.wallet.event.AddressBookChanged;
import com.mycelium.wallet.event.ExchangeRatesRefreshed;
import com.mycelium.wallet.event.SelectedCurrencyChanged;
import com.mycelium.wallet.event.SyncStopped;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wapi.model.TransactionDetails;
import com.mycelium.wapi.model.TransactionSummary;
import com.mycelium.wapi.wallet.AbstractAccount;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.WalletManager;
import com.mycelium.wapi.wallet.currency.CurrencyValue;
import com.mycelium.wapi.wallet.currency.ExactBitcoinValue;
import com.squareup.otto.Subscribe;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static android.app.Activity.RESULT_OK;
import static android.widget.Toast.LENGTH_LONG;
import static android.widget.Toast.makeText;
import static com.google.common.base.Preconditions.checkNotNull;

public class TransactionHistoryFragment extends Fragment {

    private static final int SIGN_TRANSACTION_REQUEST_CODE = 0x12f4;
    private static final int BROADCAST_REQUEST_CODE = SIGN_TRANSACTION_REQUEST_CODE + 1;

    private MbwManager mbwManager;
    private MetadataStorage metadataStorage;
    private View root;
    private ActionMode currentActionMode;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        root = inflater.inflate(R.layout.main_transaction_history_view, container, false);

        root.findViewById(R.id.btRescan).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                mbwManager.getSelectedAccount().dropCachedData();
                mbwManager.getWalletManager().startSynchronization();
            }
        });
        return root;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        setHasOptionsMenu(true);
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        mbwManager = MbwManager.getInstance(activity);
        metadataStorage = mbwManager.getMetadataStorage();
    }

    @Override
    public void onResume() {
        mbwManager.getEventBus().register(this);
        if (mbwManager.getWalletManager().getState() == WalletManager.State.READY) {
            updateTransactionHistory();
        }
        super.onResume();
    }

    @Override
    public void onPause() {
        mbwManager.getEventBus().unregister(this);
        super.onPause();
    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {
        if (requestCode == SIGN_TRANSACTION_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                Transaction transaction = (Transaction) Preconditions.checkNotNull(intent.getSerializableExtra("signedTx"));
                BroadcastTransactionActivity.callMe(getActivity(), mbwManager.getSelectedAccount().getId(), false, transaction, "CPFP", null, BROADCAST_REQUEST_CODE);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    @Subscribe
    public void syncStopped(SyncStopped event) {
        updateTransactionHistory();
    }

    @Subscribe
    public void exchangeRateChanged(ExchangeRatesRefreshed event) {
        refreshList();
    }

    private void refreshList() {
        ((ListView) root.findViewById(R.id.lvTransactionHistory)).invalidateViews();
    }

    @Subscribe
    public void fiatCurrencyChanged(SelectedCurrencyChanged event) {
        refreshList();
    }

    private void doShowDetails(TransactionSummary selected) {
        if (selected == null) {
            return;
        }
        // Open transaction details
        Intent intent = new Intent(getActivity(), TransactionDetailsActivity.class);
        intent.putExtra("transaction", selected.txid);
        startActivity(intent);
    }

    @SuppressWarnings("unchecked")
    private void updateTransactionHistory() {
        if (!isAdded()) {
            return;
        }
        WalletAccount account = mbwManager.getSelectedAccount();
        if (account.isArchived()) {
            return;
        }
        List<TransactionSummary> history = account.getTransactionHistory(0, 20);
        Collections.sort(history);
        Collections.reverse(history);
        if (history.isEmpty()) {
            root.findViewById(R.id.llNoRecords).setVisibility(View.VISIBLE);
            root.findViewById(R.id.lvTransactionHistory).setVisibility(View.GONE);
        } else {
            root.findViewById(R.id.llNoRecords).setVisibility(View.GONE);
            root.findViewById(R.id.lvTransactionHistory).setVisibility(View.VISIBLE);
            Wrapper wrapper = new Wrapper(getActivity(), history);
            ((ListView) root.findViewById(R.id.lvTransactionHistory)).setAdapter(wrapper);
            refreshList();
        }
    }

    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        if (!isVisibleToUser) {
            finishActionMode();
        }
    }

    private void finishActionMode() {
        if (currentActionMode != null) {
            currentActionMode.finish();
        }
    }

    private class TransactionHistoryAdapter extends TransactionArrayAdapter {

        TransactionHistoryAdapter(Context context, List<TransactionSummary> transactions) {
            super(context, transactions, TransactionHistoryFragment.this);
        }

        @NonNull
        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            View rowView = super.getView(position, convertView, parent);
            if (!isAdded()) {
                return rowView;
            }
            final TransactionSummary record = checkNotNull(getItem(position));
            final ActionBarActivity actionBarActivity = (ActionBarActivity) getActivity();
            rowView.setOnClickListener(new RomClickListener(context, actionBarActivity, record, position));
            return rowView;
        }
    }

    /**
     * This method determins the parent's size and fee and builds a transaction that spends from its outputs but with a fee that lifts the parent and the child to high priority.
     * TODO: consider upstream chains of unconfirmed
     * TODO: consider parallel attempts to PFP
     */
    private UnsignedTransaction tryCreateBumpTransaction(Sha256Hash txid, long feePerKB) {
        WalletAccount walletAccount = mbwManager.getSelectedAccount();
        TransactionDetails transaction = walletAccount.getTransactionDetails(txid);
        long txFee = 0;
        for (TransactionDetails.Item i : transaction.inputs) {
            txFee += i.value;
        }
        for (TransactionDetails.Item i : transaction.outputs) {
            txFee -= i.value;
        }
        if (txFee * 1000 / transaction.rawSize >= feePerKB) {
            makeText(getActivity(), "bumping not necessary", LENGTH_LONG).show();
            return null;
        }
        if (walletAccount instanceof AbstractAccount) {
            AbstractAccount account = (AbstractAccount) walletAccount;
            try {
                return account.createUnsignedCPFPTransaction(txid, feePerKB, txFee);
            } catch (InsufficientFundsException e) {
                makeText(getActivity(), getResources().getString(R.string.insufficient_funds), LENGTH_LONG).show();
            } catch (UnableToBuildTransactionException e) {
                makeText(getActivity(), getResources().getString(R.string.unable_to_build_tx), LENGTH_LONG).show();
            }
        }
        return null;
    }

    private EnterAddressLabelUtil.AddressLabelChangedHandler addressLabelChanged = new EnterAddressLabelUtil.AddressLabelChangedHandler() {
        @Override
        public void OnAddressLabelChanged(Address address, String label) {
            mbwManager.getEventBus().post(new AddressBookChanged());
            updateTransactionHistory();
        }
    };

    private void setTransactionLabel(TransactionSummary record) {
        EnterAddressLabelUtil.enterTransactionLabel(getActivity(), record.txid, metadataStorage, transactionLabelChanged);
    }

    private EnterAddressLabelUtil.TransactionLabelChangedHandler transactionLabelChanged = new EnterAddressLabelUtil.TransactionLabelChangedHandler() {

        @Override
        public void OnTransactionLabelChanged(Sha256Hash txid, String label) {
            updateTransactionHistory();
        }
    };

    private class Wrapper extends EndlessAdapter {
        private List<TransactionSummary> _toAdd;
        private final Object _toAddLock = new Object();
        private int lastOffset;
        private int chunkSize;

        private Wrapper(Context context, List<TransactionSummary> transactions) {
            super(new TransactionHistoryAdapter(context, transactions));
            _toAdd = new ArrayList<>();
            lastOffset = 0;
            chunkSize = 20;
        }

        @Override
        protected View getPendingView(ViewGroup parent) {
            //this is an empty view, getting more transaction details is fast at the moment
            return LayoutInflater.from(parent.getContext()).inflate(R.layout.transaction_history_fetching, null);
        }

        @Override
        protected boolean cacheInBackground() {
            WalletAccount acc = mbwManager.getSelectedAccount();
            synchronized (_toAddLock) {
                lastOffset += chunkSize;
                _toAdd = acc.getTransactionHistory(lastOffset, chunkSize);
            }
            return _toAdd.size() == chunkSize;
        }

        @Override
        protected void appendCachedData() {
            synchronized (_toAddLock) {
                TransactionHistoryAdapter a = (TransactionHistoryAdapter) getWrappedAdapter();
                for (TransactionSummary item : _toAdd) {
                    a.add(item);
                }
                _toAdd.clear();
            }
        }
    }

    private class RomClickListener implements View.OnClickListener {

        private Context context;
        private ActionBarActivity actionBarActivity;
        private TransactionSummary record;
        private int position;

        public RomClickListener(Context context,
                                ActionBarActivity actionBarActivity,
                                TransactionSummary record,
                                int position) {
            this.context = context;
            this.actionBarActivity = actionBarActivity;
            this.record = record;
            this.position = position;
        }

        @Override
        public void onClick(View view) {
            currentActionMode = actionBarActivity.startSupportActionMode(new ActionMode.Callback() {
                @Override
                public boolean onCreateActionMode(ActionMode actionMode, Menu menu) {
                    actionMode.getMenuInflater().inflate(R.menu.transaction_history_context_menu, menu);
                    updateActionBar(actionMode, menu);
                    return true;
                }

                @Override
                public boolean onPrepareActionMode(ActionMode actionMode, Menu menu) {
                    updateActionBar(actionMode, menu);
                    return true;
                }

                private void updateActionBar(ActionMode actionMode, Menu menu) {
                    checkNotNull(menu.findItem(R.id.miAddToAddressBook)).setVisible(record.hasAddressBook());
                    checkNotNull(menu.findItem(R.id.miCancelTransaction)).setVisible(record.canCancel());
                    checkNotNull(menu.findItem(R.id.miShowDetails)).setVisible(record.hasDetails());
                    checkNotNull(menu.findItem(R.id.miShowCoinapultDebug)).setVisible(record.canCoinapult());
                    checkNotNull(menu.findItem(R.id.miRebroadcastTransaction)).setVisible((record.confirmations == 0) && !record.canCoinapult());
                    checkNotNull(menu.findItem(R.id.miShare)).setVisible(!record.canCoinapult());
                    checkNotNull(menu.findItem(R.id.miBumpFee)).setVisible((record.confirmations == 0) && !record.canCoinapult());
                    checkNotNull(menu.findItem(R.id.miDeleteUnconfirmedTransaction)).setVisible(record.confirmations == 0);
                    currentActionMode = actionMode;
                    ((ListView) root.findViewById(R.id.lvTransactionHistory)).setItemChecked(position, true);
                }

                private void cancelTransaction() {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(context.getString(R.string.remove_queued_transaction_title))
                            .setMessage(context.getString(R.string.remove_queued_transaction))
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    boolean okay = mbwManager.getSelectedAccount().cancelQueuedTransaction(record.txid);
                                    dialog.dismiss();
                                    updateTransactionHistory();
                                    if (okay) {
                                        Utils.showSimpleMessageDialog(getActivity(), context.getString(R.string.remove_queued_transaction_hint));
                                    } else {
                                        new Toaster(getActivity()).toast(context.getString(R.string.remove_queued_transaction_error), false);
                                    }
                                }
                            })
                            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .create().show();
                }

                private void deleteUnconfirmedTransaction() {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(context.getString(R.string.delete_unconfirmed_transaction_title))
                            .setMessage(context.getString(R.string.warning_delete_unconfirmed_transaction))
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    mbwManager.getSelectedAccount().deleteTransaction(record.txid);
                                    dialog.dismiss();
                                    updateTransactionHistory();
                                }
                            })
                            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .create().show();
                }

                private void rebroadcastTransaction() {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(context.getString(R.string.rebroadcast_transaction_title))
                            .setMessage(context.getString(R.string.description_rebroadcast_transaction))
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    boolean success = BroadcastTransactionActivity.callMe(getActivity(), mbwManager.getSelectedAccount(), record.txid);
                                    if (!success) {
                                        Utils.showSimpleMessageDialog(getActivity(), context.getString(R.string.message_rebroadcast_failed));
                                    }
                                    dialog.dismiss();
                                }
                            })
                            .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();
                                }
                            })
                            .create().show();
                }

                private void bumpFee() {
                    long fee = MinerFee.PRIORITY.getFeePerKb(mbwManager.getWalletManager().getLastFeeEstimations()).getLongValue();
                    final UnsignedTransaction unsigned = tryCreateBumpTransaction(record.txid, fee);
                    if (unsigned != null) {
                        long txFee = unsigned.calculateFee();
                        ExactBitcoinValue txFeeBitcoinValue = ExactBitcoinValue.from(txFee);
                        String txFeeString = Utils.getFormattedValueWithUnit(txFeeBitcoinValue, mbwManager.getBitcoinDenomination());
                        CurrencyValue txFeeCurrencyValue = CurrencyValue.fromValue(txFeeBitcoinValue, mbwManager.getFiatCurrency(), mbwManager.getExchangeRateManager());
                        if (!CurrencyValue.isNullOrZero(txFeeCurrencyValue)) {
                            txFeeString += " (" + Utils.getFormattedValueWithUnit(txFeeCurrencyValue, mbwManager.getBitcoinDenomination()) + ")";
                        }
                        new AlertDialog.Builder(getActivity())
                                .setTitle(context.getString(R.string.bump_fee_title))
                                .setMessage(context.getString(R.string.description_bump_fee, fee / 1000, txFeeString))
                                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                    @Override
                                    public void onClick(DialogInterface dialog, int which) {
                                        Intent intent = SignTransactionActivity.getIntent(getActivity(), mbwManager.getSelectedAccount().getId(), false, unsigned);
                                        startActivityForResult(intent, SIGN_TRANSACTION_REQUEST_CODE);
                                        dialog.dismiss();
                                    }
                                })
                                .setNegativeButton(R.string.no, null)
                                .create().show();
                    }
                }

                private void share() {
                    new AlertDialog.Builder(getActivity())
                            .setTitle(R.string.share_transaction_manually_title)
                            .setMessage(R.string.share_transaction_manually_description)
                            .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    String transaction = HexUtils.toHex(mbwManager.getSelectedAccount().getTransaction(record.txid).binary);
                                    Intent shareIntent = new Intent(Intent.ACTION_SEND);
                                    shareIntent.setType("text/plain");
                                    shareIntent.putExtra(Intent.EXTRA_TEXT, transaction);
                                    startActivity(Intent.createChooser(shareIntent, getString(R.string.share_transaction)));
                                    dialog.dismiss();
                                }
                            })
                            .setNegativeButton(R.string.no, null)
                            .create().show();
                }

                @Override
                public boolean onActionItemClicked(ActionMode actionMode, MenuItem menuItem) {
                    final int itemId = menuItem.getItemId();
                    switch (itemId) {
                        case R.id.miShowDetails:
                            doShowDetails(record);
                            finishActionMode();
                            return true;
                        case R.id.miSetLabel:
                            setTransactionLabel(record);
                            finishActionMode();
                            break;
                        case R.id.miAddToAddressBook:
                            String defaultName = "";
                            EnterAddressLabelUtil.enterAddressLabel(
                                    getActivity(),
                                    mbwManager.getMetadataStorage(),
                                    record.destinationAddress.get(),
                                    defaultName,
                                    addressLabelChanged);
                            break;
                        case R.id.miCancelTransaction:
                            cancelTransaction();
                            break;
                        case R.id.miDeleteUnconfirmedTransaction:
                            deleteUnconfirmedTransaction();
                            break;
                        case R.id.miRebroadcastTransaction:
                            rebroadcastTransaction();
                            break;
                        case R.id.miBumpFee:
                            bumpFee();
                            break;
                        case R.id.miShare:
                            share();
                            break;
                    }
                    return false;
                }

                @Override
                public void onDestroyActionMode(ActionMode actionMode) {
                    ((ListView) root.findViewById(R.id.lvTransactionHistory)).setItemChecked(position, false);
                    currentActionMode = null;
                }
            });
        }
    }
}
