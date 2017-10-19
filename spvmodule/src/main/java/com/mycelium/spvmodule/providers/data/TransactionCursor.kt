package com.mycelium.spvmodule.providers.data

import android.database.MatrixCursor
import com.mycelium.spvmodule.providers.TransactionContract

/**
 * Created by Nelson on 17/10/2017.
 */
class TransactionCursor(initialCapacity: Int)
    : MatrixCursor(arrayOf(TransactionContract.Transaction._ID, TransactionContract.Transaction.VALUE,
        TransactionContract.Transaction.IS_INCOMING, TransactionContract.Transaction.TIME,
        TransactionContract.Transaction.HEIGHT, TransactionContract.Transaction.CONFIRMATIONS,
        TransactionContract.Transaction.IS_QUEUED_OUTGOING, TransactionContract.Transaction.CONFIRMATION_RISK_PROFILE,
        TransactionContract.Transaction.DESTINATION_ADDRESS, TransactionContract.Transaction.TO_ADDRESSES),
        initialCapacity) {

}