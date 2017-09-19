package com.mycelium.spvmodule

import android.content.Context
import android.content.Intent
import android.util.Log

import com.mycelium.modularizationtools.CommunicationManager
import com.mycelium.modularizationtools.ModuleMessageReceiver
import com.mycelium.spvmodule.providers.IntentContract

import org.bitcoinj.core.Transaction
import org.bitcoinj.utils.ContextPropagatingThreadFactory
import java.util.concurrent.Executors
import org.json.JSONObject
import java.io.DataOutputStream
import java.net.HttpURLConnection
import java.net.URL

class SpvMessageReceiver(private val context: Context) : ModuleMessageReceiver {
    override fun onMessage(callingPackageName: String, intent: Intent) {
        Log.d(LOG_TAG, "onMessage($callingPackageName, $intent)")
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT)
        val communicationManager = CommunicationManager.Companion.getInstance(context)
        val clone = intent.clone() as Intent
        clone.setClass(context, SpvService::class.java)
        when (intent.action) {
            IntentContract.ACTION_SEND_FUNDS -> {
                clone.action = SpvService.ACTION_SEND_FUNDS
            }
            "com.mycelium.wallet.broadcastTransaction" -> {
                val config = SpvModuleApplication.getApplication().configuration!!
                val txBytes = intent.getByteArrayExtra("TX")
                if(config.broadcastUsingWapi) {
                    asyncWapiBroadcast(txBytes)
                } else {
                    clone.action = SpvService.ACTION_BROADCAST_TRANSACTION
                }
            }
            "com.mycelium.wallet.receiveTransactions" -> {
                val wallet = SpvModuleApplication.getWallet()
                if(wallet == null || wallet.keyChainGroupSize == 0) {
                    // Ask for private Key
                    SpvMessageSender.requestPrivateKey(communicationManager)
                    return
                }

                val transactionSet = wallet.getTransactions(false)
                val utxos = wallet.unspents.toHashSet()
                if(!transactionSet.isEmpty() || !utxos.isEmpty()) {
                    SpvMessageSender.sendTransactions(communicationManager, transactionSet, utxos, callingPackageName)
                }
            }
            "com.mycelium.wallet.requestPrivateExtendedKeyCoinTypeToSPV" -> {
                val privateExtendedKeyCoinType = intent.getStringArrayListExtra("PrivateExtendedKeyCoinType")
                val creationTimeSeconds = intent.getLongExtra("creationTimeSeconds", 0)
                SpvModuleApplication.getApplication()
                        .resetBlockchainWithExtendedKey(privateExtendedKeyCoinType, creationTimeSeconds)
            }
        }
        if(intent.action != "com.mycelium.wallet.requestPrivateExtendedKeyCoinTypeToSPV" &&
                SpvModuleApplication.getWallet() != null &&
                SpvModuleApplication.getWallet()!!.keyChainGroupSize != 0) {
            Log.d(LOG_TAG, "Will start Service $clone")
            // start service to check for new transactions and maybe to broadcast a transaction
            val executorService = Executors.newSingleThreadExecutor(
                    ContextPropagatingThreadFactory("SpvMessageReceiverThreadFactory"))
            executorService.execute {
                Log.d(LOG_TAG, "Starting Service $clone")
                context.startService(clone)
            }
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
