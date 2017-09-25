package com.mycelium.spvmodule

import android.content.Context
import android.content.Intent
import android.util.Log
import com.mycelium.modularizationtools.ModuleMessageReceiver
import org.bitcoinj.utils.ContextPropagatingThreadFactory
import java.util.concurrent.Executors

class SpvMessageReceiver(private val context: Context) : ModuleMessageReceiver {
    @Synchronized
    override fun onMessage(callingPackageName: String, intent: Intent) {
        Log.d(LOG_TAG, "onMessage($callingPackageName, $intent)")
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT)
        val clone = intent.clone() as Intent
        clone.setClass(context, SpvService::class.java)
        when (intent.action) {
            IntentContract.SendFunds.ACTION -> {
                clone.action = SpvService.ACTION_SEND_FUNDS
            }
            IntentContract.ReceiveTransactions.ACTION -> {
                clone.action = SpvService.ACTION_RECEIVE_TRANSACTIONS
            }
            IntentContract.RequestPrivateExtendedKeyCoinTypeToSPV.ACTION -> {
                val bip39Passphrase = intent.getStringArrayListExtra(IntentContract.RequestPrivateExtendedKeyCoinTypeToSPV.BIP39_PASS_PHRASE_EXTRA)
                val accountIndex = intent.getIntExtra(IntentContract.ACCOUNT_INDEX_EXTRA, -1)
                if (accountIndex == -1) {
                    Log.e(LOG_TAG, "no account specified. Skipping ${intent.action}.")
                    return
                }
                val creationTimeSeconds = intent.getLongExtra(IntentContract.RequestPrivateExtendedKeyCoinTypeToSPV.CREATION_TIME_SECONDS_EXTRA, 0)
                SpvModuleApplication.getApplication()
                        .resetBlockchainWithExtendedKey(bip39Passphrase, creationTimeSeconds, accountIndex)
                return
            }
        }
        Log.d(LOG_TAG, "Will start Service $clone")
        // start service to check for new transactions and maybe to broadcast a transaction
        val executorService = Executors.newSingleThreadExecutor(
                ContextPropagatingThreadFactory("SpvMessageReceiverThreadFactory"))
        executorService.execute {
            Log.d(LOG_TAG, "Starting Service $clone")
            context.startService(clone)
        }
    }

    private val LOG_TAG: String = this.javaClass.canonicalName
}
