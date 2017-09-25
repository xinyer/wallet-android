package com.mycelium.spvmodule;

import android.content.Intent;

import java.util.ArrayList;

/**
 * The contract between the [SpvService] and clients. Contains definitions
 * for the supported Actions.
 */
public interface IntentContract {

    String ACCOUNT_INDEX_EXTRA = "ACCOUNT_INDEX";

    class ReceiveTransactions {

        public static final String ACTION = "com.mycelium.wallet.receiveTransactions";

        public static Intent createIntent(int accountId) {
            Intent intent = new Intent(ACTION);
            intent.putExtra(IntentContract.ACCOUNT_INDEX_EXTRA, accountId);
            return intent;
        }
    }


    class RequestPrivateExtendedKeyCoinTypeToSPV {

        public static final String ACTION = "com.mycelium.wallet.requestPrivateExtendedKeyCoinTypeToSPV";

        public static final String BIP39_PASS_PHRASE_EXTRA = ACTION + "_bip39Passphrase";
        public static final String CREATION_TIME_SECONDS_EXTRA = ACTION + "_creationTimeSeconds";

        public static Intent createIntent(int accountId, ArrayList<String> bip39Passphrase, long creationTimeSeconds) {
            Intent intent = new Intent(ACTION);
            intent.putExtra(IntentContract.ACCOUNT_INDEX_EXTRA, accountId);
            intent.putStringArrayListExtra(BIP39_PASS_PHRASE_EXTRA, bip39Passphrase);
            intent.putExtra(CREATION_TIME_SECONDS_EXTRA, creationTimeSeconds);
            return intent;
        }
    }

    class SendFunds {

        public static final String ACTION = "com.mycelium.wallet.sendFunds";

        public static final String ADDRESS_EXTRA = ACTION + "_address";
        public static final String AMOUNT_EXTRA = ACTION + "_amount";
        public static final String FEE_EXTRA = ACTION + "_fee";

        public static Intent createIntent(int accountId, String address, long amount, long fee) {
            Intent intent = new Intent(ACTION);
            intent.putExtra(IntentContract.ACCOUNT_INDEX_EXTRA, accountId);
            intent.putExtra(ADDRESS_EXTRA, address);
            intent.putExtra(AMOUNT_EXTRA, amount);
            intent.putExtra(FEE_EXTRA, fee);
            return intent;
        }
    }
}