package com.mycelium.spvmodule;

import android.content.Intent;

import java.util.ArrayList;

/**
 * The contract between the [SpvService] and clients. Contains definitions
 * for the supported Actions.
 */
public interface IntentContract {

    String ACCOUNT_INDEX_EXTRA = "ACCOUNT_INDEX";

    class BroadcastTransaction {

        public static final String ACTION = "com.mycelium.wallet.broadcastTransaction";

        public static final String TX_EXTRA = ACTION + "_tx";

        public static Intent createIntent(int accountId, byte[] transaction) {
            Intent intent = new Intent(ACTION);
            intent.putExtra(IntentContract.ACCOUNT_INDEX_EXTRA, accountId);
            intent.putExtra(TX_EXTRA, transaction);
            return intent;
        }
    }

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

    class SetPrivateKeyExtendedKeyCoinType {

        public static final String ACTION = "com.mycelium.wallet.setPrivateKeyExtendedKeyCoinType";

        public static final String PRIVATE_KEY = ACTION + "_data";

        public static Intent createIntent(byte[] private_key) {
            Intent intent = new Intent(ACTION);
            intent.putExtra(PRIVATE_KEY, private_key);
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

    class WaitingIntents {

        public static final String ACTION = "com.mycelium.wallet.waitingIntents";
        public static final String RESULT_ACTION = "com.mycelium.wallet.waitingIntentsResult";

        public static final String WAITING_ACTIONS = ACTION + "_actions";

        public static Intent createResultIntent(int accountId, String[] waitingActions) {
            Intent intent = new Intent(RESULT_ACTION);
            intent.putExtra(IntentContract.ACCOUNT_INDEX_EXTRA, accountId);
            intent.putExtra(WAITING_ACTIONS, waitingActions);
            return intent;
        }

        public static Intent createIntent(int accountId) {
            Intent intent = new Intent(ACTION);
            intent.putExtra(IntentContract.ACCOUNT_INDEX_EXTRA, accountId);
            return intent;
        }
    }
}