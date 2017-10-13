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
import com.google.common.util.concurrent.ListenableFuture
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
import org.bitcoinj.store.SPVBlockStore
import org.bitcoinj.utils.Threading
import org.bitcoinj.wallet.*
import java.io.*
import java.net.InetSocketAddress
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentSkipListSet
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.ArrayList

class Bip44AccountIdleService : AbstractScheduledService() {
    private val walletsAccountsMap: ConcurrentHashMap<Int, Wallet> = ConcurrentHashMap()
    private var downloadProgressTracker: DownloadProgressTracker? = null
    private val connectivityReceiver = ConnectivityReceiver()

    private var wakeLock: PowerManager.WakeLock? = null
    private var peerGroup: PeerGroup? = null

    private val spvModuleApplication = SpvModuleApplication.getApplication()
    private val sharedPreferences:SharedPreferences = spvModuleApplication.getSharedPreferences(
            SHARED_PREFERENCES_FILE_NAME, Context.MODE_PRIVATE)
    //Read list of accounts indexes
    private val accountIndexStrings: ConcurrentSkipListSet<String> = ConcurrentSkipListSet<String>().apply {
        addAll(sharedPreferences.getStringSet(ACCOUNT_INDEX_STRING_SET_PREF, emptySet()))
    }
    private val configuration = spvModuleApplication.configuration!!
    private val peerConnectivityListener: PeerConnectivityListener = PeerConnectivityListener()
    private val notificationManager = spvModuleApplication.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private lateinit var blockStore : BlockStore
    private var bip39Passphrase = sharedPreferences.getString(PASSPHRASE_PREF, "").split(" ")
    private var counterCheckImpediments: Int = 0
    private var countercheckIfDownloadIsIdling: Int = 0

    override fun shutDown() {
        Log.d(LOG_TAG, "shutDown")
        stopPeergroup()
    }

    override fun scheduler(): Scheduler =
            AbstractScheduledService.Scheduler.newFixedRateSchedule(0, 1, TimeUnit.MINUTES)

    override fun runOneIteration() {
        Log.d(LOG_TAG, "runOneIteration")
        if(walletsAccountsMap.isNotEmpty()) {
            counterCheckImpediments++
            countercheckIfDownloadIsIdling++
            if(counterCheckImpediments.rem(10) == 0 || counterCheckImpediments == 1) {
                //We do that every ten minutes
                checkImpediments()
            }
            if(countercheckIfDownloadIsIdling.rem(2) == 0) {
                //We do that every two minutes
                checkIfDownloadIsIdling()
            }
        }
    }

    override fun startUp() {
        Log.d(LOG_TAG, "startUp")
        val intentFilter = IntentFilter().apply {
            addAction(ConnectivityManager.CONNECTIVITY_ACTION)
            addAction(Intent.ACTION_DEVICE_STORAGE_LOW)
            addAction(Intent.ACTION_DEVICE_STORAGE_OK)
        }
        spvModuleApplication.applicationContext.registerReceiver(connectivityReceiver, intentFilter)

        val blockChainFile = File(spvModuleApplication.getDir("blockstore", Context.MODE_PRIVATE),
                Constants.Files.BLOCKCHAIN_FILENAME)
        blockStore = SPVBlockStore(Constants.NETWORK_PARAMETERS, blockChainFile)
        blockStore.chainHead // detect corruptions as early as possible
        initializeWalletsAccounts()
        initializePeergroup()
    }

    private fun initializeWalletAccountsListeners() {
        Log.d(LOG_TAG, "initializeWalletAccountsListeners, number of accounts = ${walletsAccountsMap.values.size}")
        walletsAccountsMap.values.forEach {
            it.addChangeEventListener(Threading.SAME_THREAD, walletEventListener)
            it.addCoinsReceivedEventListener(Threading.SAME_THREAD, walletEventListener)
            it.addCoinsSentEventListener(Threading.SAME_THREAD, walletEventListener)
        }
    }

