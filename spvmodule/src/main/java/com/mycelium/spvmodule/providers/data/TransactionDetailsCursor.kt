package com.mycelium.spvmodule.providers.data

import android.database.MatrixCursor
import com.mycelium.spvmodule.providers.TransactionContract

/**
 * Created by Nelson on 17/10/2017.
 */
class TransactionDetailsCursor()
    : MatrixCursor(arrayOf(TransactionContract.TransactionDetails._ID, TransactionContract.TransactionDetails.HEIGHT,
        TransactionContract.TransactionDetails.TIME, TransactionContract.TransactionDetails.RAW_SIZE,
        TransactionContract.TransactionDetails.INPUTS, TransactionContract.TransactionDetails.OUTPUTS),
        1) {

}