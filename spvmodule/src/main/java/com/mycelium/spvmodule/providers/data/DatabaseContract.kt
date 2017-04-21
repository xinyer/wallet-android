package com.mycelium.spvmodule.providers.data

import android.provider.BaseColumns

import com.mycelium.spvmodule.providers.BlockchainContract

internal interface DatabaseContract {
    interface Address : BaseColumns {
        companion object {
            val TABLE_NAME = BlockchainContract.Address.TABLE_NAME
            val ADDRESS_ID = BaseColumns._ID
            val CREATION_DATE = BlockchainContract.Address.CREATION_DATE
            val SYNCED_TO_BLOCK = BlockchainContract.Address.SYNCED_TO_BLOCK
        }
    }

    interface Transaction : BaseColumns {
        companion object {
            val TABLE_NAME = BlockchainContract.Transaction.TABLE_NAME
            val TRANSACTION_ID = BaseColumns._ID
            val TRANSACTION = BlockchainContract.Transaction.TRANSACTION
            val INCLUDED_IN_BLOCK = BlockchainContract.Transaction.INCLUDED_IN_BLOCK
        }
    }

    interface TransactionOutput : BaseColumns {
        companion object {
            val TABLE_NAME = BlockchainContract.TransactionOutput.TABLE_NAME
            val TRANSACTION_OUTPUT_ID = BaseColumns._ID
            val TRANSACTION_OUTPUT = BlockchainContract.TransactionOutput.TXO
        }
    }
}