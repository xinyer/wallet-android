package com.mycelium.spvmodule

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.StrictMode
import android.preference.PreferenceManager
import android.support.v4.content.LocalBroadcastManager
import android.text.format.DateUtils
import android.util.Log
import android.widget.Toast
import com.mycelium.modularizationtools.CommunicationManager
import com.mycelium.modularizationtools.ModuleMessageReceiver
import com.mycelium.spvmodule.Constants.Companion.TAG

import org.bitcoinj.core.Transaction
import org.bitcoinj.core.VerificationException
import org.bitcoinj.core.VersionMessage
import org.bitcoinj.crypto.LinuxSecureRandom
import org.bitcoinj.utils.Threading
import org.bitcoinj.wallet.UnreadableWalletException
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.WalletFiles
import org.bitcoinj.wallet.WalletProtobufSerializer

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.concurrent.TimeUnit

import org.bitcoinj.core.Context.*
import org.bitcoinj.params.TestNet2Params
import org.bitcoinj.params.TestNet3Params
import java.util.concurrent.Executors

class SpvModuleApplication : Application(), ModuleMessageReceiver {
    var configuration: Configuration? = null
        private set
    private var activityManager: ActivityManager? = null

    private var blockchainServiceIntent: Intent? = null
    private var blockchainServiceCancelCoinsReceivedIntent: Intent? = null
    private var blockchainServiceResetBlockchainIntent: Intent? = null

    private var walletFile: File? = null
    private var wallet: Wallet? = null
    var packageInfo: PackageInfo? = null
        private set
    private val spvMessageReceiver: SpvMessageReceiver = SpvMessageReceiver(this)

    override fun onMessage(callingPackageName: String, intent: Intent) = spvMessageReceiver.onMessage(callingPackageName, intent)

    override fun onCreate() {
        if (INSTANCE != null && INSTANCE !== this) {
            Log.w(LOG_TAG, "Application was instanciated more than once?")
        }
        INSTANCE = this

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
        walletFile = getFileStreamPath(Constants.Files.WALLET_FILENAME_PROTOBUF)

        loadWalletFromProtobuf()

        afterLoadWallet()

        cleanupFiles()
    }

    private fun afterLoadWallet() {
        if(wallet != null) {
            wallet!!.autosaveToFile(walletFile!!, 10, TimeUnit.SECONDS, WalletAutosaveEventListener())

            // clean up spam
            wallet!!.cleanup()

            migrateBackup()
        }
    }

    private class WalletAutosaveEventListener : WalletFiles.Listener {
        override fun onBeforeAutoSave(file: File) {
        }

        override fun onAfterAutoSave(file: File) {
            // make wallets world accessible in test mode
            //if (Constants.TEST) {
            //   Io.chmod(file, 0777);
            //}
        }
    }

    private fun loadWalletFromProtobuf() {
        if (walletFile!!.exists()) {
            val start = System.currentTimeMillis()

            var walletStream: FileInputStream? = null

            try {
                walletStream = FileInputStream(walletFile!!)

                wallet = WalletProtobufSerializer().readWallet(walletStream)

                if (wallet!!.params != Constants.NETWORK_PARAMETERS)
                    throw UnreadableWalletException("bad wallet network parameters: " + wallet!!.params.id)

                Log.i(LOG_TAG, "wallet loaded from: '$walletFile', took ${System.currentTimeMillis() - start}ms")
            } catch (x: FileNotFoundException) {
                Log.e(LOG_TAG, "problem loading wallet", x)

                Toast.makeText(this@SpvModuleApplication, x.javaClass.name, Toast.LENGTH_LONG).show()

                wallet = restoreWalletFromBackup()
            } catch (x: UnreadableWalletException) {
                Log.e(LOG_TAG, "problem loading wallet", x)
                Toast.makeText(this@SpvModuleApplication, x.javaClass.name, Toast.LENGTH_LONG).show()
                wallet = restoreWalletFromBackup()
            } finally {
                if (walletStream != null) {
                    try {
                        walletStream.close()
                    } catch (x: IOException) {
                        // swallow
                    }

                }
            }

            if (!wallet!!.isConsistent) {
                Toast.makeText(this, "inconsistent wallet: " + walletFile!!, Toast.LENGTH_LONG).show()

                wallet = restoreWalletFromBackup()
            }

            if (wallet!!.params != Constants.NETWORK_PARAMETERS)
                throw Error("bad wallet network parameters: " + wallet!!.params.id)
        } else {
            //first creation of wallet.
            //wallet = Wallet(Constants.NETWORK_PARAMETERS)
            //backupWallet()
            wallet = null
            Log.i(LOG_TAG, "null wallet created")
        }
    }

    private fun restoreWalletFromBackup(): Wallet {
        var stream: InputStream? = null

        try {
            stream = openFileInput(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF)

            val wallet = WalletProtobufSerializer().readWallet(stream, true, null)

            if (!wallet.isConsistent) {
                throw Error("inconsistent backup")
            }

            resetBlockchain()

            // Toast.makeText(this, R.string.toast_wallet_reset, Toast.LENGTH_LONG).show();

            Log.i(LOG_TAG, "wallet restored from backup: '${Constants.Files.WALLET_KEY_BACKUP_PROTOBUF}'")

            return wallet
        } catch (x: IOException) {
            throw Error("cannot read backup", x)
        } catch (x: UnreadableWalletException) {
            throw Error("cannot read backup", x)
        } finally {
            try {
                if (stream != null) {
                    stream.close()
                }
            } catch (ignored: IOException) {
            }
        }
    }

