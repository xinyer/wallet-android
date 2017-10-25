package com.mycelium.spvmodule.providers;

import android.content.ContentResolver;
import android.net.Uri;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The contract between the [TransactionContentProvider] and clients. Contains definitions
 * for the supported URIs and columns.
 */
public class TransactionContract {

    public static class TransactionSummary {
        public static final String TABLE_NAME = "txn"; // "transaction" is an SQL reserved word.
        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.mycelium.transaction";

        public static final String _ID = "_id";
        public static final String VALUE = "value";
        public static final String IS_INCOMING = "isIncoming";
        public static final String TIME = "time";
        public static final String HEIGHT = "height";
        public static final String CONFIRMATIONS = "confirmations";
        public static final String IS_QUEUED_OUTGOING = "isQueuedOutgoing";
        public static final String CONFIRMATION_RISK_PROFILE_LENGTH = "confirmationRiskProfileLength";
        public static final String CONFIRMATION_RISK_PROFILE_RBF_RISK = "confirmationRiskProfileRbfRisk";
        public static final String CONFIRMATION_RISK_PROFILE_DOUBLE_SPEND = "confirmationRiskProfileDoubleSpend";
        public static final String DESTINATION_ADDRESS = "destinationAddress";
        public static final String TO_ADDRESSES = "toAddresses";
        public static final String ACCOUNT_INDEX = "accountIndex";

        public static final String SELECTION_ACCOUNT_INDEX = ACCOUNT_INDEX + " = ?";
        @Nullable
        public static final String SELECTION_ID = SELECTION_ACCOUNT_INDEX + " AND " + _ID
            + " = ?";

        public static Uri CONTENT_URI(String packageName) {
            return Uri.withAppendedPath(AUTHORITY_URI(packageName), TABLE_NAME);
        }
    }

    public static class TransactionDetails {
        public static final String TABLE_NAME = "txndtls"; // "transaction" is an SQL reserved word.
        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.mycelium.transaction";

        public static final String _ID = "_id";

        public static final String TIME = "time";
        public static final String HEIGHT = "height";
        public static final String ACCOUNT_INDEX = "accountIndex";

        public static final String SELECTION_ACCOUNT_INDEX = ACCOUNT_INDEX + " = ?";
        @NotNull
        public static final String RAW_SIZE = "rawSize";
        @NotNull
        public static final String INPUTS = "inputs";
        @NotNull
        public static final String OUTPUTS = "outputs";

        public static Uri CONTENT_URI(String packageName) {
            return Uri.withAppendedPath(AUTHORITY_URI(packageName), TABLE_NAME);
        }
    }

    public static class AccountBalance {
        public static final String TABLE_NAME = "acntblc"; // "transaction" is an SQL reserved word.
        public static final String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.mycelium.transaction";

        public static final String _ID = "_id";

        public static final String BALANCE = "balance";
        public static final String ACCOUNT_INDEX = "accountIndex";

        public static final String SELECTION_ACCOUNT_INDEX = ACCOUNT_INDEX + " = ?";

        public static Uri CONTENT_URI(String packageName) {
            return Uri.withAppendedPath(AUTHORITY_URI(packageName), TABLE_NAME);
        }
    }

    public static String AUTHORITY(String packageName) {
        return packageName + ".providers.TransactionContentProvider";
    }

    /**
     * A content:// style uri to the authority for the contacts provider
     */
    private static Uri AUTHORITY_URI(String packageName) {
        return Uri.parse("content://" + AUTHORITY(packageName));
    }
}