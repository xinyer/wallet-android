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
    override fun onMessage(callingPackageName: String, intent: Intent) {
        Log.d(LOG_TAG, "onMessage($callingPackageName, $intent)")
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT)
        val communicationManager = CommunicationManager.Companion.getInstance(context)
        val clone = intent.clone() as Intent
        clone.setClass(context, SpvService::class.java)
        when (intent.action) {
            "com.mycelium.wallet.broadcastTransaction" -> {
                val config = SpvModuleApplication.getApplication().configuration!!
                val txBytes = intent.getByteArrayExtra("TX")
                if(config.broadcastUsingWapi) {
                    asyncWapiBroadcast(txBytes)
                } else {
                    clone.action = SpvService.ACTION_BROADCAST_TRANSACTION
                    // this assumes the transaction makes it at least into the service' storage
                    // TODO: 9/5/16 make it splice in a round trip to the service to get more solid data on broadcastability.
                    val resultData = Intent()
                    // TODO: this intent has no action yet and neither is it handled on the receiving side
                    val tx = Transaction(Constants.NETWORK_PARAMETERS, txBytes)
                    resultData.putExtra("broadcastTX", tx.hashAsString)
                    communicationManager.send(callingPackageName, resultData)
                }
            }

            "com.mycelium.wallet.receiveTransactions" -> {
                // parse call
                //val addressStrings = intent.getStringArrayExtra("ADDRESSES")
                //val addresses = Lists.newArrayListWithCapacity<Address>(addressStrings.size)
                //val contentValuesArray = Lists.newArrayList<ContentValues>()
                //var minTimestamp = Long.MAX_VALUE
                // register addresses as ours
                val wallet = SpvModuleApplication.getWallet()
                if(wallet == null || wallet.keyChainGroupSize == 0) {
                    // Ask for private Key
                    SpvMessageSender.requestPrivateKey(communicationManager)
                    return
                }
                /*
                for (addressTimeString in addressStrings) {
                    val addressTimeStrings = addressTimeString.split(";")
                    if (addressTimeStrings.size != 2) {
                        Log.e(LOG_TAG, "Received $addressTimeString but expected format address;timestamp")
                    }
                    val addressString = addressTimeStrings[0]
                    val timestamp = addressTimeStrings[1].toLong()
                    val address = Address.fromBase58(Constants.NETWORK_PARAMETERS, addressString)
                    if(!wallet.isAddressWatched(address)) {
                        // as the blockchain has to be rescanned from the earliest of the
                        // timestamps, associating the earliest timestamp with all addresses that
                        // are to be added should not be a problem.
                        // Bulk-adding only allows one timestamp.
                        minTimestamp = Math.min(minTimestamp, timestamp)
                        addresses.add(address)

                        // insert
                        val values = ContentValues()
                        values.put(BlockchainContract.Address.ADDRESS_ID, addressString)
                        values.put(BlockchainContract.Address.CREATION_DATE, timestamp)
                        values.put(BlockchainContract.Address.SYNCED_TO_BLOCK, 0)
                        contentValuesArray.add(values)
                    }
                } */
                /*
                if(addresses.size > 0) {
                    wallet.addWatchedAddresses(addresses, minTimestamp)

                    // TODO: this is unsupported in bitcoinj-core:0.15
                    // wallet.clearTransactions(getBlockHeight(minTimestamp))
                    if(minTimestamp < wallet.lastBlockSeenTimeSecs
                            && minTimestamp < (System.currentTimeMillis() / 1000) - 600) {
                        // crude heuristics to avoid an unnecessary rescan, risking to miss a rescan.
                        Log.d(LOG_TAG, "minTimestamp = $minTimestamp, wallet.lastBlockSeenTimeSecs ="
                                + " ${wallet.lastBlockSeenTimeSecs},  we reset the blockchain.")
                        SpvModuleApplication.getApplication().resetBlockchain()
                    }
                    context.contentResolver.bulkInsert(
                            BlockchainContract.Address.CONTENT_URI(BuildConfig.APPLICATION_ID),
                            contentValuesArray.toTypedArray())
                }
                */
                // send back all known transactions. others will follow as we find them.
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
                SpvModuleApplication.getWallet() != null && SpvModuleApplication.getWallet()!!.keyChainGroupSize != 0) {
            // start service to check for new transactions and maybe to broadcast a transaction
            val executorService = Executors.newSingleThreadExecutor(ContextPropagatingThreadFactory("SpvMessageReceiverThreadFactory"))
            executorService.execute {
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
                e.printStackTrace()
            }
        }).start()
    }

    private val LOG_TAG: String = this.javaClass.canonicalName
}