    fun saveWallet() {
        try {
            protobufSerializeWallet(wallet!!)
        } catch (x: IOException) {
            throw RuntimeException(x)
        }

    }

    @Throws(IOException::class)
    private fun protobufSerializeWallet(wallet: Wallet) {
        val start = System.currentTimeMillis()

        wallet.saveToFile(walletFile!!)
        Log.d(LOG_TAG, "wallet saved to: $walletFile', took ${System.currentTimeMillis() - start}ms")
    }

    private val LOG_TAG: String? = this.javaClass.canonicalName

    fun backupWallet() {
        val builder = WalletProtobufSerializer().walletToProto(wallet!!).toBuilder()

        // strip redundant
        builder.clearTransaction()
        builder.clearLastSeenBlockHash()
        builder.lastSeenBlockHeight = -1
        builder.clearLastSeenBlockTimeSecs()
        val walletProto = builder.build()

        var os: OutputStream? = null

        try {
            os = openFileOutput(Constants.Files.WALLET_KEY_BACKUP_PROTOBUF, Context.MODE_PRIVATE)
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

    private fun migrateBackup() {
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

    fun startBlockchainService(cancelCoinsReceived: Boolean) {
        val executorService = Executors.newSingleThreadExecutor()
        executorService.execute {
            if (cancelCoinsReceived)
                startService(blockchainServiceCancelCoinsReceivedIntent)
            else
                startService(blockchainServiceIntent)
        }
    }

    fun stopBlockchainService() {
        stopService(blockchainServiceIntent)
    }

    fun resetBlockchain() {
        // implicitly stops blockchain service
        val executorService = Executors.newSingleThreadExecutor()
        executorService.execute {
            startService(blockchainServiceResetBlockchainIntent)
        }
    }

    fun resetBlockchainWithExtendedKey(extendedKey: ByteArray, creationTimeSeconds : Long) {
        Log.d(LOG_TAG, "resetBlockchainWithExtendedKey, extend key = $extendedKey, creationTimeSeconds = $creationTimeSeconds")
        // implicitly stops blockchain service
        val executorService = Executors.newSingleThreadExecutor()
        executorService.execute {
            blockchainServiceResetBlockchainIntent!!.putExtra("extendedKey", extendedKey)
            blockchainServiceResetBlockchainIntent!!.putExtra("creationTimeSeconds", creationTimeSeconds)
            stopBlockchainService()
            Log.d(LOG_TAG, "resetBlockchainWithExtendedKey, startService : $blockchainServiceResetBlockchainIntent")
            startService(blockchainServiceResetBlockchainIntent)
        }
    }

    fun replaceWallet(newWallet: Wallet) {
        if(wallet != null) {
            resetBlockchain()
            wallet!!.shutdownAutosaveAndWait()
        }
        for (key in newWallet.importedKeys) {
            key.toAddress(TestNet3Params.get())
        }
        Log.d(LOG_TAG, "replaceWallet, ${newWallet.importedKeys}")
        wallet = newWallet
        configuration!!.maybeIncrementBestChainHeightEver(newWallet.lastBlockSeenHeight)
        afterLoadWallet()

        val broadcast = Intent(ACTION_WALLET_REFERENCE_CHANGED) //TODO Investigate utility of this.
        broadcast.`package` = packageName
        LocalBroadcastManager.getInstance(this).sendBroadcast(broadcast)
    }

    @Throws(VerificationException::class)
    fun processDirectTransaction(tx: Transaction) {
        if (wallet!!.isTransactionRelevant(tx)) {
            wallet!!.receivePending(tx, null)
            broadcastTransaction(tx)
        }
    }

    fun broadcastTransaction(tx: Transaction) {
        val intent = Intent(SpvService.ACTION_BROADCAST_TRANSACTION, null, this, SpvService::class.java)
        intent.putExtra(SpvService.ACTION_BROADCAST_TRANSACTION_HASH, tx.hash.bytes)
        val executorService = Executors.newSingleThreadExecutor()
        executorService.execute {
            startService(intent)
        }
    }

    fun packageInfo(): PackageInfo {
        return packageInfo!!
    }

    fun httpUserAgent(): String {
        return httpUserAgent(packageInfo().versionName)
    }

    fun maxConnectedPeers(): Int {
        return if (activityManager!!.memoryClass <= Constants.MEMORY_CLASS_LOWEND) {
            4
        } else {
            6
        }
    }

    companion object {
        private var INSTANCE: SpvModuleApplication? = null

        val ACTION_WALLET_REFERENCE_CHANGED = SpvModuleApplication::class.java.`package`.name + ".wallet_reference_changed"

        fun getApplication(): SpvModuleApplication {
            return INSTANCE!!
        }

        fun getWallet(): Wallet? {
            return INSTANCE!!.wallet
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
            val alarmIntent = PendingIntent.getService(context, 0, Intent(context, SpvService::class.java), 0)
            alarmManager.cancel(alarmIntent)

            // workaround for no inexact set() before KitKat
            val now = System.currentTimeMillis()
            alarmManager.setInexactRepeating(AlarmManager.RTC_WAKEUP, now + alarmInterval, AlarmManager.INTERVAL_DAY, alarmIntent)
        }
    }
}
