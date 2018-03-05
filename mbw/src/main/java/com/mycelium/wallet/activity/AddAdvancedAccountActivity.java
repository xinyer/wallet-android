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
import android.view.View;
import android.view.WindowManager;

import com.google.common.base.Optional;
import com.mrd.bitlib.crypto.HdKeyNode;
import com.mrd.bitlib.crypto.InMemoryPrivateKey;
import com.mrd.bitlib.model.Address;
import com.mrd.bitlib.model.NetworkParameters;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.colu.ColuAccount;
import com.mycelium.wallet.colu.ColuManager;
import com.mycelium.wallet.extsig.trezor.activity.TrezorAccountImportActivity;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wapi.wallet.AesKeyCipher;
import com.mycelium.wapi.wallet.KeyCipher;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.single.SingleAddressAccount;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class AddAdvancedAccountActivity extends Activity {
    public static final int RESULT_MSG = 25;

    public static void callMe(Activity activity, int requestCode) {
        Intent intent = new Intent(activity, AddAdvancedAccountActivity.class);
        activity.startActivityForResult(intent, requestCode);
    }

    private static final int TREZOR_RESULT_CODE = 2;
    private MbwManager _mbwManager;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.add_advanced_account_activity);
        final Activity activity = AddAdvancedAccountActivity.this;
        _mbwManager = MbwManager.getInstance(this);

        findViewById(R.id.btTrezor).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                TrezorAccountImportActivity.callMe(activity, TREZOR_RESULT_CODE);
            }
        });

    }

    @Override
    public void onActivityResult(final int requestCode, final int resultCode, final Intent intent) {

        if (requestCode == TREZOR_RESULT_CODE && resultCode == Activity.RESULT_OK) {
            // already added to the WalletManager - just return the new account
            finishOk((UUID) intent.getSerializableExtra("account"));
        } else {
            super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    // restore single account in asynctask so we can handle Colored Coins case
    private class ImportSingleAddressAccountAsyncTask extends AsyncTask<Void, Integer, UUID> {
        private InMemoryPrivateKey key;
        private MetadataStorage.BackupState backupState;
        private ProgressDialog dialog;
        private boolean askUserForColorize = false;
        private Address address;

        public ImportSingleAddressAccountAsyncTask(InMemoryPrivateKey key, MetadataStorage.BackupState backupState) {
            this.key = key;
            this.backupState = backupState;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = new ProgressDialog(AddAdvancedAccountActivity.this);
            dialog.setMessage("Importing");
            dialog.show();
        }

        @Override
        protected UUID doInBackground(Void... params) {
            UUID acc = null;

            try {
                //Check whether this address is already used in any account
                address = key.getPublicKey().toAddress(_mbwManager.getNetwork());
                Optional<UUID> accountId = _mbwManager.getAccountId(address, null);
                if (accountId.isPresent()) {
                    return null;
                }

                //check if address is colu
                // do not do this in main thread
                ColuManager coluManager = _mbwManager.getColuManager();
                List<ColuAccount.ColuAsset> asset = new ArrayList<>(coluManager.getColuAddressAssets(address));

                if (asset.size() > 0) {
                    acc = _mbwManager.getColuManager().enableAsset(asset.get(0), key);
                } else {
                    askUserForColorize = true;
                }
            } catch (IOException e) {
                askUserForColorize = true;
                return null;
            }
            return acc;
        }

        private int selectedItem;

        @Override
        protected void onPostExecute(UUID account) {
            dialog.dismiss();
            Optional accountId = _mbwManager.getAccountId(this.address, null);
            if (account != null) {
                finishOk(account);
            } else if (askUserForColorize) {
                final List<String> list = ColuAccount.ColuAsset.getAllAssetNames();
                list.add(0, "BTC");
                new AlertDialog.Builder(AddAdvancedAccountActivity.this)
                        .setTitle(R.string.restore_addres_as)
                        .setSingleChoiceItems(list.toArray(new String[list.size()]), 0, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                selectedItem = i;
                            }
                        })
                        .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                UUID account;
                                if (selectedItem == 0) {
                                    account = returnSAAccount(key, backupState);
                                } else {
                                    ColuAccount.ColuAsset coluAsset = ColuAccount.ColuAsset.getByType(ColuAccount.ColuAssetType.parse(list.get(selectedItem)));
                                    account = _mbwManager.getColuManager().enableAsset(coluAsset, key);
                                }
                                finishOk(account);
                            }
                        })
                        .create()
                        .show();
            } else if (accountId.isPresent()) {
                final WalletAccount existingAccount = _mbwManager.getWalletManager(false).getAccount((UUID) accountId.get());
                if (!existingAccount.canSpend() && (existingAccount instanceof SingleAddressAccount || existingAccount instanceof ColuAccount)) {
                    // scanned the private key of a watch only single address account
                    String existingAccountName = _mbwManager.getMetadataStorage().getLabelByAccount(existingAccount.getId());
                    new AlertDialog.Builder(AddAdvancedAccountActivity.this)
                            .setTitle(R.string.priv_key_of_watch_only_sa_account)
                            .setMessage(getString(R.string.want_to_add_priv_key_to_watch_account, existingAccountName))
                            .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    finishAlreadyExist(address);
                                }
                            })
                            .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    try {
                                        if (existingAccount instanceof SingleAddressAccount) {
                                            ((SingleAddressAccount) existingAccount).setPrivateKey(key, AesKeyCipher.defaultKeyCipher());
                                        } else {
                                            ColuAccount coluAccount = (ColuAccount) existingAccount;
                                            coluAccount.setPrivateKey(new InMemoryPrivateKey(key.getPrivateKeyBytes()));
                                            coluAccount.getLinkedAccount().setPrivateKey(key, AesKeyCipher.defaultKeyCipher());
                                        }
                                    } catch (KeyCipher.InvalidKeyCipher invalidKeyCipher) {
                                        invalidKeyCipher.printStackTrace();
                                    }
                                    finishOk(existingAccount.getId());
                                }
                            })
                            .create()
                            .show();
                } else {
                    finishAlreadyExist(address);
                }
            }
        }
    }

    private UUID returnSAAccount(InMemoryPrivateKey key, MetadataStorage.BackupState backupState) {
        UUID acc;
        try {
            acc = _mbwManager.getWalletManager(false).createSingleAddressAccount(key, AesKeyCipher.defaultKeyCipher());

            // Dont show a legacy-account warning for freshly generated or imported keys
            _mbwManager.getMetadataStorage().setIgnoreLegacyWarning(acc, true);

            _mbwManager.getMetadataStorage().setOtherAccountBackupState(acc, backupState);
            return acc;
        } catch (KeyCipher.InvalidKeyCipher invalidKeyCipher) {
            throw new RuntimeException(invalidKeyCipher);
        }
    }

    private void returnAccount(InMemoryPrivateKey key, MetadataStorage.BackupState backupState, AccountType type) {
        if (type == AccountType.SA) {
            finishOk(returnSAAccount(key, backupState));
        } else {
            new ImportSingleAddressAccountAsyncTask(key, backupState).execute();
        }
    }

    private void returnAccount(HdKeyNode hdKeyNode) {
        UUID acc = _mbwManager.getWalletManager(false).createUnrelatedBip44Account(hdKeyNode);
        // set BackupState as ignored - we currently have no option to backup xPrivs after all
        _mbwManager.getMetadataStorage().setOtherAccountBackupState(acc, MetadataStorage.BackupState.IGNORED);
        finishOk(acc);
    }

    private class ImportReadOnlySingleAddressAccountAsyncTask extends AsyncTask<Void, Integer, UUID> {
        private Address address;
        private AccountType addressType;
        private ProgressDialog dialog;
        private boolean askUserForColorize = false;

        public ImportReadOnlySingleAddressAccountAsyncTask(Address address, AccountType addressType) {
            this.address = address;
            this.addressType = addressType;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            dialog = new ProgressDialog(AddAdvancedAccountActivity.this);
            dialog.setMessage("Importing");
            dialog.show();
        }

        @Override
        protected UUID doInBackground(Void... params) {
            UUID acc = null;

            //Check whether this address is already used in any account
            Optional<UUID> accountId = _mbwManager.getAccountId(this.address, null);
            if (accountId.isPresent()) {
                return null;
            }

            try {
                switch (addressType) {
                    case Unknown: {
                        ColuManager coluManager = _mbwManager.getColuManager();
                        List<ColuAccount.ColuAsset> asset = new ArrayList<>(coluManager.getColuAddressAssets(this.address));

                        if (asset.size() > 0) {
                            acc = _mbwManager.getColuManager().enableReadOnlyAsset(asset.get(0), address);
                        } else {
                            askUserForColorize = true;
                        }
                    }
                    break;
                    case SA:
                        acc = _mbwManager.getWalletManager(false).createSingleAddressAccount(address);
                        break;
                    case Colu:
                        ColuManager coluManager = _mbwManager.getColuManager();
                        List<ColuAccount.ColuAsset> asset = new ArrayList<>(coluManager.getColuAddressAssets(this.address));

                        if (asset.size() > 0) {
                            acc = _mbwManager.getColuManager().enableReadOnlyAsset(asset.get(0), address);
                        }
                        break;
                }
            } catch (IOException e) {
                askUserForColorize = true;
                return null;
            }
            return acc;
        }

        private int selectedItem;

        @Override
        protected void onPostExecute(UUID account) {
            dialog.dismiss();
            if (account != null) {
                finishOk(account);
            } else if (askUserForColorize) {
                final List<String> list = ColuAccount.ColuAsset.getAllAssetNames();
                list.add(0, "BTC");
                new AlertDialog.Builder(AddAdvancedAccountActivity.this)
                        .setTitle(R.string.restore_addres_as)
                        .setSingleChoiceItems(list.toArray(new String[list.size()]), 0, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                selectedItem = i;
                            }
                        })
                        .setPositiveButton(R.string.button_ok, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                UUID account;
                                if (selectedItem == 0) {
                                    account = _mbwManager.getWalletManager(false).createSingleAddressAccount(address);
                                } else {
                                    ColuAccount.ColuAsset coluAsset = ColuAccount.ColuAsset.getByType(ColuAccount.ColuAssetType.parse(list.get(selectedItem)));
                                    account = _mbwManager.getColuManager().enableReadOnlyAsset(coluAsset, address);
                                }
                                finishOk(account);
                            }
                        })
                        .create()
                        .show();
            } else if (_mbwManager.getAccountId(this.address, null).isPresent()) {
                finishAlreadyExist(address);
            }
        }
    }

    private void finishAlreadyExist(Address address) {
        Intent result = new Intent();
        String accountType = "BTC Single Address";
        for (ColuAccount.ColuAssetType type : ColuAccount.ColuAssetType.values()) {
            if (_mbwManager.getColuManager().hasAccountWithType(address, type)) {
                accountType = ColuAccount.ColuAsset.getByType(type).name;
                break;
            }
        }
        result.putExtra(AddAccountActivity.RESULT_MSG, getString(R.string.account_already_exist, accountType));
        setResult(RESULT_MSG, result);
        finish();
    }

    private void returnAccount(Address address) {
        //UUID acc = _mbwManager.getWalletManager(false).createSingleAddressAccount(address);
        new ImportReadOnlySingleAddressAccountAsyncTask(address, AccountType.Unknown).execute();
    }

    private void finishOk(UUID account) {
        Intent result = new Intent();
        result.putExtra(AddAccountActivity.RESULT_KEY, account);
        setResult(RESULT_OK, result);
        finish();
    }

    enum AccountType {
        SA, Colu, Unknown
    }
}
