package com.mycelium.spvmodule.providers

import android.content.Intent

/**
 * The contract between the [SpvService] and clients. Contains definitions
 * for the supported Actions.
 */
class IntentContract {
    class SendFunds {
        companion object {
            @JvmField
            val ACTION = "com.mycelium.wallet.sendFunds"

            @JvmField
            val ADDRESS_EXTRA = ACTION + "_address"

            @JvmField
            val AMOUNT_EXTRA = ACTION + "_amount"

            @JvmField
            val FEE_EXTRA = ACTION + "_fee"

            @JvmStatic
            fun createIntent(address: String, amount: Long, fee: Long): Intent {
                val intent = Intent(IntentContract.SendFunds.ACTION)
                intent.putExtra(IntentContract.SendFunds.ADDRESS_EXTRA, address)
                intent.putExtra(IntentContract.SendFunds.AMOUNT_EXTRA, amount)
                intent.putExtra(IntentContract.SendFunds.FEE_EXTRA, fee)
                return intent;
            }
        }
    }
    companion object {
        @JvmField
        val ACCOUNT_INDEX_EXTRA = "ACCOUNT_INDEX"
    }
}