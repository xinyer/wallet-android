package com.mycelium.spvmodule.contentprovider

import android.database.MatrixCursor

/**
 * Created by Nelson on 17/10/2017.
 */
class TransactionCursor(initialCapacity: Int)
    : MatrixCursor(arrayOf(TransactionContentProvider._ID, TransactionContentProvider.VALUE,
        TransactionContentProvider.IS_INCOMING, TransactionContentProvider.TIME,
        TransactionContentProvider.HEIGHT, TransactionContentProvider.CONFIRMATIONS,
        TransactionContentProvider.IS_QUEUED_OUTGOING, TransactionContentProvider.CONFIRMATION_RISK_PROFILE,
        TransactionContentProvider.DESTINATION_ADDRESS, TransactionContentProvider.TO_ADDRESSES),
        initialCapacity) {

}