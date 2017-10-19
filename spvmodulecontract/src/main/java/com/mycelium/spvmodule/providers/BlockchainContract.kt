package com.mycelium.spvmodule.providers

import android.content.ContentResolver
import android.net.Uri

/**
 * The contract between the [BlockchainContentProvider] and clients. Contains definitions
 * for the supported URIs and columns.
 */
interface BlockchainContract {
    interface Address {
        companion object {
            val TABLE_NAME = "address"
            fun CONTENT_URI(packageName: String) = Uri.withAppendedPath(AUTHORITY_URI(packageName), TABLE_NAME)
            val CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.mycelium.address"
            val CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.com.mycelium.address"

            val ADDRESS_ID = "_id"
            val CREATION_DATE = "creationDate"
            val SYNCED_TO_BLOCK = "blockHeight"
        }
    }

    interface Transaction {
        companion object {
            val TABLE_NAME = "txn" // "transaction" is an SQL reserved word.
            fun CONTENT_URI(packageName: String) = Uri.withAppendedPath(AUTHORITY_URI(packageName), TABLE_NAME)
            val CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.mycelium.transaction"
            val CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.com.mycelium.transaction"

            val TRANSACTION_ID = "_id"
            val TRANSACTION = "tx"
            val INCLUDED_IN_BLOCK = "blockHeight"
        }
    }

    interface TransactionOutput {
        companion object {
            val TABLE_NAME = "txo"
            fun CONTENT_URI(packageName: String) = Uri.withAppendedPath(AUTHORITY_URI(packageName), TABLE_NAME)
            val CONTENT_TYPE = ContentResolver.CURSOR_DIR_BASE_TYPE + "/vnd.com.mycelium.transactionoutput"
            val CONTENT_ITEM_TYPE = ContentResolver.CURSOR_ITEM_BASE_TYPE + "/vnd.com.mycelium.transactionoutput"

            val TXO_ID = "_id"
            val TXO = "txo"
        }
    }

    companion object {
        fun AUTHORITY(packageName: String) =  "$packageName.providers.BlockchainContentProvider"

        /**
         * A content:// style uri to the authority for the contacts provider
         */
        fun AUTHORITY_URI(packageName:String) = Uri.parse("content://" + AUTHORITY(packageName))
    }
}