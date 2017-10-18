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
import android.util.Log
import com.mycelium.modularizationtools.CommunicationManager
import org.bitcoinj.core.*
import org.bitcoinj.core.Context.propagate
import org.bitcoinj.wallet.SendRequest
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

class SpvService : IntentService("SpvService") {
    private val application = SpvModuleApplication.getApplication()
    private var notificationManager: NotificationManager? = null
    private var serviceCreatedAtMillis = System.currentTimeMillis()
    private var accountIndex: Int = -1

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intentsQueue.offer(intent)
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onHandleIntent(intent: Intent?) {
        Log.i(LOG_TAG, "onHandleIntent: ${intent?.action}")
        intentsQueue.remove()
        propagate(Constants.CONTEXT)
        if (intent != null) {
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
                    application.addWalletAccountWithExtendedKey(bip39Passphrase,
                            creationTimeSeconds, accountIndex)
                    Log.d(LOG_TAG, "onHandleIntent(), ACTION_ADD_ACCOUNT, setting futureSpvService to 0")
                }
                ACTION_BROADCAST_TRANSACTION -> {
                    val transactionByteArray = intent.getByteArrayExtra("TX")
                    val transaction = Transaction(Constants.NETWORK_PARAMETERS, transactionByteArray)
                    Log.i(LOG_TAG, "onHandleIntent: ACTION_BROADCAST_TRANSACTION,  TX = " + transaction)
                    transaction.confidence.source = TransactionConfidence.Source.SELF
                    transaction.purpose = Transaction.Purpose.USER_PAYMENT
                    application.broadcastTransaction(transaction, accountIndex)
                }
                ACTION_SEND_FUNDS -> {
                    val rawAddress = intent.getStringExtra(IntentContract.SendFunds.ADDRESS_EXTRA)
                    val rawAmount = intent.getLongExtra(IntentContract.SendFunds.AMOUNT_EXTRA, -1)
                    val feePerKb = intent.getLongExtra(IntentContract.SendFunds.FEE_EXTRA, -1)
                    if (rawAddress.isEmpty() || rawAmount < 0 || feePerKb < 0) {
                        Log.e(LOG_TAG, "Could not send funds with parameters rawAddress $rawAddress, "
                                + "rawAmount $rawAmount and feePerKb $feePerKb.")
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
                    if (!SpvModuleApplication.doesWalletAccountExist(accountIndex)) {
                        // Ask for private Key
                        SpvMessageSender.requestPrivateKey(accountIndex)
                        return
                    } else {
                        application.sendTransactions(accountIndex)
                    }
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
        Log.i(LOG_TAG, "service was up for ${(System.currentTimeMillis() - serviceCreatedAtMillis) / 1000 }s")
    }

    override fun onTrimMemory(level: Int) {
        Log.i(LOG_TAG, "onTrimMemory($level)")

        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            // TODO: stop service
            Log.e(LOG_TAG, "low memory detected, not stopping service")
        }
    }

    companion object {
        private val LOG_TAG = SpvService::class.java.simpleName
        private val PACKAGE_NAME = SpvService::class.java.`package`.name
        val ACTION_PEER_STATE = PACKAGE_NAME + ".peer_state"
        val ACTION_PEER_STATE_NUM_PEERS = "num_peers"
        val ACTION_BLOCKCHAIN_STATE = PACKAGE_NAME + ".blockchain_state"
        val ACTION_CANCEL_COINS_RECEIVED = PACKAGE_NAME + ".cancel_coins_received"
        val ACTION_ADD_ACCOUNT = PACKAGE_NAME + ".reset_blockchain"
        val ACTION_BROADCAST_TRANSACTION = PACKAGE_NAME + ".broadcast_transaction"
        val ACTION_RECEIVE_TRANSACTIONS = PACKAGE_NAME + ".receive_transactions"
        val ACTION_SEND_FUNDS = PACKAGE_NAME + ".send_funds"

        val intentsQueue: Queue<Intent> = ConcurrentLinkedQueue<Intent>()
    }
}
