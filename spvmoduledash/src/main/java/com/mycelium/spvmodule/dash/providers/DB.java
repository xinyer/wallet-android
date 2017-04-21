package com.mycelium.spvmodule.dash.providers;

import android.content.ContentResolver;
import android.net.Uri;
import android.provider.BaseColumns;

import static com.mycelium.spvmodule.dash.providers.BlockchainContentProvider.AUTHORITY;

public interface DB {
    interface Addresses extends BaseColumns {
        Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/addresses");
        String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.mycelium.addresses";
        String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.com.mycelium.addresses";
        String ADDRESS = "_id"; // the ID
        String CREATION_DATE = "creationDate";
        String SYNCED_TO_BLOCK = "blockHeight";
        String ACCOUNT = "account"; // indexed. to find an account's addresses
    }

    interface Transactions extends BaseColumns {
        Uri CONTENT_URI = Uri.parse("content://" + AUTHORITY + "/transactions");
        String CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.mycelium.transactions";
        String CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.com.mycelium.transactions";
        String TRANSACTION_ID = "_id"; // the ID
        String TRANSACTION = "tx";
        String INCLUDED_IN_BLOCK = "blockHeight";
        String ACCOUNT = "account"; // indexed. to find an account's transactions
    }
}
