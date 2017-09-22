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
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.Context
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.database.Cursor
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.PowerManager.WakeLock
import android.support.v4.content.LocalBroadcastManager
import android.text.format.DateUtils
import android.util.Log
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import com.mycelium.modularizationtools.CommunicationManager
import com.mycelium.spvmodule.BlockchainState.Impediment
import com.mycelium.spvmodule.providers.BlockchainContract
import com.mycelium.spvmodule.IntentContract
import org.bitcoinj.core.*
import org.bitcoinj.core.listeners.DownloadProgressTracker
import org.bitcoinj.core.listeners.PeerConnectedEventListener
import org.bitcoinj.core.listeners.PeerDisconnectedEventListener
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.net.discovery.MultiplexingDiscovery
import org.bitcoinj.net.discovery.PeerDiscovery
import org.bitcoinj.net.discovery.PeerDiscoveryException
import org.bitcoinj.store.BlockStore
import org.bitcoinj.store.BlockStoreException
import org.bitcoinj.store.SPVBlockStore
import org.bitcoinj.utils.Threading
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.SendRequest
import org.bitcoinj.wallet.Wallet
import java.io.File
import java.io.IOException
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.locks.ReentrantLock

class SpvService : IntentService("SpvService"), Loader.OnLoadCompleteListener<Cursor> {
    private var application: SpvModuleApplication? = null
    private var config: Configuration? = null

    private var blockStore: BlockStore? = null
    private var blockChainFile: File? = null
    private var blockChain: BlockChain? = null
    private var peerGroup: PeerGroup? = null

    private val handler = Handler()
    private var wakeLock: WakeLock? = null

    private var peerConnectivityListener: PeerConnectivityListener? = null
    private var notificationManager: NotificationManager? = null
    private val impediments = EnumSet.noneOf(Impediment::class.java)
    private val transactionsReceived = AtomicInteger()
    private var serviceCreatedAtInMs: Long = 0
    private var resetBlockchainOnShutdown = false

    private var cursorLoader: CursorLoader? = null

    private lateinit var downloadProgressTracker: DownloadProgressTracker
    private lateinit var connectivityReceiver : ConnectivityReceiver


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

    private val LOADER_ID_NETWORK: Int = 0

    private val futureSpvService = SettableFuture.create<Long>()
    private var reentrantLock = ReentrantLock(true)

    private var wallet: Wallet? = null
    private var accountIndex: Int = -1

