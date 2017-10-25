package com.mycelium.spvmodule.providers.data

import android.database.MatrixCursor
import com.mycelium.spvmodule.providers.TransactionContract

/**
 * Created by Nelson on 17/10/2017.
 */
class AccountBalanceCursor()
    : MatrixCursor(arrayOf(TransactionContract.AccountBalance._ID,
        TransactionContract.AccountBalance.BALANCE),
        1) {

}