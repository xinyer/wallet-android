package com.mycelium.bitcoinaccountmodule.providers

import android.content.ContentResolver
import android.net.Uri

/**
 * The contract between the [BitcoinAccountContentProvider] and clients. Contains definitions
 * for the supported URIs and columns.
 */
interface BitcoinAccountContract {
    interface Address {
        companion object {
            val TABLE_NAME = "address"
            val CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, TABLE_NAME)
            val CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.mycelium.bitcoinaccount.address"
            val CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.com.mycelium.bitcoinaccount.address"
            val ADDRESS_ID = "_id"
            val LABEL = "label"
        }
    }

    interface Transaction {
        companion object {
            val TABLE_NAME = "txn" // "transaction" is an SQL reserved word.
            val CONTENT_URI = Uri.withAppendedPath(AUTHORITY_URI, TABLE_NAME)
            val CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.mycelium.bitcoinaccount.transaction"
            val CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.com.mycelium.bitcoinaccount.transaction"
            val TRANSACTION_ID = "_id"
            val LABEL = "label"
        }
    }

    companion object {
        val AUTHORITY = "com.mycelium.bitcoinaccountmodule.providers.BitcoinAccountContentProvider"

        /**
         * A content:// style uri to the authority for the contacts provider
         */
        val AUTHORITY_URI = Uri.parse("content://" + AUTHORITY)
    }
}