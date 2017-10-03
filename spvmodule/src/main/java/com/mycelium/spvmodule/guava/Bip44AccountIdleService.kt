package com.mycelium.spvmodule.guava

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.*
import android.content.Context
import android.net.ConnectivityManager
import android.os.AsyncTask
import android.os.PowerManager
import android.support.v4.content.LocalBroadcastManager
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.AbstractScheduledService
import com.mycelium.modularizationtools.CommunicationManager
import com.mycelium.spvmodule.*
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
import org.bitcoinj.wallet.*
import java.io.*
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by Nelson on 27/09/2017.
 */
class Bip44AccountIdleService : AbstractScheduledService() {

    private var LOG_TAG = this.javaClass.canonicalName
    private val walletsAccounts: MutableMap<Int, Wallet> = mutableMapOf()
    private lateinit var accountIndexStrings: MutableSet<String>
    private lateinit var sharedPreferences:SharedPreferences
    private lateinit var downloadProgressTracker: DownloadProgressTracker
    private lateinit var connectivityReceiver : ConnectivityReceiver
    private var wakeLock: PowerManager.WakeLock? = null


    /**
     * Start the service.
     *
     */
    override fun startUp() {
        //Read list of accounts indexes
        sharedPreferences = spvModuleApplication.getSharedPreferences(
                spvModuleApplication.getString(R.string.sharedpreferences_file_name),
                Context.MODE_PRIVATE)
        accountIndexStrings = sharedPreferences.getStringSet(
                spvModuleApplication.getString(R.string.account_index_stringset), emptySet())
        initialize()
    }

    /**
     * Stop the service. This is guaranteed not to run concurrently with [.runOneIteration].
     */
    override fun shutDown() {
        stopPeergroup()
    }

    /**
     * Returns the [Scheduler] object used to configure this service.  This method will only be
     * called once.
     */
    override fun scheduler(): Scheduler =
            AbstractScheduledService.Scheduler.newFixedDelaySchedule(0, 1, TimeUnit.MINUTES)

