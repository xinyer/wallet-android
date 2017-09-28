package com.mycelium.spvmodule

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.StrictMode
import android.preference.PreferenceManager
import android.util.Log
import com.mycelium.modularizationtools.ModuleMessageReceiver
import com.mycelium.spvmodule.guava.Bip44AccountIdleService
import org.bitcoinj.core.*
import org.bitcoinj.core.Context.enableStrictMode
import org.bitcoinj.core.Context.propagate
import org.bitcoinj.crypto.LinuxSecureRandom
import org.bitcoinj.utils.Threading
import org.bitcoinj.wallet.SendRequest
import java.util.*

class SpvModuleApplication : Application(), ModuleMessageReceiver {
    var configuration: Configuration? = null
        private set
    private var activityManager: ActivityManager? = null

    private var blockchainServiceIntent: Intent? = null
    private var blockchainServiceCancelCoinsReceivedIntent: Intent? = null
    private var blockchainServiceResetBlockchainIntent: Intent? = null

    private lateinit var bip44AccountIdleService: Bip44AccountIdleService

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
        blockchainServiceResetBlockchainIntent = Intent(SpvService.ACTION_ADD_ACCOUNT, null, this, SpvService::class.java)
        bip44AccountIdleService = Bip44AccountIdleService()
        bip44AccountIdleService = bip44AccountIdleService.startAsync() as Bip44AccountIdleService
    }

    private val LOG_TAG: String? = this.javaClass.canonicalName

    /*
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
*/
    fun stopBlockchainService() {
        stopService(blockchainServiceIntent)
    }
/*
    fun resetBlockchain() {
        Log.d(LOG_TAG, "resetBlockchain")
        Handler(Looper.getMainLooper()).post {
            // implicitly stops blockchain service
            startService(blockchainServiceResetBlockchainIntent)
        }
    }
    */

    fun addAccountWalletWithExtendedKey(bip39Passphrase: ArrayList<String>, creationTimeSeconds: Long,
                                        accountIndex: Int) {
        bip44AccountIdleService.addWalletAccount(bip39Passphrase, creationTimeSeconds, accountIndex)
    }

    fun broadcastTransaction(tx: Transaction, accountIndex: Int) {
        bip44AccountIdleService.broadcastTransaction(tx, accountIndex)
    }

    fun broadcastTransaction(sendRequest: SendRequest, accountIndex: Int) {
        bip44AccountIdleService.broadcastTransaction(sendRequest, accountIndex)
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

        /*
        fun getWallet(): Wallet? =  INSTANCE!!.wallet

        fun getWallet(accountIndex: Int): Wallet? {
            return getWallet()
        }
        */

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

        /*
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
*/
        fun getMbwModuleName(): String = when (BuildConfig.APPLICATION_ID) {
            "com.mycelium.spvmodule_testrelease" -> "com.mycelium.testnetwallet_spore"
            "com.mycelium.spvmodule.test" -> "com.mycelium.devwallet_spore"
            else -> throw RuntimeException("No mbw module defined for BuildConfig " + BuildConfig.APPLICATION_ID)
        }
    }
}
