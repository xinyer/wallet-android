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
import android.content.Context
import android.database.Cursor
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Handler
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.support.v4.content.LocalBroadcastManager
import android.text.format.DateUtils
import android.util.Log
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.mycelium.modularizationtools.CommunicationManager
import com.mycelium.spvmodule.BlockchainState.Impediment
import com.mycelium.spvmodule.providers.BlockchainContract
import org.bitcoinj.core.*
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.store.BlockStoreException
import org.bitcoinj.utils.Threading
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.io.IOException
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

class SpvService : IntentService("SpvService"), Loader.OnLoadCompleteListener<Cursor> {
    private var application: SpvModuleApplication? = null
    private var config: Configuration? = null


    private val handler = Handler()
    private var wakeLock: WakeLock? = null

    private var notificationManager: NotificationManager? = null
    private val transactionsReceived = AtomicInteger()
    private var serviceCreatedAtInMs: Long = 0
    private var resetBlockchainOnShutdown = false

    private var cursorLoader: CursorLoader? = null




    private val walletEventListener = object
        : ThrottlingWalletChangeListener(APPWIDGET_THROTTLE_MS) {
        override fun onChanged(wallet: Wallet) {
            notifyTransactions(wallet!!.getTransactions(true))
        }

        override fun onTransactionConfidenceChanged(wallet: Wallet, tx: Transaction) {
            if(tx.confidence?.depthInBlocks?:0 > 7) {
                // don't update on all transactions individually just because we found a new block
                return
            }
            super.onTransactionConfidenceChanged(wallet, tx)
        }

        override fun onCoinsReceived(wallet: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin) {
            transactionsReceived.incrementAndGet()
            super.onCoinsReceived(wallet, tx, prevBalance, newBalance)
        }

        override fun onCoinsSent(wallet: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin) {
            transactionsReceived.incrementAndGet()
            super.onCoinsSent(wallet, tx, prevBalance, newBalance)
        }
    }

    private val LOG_TAG: String? = this::class.java.canonicalName

    /*
    private val tickReceiver = TickReceiver()
    inner class TickReceiver : BroadcastReceiver() {
        private var lastChainHeight = 0
        private val activityHistory = LinkedList<ActivityHistoryEntry>()

        override fun onReceive(context: Context, intent: Intent) {
            val chainHeight = blockChain!!.bestChainHeight

            if (lastChainHeight > 0) {
                val numBlocksDownloaded = chainHeight - lastChainHeight
                val numTransactionsReceived = transactionsReceived.getAndSet(0)

                // push history
                activityHistory.add(0, ActivityHistoryEntry(numTransactionsReceived, numBlocksDownloaded))

                // trim
                while (activityHistory.size > MAX_HISTORY_SIZE)
                    activityHistory.removeAt(activityHistory.size - 1)

                // print
                val builder = StringBuilder()
                for (entry in activityHistory) {
                    if (builder.isNotEmpty()) {
                        builder.append(", ")
                    }
                    builder.append(entry)
                }
                Log.i(LOG_TAG, "History of transactions/blocks: " + builder)

                // determine if block and transaction activity is idling
                var isIdle = false
                if (activityHistory.size >= MIN_COLLECT_HISTORY) {
                    isIdle = true
                    for (i in activityHistory.indices) {
                        val entry = activityHistory[i]
                        val blocksActive = entry.numBlocksDownloaded > 0 && i <= IDLE_BLOCK_TIMEOUT_MIN
                        val transactionsActive = entry.numTransactionsReceived > 0 && i <= IDLE_TRANSACTION_TIMEOUT_MIN

                        if (blocksActive || transactionsActive) {
                            isIdle = false
                            break
                        }
                    }
                }

                // if idling, shutdown service
                if (isIdle) {
                    Log.i(LOG_TAG, "Think that idling is detected, would have tried to stop service")
                }
            }

            lastChainHeight = chainHeight
        }
    }
    */

    private val LOADER_ID_NETWORK: Int = 0

    //private var futureSpvService : SettableFuture<Long>? = null
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

            //futureSpvService = SettableFuture.create<Long>();

