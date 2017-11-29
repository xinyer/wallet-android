package com.mycelium.spvmodule.providers.data

import android.database.MatrixCursor
import com.mycelium.spvmodule.providers.TransactionContract

class VerifyAddressCursor()
    : MatrixCursor(arrayOf(TransactionContract.ValidateAddress.IS_CORRECT_ADDRESS), 1) {

}