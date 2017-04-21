package com.mycelium.bitcoinaccountmodule.providers.data

import android.provider.BaseColumns

import com.mycelium.bitcoinaccountmodule.providers.BitcoinAccountContract


internal interface DatabaseContract {
    interface Address : BaseColumns {
        companion object {
            val TABLE_NAME = BitcoinAccountContract.Address.TABLE_NAME
            val ADDRESS_ID = BaseColumns._ID
            val LABEL = BitcoinAccountContract.Address.LABEL
        }
    }

    interface Transaction : BaseColumns {
        companion object {
            val TABLE_NAME = BitcoinAccountContract.Transaction.TABLE_NAME
            val TRANSACTION_ID = BaseColumns._ID
            val LABEL = BitcoinAccountContract.Transaction.LABEL
        }
    }
}