package com.mycelium.spvmodule.providers.data

import android.database.MatrixCursor
import com.mycelium.spvmodule.providers.TransactionContract

/**
 * Created by Nelson on 17/10/2017.
 */
class TransactionsSummaryCursor(initialCapacity: Int)
    : MatrixCursor(arrayOf(TransactionContract.TransactionSummary._ID, TransactionContract.TransactionSummary.VALUE,
        TransactionContract.TransactionSummary.IS_INCOMING, TransactionContract.TransactionSummary.TIME,
        TransactionContract.TransactionSummary.HEIGHT, TransactionContract.TransactionSummary.CONFIRMATIONS,
        TransactionContract.TransactionSummary.IS_QUEUED_OUTGOING, TransactionContract.TransactionSummary.CONFIRMATION_RISK_PROFILE,
        TransactionContract.TransactionSummary.DESTINATION_ADDRESS, TransactionContract.TransactionSummary.TO_ADDRESSES),
        initialCapacity) {

}