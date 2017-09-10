package com.mycelium.spvmodule

import android.content.Intent
import android.util.Log
import com.mycelium.modularizationtools.CommunicationManager
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionConfidence
import org.bitcoinj.core.TransactionOutput
import org.spongycastle.util.encoders.Hex
import org.spongycastle.util.encoders.HexEncoder
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList

class SpvMessageSender {
    companion object {
        private val LOG_TAG: String = SpvMessageSender::class.java.canonicalName

        fun sendTransactions(communicationManager: CommunicationManager,
                             transactionSet: Set<Transaction>,
                             unspentTransactionOutputSet: Set<TransactionOutput>,
                             receivingPackage:String? = null) {
            val transactions = transactionSet.map {
                val transactionBytes = it.bitcoinSerialize()
                //Log.d(LOG_TAG, "Sharing transaction $it with transaction bytes ${Hex.encode(transactionBytes)}")
                val blockHeight = when (it.confidence.confidenceType) {
                    TransactionConfidence.ConfidenceType.BUILDING -> it.confidence.appearedAtChainHeight
                // at the risk of finding Satoshi, values up to 5 are interpreted as type.
                // Sorry dude. Don't file this bug report.
                    else -> it.confidence.confidenceType.value
                }
                ByteBuffer.allocate(/* 1 int */ 4 + transactionBytes.size + /* 1 Long */ 8)
                        .putInt(blockHeight).put(transactionBytes).putLong(it.updateTime.time).array()
            }.toTypedArray()
            val txos = HashMap<String, ByteArray>()
            for(tx in transactionSet) {
                for(txi in tx.inputs) {
                    txos[txi.outpoint.toString()] = txi.connectedOutput?.bitcoinSerialize() ?: continue
                    //Log.d(LOG_TAG, "Sharing connected output $txi with ${Hex.encode(txi!!.connectedOutput!!.bitcoinSerialize())}")
                }
            }
            val utxos = unspentTransactionOutputSet.associate {
                //Log.d(LOG_TAG, "Sharing utxo ${it.outPointFor} ${Hex.encode(it.outPointFor.bitcoinSerialize())}")
                Pair(it.outPointFor.toString(), it.outPointFor.bitcoinSerialize())
            }
            val utxoHM = HashMap<String, ByteArray>(utxos.size).apply { putAll(utxos) }
            // report back known transactions
            val intent = Intent()
            intent.action = "com.mycelium.wallet.receivedTransactions"
            intent.putExtra("TRANSACTIONS", transactions)
            //dumpTxos(txos)
            intent.putExtra("CONNECTED_OUTPUTS", txos)
            intent.putExtra("UTXOS", utxoHM)
            send(communicationManager, intent)
        }

        private fun dumpTxos(txos: HashMap<String, ByteArray>) {
            txos.entries.forEach {
                val hexString = it.value.joinToString(separator = "") { String.format("%02x", it) }
                Log.d(LOG_TAG, "dumpTxos, ${it.key} : $hexString")
            }
        }

        fun send(communicationManager: CommunicationManager, intent: Intent) {
            communicationManager.send(SpvModuleApplication.getMbwModuleName(), intent)
        }

        fun requestPrivateKey(communicationManager: CommunicationManager) {
            if(BuildConfig.DEBUG) {
                Log.d(LOG_TAG, "requestPrivateKey")
            }
            val requestPrivateExtendedKeyCoinTypeIntent = Intent()
            requestPrivateExtendedKeyCoinTypeIntent.action = "com.mycelium.wallet.requestPrivateExtendedKeyCoinTypeToMBW"
            send(communicationManager, requestPrivateExtendedKeyCoinTypeIntent)
        }
    }
}