    private fun initializeWalletsAccounts() {
        if(BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "initializeWalletsAccounts, number of accounts = ${accountIndexStrings.size}")
        }
        var shouldInitializeCheckpoint = true
        for (accountIndexString in accountIndexStrings) {
            val accountIndex: Int = accountIndexString.toInt()
            val walletAccount = getAccountWallet(accountIndex)
            if (walletAccount != null) {
                walletsAccountsMap[accountIndex] = walletAccount
                if(walletAccount.lastBlockSeenHeight >= 0 && shouldInitializeCheckpoint == true) {
                    shouldInitializeCheckpoint = false
                }
            }
        }
        if (shouldInitializeCheckpoint) {
            val earliestKeyCreationTime = initializeEarliestKeyCreationTime()
            if(earliestKeyCreationTime > 0L) {
                initializeCheckpoint(earliestKeyCreationTime)
            }
        }
        blockChain = BlockChain(Constants.NETWORK_PARAMETERS, walletsAccountsMap.values.toList(),
                blockStore)
        initializeWalletAccountsListeners()
    }

    private fun initializeEarliestKeyCreationTime(): Long {
        if(BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "initializeEarliestKeyCreationTime")
        }
        var earliestKeyCreationTime = 0L
        for (walletAccount in walletsAccountsMap.values) {
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
        if(BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "initializeCheckpoint, earliestKeyCreationTime = $earliestKeyCreationTime")
        }
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
        if(BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "initializePeergroup")
        }
        peerGroup = PeerGroup(Constants.NETWORK_PARAMETERS, blockChain)
        peerGroup!!.setDownloadTxDependencies(0) // recursive implementation causes StackOverflowError

        peerGroup!!.setUserAgent(Constants.USER_AGENT, spvModuleApplication.packageInfo!!.versionName)

        peerGroup!!.addConnectedEventListener(peerConnectivityListener)
        peerGroup!!.addDisconnectedEventListener(peerConnectivityListener)

        val trustedPeerHost = configuration.trustedPeerHost
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
        //Starting peerGroup;
        Log.i(LOG_TAG, "initializePeergroup, peergroup startAsync")
        peerGroup!!.startAsync()
    }

    private fun stopPeergroup() {
        Log.d(LOG_TAG, "stopPeergroup")
        if(peerGroup != null) {
            if(peerGroup!!.isRunning) {
                peerGroup!!.stopAsync()
            }
            peerGroup!!.removeDisconnectedEventListener(peerConnectivityListener)
            peerGroup!!.removeConnectedEventListener(peerConnectivityListener)
            for (walletAccount in walletsAccountsMap.values) {
                peerGroup!!.removeWallet(walletAccount)
            }
        }

        try {
            spvModuleApplication.unregisterReceiver(connectivityReceiver)
        } catch (e : IllegalArgumentException) {
            //Receiver not registered.
            //Log.e(LOG_TAG, e.localizedMessage, e)
        } catch (e : UninitializedPropertyAccessException) {}

        peerConnectivityListener.stop()

        for(idWallet in walletsAccountsMap) {
            idWallet.value.run {
                saveToFile(walletFile(idWallet.key))
                removeChangeEventListener(walletEventListener)
                removeCoinsReceivedEventListener(walletEventListener)
                removeCoinsSentEventListener(walletEventListener)
            }
        }
        blockStore.close()
        Log.d(LOG_TAG, "stopPeergroup DONE")
    }

    @Synchronized
    private fun checkImpediments() {
        if(BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "checkImpediments, peergroup.isRunning = ${peerGroup!!.isRunning},"
                    + "downloadProgressTracker condition is "
                    + "${(downloadProgressTracker == null || downloadProgressTracker!!.future.isDone)}")
        }
        //Second condition (downloadProgressTracker) prevent the case where the peergroup is
        // currently downloading the blockchain.
        if(peerGroup!!.isRunning
                && (downloadProgressTracker == null || downloadProgressTracker!!.future.isDone)) {
            if (wakeLock == null) {
                // if we still hold a wakelock, we don't leave it dangling to block until later.
                val powerManager = spvModuleApplication.getSystemService(Context.POWER_SERVICE) as PowerManager
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "${spvModuleApplication.packageName} blockchain sync")
            }
            if (!wakeLock!!.isHeld) {
                wakeLock!!.acquire()
            }
            for (walletAccount in walletsAccountsMap.values) {
                peerGroup!!.addWallet(walletAccount)
            }
            if (impediments.isEmpty() && peerGroup != null) {
                downloadProgressTracker = DownloadProgressTrackerExt()

                //Start download blockchain
                Log.i(LOG_TAG, "checkImpediments, peergroup startBlockChainDownload")
                peerGroup!!.startBlockChainDownload(downloadProgressTracker)
                //Release wakelock
                if (wakeLock != null && wakeLock!!.isHeld) {
                    wakeLock!!.release()
                    wakeLock = null
                }
            } else {
                Log.i(LOG_TAG, "checkImpediments, impediments size is ${impediments.size} && peergroup is $peerGroup")
            }
            broadcastBlockchainState()
        }
    }

    private fun getAccountWallet(accountIndex: Int) : Wallet? {
        var wallet : Wallet? = walletsAccountsMap[accountIndex]
        if(wallet != null) {
            return wallet
        }
        val walletFile = walletFile(accountIndex)
        if (walletFile.exists()) {
            wallet = loadWalletFromProtobuf(accountIndex, walletFile)
            afterLoadWallet(wallet, accountIndex)
            cleanupFiles(accountIndex)
        }
        return wallet
    }

    private fun loadWalletFromProtobuf(accountIndex: Int, walletAccountFile: File) : Wallet {
        var wallet = FileInputStream(walletAccountFile).use { walletStream ->
            try {
                WalletProtobufSerializer().readWallet(walletStream).apply {
                    if (params != Constants.NETWORK_PARAMETERS) {
                        throw UnreadableWalletException("bad wallet network parameters: ${params.id}")
                    }
                }
            } catch (x: FileNotFoundException) {
                Log.e(LOG_TAG, "problem loading wallet", x)
                Toast.makeText(spvModuleApplication, x.javaClass.name, Toast.LENGTH_LONG).show()
                restoreWalletFromBackup(accountIndex)
            } catch (x: UnreadableWalletException) {
                Log.e(LOG_TAG, "problem loading wallet", x)
                Toast.makeText(spvModuleApplication, x.javaClass.name, Toast.LENGTH_LONG).show()
                restoreWalletFromBackup(accountIndex)
            }
        }

        if (!wallet!!.isConsistent) {
            Toast.makeText(spvModuleApplication, "inconsistent wallet: " + walletAccountFile, Toast.LENGTH_LONG).show()
            wallet = restoreWalletFromBackup(accountIndex)
        }

        if (wallet.params != Constants.NETWORK_PARAMETERS) {
            throw Error("bad wallet network parameters: ${wallet.params.id}")
        }
        return wallet
    }

    private fun restoreWalletFromBackup(accountIndex: Int): Wallet {
        backupFileInputStream(accountIndex).use { stream ->
            val walletAccount = WalletProtobufSerializer().readWallet(stream, true, null)
            if (!walletAccount.isConsistent) {
                throw Error("inconsistent backup")
            }
            //TODO : Reset Blockchain ?
            Log.i(LOG_TAG, "wallet/account restored from backup: "
                    + "'${backupFileName(accountIndex)}'")
            return walletAccount
        }
    }

    private fun afterLoadWallet(walletAccount: Wallet, accountIndex: Int) {
        if(BuildConfig.DEBUG) {
            Log.d(LOG_TAG, "afterLoadWallet, accountIndex = $accountIndex")
        }
        walletAccount.autosaveToFile(walletFile(accountIndex), 10, TimeUnit.SECONDS, WalletAutosaveEventListener())
        // clean up spam
        walletAccount.cleanup()
        migrateBackup(walletAccount, accountIndex)
    }

    private fun migrateBackup(walletAccount: Wallet, accountIndex: Int) {
        if (!backupFile(accountIndex).exists()) {
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

        backupFileOutputStream(accountIndex).use {
            try {
                walletProto.writeTo(it)
            } catch (x: IOException) {
                Log.e(LOG_TAG, "problem writing key backup", x)
            }
        }
    }

    private fun cleanupFiles(accountIndex: Int) {
        for (filename in spvModuleApplication.fileList()) {
            if (filename.startsWith(Constants.Files.WALLET_KEY_BACKUP_BASE58)
                    || filename.startsWith(backupFileName(accountIndex) + '.')
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
            configuration.registerOnSharedPreferenceChangeListener(this)
        }

        internal fun stop() {
            stopped.set(true)

            configuration.unregisterOnSharedPreferenceChangeListener(this)
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
                AsyncTask.execute {
                    this@Bip44AccountIdleService.changed()
                }
            }
        }
    }

    private val impediments = EnumSet.noneOf(BlockchainState.Impediment::class.java)

    private val blockchainState: BlockchainState
        get() {
            val chainHead = blockChain!!.chainHead
            val bestChainDate = chainHead.header.time
            val bestChainHeight = chainHead.height
            val replaying = chainHead.height < configuration.bestChainHeightEver

            return BlockchainState(bestChainDate, bestChainHeight, replaying, impediments)
        }

    private fun broadcastPeerState(numPeers: Int) {
        val broadcast = Intent(SpvService.ACTION_PEER_STATE)
        broadcast.`package` = spvModuleApplication.packageName
        broadcast.putExtra(SpvService.ACTION_PEER_STATE_NUM_PEERS, numPeers)

        LocalBroadcastManager.getInstance(spvModuleApplication).sendBroadcast(broadcast)
    }

    private fun changed() {
        val connectivityNotificationEnabled = configuration.connectivityNotificationEnabled

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
                val impedimentsString = blockchainState.impediments.joinToString {it.toString()}
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
    fun addWalletAccount(bip39Passphrase: List<String>, creationTimeSeconds: Long,
                         accountIndex: Int) {
        Log.d(LOG_TAG, "addWalletAccount, accountIndex = $accountIndex," +
                " creationTimeSeconds = $creationTimeSeconds")
        this.bip39Passphrase = bip39Passphrase
        sharedPreferences.edit()
                .putString(PASSPHRASE_PREF, bip39Passphrase.joinToString(" "))
                .apply()
        if (accountIndexStrings.size == 0) {
            var i = 0
            while (i < 3) {
                createOneAccount(bip39Passphrase, creationTimeSeconds, accountIndex + i)
                i++
            }
        } else if (accountIndexStrings.contains(accountIndex.toString())) {
            var highestCurrentAccountIndex = accountIndex
            for (accountIndexString in accountIndexStrings) {
                if(accountIndexString.toInt() > highestCurrentAccountIndex) {
                    highestCurrentAccountIndex = accountIndexString.toInt()
                }
            }
            createOneAccount(bip39Passphrase, creationTimeSeconds, highestCurrentAccountIndex + 1)
        } else {
            //Should not happen.
            createOneAccount(bip39Passphrase, creationTimeSeconds, accountIndex)
        }
    }

    private fun createOneAccount(bip39Passphrase: List<String>, creationTimeSeconds: Long, accountIndex: Int) {
        Log.d(LOG_TAG, "createOneAccount, accountIndex = $accountIndex," +
                " creationTimeSeconds = $creationTimeSeconds")
        val walletAccount = Wallet.fromSeed(
                Constants.NETWORK_PARAMETERS,
                DeterministicSeed(bip39Passphrase, null, "", creationTimeSeconds),
                ImmutableList.of(ChildNumber(44, true), ChildNumber(1, true),
                        ChildNumber(accountIndex, true)))
        walletAccount.keyChainGroupLookaheadSize = 20
        accountIndexStrings.add(accountIndex.toString())
        sharedPreferences.edit()
                .putStringSet(ACCOUNT_INDEX_STRING_SET_PREF, accountIndexStrings)
                .apply()
        configuration.maybeIncrementBestChainHeightEver(walletAccount.lastBlockSeenHeight)

        walletAccount.saveToFile(walletFile(accountIndex))
    }

    @Synchronized
    fun broadcastTransaction(transaction: Transaction, accountIndex: Int) {
        val wallet = walletsAccountsMap[accountIndex]!!
        wallet.commitTx(transaction)
        wallet.saveToFile(walletFile(accountIndex))
        val transactionBroadcast = peerGroup!!.broadcastTransaction(transaction)
        val future = transactionBroadcast.future()
        future.get()
    }

    fun broadcastTransaction(sendRequest: SendRequest, accountIndex: Int) {
        walletsAccountsMap[accountIndex]?.completeTx(sendRequest)
        broadcastTransaction(sendRequest.tx, accountIndex)
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

    private val transactionsReceived = AtomicInteger()

    private val walletEventListener = object: ThrottlingWalletChangeListener(APPWIDGET_THROTTLE_MS) {
        override fun onCoinsReceived(walletAccount: Wallet?, transaction: Transaction?,
                                     prevBalance: Coin?, newBalance: Coin?) {
            Log.d(LOG_TAG, "walletEventListener, onCoinsReceived")
            transactionsReceived.incrementAndGet()
            checkIfFirstTransaction(walletAccount)
        }

        override fun onCoinsSent(walletAccount: Wallet?, transaction: Transaction?,
                                 prevBalance: Coin?, newBalance: Coin?) {
            Log.d(LOG_TAG, "walletEventListener, onCoinsSent")
            transactionsReceived.incrementAndGet()
            checkIfFirstTransaction(walletAccount)
        }

        private fun checkIfFirstTransaction(walletAccount: Wallet?) {
            //If this is the first transaction found on that wallet/account, stop the download of the blockchain.
            if (walletAccount!!.getRecentTransactions(2, true).size == 1) {
                var accountIndex = 0;
                for (key in walletsAccountsMap.keys()) {
                    if (walletsAccountsMap.get(key)!!.currentReceiveAddress() ==
                            walletAccount.currentReceiveAddress()) {
                        accountIndex = key
                    }
                }
                val newAccountIndex = accountIndex + 1
                if(doesWalletAccountExist(newAccountIndex + 3)) {
                    return
                }
                if (BuildConfig.DEBUG) {
                    Log.d(LOG_TAG, "walletEventListener, checkIfFirstTransaction, first transaction " +
                            "found on that wallet/account with accountIndex = $accountIndex," +
                            " stop the download of the blockchain")
                }
                //TODO Investigate why it is stuck while stopping.
                val listenableFuture = peerGroup!!.stopAsync()
                listenableFuture.addListener(
                        Runnable { Log.d(LOG_TAG, "walletEventListener, checkIfFirstTransaction, will try to " +
                                "addWalletAccountWithExtendedKey with newAccountIndex = $newAccountIndex")
                            spvModuleApplication.addWalletAccountWithExtendedKey(bip39Passphrase,
                                    walletAccount.lastBlockSeenTimeSecs + 1,
                                    newAccountIndex) },
                        Executors.newSingleThreadExecutor())
            }
        }

        override fun onChanged(walletAccount: Wallet) {
            notifyTransactions(walletAccount.getTransactions(true), walletAccount.unspents.toSet())
        }
    }

    @Synchronized
    private fun notifyTransactions(transactions: Set<Transaction>, utxos: Set<TransactionOutput> ) {
        if (!transactions.isEmpty()) {
            // send the new transaction and the *complete* utxo set of the wallet
            SpvMessageSender.sendTransactions(CommunicationManager.getInstance(spvModuleApplication),
                    transactions, utxos)
        }
    }

    private val activityHistory = LinkedList<ActivityHistoryEntry>()

    private fun checkIfDownloadIsIdling() {
        Log.d(LOG_TAG, "checkIfDownloadIsIdling, activityHistory.size = ${activityHistory.size}")
        // determine if block and transaction activity is idling
        var isIdle = false
        if(activityHistory.size == 0) {
            isIdle = true
        }
        for (i in activityHistory.indices) {
            val entry = activityHistory[i]
           /* Log.d(LOG_TAG, "checkIfDownloadIsIdling, activityHistory indice is $i, " +
                    "entry.numBlocksDownloaded = ${entry.numBlocksDownloaded}, " +
                    "entry.numTransactionsReceived = ${entry.numTransactionsReceived}") */
            if (entry.numBlocksDownloaded == 0) {
                isIdle = true
                break
            }
        }
        //We empty the Activity history
        activityHistory.removeAll(activityHistory.clone() as Collection<*>)

        // if idling, shutdown service
        if (isIdle) {
            Log.i(LOG_TAG, "Idling is detected, restart the $LOG_TAG")
            spvModuleApplication.restartBip44AccountIdleService()
        } else {
            countercheckIfDownloadIsIdling = 0
        }
    }

    inner class DownloadProgressTrackerExt : DownloadProgressTracker() {
        override fun onChainDownloadStarted(peer: Peer?, blocksLeft: Int) {
            Log.d(LOG_TAG, "onChainDownloadStarted(), Blockchain's download is starting. " +
                    "Blocks left to download is $blocksLeft, peer = $peer")
            super.onChainDownloadStarted(peer, blocksLeft)
        }

        private val lastMessageTime = AtomicLong(0)

        private var lastChainHeight = 0


        override fun onBlocksDownloaded(peer: Peer, block: Block, filteredBlock: FilteredBlock?,
                                        blocksLeft: Int) {
            if(BuildConfig.DEBUG) {
                //Log.d(LOG_TAG, "onBlocksDownloaded, blocks left + $blocksLeft")
            }
            val now = System.currentTimeMillis()

            updateActivityHistory()

            if (now - lastMessageTime.get() > BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS
                    || blocksLeft == 0) {
                AsyncTask.execute(runnable)
            }
            super.onBlocksDownloaded(peer, block, filteredBlock, blocksLeft)
        }

        private fun updateActivityHistory() {
            val chainHeight = blockChain!!.bestChainHeight
            val numBlocksDownloaded = chainHeight - lastChainHeight
            val numTransactionsReceived = transactionsReceived.getAndSet(0)

            // push history
            activityHistory.add(0, ActivityHistoryEntry(numTransactionsReceived, numBlocksDownloaded))
            lastChainHeight = chainHeight

            // trim
            while (activityHistory.size > MAX_HISTORY_SIZE) {
                activityHistory.removeAt(activityHistory.size - 1)
            }
        }

        override fun doneDownload() {
            Log.d(LOG_TAG, "doneDownload(), Blockchain is fully downloaded.")
            for (walletAccount in walletsAccountsMap.values) {
                peerGroup!!.removeWallet(walletAccount)
            }
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
            configuration.maybeIncrementBestChainHeightEver(blockChain!!.chainHead.height)
            broadcastBlockchainState()
            changed()
        }
    }

    inner class ConnectivityReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                ConnectivityManager.CONNECTIVITY_ACTION -> {
                    val hasConnectivity = !intent.getBooleanExtra(ConnectivityManager.EXTRA_NO_CONNECTIVITY, false)
                    Log.i(LOG_TAG, "ConnectivityReceiver, network is " + if (hasConnectivity) "up" else "down")
                    if (hasConnectivity) {
                        impediments.remove(BlockchainState.Impediment.NETWORK)
                    } else {
                        impediments.add(BlockchainState.Impediment.NETWORK)
                    }
                }
                Intent.ACTION_DEVICE_STORAGE_LOW -> {
                    Log.i(LOG_TAG, "ConnectivityReceiver, device storage low")
                    impediments.add(BlockchainState.Impediment.STORAGE)
                }
                Intent.ACTION_DEVICE_STORAGE_OK -> {
                    Log.i(LOG_TAG, "ConnectivityReceiver, device storage ok")

                    impediments.remove(BlockchainState.Impediment.STORAGE)
                }
            }
        }
    }

    private class WalletAutosaveEventListener : WalletFiles.Listener {
        override fun onBeforeAutoSave(file: File) {
            Log.d("WalletAutosaveEventListener", "onBeforeAutoSave ${file.absolutePath}")
        }

        override fun onAfterAutoSave(file: File) {
            Log.d("WalletAutosaveEventListener", "onAfterAutoSave ${file.absolutePath}")
        }
    }

    private class ActivityHistoryEntry(val numTransactionsReceived: Int, val numBlocksDownloaded: Int) {
        override fun toString(): String = "$numTransactionsReceived / $numBlocksDownloaded"
    }

    private fun backupFileOutputStream(accountIndex: Int): FileOutputStream =
            spvModuleApplication.openFileOutput(backupFileName(accountIndex), Context.MODE_PRIVATE)

    private fun backupFileInputStream(accountIndex: Int): FileInputStream =
            spvModuleApplication.openFileInput(backupFileName(accountIndex))

    private fun backupFile(accountIndex: Int): File =
            spvModuleApplication.getFileStreamPath(backupFileName(accountIndex))

    private fun backupFileName(accountIndex: Int): String =
            Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + "_$accountIndex"

    private fun walletFile(accountIndex: Int): File =
            spvModuleApplication.getFileStreamPath(walletFileName(accountIndex))

    private fun walletFileName(accountIndex: Int): String =
            Constants.Files.WALLET_FILENAME_PROTOBUF + "_$accountIndex"

    companion object {
        private val LOG_TAG = Bip44AccountIdleService::class.java.simpleName
        private val BLOCKCHAIN_STATE_BROADCAST_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS
        private val APPWIDGET_THROTTLE_MS = DateUtils.SECOND_IN_MILLIS
        private val MAX_HISTORY_SIZE = 10
        private val SHARED_PREFERENCES_FILE_NAME = "com.mycelium.spvmodule.PREFERENCE_FILE_KEY"
        private val ACCOUNT_INDEX_STRING_SET_PREF = "account_index_stringset"
        private val PASSPHRASE_PREF = "bip39Passphrase"
    }

    fun doesWalletAccountExist(accountIndex: Int): Boolean {
        val tmpWallet = walletsAccountsMap.get(accountIndex)
        return !(tmpWallet == null)
    }
}