    /**
     * Run one iteration of the scheduled task. If any invocation of this method throws an exception,
     * the service will transition to the [Service.State.FAILED] state and this method will no
     * longer be called.
     */
    override fun runOneIteration() {
        if(!peerGroup!!.isRunning) {
            if(wakeLock == null) {
                // if we still hold a wakelock, we don't leave it dangling to block until later.
                val powerManager = spvModuleApplication.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "${spvModuleApplication.packageName} blockchain sync")
            }
            if(!wakeLock!!.isHeld) {
                wakeLock!!.acquire()
            }
            for (walletAccount in walletsAccounts.values) {
                peerGroup!!.addWallet(walletAccount)
            }

            //Starting peerGroup;
            peerGroup!!.startAsync()
            //Start download blockchain
            peerGroup!!.downloadBlockChain()
            //Stop the peergroup after having downloaded the blockchain.
            stopPeergroup()
            //Release wakelock
            if (wakeLock != null && wakeLock!!.isHeld) {
                wakeLock!!.release()
                wakeLock = null
            }
        }
    }

    private lateinit var peerConnectivityListener: PeerConnectivityListener
    private var peerGroup: PeerGroup? = null
    private var spvModuleApplication = SpvModuleApplication.getApplication()
    private val configuration = spvModuleApplication.configuration
    val notificationManager = spvModuleApplication.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var blockStore : BlockStore


    private fun initialize() {
        val blockChainFile = File(spvModuleApplication.getDir("blockstore", Context.MODE_PRIVATE),
                Constants.Files.BLOCKCHAIN_FILENAME)

        try {
            blockStore = SPVBlockStore(Constants.NETWORK_PARAMETERS, blockChainFile)
            blockStore.chainHead // detect corruptions as early as possible
            initializeWalletsAccounts()
            val earliestKeyCreationTime = initializeEarliestKeyCreationTime()
            if (earliestKeyCreationTime > 0L) {
                initializeCheckpoint(earliestKeyCreationTime)
            }
            blockChain = BlockChain(Constants.NETWORK_PARAMETERS, walletsAccounts.values.toList(),
                    blockStore)
        } catch (x: BlockStoreException) {
            blockChainFile.delete()
            throw Error(x.localizedMessage, x)
        }
        initializePeergroup()

        val intentFilter = IntentFilter()
        intentFilter.addAction(ConnectivityManager.CONNECTIVITY_ACTION)
        intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
        intentFilter.addAction(Intent.ACTION_DEVICE_STORAGE_OK)

        Log.d(LOG_TAG, "initializeBlockchain, registering ConnectivityReceiver")
        spvModuleApplication.registerReceiver(connectivityReceiver, intentFilter) // implicitly start PeerGroup
        initializeWalletAccountsListeners()
    }

    private fun initializeWalletAccountsListeners() {
        for (walletAccount in walletsAccounts.values) {
            walletAccount.addCoinsReceivedEventListener(Threading.SAME_THREAD, walletEventListener)
            walletAccount.addCoinsSentEventListener(Threading.SAME_THREAD, walletEventListener)
            walletAccount.addChangeEventListener(Threading.SAME_THREAD, walletEventListener)
            walletAccount.addTransactionConfidenceEventListener(Threading.SAME_THREAD, walletEventListener)
        }
    }

    private fun initializeWalletsAccounts() {
        for (accountIndexString in accountIndexStrings) {
            val accountIndex: Int = accountIndexString.toInt()
            val walletAccount = getAccountWallet(accountIndex);
            if (walletAccount != null) {
                walletsAccounts.put(accountIndex, walletAccount)
            }
        }
    }

    private fun initializeEarliestKeyCreationTime(): Long {
        var earliestKeyCreationTime = 0L
        for (walletAccount in walletsAccounts.values) {
            if (earliestKeyCreationTime != 0L) {
                if (walletAccount.earliestKeyCreationTime < earliestKeyCreationTime) {
                    earliestKeyCreationTime = walletAccount.earliestKeyCreationTime
                }
            } else {
                earliestKeyCreationTime = walletAccount.earliestKeyCreationTime
            }
        }
        return earliestKeyCreationTime
    }

    private fun initializeCheckpoint(earliestKeyCreationTime: Long) {
        try {
            val start = System.currentTimeMillis()
            val checkpointsInputStream = spvModuleApplication.assets.open(Constants.Files.CHECKPOINTS_FILENAME)
            //earliestKeyCreationTime = 1477958400L //Should be earliestKeyCreationTime, testing something.
            CheckpointManager.checkpoint(Constants.NETWORK_PARAMETERS, checkpointsInputStream,
                    blockStore, earliestKeyCreationTime)
            Log.i(LOG_TAG, "checkpoints loaded from '${Constants.Files.CHECKPOINTS_FILENAME}',"
                    + " took ${System.currentTimeMillis() - start}ms, "
                    + "earliestKeyCreationTime = '$earliestKeyCreationTime'")
        } catch (x: IOException) {
            Log.e(LOG_TAG, "problem reading checkpoints, continuing without", x)
        }
    }

    private fun initializePeergroup() {
        peerGroup = PeerGroup(Constants.NETWORK_PARAMETERS, blockChain)
        peerGroup!!.setDownloadTxDependencies(0) // recursive implementation causes StackOverflowError

        peerGroup!!.setUserAgent(Constants.USER_AGENT, spvModuleApplication.packageInfo!!.versionName)

        peerConnectivityListener = PeerConnectivityListener()

        peerGroup!!.addConnectedEventListener(peerConnectivityListener)
        peerGroup!!.addDisconnectedEventListener(peerConnectivityListener)

        val trustedPeerHost = configuration!!.trustedPeerHost
        val hasTrustedPeer = trustedPeerHost != null

        val connectTrustedPeerOnly = hasTrustedPeer && configuration.trustedPeerOnly
        peerGroup!!.maxConnections = if (connectTrustedPeerOnly) 1 else spvModuleApplication.maxConnectedPeers()
        peerGroup!!.setConnectTimeoutMillis(Constants.PEER_TIMEOUT_MS)
        peerGroup!!.setPeerDiscoveryTimeoutMillis(Constants.PEER_DISCOVERY_TIMEOUT_MS.toLong())

        peerGroup!!.addPeerDiscovery(object : PeerDiscovery {
            private val normalPeerDiscovery = MultiplexingDiscovery.forServices(Constants.NETWORK_PARAMETERS, 0)

            @Throws(PeerDiscoveryException::class)
            override fun getPeers(services: Long, timeoutValue: Long, timeoutUnit: TimeUnit)
                    : Array<InetSocketAddress> {
                val peers = LinkedList<InetSocketAddress>()

                var needsTrimPeersWorkaround = false

                if (hasTrustedPeer) {
                    Log.i(LOG_TAG, "check(), trusted peer '$trustedPeerHost' " +
                            if (connectTrustedPeerOnly) " only." else "")

                    val addr = InetSocketAddress(trustedPeerHost, Constants.NETWORK_PARAMETERS.port)
                    if (addr.address != null) {
                        peers.add(addr)
                        needsTrimPeersWorkaround = true
                    }
                }

                if (!connectTrustedPeerOnly) {
                    peers.addAll(Arrays.asList(*normalPeerDiscovery.getPeers(services, timeoutValue, timeoutUnit)))
                }

                // workaround because PeerGroup will shuffle peers
                if (needsTrimPeersWorkaround) {
                    while (peers.size >= spvModuleApplication.maxConnectedPeers()) {
                        peers.removeAt(peers.size - 1)
                    }
                }

                return peers.toTypedArray()
            }

            override fun shutdown() {
                normalPeerDiscovery.shutdown()
            }
        })
    }

    private fun stopPeergroup() {
        peerGroup!!.removeDisconnectedEventListener(peerConnectivityListener)
        peerGroup!!.removeConnectedEventListener(peerConnectivityListener)
        for (walletAccount in walletsAccounts.values) {
            peerGroup!!.removeWallet(walletAccount)
        }
        peerGroup!!.stopAsync()
        peerGroup = null

        try {
            spvModuleApplication.unregisterReceiver(connectivityReceiver)
        } catch (e : IllegalArgumentException) {
            //Receiver not registered.
            //Log.e(LOG_TAG, e.localizedMessage, e)
        } catch (e : UninitializedPropertyAccessException) {}

        peerConnectivityListener.stop()

        for (walletAccountIndexMapItem in walletsAccounts) {
            val walletFile = spvModuleApplication.getFileStreamPath(Constants.Files.WALLET_FILENAME_PROTOBUF
                    + "_${walletAccountIndexMapItem.key}")
            walletAccountIndexMapItem.value.saveToFile(walletFile)
            walletAccountIndexMapItem.value.removeChangeEventListener(walletEventListener)
            walletAccountIndexMapItem.value.removeCoinsSentEventListener(walletEventListener)
            walletAccountIndexMapItem.value.removeCoinsReceivedEventListener(walletEventListener)
            walletAccountIndexMapItem.value.removeTransactionConfidenceEventListener(walletEventListener)
        }
        blockStore.close()
    }

    private fun getAccountWallet(accountIndex: Int) : Wallet? {
        Log.d(LOG_TAG, "switchAccount, accountIndex = $accountIndex")
        var walletAccount : Wallet? = null
        val walletAccountFile = spvModuleApplication.getFileStreamPath(
                Constants.Files.WALLET_FILENAME_PROTOBUF + "_$accountIndex")
        if (walletAccountFile.exists()) {
            walletAccount = loadWalletFromProtobuf(accountIndex)
            afterLoadWallet(walletAccount, accountIndex)
            cleanupFiles(accountIndex)
        }
        return walletAccount
    }

    private fun loadWalletFromProtobuf(accountIndex: Int) : Wallet {
        var walletStream: FileInputStream? = null
        var walletAccount : Wallet?
        val start = System.currentTimeMillis()
        val walletAccountFile = spvModuleApplication.getFileStreamPath(
                Constants.Files.WALLET_FILENAME_PROTOBUF + "_$accountIndex")
        try {
            walletStream = FileInputStream(walletAccountFile!!)

            walletAccount = WalletProtobufSerializer().readWallet(walletStream)

            if (walletAccount.params != Constants.NETWORK_PARAMETERS)
                throw UnreadableWalletException("bad wallet network parameters: "
                        + walletAccount!!.params.id)

            Log.i(LOG_TAG, "wallet loaded from: '$walletAccountFile', took ${System.currentTimeMillis() - start}ms")
        } catch (x: FileNotFoundException) {
            Log.e(LOG_TAG, "problem loading wallet", x)

            Toast.makeText(spvModuleApplication, x.javaClass.name, Toast.LENGTH_LONG).show()

            walletAccount = restoreWalletFromBackup(accountIndex)
        } catch (x: UnreadableWalletException) {
            Log.e(LOG_TAG, "problem loading wallet", x)
            Toast.makeText(spvModuleApplication, x.javaClass.name, Toast.LENGTH_LONG).show()
            walletAccount = restoreWalletFromBackup(accountIndex)
        } finally {
            if (walletStream != null) {
                try {
                    walletStream.close()
                } catch (ignore: IOException) {
                }
            }
        }

        if (!walletAccount!!.isConsistent) {
            Toast.makeText(spvModuleApplication, "inconsistent wallet: " + walletAccountFile!!, Toast.LENGTH_LONG).show()

            walletAccount = restoreWalletFromBackup(accountIndex)
        }

        if (walletAccount.params != Constants.NETWORK_PARAMETERS) {
            throw Error("bad wallet network parameters: ${walletAccount.params.id}")
        }
        return walletAccount
    }

    private fun restoreWalletFromBackup(accountIndex: Int): Wallet {
        spvModuleApplication.openFileInput(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + accountIndex).use { stream ->
            val walletAccount = WalletProtobufSerializer().readWallet(stream, true, null)
            if (!walletAccount.isConsistent) {
                throw Error("inconsistent backup")
            }
            //TODO : Reset Blockchain ?
            Log.i(LOG_TAG, "wallet/account restored from backup: "
                    + "'${Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + accountIndex}'")
            return walletAccount
        }
    }

    private fun afterLoadWallet(walletAccount: Wallet, accountIndex: Int) {
        val walletAccountFile = spvModuleApplication.getFileStreamPath(
                Constants.Files.WALLET_FILENAME_PROTOBUF + "_$accountIndex")
        walletAccount.autosaveToFile(walletAccountFile, 10, TimeUnit.SECONDS, WalletAutosaveEventListener())
        // clean up spam
        walletAccount.cleanup()
        migrateBackup(walletAccount, accountIndex)
    }

    private fun migrateBackup(walletAccount: Wallet, accountIndex: Int) {
        // TODO: make this multi-wallet aware
        if (!spvModuleApplication.getFileStreamPath(
                Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + accountIndex).exists()) {
            Log.i(LOG_TAG, "migrating automatic backup to protobuf")
            // make sure there is at least one recent backup
            backupWallet(walletAccount, accountIndex)
        }
    }

    private fun backupWallet(walletAccount: Wallet, accountIndex: Int) {
        val builder = WalletProtobufSerializer().walletToProto(walletAccount).toBuilder()

        // strip redundant
        builder.clearTransaction()
        builder.clearLastSeenBlockHash()
        builder.lastSeenBlockHeight = -1
        builder.clearLastSeenBlockTimeSecs()
        val walletProto = builder.build()

        var os: OutputStream? = null

        try {
            os = spvModuleApplication.openFileOutput(
                    Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + accountIndex, Context.MODE_PRIVATE)
            walletProto.writeTo(os)
        } catch (x: IOException) {
            Log.e(LOG_TAG, "problem writing key backup", x)
        } finally {
            try {
                if (os != null) {
                    os.close()
                }
            } catch (ignored: IOException) {
            }
        }
    }

    private fun cleanupFiles(accountIndex: Int) {
        for (filename in spvModuleApplication.fileList()) {
            if (filename.startsWith(Constants.Files.WALLET_KEY_BACKUP_BASE58)
                    || filename.startsWith(
                    Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + accountIndex + '.')
                    || filename.endsWith(".tmp")) {
                val file = File(spvModuleApplication.filesDir, filename)
                Log.i(LOG_TAG, "removing obsolete file: '$file'")
                file.delete()
            }
        }
    }

    private var blockChain: BlockChain? = null

    var peerCount: Int = 0

    private inner class PeerConnectivityListener internal constructor()
        : PeerConnectedEventListener, PeerDisconnectedEventListener,
            SharedPreferences.OnSharedPreferenceChangeListener {
        private val stopped = AtomicBoolean(false)

        init {
            configuration!!.registerOnSharedPreferenceChangeListener(this)
        }

        internal fun stop() {
            stopped.set(true)

            configuration!!.unregisterOnSharedPreferenceChangeListener(this)
            notificationManager.cancel(Constants.NOTIFICATION_ID_CONNECTED)
        }

        override fun onPeerConnected(peer: Peer, peerCount: Int) = onPeerChanged(peerCount)

        override fun onPeerDisconnected(peer: Peer, peerCount: Int) = onPeerChanged(peerCount)

        private fun onPeerChanged(peerCount: Int) {
            this@Bip44AccountIdleService.peerCount = peerCount
            changed()
        }

        override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String) {
            if (Configuration.PREFS_KEY_CONNECTIVITY_NOTIFICATION == key) {
                changed()
            }
        }

        private fun changed() {
            if(!stopped.get()) {
                AsyncTask.execute(Runnable { this@Bip44AccountIdleService.changed() })
            }
        }
    }

    private val impediments = EnumSet.noneOf(BlockchainState.Impediment::class.java)

    private val blockchainState: BlockchainState
        get() {
            val chainHead = blockChain!!.chainHead
            val bestChainDate = chainHead.header.time
            val bestChainHeight = chainHead.height
            val replaying = chainHead.height < configuration!!.bestChainHeightEver

            return BlockchainState(bestChainDate, bestChainHeight, replaying, impediments)
        }

    private fun broadcastPeerState(numPeers: Int) {
        val broadcast = Intent(SpvService.ACTION_PEER_STATE)
        broadcast.`package` = spvModuleApplication.packageName
        broadcast.putExtra(SpvService.ACTION_PEER_STATE_NUM_PEERS, numPeers)

        LocalBroadcastManager.getInstance(spvModuleApplication).sendBroadcast(broadcast)
    }

    private fun changed() {
        val connectivityNotificationEnabled = configuration!!.connectivityNotificationEnabled

        if (!connectivityNotificationEnabled || peerCount == 0) {
            notificationManager.cancel(Constants.NOTIFICATION_ID_CONNECTED)
        } else {
            val notification = Notification.Builder(spvModuleApplication)
            notification.setSmallIcon(R.drawable.stat_sys_peers, if (peerCount > 4) 4 else peerCount)
            notification.setContentTitle(spvModuleApplication.getString(R.string.app_name))
            var contentText = spvModuleApplication.getString(R.string.notification_peers_connected_msg, peerCount)
            val daysBehind = (Date().time - blockchainState.bestChainDate.time) / DateUtils.DAY_IN_MILLIS
            if(daysBehind > 1) {
                contentText += " " + spvModuleApplication.getString(R.string.notification_chain_status_behind, daysBehind)
            }
            if(blockchainState.impediments.size > 0) {
                // TODO: this is potentially unreachable as the service stops when offline.
                // Not sure if impediment STORAGE ever shows. Probably both should show.
                val impedimentsString = blockchainState.impediments.map {it.toString()}.joinToString()
                contentText += " " +  spvModuleApplication.getString(R.string.notification_chain_status_impediment, impedimentsString)
            }
            notification.setContentText(contentText)

            notification.setContentIntent(PendingIntent.getActivity(spvModuleApplication, 0,
                    Intent(spvModuleApplication, PreferenceActivity::class.java), 0))
            notification.setWhen(System.currentTimeMillis())
            notification.setOngoing(true)
            notificationManager.notify(Constants.NOTIFICATION_ID_CONNECTED, notification.build())
        }

        // send broadcast
        broadcastPeerState(peerCount)
    }


    @Synchronized
    fun addWalletAccount(bip39Passphrase: ArrayList<String>, creationTimeSeconds: Long,
                         accountIndex: Int) {
        stopPeergroup()
        val walletAccount = Wallet.fromSeed(
                Constants.NETWORK_PARAMETERS,
                DeterministicSeed(bip39Passphrase, null, "", creationTimeSeconds),
                ImmutableList.of(ChildNumber(44, true), ChildNumber(1, true),
                        ChildNumber(accountIndex, true)))


        walletAccount.keyChainGroupLookaheadSize = 30

        accountIndexStrings.add(accountIndex.toString())
        val editor = sharedPreferences.edit()
        editor.putStringSet(spvModuleApplication.getString(R.string.account_index_stringset),
                accountIndexStrings)
        editor.commit()

        walletsAccounts.put(accountIndex, walletAccount)
        walletAccount.shutdownAutosaveAndWait()
        configuration!!.maybeIncrementBestChainHeightEver(walletAccount.lastBlockSeenHeight)
        afterLoadWallet(walletAccount, accountIndex)

        /*
        val broadcast = Intent(SpvModuleApplication.ACTION_WALLET_REFERENCE_CHANGED) //TODO Investigate utility of this.
        broadcast.`package` = packageName
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
        */
    }

    @Synchronized
    fun broadcastTransaction(transaction: Transaction, accountIndex: Int) {
        val walletAccount = walletsAccounts.get(accountIndex)
        walletAccount!!.commitTx(transaction)
        val walletAccountFile = spvModuleApplication.getFileStreamPath(
                Constants.Files.WALLET_FILENAME_PROTOBUF + "_$accountIndex")
        walletAccount.saveToFile(walletAccountFile)

        // A proposed transaction is now sitting in request.tx - send it in the background.
        val transactionBroadcast = peerGroup!!.broadcastTransaction(transaction)
        val future = transactionBroadcast.future()

        // The future will complete when we've seen the transaction ripple across the network to a sufficient degree.
        // Here, we just wait for it to finish, but we can also attach a listener that'll get run on a background
        // thread when finished. Or we could just assume the network accepts the transaction and carry on.
        future.get()
    }

    fun broadcastTransaction(sendRequest: SendRequest, accountIndex: Int) {
        val transaction = walletsAccounts.get(accountIndex)!!.sendCoinsOffline(sendRequest)
        broadcastTransaction(transaction, accountIndex)
    }

    private fun broadcastBlockchainState() {
        val localBroadcast = Intent(SpvService.ACTION_BLOCKCHAIN_STATE)
        localBroadcast.`package` = spvModuleApplication.packageName
        blockchainState.putExtras(localBroadcast)
        LocalBroadcastManager.getInstance(spvModuleApplication).sendBroadcast(localBroadcast)

        // "broadcast" to registered consumers
        val securedMulticastIntent = Intent()
        securedMulticastIntent.action = "com.mycelium.wallet.blockchainState"
        blockchainState.putExtras(securedMulticastIntent)

        SpvMessageSender.send(CommunicationManager.getInstance(spvModuleApplication), securedMulticastIntent)
    }

    private val walletEventListener = object
        : ThrottlingWalletChangeListener(APPWIDGET_THROTTLE_MS) {
        override fun onReorganize(p0: Wallet?) {
            //Do nothing.
        }

        override fun onChanged(walletAccount: Wallet) {
            notifyTransactions(walletAccount.getTransactions(true), walletAccount.unspents.toSet())
        }

        override fun onTransactionConfidenceChanged(walletAccount: Wallet, tx: Transaction) {
            //Do nothing.
            /*
            if(tx.confidence?.depthInBlocks?:0 > 7) {
                // don't update on all transactions individually just because we found a new block
                return
            }
            */
        }

        override fun onCoinsReceived(walletAccount: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin) {
            //Do nothing.
        }

        override fun onCoinsSent(walletAccount: Wallet, tx: Transaction, prevBalance: Coin, newBalance: Coin) {
            //Do nothing.
        }
    }

    @Synchronized
    private fun notifyTransactions(transactions: Set<Transaction>, utxos: Set<TransactionOutput> ) {
        if (!transactions.isEmpty()) {
            // send the new transaction and the *complete* utxo set of the wallet
            SpvMessageSender.sendTransactions(CommunicationManager.getInstance(spvModuleApplication), transactions, utxos)
        }
    }

    inner class DownloadProgressTrackerExt : DownloadProgressTracker() {

        override fun onChainDownloadStarted(peer: Peer?, blocksLeft: Int) {
            Log.d(LOG_TAG, "onChainDownloadStarted(), Blockchain's download is starting. " +
                    "Blocks left to download is $blocksLeft, peer = $peer")
            super.onChainDownloadStarted(peer, blocksLeft)
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
            Log.d(LOG_TAG, "doneDownload(), Blockchain is fully downloaded.")
            super.doneDownload()
            /*
            if(peerGroup!!.isRunning) {
                Log.i(LOG_TAG, "doneDownload(), stopping peergroup")
                peerGroup!!.stopAsync()
            }
            */
        }

        private val runnable = Runnable {
            lastMessageTime.set(System.currentTimeMillis())
            configuration!!.maybeIncrementBestChainHeightEver(blockChain!!.chainHead.height)
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
                    Log.i(LOG_TAG, "ConnectivityReceiver, network is " + if (hasConnectivity) "up" else "down")

                    if (hasConnectivity) {
                        impediments.remove(BlockchainState.Impediment.NETWORK)
                    } else {
                        impediments.add(BlockchainState.Impediment.NETWORK)
                    }
                    check()
                }
                Intent.ACTION_DEVICE_STORAGE_LOW -> {
                    Log.i(LOG_TAG, "ConnectivityReceiver, device storage low")

                    impediments.add(BlockchainState.Impediment.STORAGE)
                    check()
                }
                Intent.ACTION_DEVICE_STORAGE_OK -> {
                    Log.i(LOG_TAG, "ConnectivityReceiver, device storage ok")

                    impediments.remove(BlockchainState.Impediment.STORAGE)
                    check()
                }
            }
        }

        //@SuppressLint("Wakelock")
        private fun check() {
            if (impediments.isEmpty() && peerGroup == null) {
                Log.i(LOG_TAG, "check(), starting peergroup")

                // start peergroup
                downloadProgressTracker = DownloadProgressTrackerExt()
                Log.i(LOG_TAG, "check(), peergroup startAsync")
                peerGroup!!.startAsync()
                Log.i(LOG_TAG, "check(), peergroup startBlockChainDownload")
                peerGroup!!.startBlockChainDownload(downloadProgressTracker)
            } else if (!impediments.isEmpty() && peerGroup != null) {
                Log.i(LOG_TAG, "check(), stopping peergroup")
                stopPeergroup()
                Log.i(LOG_TAG, "check(), impediments is not empty && peergroup is not null")
            } else {
                Log.i(LOG_TAG, "check(), impediments size is ${impediments.size} && peergroup is $peerGroup")
            }
            broadcastBlockchainState()
        }
    }

    private class WalletAutosaveEventListener : WalletFiles.Listener {
        override fun onBeforeAutoSave(file: File) = Unit

        override fun onAfterAutoSave(file: File) = // make walletsAccounts world accessible in test mode
                //if (Constants.TEST) {
                //   Io.chmod(file, 0777);
                //}
                Unit
    }

    companion object {
        private val BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS
        private val APPWIDGET_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS
    }
}