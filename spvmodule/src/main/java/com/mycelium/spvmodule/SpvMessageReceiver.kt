package com.mycelium.spvmodule

import android.content.Context
import android.content.Intent
import android.util.Log

import com.mycelium.modularizationtools.CommunicationManager
import com.mycelium.modularizationtools.ModuleMessageReceiver

import org.bitcoinj.core.Transaction
import org.bitcoinj.utils.ContextPropagatingThreadFactory
import java.util.concurrent.Executors
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class SpvMessageReceiver(private val context: Context) : ModuleMessageReceiver {
    @Synchronized
    override fun onMessage(callingPackageName: String, intent: Intent) {
        Log.d(LOG_TAG, "onMessage($callingPackageName, $intent)")
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT)
        val clone = intent.clone() as Intent
        clone.setClass(context, SpvService::class.java)
        clone.putExtra(IntentContract.ACCOUNT_INDEX_EXTRA, intent.getIntExtra(IntentContract.ACCOUNT_INDEX_EXTRA, -1))
        when (intent.action) {
            IntentContract.SendFunds.ACTION -> {
                clone.action = SpvService.ACTION_SEND_FUNDS
            }
            IntentContract.BroadcastTransaction.ACTION -> {
                val config = SpvModuleApplication.getApplication().configuration!!
                val txBytes = intent.getByteArrayExtra(IntentContract.BroadcastTransaction.TX_EXTRA)
                if (config.broadcastUsingWapi) {
                    asyncWapiBroadcast(txBytes)
                    return
                } else {
                    clone.action = SpvService.ACTION_BROADCAST_TRANSACTION
                }
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

    private fun asyncWapiBroadcast(tx: ByteArray) {
        Thread(Runnable {
            try {
                val url = URL("https://${if (Constants.TEST) "testnet." else "" }blockexplorer.com/api/tx/send")
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestMethod("POST")
                conn.setRequestProperty("Content-Type", "application/json;charset=UTF-8")
                conn.setRequestProperty("Accept", "application/json")
                conn.setDoOutput(true)
                conn.setDoInput(true)

                val jsonParam = JSONObject()
                jsonParam.put("rawtx", tx.map {String.format("%02X", it)}.joinToString(""))

                Log.i("JSON", jsonParam.toString())
                val os = DataOutputStream(conn.getOutputStream())
                os.writeBytes(jsonParam.toString())

                os.flush()
                os.close()

                val transaction = Transaction(Constants.NETWORK_PARAMETERS, tx)
                val intent = Intent("com.mycelium.wallet.broadcaststatus")
                intent.putExtra("tx", transaction.hash)
                intent.putExtra("result", if(conn.responseCode == 200) "success" else "failure")
                SpvMessageSender.send(CommunicationManager.getInstance(context), intent)

                conn.disconnect()
            } catch (e: Exception) {
                Log.e(LOG_TAG, e.localizedMessage, e)
            }
        }).start()
    }

    private val LOG_TAG: String = this.javaClass.canonicalName
}
