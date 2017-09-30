package com.mycelium.spvmodule

import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.preference.PreferenceManager
import android.support.v4.content.LocalBroadcastManager
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import com.mycelium.modularizationtools.ModuleMessageReceiver
import org.bitcoinj.core.Context.enableStrictMode
import org.bitcoinj.core.Context.propagate
import org.bitcoinj.core.Transaction
import org.bitcoinj.core.VerificationException
import org.bitcoinj.core.VersionMessage
import org.bitcoinj.crypto.LinuxSecureRandom
import org.bitcoinj.utils.Threading
import org.bitcoinj.wallet.UnreadableWalletException
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.WalletFiles
import org.bitcoinj.wallet.WalletProtobufSerializer
import java.io.*
import java.util.concurrent.TimeUnit

class SpvModuleApplication : Application(), ModuleMessageReceiver {
    var configuration: Configuration? = null
        private set
    private var activityManager: ActivityManager? = null

    private var blockchainServiceIntent: Intent? = null
    private var blockchainServiceCancelCoinsReceivedIntent: Intent? = null
    private var blockchainServiceResetBlockchainIntent: Intent? = null

    private var walletFile: File? = null
    private var wallet: Wallet? = null
    private var walletAccountIndex : Int? = null
    var packageInfo: PackageInfo? = null
        private set
    private val spvMessageReceiver: SpvMessageReceiver = SpvMessageReceiver(this)

    override fun onMessage(callingPackageName: String, intent: Intent) = spvMessageReceiver.onMessage(callingPackageName, intent)

    override fun onCreate() {
        INSTANCE = if (INSTANCE != null && INSTANCE !== this) {
            throw Error("Application was instanciated more than once?")
        } else {
            this
        }

        LinuxSecureRandom() // init proper random number generator

        StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectAll().permitDiskReads().permitDiskWrites().penaltyLog().build())

        Threading.throwOnLockCycles()
        enableStrictMode()
        propagate(Constants.CONTEXT)

        Log.i(LOG_TAG, "=== starting app using configuration: ${if (Constants.TEST) "test" else "prod"}, ${Constants.NETWORK_PARAMETERS.id}")

        super.onCreate()

        packageInfo = packageInfoFromContext(this)

        configuration = Configuration(PreferenceManager.getDefaultSharedPreferences(this))
        activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