            if(wakeLock == null) {
                // if we still hold a wakelock, we don't leave it dangling to block until later.
                val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName blockchain sync")
            } else {
                Log.d(LOG_TAG, "recycling wakeLock")
            }
            if(!wakeLock!!.isHeld) {
                wakeLock!!.acquire()
            } else {
                Log.d(LOG_TAG, "already held wakeLock")
            }

            when (intent.action) {
                ACTION_CANCEL_COINS_RECEIVED -> {
                    notificationManager!!.cancel(Constants.NOTIFICATION_ID_COINS_RECEIVED)
                }
                ACTION_ADD_ACCOUNT -> {
                    val bip39Passphrase = intent.getStringArrayListExtra("bip39Passphrase")
                    val creationTimeSeconds = intent.getLongExtra("creationTimeSeconds", 0)
                    application!!.addAccountWalletWithExtendedKey(bip39Passphrase,
                            creationTimeSeconds, accountIndex)
                    Log.d(LOG_TAG, "onHandleIntent(), ACTION_ADD_ACCOUNT, setting futureSpvService to 0")
                }
                ACTION_BROADCAST_TRANSACTION -> {
                    val transactionByteArray = intent.getByteArrayExtra("TX")
                    val transaction = Transaction(Constants.NETWORK_PARAMETERS, transactionByteArray)
                    Log.i(LOG_TAG, "onHandleIntent: ACTION_BROADCAST_TRANSACTION,  TX = " + transaction)
                    transaction.getConfidence().setSource(TransactionConfidence.Source.SELF);
                    transaction.setPurpose(Transaction.Purpose.USER_PAYMENT);
                    application!!.broadcastTransaction(transaction, accountIndex)
                }
                ACTION_SEND_FUNDS -> {
                    val rawAddress = intent.getStringExtra(IntentContract.SendFunds.ADDRESS_EXTRA)
                    val rawAmount = intent.getLongExtra(IntentContract.SendFunds.AMOUNT_EXTRA, 0)
                    val feePerKb = intent.getLongExtra(IntentContract.SendFunds.FEE_EXTRA, 0)
                    val address = Address.fromBase58(Constants.NETWORK_PARAMETERS, rawAddress)
                    val amount = Coin.valueOf(rawAmount)
                    val sendRequest = SendRequest.to(address, amount)
                    if (feePerKb > 0) {
                        sendRequest.feePerKb = Coin.valueOf(feePerKb)
                    }
                    application!!.broadcastTransaction(sendRequest, accountIndex)
                }
                ACTION_RECEIVE_TRANSACTIONS -> {
                    /*
                    val tmpWallet = SpvModuleApplication.getWallet(accountIndex)
                    if(tmpWallet == null || tmpWallet.keyChainGroupSize == 0) {
                        // Ask for private Key
                        SpvMessageSender.requestPrivateKey(CommunicationManager.getInstance(this), accountIndex)
                        return
                    }
                    initializeBlockchain(null, 0)
                    Log.d(LOG_TAG, "com.mycelium.wallet.receiveTransactions,  accountIndex = $accountIndex")
                    val transactionSet = wallet!!.getTransactions(false)
                    val utxos = wallet!!.unspents.toHashSet()
                    if(!transactionSet.isEmpty() || !utxos.isEmpty()) {
                        SpvMessageSender.sendTransactions(CommunicationManager.getInstance(this), transactionSet, utxos)
                    }
                    */
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

    @Synchronized // TODO: why are we getting here twice in parallel???
    fun initializeBlockchainTODELETE(seedStringArray: ArrayList<String>?, creationTimeSeconds : Long) {
        serviceCreatedAtInMs = System.currentTimeMillis()

        try {
            unregisterReceiver(connectivityReceiver)
        } catch (e : IllegalArgumentException) {
            //Receiver not registered.
            //Log.e(LOG_TAG, e.localizedMessage, e)
            // TODO TEMP FIX // DEBUG THAT

        } catch (e : UninitializedPropertyAccessException) {}

        connectivityReceiver = ConnectivityReceiver()
        Log.d(LOG_TAG, "initializeBlockchain, registering ConnectivityReceiver")
        registerReceiver(connectivityReceiver, intentFilter) // implicitly start PeerGroup
        wallet!!.addCoinsReceivedEventListener(Threading.SAME_THREAD, walletEventListener)
        wallet!!.addCoinsSentEventListener(Threading.SAME_THREAD, walletEventListener)
        wallet!!.addChangeEventListener(Threading.SAME_THREAD, walletEventListener)
        wallet!!.addTransactionConfidenceEventListener(Threading.SAME_THREAD, walletEventListener)
/*
        try {
            unregisterReceiver(tickReceiver)
        } catch (e : IllegalArgumentException) {
            //Receiver not registered.
            //Log.e(LOG_TAG, e.localizedMessage, e)
            // TODO TEMP FIX // DEBUG THAT
        }
        registerReceiver(tickReceiver, IntentFilter(Intent.ACTION_TIME_TICK))
        */
    }

    override fun onDestroy() {
        Log.d(LOG_TAG, ".onDestroy()")

        intentsQueue.clear()

        // Stop the cursor loader
        if (cursorLoader != null) {
            cursorLoader!!.unregisterListener(this)
            cursorLoader!!.cancelLoad()
            cursorLoader!!.stopLoading()
        }
        cursor?.close()

        SpvModuleApplication.scheduleStartBlockchainService(this)
        /*try {
            unregisterReceiver(tickReceiver)
        } catch (e : IllegalArgumentException) {
            //Receiver not registered.
            //Log.e(LOG_TAG, e.localizedMessage, e)
        } */
        if(wallet != null) {
            wallet!!.removeChangeEventListener(walletEventListener)
            wallet!!.removeCoinsSentEventListener(walletEventListener)
            wallet!!.removeCoinsReceivedEventListener(walletEventListener)
        }

        try {
            unregisterReceiver(connectivityReceiver)
        } catch (e : IllegalArgumentException) {
            //Receiver not registered.
            //Log.e(LOG_TAG, e.localizedMessage, e)
        } catch (e : UninitializedPropertyAccessException) {}

        if (peerGroup != null) {
            peerGroup!!.removeDisconnectedEventListener(peerConnectivityListener!!)
            peerGroup!!.removeConnectedEventListener(peerConnectivityListener!!)
            peerGroup!!.removeWallet(wallet)
            if(peerGroup!!.isRunning) {
                peerGroup!!.stop()
            }

            Log.i(LOG_TAG, "onDestroy, peergroup stopped")
        }

        if(peerConnectivityListener != null) {
            peerConnectivityListener!!.stop()
        }
        blockStore?.close()

        val start = System.currentTimeMillis()

        if(wallet != null) {
            val walletFile = getFileStreamPath(Constants.Files.WALLET_FILENAME_PROTOBUF + "_$accountIndex")
            wallet!!.saveToFile(walletFile)
            Log.d(LOG_TAG, "wallet saved to: $walletFile', took ${System.currentTimeMillis() - start}ms")
        }

        if (resetBlockchainOnShutdown && blockChainFile != null) {
            Log.i(LOG_TAG, "removing blockchain, reset blockchain on shutdown")
            blockChainFile!!.delete()
            resetBlockchainOnShutdown = false
        }

        if (wakeLock != null && wakeLock!!.isHeld) {
            Log.d(LOG_TAG, "wakelock still held, releasing")
            wakeLock!!.release()
            wakeLock = null
        }

        super.onDestroy()
        Log.i(LOG_TAG, "service was up for ${(System.currentTimeMillis() - serviceCreatedAtInMs) / 1000 / 60} minutes")
    }

    override fun onTrimMemory(level: Int) {
        Log.i(LOG_TAG, "onTrimMemory($level)")

        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            Log.w(LOG_TAG, "low memory detected, stopping service")
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

    private fun notifyTransactions(transactions: Set<Transaction>) {
        semaphore.acquire()
        if (!transactions.isEmpty()) {
            // send the new transaction and the *complete* utxo set of the wallet
            val utxos = wallet!!.unspents.toSet()
            SpvMessageSender.sendTransactions(CommunicationManager.getInstance(this), transactions, utxos)
        }
        semaphore.release()
    }

    companion object {
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
