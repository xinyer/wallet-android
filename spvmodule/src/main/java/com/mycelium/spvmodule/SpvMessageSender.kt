package com.mycelium.spvmodule

import android.content.Intent
import android.util.Log
import com.mycelium.modularizationtools.CommunicationManager
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionConfidence
import org.bitcoinj.core.TransactionOutput
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList

class SpvMessageSender {
    companion object {
        private val LOG_TAG: String? = SpvMessageSender.javaClass.canonicalName

        fun sendTransactions(communicationManager: CommunicationManager,
                             transactionSet: Set<Transaction>,
                             unspentTransactionOutputSet: Set<TransactionOutput>,
                             receivingPackage:String? = null) {
            val transactions = transactionSet.map {
                val transactionBytes = it.bitcoinSerialize()
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
                }
            }
            val utxos = unspentTransactionOutputSet.associate { Pair(it.outPointFor.toString(), it.outPointFor.bitcoinSerialize()) }
            val utxoHM = HashMap<String, ByteArray>(utxos.size).apply { putAll(utxos) }
            // report back known transactions
            val intent = Intent()
            intent.action = "com.mycelium.wallet.receivedTransactions"
            intent.putExtra("TRANSACTIONS", transactions)
            intent.putExtra("CONNECTED_OUTPUTS", txos)
            intent.putExtra("UTXOS", utxoHM)
            send(communicationManager, intent, receivingPackage)
        }

        fun send(communicationManager: CommunicationManager, intent: Intent, receivingPackage: String? = null) {
            // TODO: This should share the information with whoever is using the package, in a more consumer agnostic way.
            // CommunicationManager.getInstance(this).getPairedPackages( ;) )
            if(receivingPackage != null) {
                communicationManager.send(receivingPackage, intent)
            } else {
                if (BuildConfig.APPLICATION_ID.contains(".test")) {
                    arrayOf("com.mycelium.devwallet_spore", "com.mycelium.testnetwallet")
                            .forEach {communicationManager.send(it, intent)}
                } else {
                    communicationManager.send("com.mycelium.wallet", intent)
                }
            }
        }
    }
}
