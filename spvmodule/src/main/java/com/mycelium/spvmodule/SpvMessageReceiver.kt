package com.mycelium.spvmodule

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
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
import org.bitcoinj.store.BlockStoreException
import org.bitcoinj.store.SPVBlockStore
import java.io.File
import java.nio.ByteBuffer
import java.util.*
import java.util.concurrent.Executors

class SpvMessageReceiver(private val context: Context) : ModuleMessageReceiver {

    override fun onMessage(callingPackageName: String, intent: Intent) {
        Log.d(LOG_TAG, "onMessage($callingPackageName, $intent)")
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT)
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

                    // TODO: this is unsupported in bitcoinj-core:0.14.3
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
                val transactionSet = wallet!!.getTransactions(false)
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
            val executorService = Executors.newSingleThreadExecutor()
            executorService.execute {
                context.startService(clone)
            }
        }
    }

    private val LOG_TAG: String? = this.javaClass.canonicalName

    private fun  getBlockHeight(minTimestamp: Long): Int {
        val blockChainFile = File(context.getDir("blockstore", Context.MODE_PRIVATE), Constants.Files.BLOCKCHAIN_FILENAME)
        if (!blockChainFile.exists()) {
            Log.e(LOG_TAG, "blockchain file not found!?!?")
            return -1
        }
        try {
            val blockStore = SPVBlockStore(Constants.NETWORK_PARAMETERS, blockChainFile)
            var block = blockStore.chainHead // detect corruptions as early as possible
            while(block.header.timeSeconds > minTimestamp - 4 * 60 * 60) { // block timestamps may be off by 4h. be safe.
                block = block.getPrev(blockStore)
            }
            return block.height
        } catch (x: BlockStoreException) {
            val msg = "blockstore cannot be created"
            Log.e(LOG_TAG, msg, x)
            throw Error(msg, x)
        }
    }
}
