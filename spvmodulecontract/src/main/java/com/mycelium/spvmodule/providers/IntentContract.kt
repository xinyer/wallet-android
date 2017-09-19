package com.mycelium.spvmodule.providers

/**
 * The contract between the [SpvService] and clients. Contains definitions
 * for the supported Actions.
 */
class IntentContract {
    companion object {
        @JvmField
        val ACTION_SEND_FUNDS = "com.mycelium.wallet.sendFunds"
    }
}