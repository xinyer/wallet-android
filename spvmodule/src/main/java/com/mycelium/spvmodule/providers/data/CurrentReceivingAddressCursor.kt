package com.mycelium.spvmodule.providers.data

import android.database.MatrixCursor
import com.mycelium.spvmodule.providers.TransactionContract

class CurrentReceivingAddressCursor()
    : MatrixCursor(arrayOf(TransactionContract.CurrentReceiveAddress._ID,
        TransactionContract.CurrentReceiveAddress.ADDRESS),
        1) {

}