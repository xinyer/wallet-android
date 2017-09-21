package com.mycelium.spvmodule.providers

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
        }
    }
}