        blockchainServiceIntent = Intent(this, SpvService::class.java)
        blockchainServiceCancelCoinsReceivedIntent = Intent(SpvService.ACTION_CANCEL_COINS_RECEIVED, null, this,
                SpvService::class.java)
        blockchainServiceResetBlockchainIntent = Intent(SpvService.ACTION_RESET_BLOCKCHAIN, null, this, SpvService::class.java)
    }

    private fun switchAccount(accountIndex: Int) {
        Log.d(LOG_TAG, "switchAccount, accountIndex = $accountIndex")
        walletFile = getFileStreamPath(Constants.Files.WALLET_FILENAME_PROTOBUF + "_$accountIndex")
        loadWalletFromProtobuf()
        afterLoadWallet()
        cleanupFiles()
        walletAccountIndex = accountIndex
    }

    private fun afterLoadWallet() {
        if (wallet != null) {
            wallet!!.autosaveToFile(walletFile!!, 10, TimeUnit.SECONDS, WalletAutosaveEventListener())

            // clean up spam
            wallet!!.cleanup()

            migrateBackup()
        }
    }

    private class WalletAutosaveEventListener : WalletFiles.Listener {
        override fun onBeforeAutoSave(file: File) = Unit

        override fun onAfterAutoSave(file: File) =
                // make wallets world accessible in test mode
                //if (Constants.TEST) {
                //   Io.chmod(file, 0777);
                //}
                Unit
    }

    private fun loadWalletFromProtobuf() {
        if (walletFile!!.exists()) {
            val start = System.currentTimeMillis()

            try {
                FileInputStream(walletFile!!).use { walletStream ->
                    wallet = WalletProtobufSerializer().readWallet(walletStream)
                    if (wallet!!.params != Constants.NETWORK_PARAMETERS) {
                        throw UnreadableWalletException("bad wallet network parameters: " + wallet!!.params.id)
                    }

                    Log.i(LOG_TAG, "wallet loaded from: '$walletFile', took ${System.currentTimeMillis() - start}ms")
                }
            } catch (x: FileNotFoundException) {
                Log.e(LOG_TAG, "problem loading wallet", x)
                Toast.makeText(this@SpvModuleApplication, x.javaClass.name, Toast.LENGTH_LONG).show()
                wallet = restoreWalletFromBackup()
            } catch (x: UnreadableWalletException) {
                Log.e(LOG_TAG, "problem loading wallet", x)
                Toast.makeText(this@SpvModuleApplication, x.javaClass.name, Toast.LENGTH_LONG).show()
                wallet = restoreWalletFromBackup()
            }

            if (!wallet!!.isConsistent) {
                Toast.makeText(this, "inconsistent wallet: $walletFile", Toast.LENGTH_LONG).show()
                wallet = restoreWalletFromBackup()
            }

            if (wallet!!.params != Constants.NETWORK_PARAMETERS) {
                throw Error("bad wallet network parameters: ${wallet!!.params.id}")
            }
        } else {
            wallet = null
        }
    }

    private fun restoreWalletFromBackup(): Wallet {
        openFileInput(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF).use { stream ->
            val wallet = WalletProtobufSerializer().readWallet(stream, true, null)
            if (!wallet.isConsistent) {
                throw Error("inconsistent backup")
            }
            resetBlockchain()
            Log.i(LOG_TAG, "wallet restored from backup: '${Constants.Files.WALLET_KEY_BACKUP_PROTOBUF}'")
            return wallet
        }
    }

    fun backupWallet() {
        val builder = WalletProtobufSerializer().walletToProto(wallet!!).toBuilder()

        // strip redundant
        builder.clearTransaction()
        builder.clearLastSeenBlockHash()
        builder.lastSeenBlockHeight = -1
        builder.clearLastSeenBlockTimeSecs()
        val walletProto = builder.build()

        try {
            openFileOutput(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF, Context.MODE_PRIVATE).use { os ->
                walletProto.writeTo(os)
            }
        } catch (x: IOException) {
            Log.e(LOG_TAG, "problem writing key backup", x)
        }
    }

    private fun migrateBackup() {
        // TODO: make this multi-wallet aware
        if (!getFileStreamPath(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF).exists()) {
            Log.i(LOG_TAG, "migrating automatic backup to protobuf")
            // make sure there is at least one recent backup
            backupWallet()
        }
    }

    private fun cleanupFiles() {
        for (filename in fileList()) {
            if (filename.startsWith(Constants.Files.WALLET_KEY_BACKUP_BASE58)
                    || filename.startsWith(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF + '.') || filename.endsWith(".tmp")) {
                val file = File(filesDir, filename)
                Log.i(LOG_TAG, "removing obsolete file: '$file'")
                file.delete()
            }
        }
    }

    fun startBlockchainService(cancelCoinsReceived: Boolean, accountIndex: Int) {
        Handler(Looper.getMainLooper()).post {
            if (cancelCoinsReceived) {
                blockchainServiceCancelCoinsReceivedIntent!!.putExtra(IntentContract.ACCOUNT_INDEX_EXTRA, accountIndex)
                startService(blockchainServiceCancelCoinsReceivedIntent)
            }
            else
                startService(blockchainServiceIntent)
        }
    }

    fun stopBlockchainService() {
        stopService(blockchainServiceIntent)
    }

    fun resetBlockchain() {
        Log.d(LOG_TAG, "resetBlockchain")
        Handler(Looper.getMainLooper()).post {
            // implicitly stops blockchain service
            startService(blockchainServiceResetBlockchainIntent)
        }
    }

    @Synchronized
    fun resetBlockchainWithExtendedKey(bip39Passphrase: ArrayList<String>, creationTimeSeconds: Long,
                                       accountIndex: Int) {
        // implicitly stops blockchain service
        Handler(Looper.getMainLooper()).post {
            blockchainServiceResetBlockchainIntent!!
                    .putExtra("bip39Passphrase", bip39Passphrase)
            blockchainServiceResetBlockchainIntent!!
                    .putExtra(IntentContract.ACCOUNT_INDEX_EXTRA, accountIndex)
            blockchainServiceResetBlockchainIntent!!.putExtra("creationTimeSeconds", creationTimeSeconds)
            stopBlockchainService()
            Log.d(LOG_TAG, "resetBlockchainWithExtendedKey, startService : $blockchainServiceResetBlockchainIntent")
            startService(blockchainServiceResetBlockchainIntent)
        }
    }

    fun replaceWallet(newWallet: Wallet) {
        if (wallet != null) {
            resetBlockchain()
            wallet!!.shutdownAutosaveAndWait()
        }
        wallet = newWallet
        configuration!!.maybeIncrementBestChainHeightEver(newWallet.lastBlockSeenHeight)
        afterLoadWallet()

        val broadcast = Intent(ACTION_WALLET_REFERENCE_CHANGED) //TODO Investigate utility of this.
        broadcast.`package` = packageName
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
    }

    @Throws(VerificationException::class)
    fun processDirectTransaction(tx: Transaction, accountIndex: Int) {
        if (wallet!!.isTransactionRelevant(tx)) {
            wallet!!.receivePending(tx, null)
            broadcastTransaction(tx, accountIndex)
        }
    }

    fun broadcastTransaction(tx: Transaction, accountIndex: Int) {
        val intent = Intent(SpvService.ACTION_BROADCAST_TRANSACTION, null, this, SpvService::class.java)
        intent.putExtra(SpvService.ACTION_BROADCAST_TRANSACTION_HASH, tx.hash.bytes)
        intent.putExtra(IntentContract.ACCOUNT_INDEX_EXTRA, accountIndex)
        Handler(Looper.getMainLooper()).post {
            startService(intent)
        }
    }

    fun packageInfo(): PackageInfo = packageInfo!!

    fun httpUserAgent(): String = httpUserAgent(packageInfo().versionName)

    fun maxConnectedPeers(): Int =
            if (activityManager!!.memoryClass <= Constants.MEMORY_CLASS_LOWEND) {
                4
            } else {
                6
            }

    companion object {
        private var INSTANCE: SpvModuleApplication? = null

        val ACTION_WALLET_REFERENCE_CHANGED = SpvModuleApplication::class.java.`package`.name + ".wallet_reference_changed"

        fun getApplication(): SpvModuleApplication = INSTANCE!!

        fun getWallet(): Wallet? =  INSTANCE!!.wallet

        fun getWallet(index : Int): Wallet? {
            if(INSTANCE!!.walletAccountIndex != index) {
                INSTANCE!!.switchAccount(index)
            }
            return getWallet()
        }

        fun packageInfoFromContext(context: Context): PackageInfo {
            try {
                return context.packageManager.getPackageInfo(context.packageName, 0)
            } catch (x: PackageManager.NameNotFoundException) {
                throw RuntimeException(x)
            }
        }

        fun httpUserAgent(versionName: String): String {
            val versionMessage = VersionMessage(Constants.NETWORK_PARAMETERS, 0)
            versionMessage.appendToSubVer(Constants.USER_AGENT, versionName, null)
            return versionMessage.subVer
        }

        private val LOG_TAG: String? = this::class.java.canonicalName

        fun scheduleStartBlockchainService(context: Context) {
            val config = Configuration(PreferenceManager.getDefaultSharedPreferences(context))
            val lastUsedAgo = config.lastUsedAgo

            // apply some backoff
            val alarmInterval: Long
            if (lastUsedAgo < Constants.LAST_USAGE_THRESHOLD_JUST_MS)
                alarmInterval = AlarmManager.INTERVAL_FIFTEEN_MINUTES
            else if (lastUsedAgo < Constants.LAST_USAGE_THRESHOLD_RECENTLY_MS)
                alarmInterval = AlarmManager.INTERVAL_HALF_DAY
            else
                alarmInterval = AlarmManager.INTERVAL_DAY

            Log.i(LOG_TAG, "last used ${lastUsedAgo / DateUtils.MINUTE_IN_MILLIS} minutes ago, rescheduling blockchain sync in roughly ${alarmInterval / DateUtils.MINUTE_IN_MILLIS} minutes")

            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent =  Intent(context, SpvService::class.java)
            intent.putExtra(IntentContract.ACCOUNT_INDEX_EXTRA, INSTANCE!!.walletAccountIndex)
            val alarmIntent = PendingIntent.getService(context, 0, intent, 0)
            alarmManager.cancel(alarmIntent)

            // workaround for no inexact set() before KitKat
            val now = System.currentTimeMillis()
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, now + alarmInterval, AlarmManager.INTERVAL_DAY, alarmIntent)
        }

        fun getMbwModuleName(): String = when (BuildConfig.APPLICATION_ID) {
            "com.mycelium.spvmodule_testrelease" -> "com.mycelium.testnetwallet_spore"
            "com.mycelium.spvmodule.test" -> "com.mycelium.devwallet_spore"
            else -> throw RuntimeException("No mbw module defined for BuildConfig " + BuildConfig.APPLICATION_ID)
        }
    }
}
