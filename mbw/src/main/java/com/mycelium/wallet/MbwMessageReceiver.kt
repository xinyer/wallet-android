package com.mycelium.wallet

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.TextUtils
import android.util.Log
import com.mrd.bitlib.model.*
import com.mrd.bitlib.model.NetworkParameters.NetworkType.*
import com.mrd.bitlib.util.Sha256Hash
import com.mycelium.modularizationtools.ModuleMessageReceiver
import com.mycelium.wallet.activity.modern.ModernMain
import com.mycelium.wallet.event.SpvSyncChanged
import com.mycelium.wallet.persistence.MetadataStorage
import com.mycelium.wapi.model.TransactionEx
import com.mycelium.wapi.wallet.AbstractAccount
import com.mycelium.wapi.wallet.WalletAccount
import com.squareup.otto.Bus
import org.bitcoinj.core.TransactionConfidence.ConfidenceType.*
import org.bitcoinj.core.TransactionOutPoint
import java.nio.ByteBuffer
import java.util.*

class MbwMessageReceiver constructor(private val context: Context) : ModuleMessageReceiver {
    private val eventBus: Bus = MbwManager.getInstance(context).eventBus

    override fun onMessage(callingPackageName: String, intent: Intent) {
        Log.d(TAG, "onMessage($callingPackageName, $intent)")
        val walletManager = MbwManager.getInstance(context).getWalletManager(false)
        when (intent.action) {
            "com.mycelium.wallet.receivedTransactions" -> {
                val transactionsBytes = intent.getSerializableExtra("TRANSACTIONS") as Array<ByteArray>
                val outputs = bitcoinJ2Bitlib(intent.getSerializableExtra("CONNECTED_OUTPUTS") as Map<String, ByteArray>)//Funding outputs
                val utxoSet = (intent.getSerializableExtra("UTXOS") as Map<String, ByteArray>).keys.map {
                    val parts = it.split(":")
                    val hash = Sha256Hash.fromString(parts[0])
                    val index = parts[1].toInt()
                    OutPoint(hash, index)
                }.toSet()
                if (transactionsBytes.isEmpty()) {
                    Log.d(TAG, "onMessage: received an empty transaction notification")
                    return
                }
                val network = MbwEnvironment.verifyEnvironment(context).network
                val protocolId = when (network.networkType) {
                    PRODNET -> "main"
                    REGTEST -> "regtest"
                    TESTNET -> "test"
                    else -> ""
                }
                val networkBJ = org.bitcoinj.core.NetworkParameters.fromPmtProtocolID(protocolId)
                var satoshisReceived = 0L
                val mds = MbwManager.getInstance(context).metadataStorage
                val affectedAccounts = ArrayList<WalletAccount>()
                try {
                    for (confTransactionBytes in transactionsBytes) {
                        val bb = ByteBuffer.wrap(confTransactionBytes)
                        val blockHeight = bb.int
                        val txBytes = ByteArray(bb.capacity() - 4)
                        bb.get(txBytes, 0, txBytes.size)

                        val tx = Transaction.fromBytes(txBytes)
                        Log.d(TAG, "onReceive: tx received: " + tx)
                        val connectedOutputs = HashMap<OutPoint, TransactionOutput>()
                        for (input in tx.inputs) {
                            val connectedOutput = outputs[input.outPoint] ?: // skip this
                                    continue
                            connectedOutputs.put(input.outPoint, connectedOutput)
                            val address = connectedOutput.script.getAddress(network)
                            val optionalAccount = walletManager.getAccountByAddress(address)
                            if (optionalAccount.isPresent) {
                                val account = walletManager.getAccount(optionalAccount.get())
                                if (account.getTransaction(tx.hash) == null) {
                                    // The transaction is new and relevant for the account.
                                    // We found spending from the account.
                                    satoshisReceived -= connectedOutput.value
                                }
                                affectedAccounts.add(account)
                            }
                        }
                        for (output in tx.outputs) {
                            val address = output.script.getAddress(network)
                            val optionalAccount = walletManager.getAccountByAddress(address)
                            if (optionalAccount.isPresent) {
                                val account = walletManager.getAccount(optionalAccount.get())
                                if (account.getTransaction(tx.hash) == null) {
                                    // The transaction is new and relevant for the account.
                                    // We found spending from the account.
                                    satoshisReceived += output.value
                                }
                                affectedAccounts.add(account)
                            }
                        }
                        for (a in affectedAccounts) {
                            when (blockHeight) {
                                DEAD.value -> Log.e(TAG, "transaction is dead")
                                IN_CONFLICT.value -> Log.e(TAG, "transaction is in conflict")
                                UNKNOWN.value, PENDING.value -> a.notifyNewTransactionDiscovered(TransactionEx.fromUnconfirmedTransaction(txBytes), connectedOutputs, utxoSet, false)
                                else -> {
                                    val txBJ = org.bitcoinj.core.Transaction(networkBJ, txBytes)
                                    val txid = Sha256Hash.fromString(txBJ.hash.toString())
                                    val time = (txBJ.updateTime.time / 1000L).toInt()
                                    val tEx = TransactionEx(txid, blockHeight, time, txBytes)
                                    a.notifyNewTransactionDiscovered(tEx, connectedOutputs, utxoSet, false)
                                }
                            }
                        }
                    }
                    // account has a new incoming transaction!
                    if (satoshisReceived > 0) {
                        notifySatoshisReceived(satoshisReceived, mds, affectedAccounts)
                    }
                } catch (e: Transaction.TransactionParsingException) {
                    Log.e(TAG, e.message, e)
                }
            }
            "com.mycelium.wallet.blockchainState" -> {
                val bestChainDate = intent.getLongExtra("best_chain_date", 0L)
                val bestChainHeight = intent.getIntExtra("best_chain_height", 0)
                val replaying = intent.getBooleanExtra("replaying", true)
                val impediments = intent.getStringArrayExtra("impediment")
                Log.d(TAG, """
                Blockchain state is
                Best date:   ${Date(bestChainDate)}
                Best height: $bestChainHeight
                Replaying:   ${if (replaying) "yes" else "no"}
                Impediments: [${TextUtils.join(",", impediments)}]
                """.trimIndent())
                walletManager.activeAccounts
                        .filterIsInstance<AbstractAccount>()
                        .forEach { it.blockChainHeight = bestChainHeight }
                eventBus.post(SpvSyncChanged(Date(bestChainDate), bestChainHeight.toLong()))
            }
            null -> Log.w(TAG, "onMessage failed. No action defined.")
            else -> Log.e(TAG, "onMessage failed. Unknown action ${intent.action}")
        }
    }