    override fun onHandleIntent(intent: Intent?) {
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
                    initializeBlockchain(null, 0)
                    notificationManager!!.cancel(Constants.NOTIFICATION_ID_COINS_RECEIVED)
                }
                ACTION_RESET_BLOCKCHAIN -> {
                    val bip39Passphrase = intent.getStringArrayListExtra("bip39Passphrase")
                    val creationTimeSeconds = intent.getLongExtra("creationTimeSeconds", 0)
                    initializeBlockchain(bip39Passphrase, creationTimeSeconds)
                    futureSpvService.set(0)
                    resetBlockchainOnShutdown = true
                    stopSelf()
                }
                ACTION_BROADCAST_TRANSACTION -> {
                    initializeBlockchain(null, 0)
                    val transactionByteArray = intent.getByteArrayExtra("TX")
                    val tx = Transaction(Constants.NETWORK_PARAMETERS, transactionByteArray)
                    Log.i(LOG_TAG, "onReceive: TX = " + tx)
                    tx.getConfidence().setSource(TransactionConfidence.Source.SELF);
                    tx.setPurpose(Transaction.Purpose.USER_PAYMENT);
                    wallet!!.maybeCommitTx(tx)
                    if (peerGroup != null) {
                        Log.i(LOG_TAG, "broadcasting transaction ${tx.hashAsString}")

                        // A proposed transaction is now sitting in tx - send it in the background.
                        val transactionBroadcast: TransactionBroadcast = peerGroup!!.broadcastTransaction(tx)
                        transactionBroadcast.setMinConnections(1)
                        val future : ListenableFuture<Transaction> = transactionBroadcast.future()
                        // The future will complete when we've seen the transaction ripple across
                        // the network to a sufficient degree.

                        // Here, we just wait for it to finish, but we can also attach a listener
                        // that'll get run on a background

                        // thread when finished. Or we could just assume the network accepts the
                        // transaction and carry on.
                        future.get()
                        Log.i(LOG_TAG, "transaction ${tx.hashAsString} broadcasted")
                    } else {
                        Log.w(LOG_TAG, "peergroup not available, not broadcasting transaction ${tx.hashAsString}")
                    }
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
                    wallet = SpvModuleApplication.getWallet(accountIndex)!!
                    val tx = wallet!!.sendCoinsOffline(sendRequest)
                    if (peerGroup != null) {
                        Log.i(LOG_TAG, "broadcasting transaction ${tx.hashAsString}")
                        peerGroup!!.broadcastTransaction(tx)
                    } else {
                        Log.w(LOG_TAG, "peergroup not available, not broadcasting transaction ${tx.hashAsString}")
                    }
                    return
                }
                IntentContract.ReceiveTransactions.ACTION -> {
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
                }
                else -> {
                    Log.e(LOG_TAG, "Unhandled action was ${intent.action}. Initializing blockchain for account $accountIndex.")
                }
            }
            broadcastBlockchainState()
            futureSpvService.get()
        } else {
            Log.w(LOG_TAG, "onHandleIntent: service restart, although it was started as non-sticky")
            broadcastBlockchainState()
        }
    }

    @Synchronized // TODO: why are we getting here twice in parallel???
    fun initializeBlockchain(seedStringArray: ArrayList<String>?, creationTimeSeconds : Long) {
        serviceCreatedAtInMs = System.currentTimeMillis()
        Log.d(LOG_TAG, "initializeBlockchain() with seedStringArray ????")

        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        application = getApplication() as SpvModuleApplication
        config = application!!.configuration
        wallet = SpvModuleApplication.getWallet(accountIndex) ?: if(seedStringArray != null) {
            /*val key = ECKey.fromPrivate(privateExtendedKeyAccountLevel)
            key.creationTimeSeconds = creationTimeSeconds
            val keyList : MutableList<ECKey> = mutableListOf()
            keyList.add(key) */
            val newWallet : Wallet = Wallet.fromSeed(
                    Constants.NETWORK_PARAMETERS,
                    DeterministicSeed(seedStringArray, null, "", creationTimeSeconds),
                    ImmutableList.of(ChildNumber(44, true), ChildNumber(1, true),
                            ChildNumber(accountIndex, true)))


            SpvModuleApplication.getApplication().replaceWallet(newWallet)
            val tmpWallet = SpvModuleApplication.getWallet(accountIndex)!!
            tmpWallet.keyChainGroupLookaheadSize = 30
            /* tmpWallet!!.upgradeToDeterministic(null)
            Log.d(LOG_TAG, "initializeBlockchain, " +
                    "creationTime = ${Date(creationTimeSeconds * DateUtils.SECOND_IN_MILLIS)}, " +
                    "freshReceiveAddress = ${tmpWallet.freshReceiveAddress().toBase58()}")
                    */

            //we failed to get the wallet. make sure there is no corrupt file left
            File(getDir("blockstore", Context.MODE_PRIVATE),
                    Constants.Files.BLOCKCHAIN_FILENAME + "_$accountIndex").run {
                if(exists()) {
                    delete()
                }
            }
            Log.d(LOG_TAG, "initializeBlockchain, wallet's watched addresses count is " +
                    "${tmpWallet.watchedAddresses.size}")
            for (address in tmpWallet.watchedAddresses) {
                Log.d(LOG_TAG, "initializeBlockchain, address = $address")
            }
            tmpWallet
        } else {
            throw Error("We don't have wallet $accountIndex yet and need priv key material to create it.")
        }
        peerConnectivityListener = PeerConnectivityListener()

        broadcastPeerState(0)

        cursorLoader = CursorLoader(applicationContext,
                BlockchainContract.Transaction.CONTENT_URI(packageName),
                arrayOf(BlockchainContract.Transaction.TRANSACTION_ID),
                "${BlockchainContract.Transaction.TRANSACTION_ID}='?'",
                null, null)
        cursorLoader!!.registerListener(LOADER_ID_NETWORK, this)
        cursorLoader!!.startLoading()

        blockChainFile = File(getDir("blockstore", Context.MODE_PRIVATE),
                Constants.Files.BLOCKCHAIN_FILENAME+ "_$accountIndex")
        val blockChainFileExists = blockChainFile!!.exists()

        if (!blockChainFileExists) {
            Log.i(LOG_TAG, "blockchain does not exist, resetting wallet")
            wallet!!.reset()
        }

        try {
            blockStore = SPVBlockStore(Constants.NETWORK_PARAMETERS, blockChainFile!!)
            blockStore!!.chainHead // detect corruptions as early as possible

            val earliestKeyCreationTime = wallet!!.earliestKeyCreationTime

            if (!blockChainFileExists && earliestKeyCreationTime > 0) {
                try {
                    val start = System.currentTimeMillis()
                    val checkpointsInputStream = assets.open(Constants.Files.CHECKPOINTS_FILENAME)
                    //earliestKeyCreationTime = 1477958400L //Should be earliestKeyCreationTime, testing something.
                    CheckpointManager.checkpoint(Constants.NETWORK_PARAMETERS, checkpointsInputStream,
                            blockStore!!, earliestKeyCreationTime)
                    Log.i(LOG_TAG, "checkpoints loaded from '${Constants.Files.CHECKPOINTS_FILENAME}',"
                            + " took ${System.currentTimeMillis() - start}ms, "
                            + "earliestKeyCreationTime = '$earliestKeyCreationTime'")
                } catch (x: IOException) {
                    Log.e(LOG_TAG, "problem reading checkpoints, continuing without", x)
                }

            }
        } catch (x: BlockStoreException) {
            blockChainFile!!.delete()

            val msg = "blockstore cannot be created"
            throw Error(msg, x)
        }

        try {
            blockChain = BlockChain(Constants.NETWORK_PARAMETERS, wallet, blockStore!!)
        } catch (x: BlockStoreException) {
            throw Error("blockchain cannot be created", x)
        }

        val intentFilter = IntentFilter()
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
        intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK)

        try {
            unregisterReceiver(connectivityReceiver)
        } catch (e : IllegalArgumentException) {
            //Receiver not registered.
            //Log.e(LOG_TAG, e.localizedMessage, e)
            // TODO TEMP FIX // DEBUG THAT

        } catch (e : UninitializedPropertyAccessException) {}

        connectivityReceiver = ConnectivityReceiver()
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

            Log.i(LOG_TAG, "peergroup stopped")
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

    var peerCount: Int = 0
    private inner class PeerConnectivityListener internal constructor()
        : PeerConnectedEventListener, PeerDisconnectedEventListener,
            OnSharedPreferenceChangeListener {
        private val stopped = AtomicBoolean(false)

        init {
            config!!.registerOnSharedPreferenceChangeListener(this)
        }

        internal fun stop() {
            stopped.set(true)

            config!!.unregisterOnSharedPreferenceChangeListener(this)

            notificationManager!!.cancel(Constants.NOTIFICATION_ID_CONNECTED)
        }

        override fun onPeerConnected(peer: Peer, peerCount: Int) = onPeerChanged(peerCount)

        override fun onPeerDisconnected(peer: Peer, peerCount: Int) = onPeerChanged(peerCount)

        private fun onPeerChanged(peerCount: Int) {
            this@SpvService.peerCount = peerCount
            changed()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
            if (Configuration.PREFS_KEY_CONNECTIVITY_NOTIFICATION == key) {
                changed()
            }
        }

        private fun changed() {
            if(!stopped.get()) {
                AsyncTask.execute(Runnable { this@SpvService.changed() })
            }
        }
    }

    private fun changed() {
            val connectivityNotificationEnabled = config!!.connectivityNotificationEnabled

            if (!connectivityNotificationEnabled || peerCount == 0) {
                notificationManager!!.cancel(Constants.NOTIFICATION_ID_CONNECTED)
            } else {
                val notification = Notification.Builder(this@SpvService)
                notification.setSmallIcon(R.drawable.stat_sys_peers, if (peerCount > 4) 4 else peerCount)
                notification.setContentTitle(getString(R.string.app_name))
                var contentText = getString(R.string.notification_peers_connected_msg, peerCount)
                val daysBehind = (Date().time - blockchainState.bestChainDate.time) / DateUtils.DAY_IN_MILLIS
                if(daysBehind > 1) {
                    contentText += " " +  getString(R.string.notification_chain_status_behind, daysBehind)
                }
                if(blockchainState.impediments.size > 0) {
                    // TODO: this is potentially unreachable as the service stops when offline.
                    // Not sure if impediment STORAGE ever shows. Probably both should show.
                    val impedimentsString = blockchainState.impediments.map {it.toString()}.joinToString()
                    contentText += " " +  getString(R.string.notification_chain_status_impediment, impedimentsString)
                }
                notification.setContentText(contentText)

                notification.setContentIntent(PendingIntent.getActivity(this@SpvService, 0, Intent(this@SpvService,
                        PreferenceActivity::class.java), 0))
                notification.setWhen(System.currentTimeMillis())
                notification.setOngoing(true)
                notificationManager!!.notify(Constants.NOTIFICATION_ID_CONNECTED, notification.build())
            }

            // send broadcast
            broadcastPeerState(peerCount)
    }

    inner class DownloadProgressTrackerExt : DownloadProgressTracker() {

        override fun onChainDownloadStarted(peer: Peer?, blocksLeft: Int) {
            super.onChainDownloadStarted(peer, blocksLeft)
            Log.d(LOG_TAG, "onChainDownloadStarted(), Blockchain's download is starting. " +
                    "Blocks left to download is $blocksLeft")
        }

        private val lastMessageTime = AtomicLong(0)

        override fun onBlocksDownloaded(peer: Peer, block: Block, filteredBlock: FilteredBlock?, blocksLeft: Int) {
            if(BuildConfig.DEBUG) {
                //Log.d(LOG_TAG, "onBlocksDownloaded, blocks left + $blocksLeft")
            }
            val now = System.currentTimeMillis()

            if (now - lastMessageTime.get() > BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS
                    || blocksLeft == 0) {
                AsyncTask.execute(runnable)
            }
            super.onBlocksDownloaded(peer, block, filteredBlock, blocksLeft)
        }

        override fun doneDownload() {
            super.doneDownload()
            Log.d(LOG_TAG, "doneDownload(), Blockchain is fully downloaded.")
            futureSpvService.set(config!!.bestChainHeightEver.toLong())
        }

        private val runnable = Runnable {
            lastMessageTime.set(System.currentTimeMillis())
            config!!.maybeIncrementBestChainHeightEver(blockChain!!.chainHead.height)
            broadcastBlockchainState()
            changed()
        }
    }

    inner class ConnectivityReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            when (action) {
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    val hasConnectivity = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)
                    Log.i(LOG_TAG, "network is " + if (hasConnectivity) "up" else "down")

                    if (hasConnectivity) {
                        impediments.remove(Impediment.NETWORK)
                    } else {
                        impediments.add(Impediment.NETWORK)
                    }
                    check()
                }
                Intent.ACTION_DEVICE_STORAGE_LOW -> {
                    Log.i(LOG_TAG, "device storage low")

                    impediments.add(Impediment.STORAGE)
                    check()
                }
                Intent.ACTION_DEVICE_STORAGE_OK -> {
                    Log.i(LOG_TAG, "device storage ok")

                    impediments.remove(Impediment.STORAGE)
                    check()
                }
            }
        }

        //@SuppressLint("Wakelock")
        private fun check() {
            if (impediments.isEmpty() && peerGroup == null) {
                Log.d(LOG_TAG, "check(), acquiring wakelock")
                wakeLock!!.acquire()

                // consistency check
                val walletLastBlockSeenHeight = wallet!!.lastBlockSeenHeight
                val bestChainHeight = blockChain!!.bestChainHeight
                if (walletLastBlockSeenHeight != -1 && walletLastBlockSeenHeight != bestChainHeight) {
                    val message = "check(), wallet/blockchain out of sync: $walletLastBlockSeenHeight/$bestChainHeight"
                    Log.e(LOG_TAG, message)
                }

                Log.i(LOG_TAG, "check(), starting peergroup")
                peerGroup = PeerGroup(Constants.NETWORK_PARAMETERS, blockChain)
                peerGroup!!.setDownloadTxDependencies(0) // recursive implementation causes StackOverflowError

                /*
                peerGroup!!.addWallet(wallet)
                */


                peerGroup!!.setUserAgent(Constants.USER_AGENT, application!!.packageInfo!!.versionName)
                peerGroup!!.addConnectedEventListener(peerConnectivityListener)
                peerGroup!!.addDisconnectedEventListener(peerConnectivityListener)

                val maxConnectedPeers = application!!.maxConnectedPeers()

                val trustedPeerHost = config!!.trustedPeerHost
                val hasTrustedPeer = trustedPeerHost != null

                val connectTrustedPeerOnly = hasTrustedPeer && config!!.trustedPeerOnly
                peerGroup!!.maxConnections = if (connectTrustedPeerOnly) 1 else maxConnectedPeers
                peerGroup!!.setConnectTimeoutMillis(Constants.PEER_TIMEOUT_MS)
                peerGroup!!.setPeerDiscoveryTimeoutMillis(Constants.PEER_DISCOVERY_TIMEOUT_MS.toLong())

                peerGroup!!.addPeerDiscovery(object : PeerDiscovery {
                    private val normalPeerDiscovery = MultiplexingDiscovery.forServices(Constants.NETWORK_PARAMETERS, 0)

                    @Throws(PeerDiscoveryException::class)
                    override fun getPeers(services: Long, timeoutValue: Long, timeoutUnit: TimeUnit): Array<InetSocketAddress> {
                        val peers = LinkedList<InetSocketAddress>()

                        var needsTrimPeersWorkaround = false

                        if (hasTrustedPeer) {
                            Log.i(LOG_TAG, "check(), trusted peer '$trustedPeerHost' " +
                                    if (connectTrustedPeerOnly) " only" else "")

                            val addr = InetSocketAddress(trustedPeerHost, Constants.NETWORK_PARAMETERS.port)
                            if (addr.address != null) {
                                peers.add(addr)
                                needsTrimPeersWorkaround = true
                            }
                        }

                        if (!connectTrustedPeerOnly)
                            peers.addAll(Arrays.asList(*normalPeerDiscovery.getPeers(services, timeoutValue, timeoutUnit)))

                        // workaround because PeerGroup will shuffle peers
                        if (needsTrimPeersWorkaround)
                            while (peers.size >= maxConnectedPeers)
                                peers.removeAt(peers.size - 1)

                        return peers.toTypedArray()
                    }

                    override fun shutdown() {wakeLock
                        normalPeerDiscovery.shutdown()
                    }
                })

                //blockChain!!.addWallet(wallet)
                //peerGroup!!.addWallet(wallet)

                // start peergroup
                downloadProgressTracker = DownloadProgressTrackerExt()
                peerGroup!!.start()
                peerGroup!!.startBlockChainDownload(downloadProgressTracker)
                downloadProgressTracker.await()
                peerGroup!!.stop()
            } else if (!impediments.isEmpty() && peerGroup != null) {
                Log.i(LOG_TAG, "check(), stopping peergroup")
                peerGroup!!.removeDisconnectedEventListener(peerConnectivityListener!!)
                peerGroup!!.removeConnectedEventListener(peerConnectivityListener!!)
                peerGroup!!.removeWallet(wallet)
                peerGroup!!.stopAsync()
                peerGroup = null

                Log.d(LOG_TAG, "check(), releasing wakelock")
                wakeLock!!.release()
            }

            broadcastBlockchainState()
        }
    }

    private class ActivityHistoryEntry(val numTransactionsReceived: Int, val numBlocksDownloaded: Int) {
        override fun toString(): String = "$numTransactionsReceived / $numBlocksDownloaded"
    }

    private val blockchainState: BlockchainState
        get() {
            val chainHead = blockChain!!.chainHead
            val bestChainDate = chainHead.header.time
            val bestChainHeight = chainHead.height
            val replaying = chainHead.height < config!!.bestChainHeightEver

            return BlockchainState(bestChainDate, bestChainHeight, replaying, impediments)
        }

    private fun broadcastPeerState(numPeers: Int) {
        val broadcast = Intent(ACTION_PEER_STATE)
        broadcast.`package` = packageName
        broadcast.putExtra(ACTION_PEER_STATE_NUM_PEERS, numPeers)

        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
    }

    private fun broadcastBlockchainState() {
        val localBroadcast = Intent(ACTION_BLOCKCHAIN_STATE)
        localBroadcast.`package` = packageName
        blockchainState.putExtras(localBroadcast)
        LocalBroadcastManager.getInstance(this).sendBroadcast(localBroadcast)

        // "broadcast" to registered consumers
        val securedMulticastIntent = Intent()
        securedMulticastIntent.action = "com.mycelium.wallet.blockchainState"
        securedMulticastIntent.putExtra(IntentContract.ACCOUNT_INDEX_EXTRA, accountIndex)
        blockchainState.putExtras(securedMulticastIntent)

        SpvMessageSender.send(CommunicationManager.getInstance(this), securedMulticastIntent)
    }

    companion object {
        private val PACKAGE_NAME = SpvService::class.java.`package`.name
        val ACTION_PEER_STATE = PACKAGE_NAME + ".peer_state"
        val ACTION_PEER_STATE_NUM_PEERS = "num_peers"
        val ACTION_BLOCKCHAIN_STATE = PACKAGE_NAME + ".blockchain_state"
        val ACTION_CANCEL_COINS_RECEIVED = PACKAGE_NAME + ".cancel_coins_received"
        val ACTION_RESET_BLOCKCHAIN = PACKAGE_NAME + ".reset_blockchain"
        val ACTION_BROADCAST_TRANSACTION = PACKAGE_NAME + ".broadcast_transaction"
        val ACTION_BROADCAST_TRANSACTION_HASH = "hash"
        val ACTION_SEND_FUNDS = PACKAGE_NAME + ".send_funds"

        private val MIN_COLLECT_HISTORY = 2
        private val IDLE_BLOCK_TIMEOUT_MIN = 2
        private val IDLE_TRANSACTION_TIMEOUT_MIN = 9
        private val MAX_HISTORY_SIZE = Math.max(IDLE_TRANSACTION_TIMEOUT_MIN, IDLE_BLOCK_TIMEOUT_MIN)
        private val APPWIDGET_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS
        private val BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS
    }
}
