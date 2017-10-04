/*
 * Copyright 2011-2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mycelium.spvmodule

import android.app.IntentService
import android.app.NotificationManager
import android.content.*
import android.database.Cursor
import android.text.format.DateUtils
import android.util.Log
import org.bitcoinj.core.*
import org.bitcoinj.wallet.SendRequest
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.locks.ReentrantLock

class SpvService : IntentService("SpvService"), Loader.OnLoadCompleteListener<Cursor> {
    private val application = SpvModuleApplication.getApplication()
    private var notificationManager: NotificationManager? = null
    private var serviceCreatedAtMillis = System.currentTimeMillis()
    private val LOADER_ID_NETWORK: Int = 0
    private var reentrantLock = ReentrantLock(true)
    private var accountIndex: Int = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intentsQueue.offer(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        intentsQueue.remove()
        org.bitcoinj.core.Context.propagate(Constants.CONTEXT)
        if (intent != null) {
            if(BuildConfig.DEBUG) {
                Log.i(LOG_TAG, "onHandleIntent: $intent ${
                if (intent.hasExtra(Intent.EXTRA_ALARM_COUNT))
                    " (alarm count: ${intent.getIntExtra(Intent.EXTRA_ALARM_COUNT, 0)})"
                else
                    ""
                }")
            }
            accountIndex = intent.getIntExtra(IntentContract.ACCOUNT_INDEX_EXTRA, -1)
            if (accountIndex == -1) {
                Log.e(LOG_TAG, "no account specified. Skipping ${intent.action}.")
                return
            }


            when (intent.action) {
                ACTION_CANCEL_COINS_RECEIVED -> {
                    notificationManager!!.cancel(Constants.NOTIFICATION_ID_COINS_RECEIVED)
                }
                ACTION_ADD_ACCOUNT -> {
                    val bip39Passphrase = intent.getStringArrayListExtra("bip39Passphrase")
                    val creationTimeSeconds = intent.getLongExtra("creationTimeSeconds", 0)
                    application.addAccountWalletWithExtendedKey(bip39Passphrase,
                            creationTimeSeconds, accountIndex)
                    Log.d(LOG_TAG, "onHandleIntent(), ACTION_ADD_ACCOUNT, setting futureSpvService to 0")
                }
                ACTION_BROADCAST_TRANSACTION -> {
                    val transactionByteArray = intent.getByteArrayExtra("TX")
                    val transaction = Transaction(Constants.NETWORK_PARAMETERS, transactionByteArray)
                    Log.i(LOG_TAG, "onHandleIntent: ACTION_BROADCAST_TRANSACTION,  TX = " + transaction)
                    transaction.getConfidence().setSource(TransactionConfidence.Source.SELF);
                    transaction.setPurpose(Transaction.Purpose.USER_PAYMENT);
                    application.broadcastTransaction(transaction, accountIndex)
                }
                ACTION_SEND_FUNDS -> {
                    val rawAddress = intent.getStringExtra(IntentContract.SendFunds.ADDRESS_EXTRA)
                    val rawAmount = intent.getLongExtra(IntentContract.SendFunds.AMOUNT_EXTRA, -1)
                    val feePerKb = intent.getLongExtra(IntentContract.SendFunds.FEE_EXTRA, -1)
                    if (rawAddress.isEmpty() || rawAmount < 0 || feePerKb < 0) {
                        Log.e(LOG_TAG, "Could not send funds with parameters rawAddress $rawAddress, rawAmount $rawAmount and feePerKb $feePerKb.")
                        return
                    }
                    val address = Address.fromBase58(Constants.NETWORK_PARAMETERS, rawAddress)
                    val amount = Coin.valueOf(rawAmount)
                    val sendRequest = SendRequest.to(address, amount)
                    if (feePerKb > 0) {
                        sendRequest.feePerKb = Coin.valueOf(feePerKb)
                    }
                    application.broadcastTransaction(sendRequest, accountIndex)
                }
                ACTION_RECEIVE_TRANSACTIONS -> {
                    //Not relevant anymore.
                }
                else -> {
                    Log.e(LOG_TAG,
                            "Unhandled action was ${intent.action}. Initializing blockchain " +
                                    "for account $accountIndex.")
                }
            }
        } else {
            Log.w(LOG_TAG, "onHandleIntent: service restart, although it was started as non-sticky")
        }
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, ".onDestroy()")

        intentsQueue.clear()
        super.onDestroy()
        Log.i(LOG_TAG, "service was up for ${(System.currentTimeMillis() - serviceCreatedAtMillis) / 1000 / 60} minutes")
    }

    override fun onTrimMemory(level: Int) {
        Log.i(LOG_TAG, "onTrimMemory($level)")

        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            // TODO: stop service
            Log.e(LOG_TAG, "low memory detected, not stopping service")
        }
    }

    private var cursor: Cursor? = null
    /**
     * Called on the thread that created the Loader when the load is complete.
     *
     * @param loader the loader that completed the load
     * @param data the result of the load
     */
    override fun onLoadComplete(loader: Loader<Cursor>?, data: Cursor?) {
        if(BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "onLoadComplete, Cursor is ${data?.count} size")
        }
        cursor?.close()
        cursor = data
        if(reentrantLock.isLocked) {
            reentrantLock.unlock()
        }
    }

    private val semaphore = Semaphore(1, true)

    /*
    private fun notifyTransactions(transactions: Set<Transaction>) {
        semaphore.acquire()
        if (!transactions.isEmpty()) {
            // send the new transaction and the *complete* utxo set of the wallet
            val utxos = wallet!!.unspents.toSet()
            SpvMessageSender.sendTransactions(CommunicationManager.getInstance(this), transactions, utxos)
        }
        semaphore.release()
    }
    */

    companion object {
        private val LOG_TAG = this::class.java.canonicalName
        private val PACKAGE_NAME = SpvService::class.java.`package`.name
        val ACTION_PEER_STATE = PACKAGE_NAME + ".peer_state"
        val ACTION_PEER_STATE_NUM_PEERS = "num_peers"
        val ACTION_BLOCKCHAIN_STATE = PACKAGE_NAME + ".blockchain_state"
        val ACTION_CANCEL_COINS_RECEIVED = PACKAGE_NAME + ".cancel_coins_received"
        val ACTION_ADD_ACCOUNT = PACKAGE_NAME + ".reset_blockchain"
        val ACTION_BROADCAST_TRANSACTION = PACKAGE_NAME + ".broadcast_transaction"
        val ACTION_RECEIVE_TRANSACTIONS = PACKAGE_NAME + ".receive_transactions"
        val ACTION_BROADCAST_TRANSACTION_HASH = "hash"
        val ACTION_SEND_FUNDS = PACKAGE_NAME + ".send_funds"

        private val MIN_COLLECT_HISTORY = 2
        private val IDLE_BLOCK_TIMEOUT_MIN = 2
        private val IDLE_TRANSACTION_TIMEOUT_MIN = 9
        private val MAX_HISTORY_SIZE = Math.max(IDLE_TRANSACTION_TIMEOUT_MIN, IDLE_BLOCK_TIMEOUT_MIN)
        private val APPWIDGET_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS

        var intentsQueue: Queue<Intent> = LinkedList<Intent>()
    }
}