    private fun notifySatoshisReceived(satoshisReceived: Long, mds: MetadataStorage,
                                       affectedAccounts: List<WalletAccount>) {
        val builder = Notification.Builder(context)
        // TODO: bitcoin icon
        builder.setSmallIcon(R.drawable.holo_dark_ic_action_new_usd_account)
        builder.setContentTitle(context.getString(R.string.app_name))
        var contentText = context.getString(R.string.receiving, satoshisReceived.toString() + "sat")
        val accountString: String
        if (affectedAccounts.size > 1) {
            accountString = "various accounts"
        } else {
            accountString = mds.getLabelByAccount(affectedAccounts[0].id)
        }
        contentText += " To $accountString"
        builder.setContentText(contentText)
        builder.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, ModernMain::class.java), 0))
        builder.setWhen(System.currentTimeMillis())
        builder.setSound(Uri.parse("android.resource://${context.packageName}/${R.raw.coins_received}"))
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(TRANSACTION_NOTIFICATION_ID, builder.build())
    }

    private fun bitcoinJ2Bitlib(bitcoinJConnectedOutputs: Map<String, ByteArray>): Map<OutPoint, TransactionOutput> {
        val connectedOutputs = HashMap<OutPoint, TransactionOutput>(bitcoinJConnectedOutputs.size)
        for (id in bitcoinJConnectedOutputs.keys) {
            val parts = id.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val hash = Sha256Hash.fromString(parts[0])
            val index = Integer.parseInt(parts[1])
            val outPoint = OutPoint(hash, index)

            val buffer = ByteBuffer.wrap(bitcoinJConnectedOutputs[id])
            val value = buffer.long
            val scriptBytes = ByteArray(buffer.capacity() - 8)
            buffer.get(scriptBytes, 0, scriptBytes.size)
            val transactionOutput = TransactionOutput(value, ScriptOutput.fromScriptBytes(scriptBytes))
            connectedOutputs.put(outPoint, transactionOutput)
        }
        return connectedOutputs
    }

    companion object {
        private val TAG = MbwMessageReceiver::class.java.canonicalName
        val TRANSACTION_NOTIFICATION_ID = -553794088
    }
}
