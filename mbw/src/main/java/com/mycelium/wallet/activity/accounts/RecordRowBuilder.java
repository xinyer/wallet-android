package com.mycelium.wallet.activity.accounts;

import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.text.Html;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import com.google.common.base.Optional;
import com.mrd.bitlib.model.Address;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wapi.model.Balance;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.bip44.Bip44Account;
import com.mycelium.wapi.wallet.bip44.Bip44PubOnlyAccount;
import com.mycelium.wapi.wallet.currency.CurrencyBasedBalance;
import com.mycelium.wapi.wallet.single.SingleAddressAccount;

public class RecordRowBuilder {
    private final MbwManager mbwManager;
    private final Resources resources;
    private final LayoutInflater inflater;

    public RecordRowBuilder(MbwManager mbwManager, Resources resources, LayoutInflater inflater) {
        this.mbwManager = mbwManager;
        this.resources = resources;
        this.inflater = inflater;
    }

    public View buildRecordView(ViewGroup parent, WalletAccount walletAccount, boolean isSelected, boolean hasFocus, View convertView) {
        View rowView = convertView;
        if (rowView == null) {
            rowView = inflater.inflate(R.layout.record_row, parent, false);
        }

        // Make grey if not part of the balance
        if (!isSelected) {
            Utils.setAlpha(rowView, 0.5f);
        } else {
            Utils.setAlpha(rowView, 1f);
        }

        int textColor = resources.getColor(R.color.white);

        // Show focus if applicable
        if (hasFocus) {
            rowView.setBackgroundColor(resources.getColor(R.color.selectedrecord));
        } else {
            rowView.setBackgroundColor(resources.getColor(R.color.transparent));
        }

        // Show/hide key icon
        ImageView icon = (ImageView) rowView.findViewById(R.id.ivIcon);

        Drawable drawableForAccount = Utils.getDrawableForAccount(walletAccount, isSelected, resources);
        if (drawableForAccount == null) {
            icon.setVisibility(View.INVISIBLE);
        } else {
            icon.setVisibility(View.VISIBLE);
            icon.setImageDrawable(drawableForAccount);
        }

        TextView tvLabel = ((TextView) rowView.findViewById(R.id.tvLabel));
        TextView tvWhatIsIt = ((TextView) rowView.findViewById(R.id.tvWhatIsIt));
        String name = mbwManager.getMetadataStorage().getLabelByAccount(walletAccount.getId());
        tvWhatIsIt.setVisibility(View.GONE);
        if (name.length() == 0) {
            rowView.findViewById(R.id.tvLabel).setVisibility(View.GONE);
        } else {
            // Display name
            tvLabel.setVisibility(View.VISIBLE);
            tvLabel.setText(Html.fromHtml(name));
            tvLabel.setTextColor(textColor);
        }

        String displayAddress;
        if (walletAccount.isActive()) {
            if (walletAccount instanceof Bip44PubOnlyAccount) {
                int numKeys = ((Bip44Account) walletAccount).getPrivateKeyCount();
                if (numKeys > 1) {
                    displayAddress = resources.getString(R.string.contains_addresses, Integer.toString(numKeys));
                } else {
                    displayAddress = resources.getString(R.string.account_contains_one_address_info);
                }
            } else if (walletAccount instanceof Bip44Account) {
                int numKeys = ((Bip44Account) walletAccount).getPrivateKeyCount();
                if (numKeys > 1) {
                    displayAddress = resources.getString(R.string.contains_keys, Integer.toString(numKeys));
                } else {
                    displayAddress = resources.getString(R.string.account_contains_one_key_info);
                }
            } else {
                Optional<Address> receivingAddress = walletAccount.getReceivingAddress();
                if (receivingAddress.isPresent()) {
                    if (name.length() == 0) {
                        // Display address in it's full glory, chopping it into three
                        displayAddress = receivingAddress.get().toMultiLineString();
                    } else {
                        // Display address in short form
                        displayAddress = receivingAddress.get().getShortAddress();
                    }
                } else {
                    displayAddress = "";
                }
            }
        } else {
            displayAddress = ""; //dont show key count of archived accs
        }

        TextView tvAddress = ((TextView) rowView.findViewById(R.id.tvAddress));
        tvAddress.setText(displayAddress);
        tvAddress.setTextColor(textColor);

        // Set tag
        rowView.setTag(walletAccount);

        // Set balance
        if (walletAccount.isActive()) {
            CurrencyBasedBalance balance = walletAccount.getCurrencyBasedBalance();
            rowView.findViewById(R.id.tvBalance).setVisibility(View.VISIBLE);
            String balanceString = Utils.getFormattedValueWithUnit(balance.confirmed, mbwManager.getBitcoinDenomination());
            TextView tvBalance = ((TextView) rowView.findViewById(R.id.tvBalance));
            tvBalance.setText(balanceString);
            tvBalance.setTextColor(textColor);

            boolean showBackupMissingWarning = showBackupMissingWarning(walletAccount, mbwManager);
            rowView.findViewById(R.id.tvBackupMissingWarning).setVisibility(showBackupMissingWarning ? View.VISIBLE : View.GONE);

        } else {
            // We don't show anything if the account is archived
            rowView.findViewById(R.id.tvBalance).setVisibility(View.GONE);
            rowView.findViewById(R.id.tvBackupMissingWarning).setVisibility(View.GONE);
        }

        rowView.findViewById(R.id.tvTraderKey).setVisibility(View.GONE);

        return rowView;
    }

    public static boolean showLegacyAccountWarning(WalletAccount account, MbwManager mbwManager) {
        if (account.isArchived()) {
            return false;
        }
        Balance balance = account.getBalance();
        return account instanceof SingleAddressAccount
                && balance.getReceivingBalance() + balance.getSpendableBalance() > 0
                && account.canSpend()
                && !mbwManager.getMetadataStorage().getIgnoreLegacyWarning(account.getId());
    }

    private static boolean showBackupMissingWarning(WalletAccount account, MbwManager mbwManager) {
        if (account.isArchived()) {
            return false;
        }

        boolean showBackupMissingWarning = false;
        if (account.canSpend()) {
            if (account.isDerivedFromInternalMasterseed()) {
                showBackupMissingWarning = mbwManager.getMetadataStorage().getMasterSeedBackupState() != MetadataStorage.BackupState.VERIFIED;
            } else {
                MetadataStorage.BackupState backupState = mbwManager.getMetadataStorage().getOtherAccountBackupState(account.getId());
                showBackupMissingWarning = (backupState != MetadataStorage.BackupState.VERIFIED) && (backupState != MetadataStorage.BackupState.IGNORED);
            }
        }

        return showBackupMissingWarning;
    }
}
