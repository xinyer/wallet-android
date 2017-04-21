package com.mycelium.spvmodule

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.util.Log

import com.google.common.collect.Lists
import com.mycelium.modularizationtools.CommunicationManager
import com.mycelium.modularizationtools.ModuleMessageReceiver
import com.mycelium.spvmodule.Constants.Companion.TAG
import com.mycelium.spvmodule.providers.BlockchainContract

import org.bitcoinj.core.Address
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.TransactionConfidence
import org.bitcoinj.core.TransactionConfidence.ConfidenceType.*
import org.bitcoinj.core.TransactionOutput
import java.nio.ByteBuffer
import java.util.*

class SpvMessageReceiver(private val context: Context) : ModuleMessageReceiver {
    override fun onMessage(callingPackageName: String, intent: Intent) {
        Log.d(TAG, "onStartCommand($callingPackageName, $intent)")
        val communicationManager = CommunicationManager.Companion.getInstance(context)
        val clone = intent.clone() as Intent
        clone.setClass(context, SpvService::class.java)
        when (intent.action) {
            "com.mycelium.wallet.broadcastTransaction" -> {
                clone.action = SpvService.ACTION_BROADCAST_TRANSACTION
                val txBytes = intent.getByteArrayExtra("TX")
                val tx = Transaction(Constants.NETWORK_PARAMETERS, txBytes)
                // this assumes the transaction makes it at least into the service' storage
                // TODO: 9/5/16 make it splice in a round trip to the service to get more solid data on broadcastability.
                val resultData = Intent()
                // TODO: this intent has no action yet and neither is it handled on the receiving side
                resultData.putExtra("broadcastTX", tx.hashAsString)
                communicationManager.send(callingPackageName, resultData)
            }
            "com.mycelium.wallet.receiveTransactions" -> {
                // parse call
                val addressStrings = intent.getStringArrayExtra("ADDRESSES")
                val addresses = Lists.newArrayListWithCapacity<Address>(addressStrings.size)
                val contentValuesArray = Lists.newArrayList<ContentValues>()
                var minTimestamp = Long.MAX_VALUE
                // register addresses as ours
                val wallet = SpvModuleApplication.getWallet()
                for (addressTimeString in addressStrings) {
                    val addressTimeStrings = addressTimeString.split(";")
                    if (addressTimeStrings.size != 2) {
                        Log.e(TAG, "Received $addressTimeString but expected address;timestamp")
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
                }
                if(addresses.size > 0) {
                    // bulk insert now
                    wallet.addWatchedAddresses(addresses, minTimestamp)
                    context.contentResolver.bulkInsert(BlockchainContract.Address.CONTENT_URI(BuildConfig.APPLICATION_ID), contentValuesArray.toTypedArray())
                }
                // send back all known transactions. others will follow as we find them.
                val transactionSet = wallet.getTransactions(false)
                val utxos = wallet.unspents.toHashSet()
                SpvMessageSender.sendTransactions(communicationManager, transactionSet, utxos, callingPackageName)
            }
        }
        // start service to check for new transactions and maybe to broadcast a transaction
        context.startService(clone)
    }
}
