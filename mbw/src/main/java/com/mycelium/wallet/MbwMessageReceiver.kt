package com.mycelium.wallet

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mrd.bitlib.crypto.Bip39
import com.mrd.bitlib.model.*
import com.mrd.bitlib.model.NetworkParameters.NetworkType.*
import com.mrd.bitlib.util.Sha256Hash
import com.mycelium.modularizationtools.CommunicationManager
import com.mycelium.modularizationtools.ModuleMessageReceiver
import com.mycelium.spvmodule.IntentContract
import com.mycelium.wallet.activity.modern.ModernMain
import com.mycelium.wallet.event.SpvSyncChanged
import com.mycelium.wallet.persistence.MetadataStorage
import com.mycelium.wapi.model.TransactionEx
import com.mycelium.wapi.wallet.*
import com.mycelium.wapi.wallet.bip44.Bip44Account
import com.squareup.otto.Bus
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.core.TransactionConfidence.ConfidenceType.*
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.crypto.DeterministicKey
import org.bitcoinj.crypto.HDKeyDerivation
import java.nio.ByteBuffer
import java.util.*
import kotlin.collections.ArrayList

class MbwMessageReceiver constructor(private val context: Context) : ModuleMessageReceiver {
    private val eventBus: Bus = MbwManager.getInstance(context).eventBus

    @Suppress("UNCHECKED_CAST")
    override fun onMessage(callingPackageName: String, intent: Intent) {
        if(intent.action != "com.mycelium.wallet.blockchainState") {
            Log.d(TAG, "onMessage($callingPackageName, $intent)")
        }
        val walletManager = MbwManager.getInstance(context).getWalletManager(false)
        when (intent.action) {
            "com.mycelium.wallet.receivedTransactions" -> {
                val network = MbwEnvironment.verifyEnvironment(context).network
                val protocolId = when (network.networkType) {
                    PRODNET -> "main"
                    REGTEST -> "regtest"
                    TESTNET -> "test"
                    else -> ""
                }
                val networkBJ = org.bitcoinj.core.NetworkParameters.fromPmtProtocolID(protocolId)
                val transactionsBytes = intent.getSerializableExtra("TRANSACTIONS") as Array<ByteArray>
                val fundingOutputs = bitcoinJ2Bitlib(intent.getSerializableExtra("CONNECTED_OUTPUTS")
                        as Map<String, ByteArray>, networkBJ!!) //Funding outputs

                // Unspent Transaction Output (UTXO)
                val utxoSet = (intent.getSerializableExtra("UTXOS") as Map<String, ByteArray>).keys.map {
                    val parts = it.split(":")
                    val hash = Sha256Hash.fromString(parts[0])
                    val index = parts[1].toInt()
                    OutPoint(hash, index)
                }.toSet()

                if (transactionsBytes.isEmpty()) {
                    Log.d(TAG, "onMessage: received an empty transaction notification")
                    return
                }
                var satoshisReceived = 0L
                val mds = MbwManager.getInstance(context).metadataStorage
                val affectedAccounts = HashSet<WalletAccount>()
                try {
                    for (confTransactionBytes in transactionsBytes) {
                        val transactionBytesBuffer = ByteBuffer.wrap(confTransactionBytes)
                        val blockHeight = transactionBytesBuffer.int
                        val transactionBytes = ByteArray(transactionBytesBuffer.capacity() - (4 + 8))
                        //Filling up transactionBytes.
                        transactionBytesBuffer.get(transactionBytes, 0, transactionBytes.size)

                        val updateAtTime = transactionBytesBuffer.long

                        val transaction = Transaction.fromBytes(transactionBytes)

                        val connectedOutputs = HashMap<OutPoint, TransactionOutput>()

                        for (input in transaction.inputs) {
                            val connectedOutput = fundingOutputs[input.outPoint] ?: // skip this
                                     continue
                            connectedOutputs.put(input.outPoint, connectedOutput)
                            val address = connectedOutput.script.getAddress(network)

                            val optionalAccount = walletManager.getAccountByAddress(address)
                            if (optionalAccount.isPresent) {
                                val account : Bip44Account = walletManager.getAccount(optionalAccount.get()) as Bip44Account
                                account.storeAddressOldestActivityTime(address, updateAtTime / 1000)
                                if (account.getTransaction(transaction.hash) == null) {
                                    // The transaction is new and relevant for the account.
                                    // We found spending from the account.
                                    satoshisReceived -= connectedOutput.value
                                    //Should we update lookahead of adresses / Accounts that needs to be look at
                                    //by SPV module ?
                                }
                                affectedAccounts.add(account)
                            }
                        }

                        for (output in transaction.outputs) {
                            //Transaction received and Change from transaction.
                            val address = output.script.getAddress(network)
                            val optionalAccount = walletManager.getAccountByAddress(address)
                            if (optionalAccount.isPresent) {
                                val account = walletManager.getAccount(optionalAccount.get())
                                account.storeAddressOldestActivityTime(address, updateAtTime / 1000)
                                if (account.getTransaction(transaction.hash) == null) {
                                    // The transaction is new and relevant for the account.
                                    // We found spending from the account.
                                    satoshisReceived += output.value
                                }
                                affectedAccounts.add(account)
                            }
                        }
                        for (account in affectedAccounts) {
                            when (blockHeight) {
                                DEAD.value -> Log.e(TAG, "transaction is dead")
                                IN_CONFLICT.value -> Log.e(TAG, "transaction is in conflict")
                                UNKNOWN.value, PENDING.value -> {
                                    account.notifyNewTransactionDiscovered(
                                            TransactionEx.fromUnconfirmedTransaction(transactionBytes),
                                            connectedOutputs, utxoSet, false)
                                    if(account is Bip44Account) {
                                        createNextAccount(account, walletManager, false)
                                    }
                                }
                                else -> {
                                    val txBJ = org.bitcoinj.core.Transaction(networkBJ, transactionBytes)
                                    val txid = Sha256Hash.fromString(txBJ.hash.toString())
                                    txBJ.updateTime = Date(updateAtTime)
                                    val time = (txBJ.updateTime.time / 1000L).toInt()
                                    val tEx = TransactionEx(txid, blockHeight, time, transactionBytes)
                                    account.notifyNewTransactionDiscovered(tEx, connectedOutputs, utxoSet, false)
                                    // Need to launch synchronisation after we notified of a new transaction
                                    // discovered and updated the lookahead of address to observe when using SPV
                                    // module.

                                    // But before that we might need to create the next account if it does not exist.
                                    if(account is Bip44Account) {
                                        createNextAccount(account, walletManager, false)
                                    }
                                }
                            }
                        }
                    }
                    // account has a new incoming transaction!
                    if (satoshisReceived > 0) {
                        notifySatoshisReceived(satoshisReceived, mds, affectedAccounts)
                    }
                } catch (e: Transaction.TransactionParsingException) {
                    Log.e(TAG, e.message, e)
                }
            }
            "com.mycelium.wallet.blockchainState" -> {
                val bestChainDate = intent.getLongExtra("best_chain_date", 0L)
                val bestChainHeight = intent.getIntExtra("best_chain_height", 0)
                // val replaying = intent.getBooleanExtra("replaying", true)
                // val impediments = intent.getStringArrayExtra("impediment")
                walletManager.activeAccounts
                        .filterIsInstance<Bip44Account>()
                        .forEach {
                            if(it.accountIndex
                                    == intent.getIntExtra(IntentContract.ACCOUNT_INDEX_EXTRA, -1)) {
                                it.blockChainHeight = bestChainHeight
                            }
                        }
                // Defines a Handler object that's attached to the UI thread
                val runnable = Runnable {
                    eventBus.post(SpvSyncChanged(Date(bestChainDate), bestChainHeight.toLong())) }
                Handler(Looper.getMainLooper()).post(runnable)

            }
            "com.mycelium.wallet.requestPrivateExtendedKeyCoinTypeToMBW" -> {
                val _mbwManager = MbwManager.getInstance(context)
                val accountIndex = intent.getIntExtra(IntentContract.ACCOUNT_INDEX_EXTRA,
                        (_mbwManager.selectedAccount as Bip44Account).accountIndex)
                val masterSeed: Bip39.MasterSeed
                try {
                    masterSeed = _mbwManager.getWalletManager(false).getMasterSeed(AesKeyCipher.defaultKeyCipher())
                } catch (invalidKeyCipher: KeyCipher.InvalidKeyCipher) {
                    throw RuntimeException(invalidKeyCipher)
                }
                //_mbwManager.getWalletManager(false).getBip44Account(0).allVisibleAddresses dsihdsohs

                val masterDeterministicKey : DeterministicKey = HDKeyDerivation.createMasterPrivateKey(masterSeed.bip32Seed)
                val bip44LevelDeterministicKey = HDKeyDerivation.deriveChildKey(masterDeterministicKey, ChildNumber(44, true))
                val coinType = if (_mbwManager.network.isTestnet) {
                    1 //Bitcoin Testnet https://github.com/satoshilabs/slips/blob/master/slip-0044.md
                } else {
                    0 //Bitcoin Mainnet https://github.com/satoshilabs/slips/blob/master/slip-0044.md
                }
                val cointypeLevelDeterministicKey =
                        HDKeyDerivation.deriveChildKey(bip44LevelDeterministicKey, ChildNumber(coinType, true))
                val networkParameters : NetworkParameters = if (_mbwManager.network.isTestnet) {
                    NetworkParameters.fromID(NetworkParameters.ID_TESTNET)!!
                } else {
                    NetworkParameters.fromID(NetworkParameters.ID_MAINNET)!!
                }
                //val byteArrayToTransmitToSPVModule = accountLevelDeterministicKey.serializePrivate(networkParameters)

                for (address in _mbwManager.getWalletManager(false).addresses) {
                    Log.d(TAG, "onMessage, com.mycelium.wallet.requestPrivateExtendedKeyCoinTypeToMBW, " +
                            "address = $address")
                }

                //HexUtils.toHex(masterSeed.bip32Seed)
                val bip39PassphraseList : ArrayList<String> = ArrayList(masterSeed.bip39WordList)
                val service = IntentContract.RequestPrivateExtendedKeyCoinTypeToSPV.createIntent(
                        accountIndex, bip39PassphraseList,
                        1504664986L) //TODO Change value after test. Nelson
                CommunicationManager.getInstance(context).send(WalletApplication.getSpvModuleName(), service)
            }
            null -> Log.w(TAG, "onMessage failed. No action defined.")
            else -> Log.e(TAG, "onMessage failed. Unknown action ${intent.action}")
        }
    }

    private fun createNextAccount(account: Bip44Account, walletManager: WalletManager,
                                  archived: Boolean) {
        if(account.hasHadActivity()
                && !walletManager.doesBip44AccountExists(account.accountIndex + 1)) {
            val newAccountUUID = walletManager.createArchivedGapFiller(AesKeyCipher.defaultKeyCipher(),
                    account.accountIndex + 1, archived)
            MbwManager.getInstance(context).metadataStorage
                    .storeAccountLabel(newAccountUUID, "Account " + account.accountIndex + 1)
            walletManager.startSynchronization()
        }
    }

    private fun notifySatoshisReceived(satoshisReceived: Long, mds: MetadataStorage,
                                       affectedAccounts: Collection<WalletAccount>) {
        val builder = Notification.Builder(context)
        // TODO: bitcoin icon
        builder.setSmallIcon(R.drawable.holo_dark_ic_action_new_usd_account)
        builder.setContentTitle(context.getString(R.string.app_name))
        var contentText = context.getString(R.string.receiving, satoshisReceived.toString() + "sat")
        val accountString: String = if (affectedAccounts.size > 1) {
            "various accounts"
        } else {
            mds.getLabelByAccount(affectedAccounts.toList()[0].id)
        }
        contentText += " To $accountString"
        builder.setContentText(contentText)
        builder.setContentIntent(PendingIntent.getActivity(context, 0, Intent(context, ModernMain::class.java), 0))
        builder.setWhen(System.currentTimeMillis())
        builder.setSound(Uri.parse("android.resource://${context.packageName}/${R.raw.coins_received}"))
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify(TRANSACTION_NOTIFICATION_ID, builder.build())
    }

    private fun bitcoinJ2Bitlib(bitcoinJConnectedOutputs: Map<String, ByteArray>, networkBJ: NetworkParameters): Map<OutPoint, TransactionOutput> {
        val connectedOutputs = HashMap<OutPoint, TransactionOutput>(bitcoinJConnectedOutputs.size)
        for (id in bitcoinJConnectedOutputs.keys) {
            val parts = id.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val hash = Sha256Hash.fromString(parts[0])
            val index = Integer.parseInt(parts[1])
            val outPoint = OutPoint(hash, index)
            val bitcoinJTransactionOutput = org.bitcoinj.core.TransactionOutput(networkBJ, null, bitcoinJConnectedOutputs[id], 0)
            val value = bitcoinJTransactionOutput.value.longValue()
            val scriptBytes = bitcoinJTransactionOutput.scriptBytes
            val transactionOutput = TransactionOutput(value, ScriptOutput.fromScriptBytes(scriptBytes))
            connectedOutputs.put(outPoint, transactionOutput)
        }
        return connectedOutputs
    }

    companion object {
        private val TAG = MbwMessageReceiver::class.java.canonicalName
        val TRANSACTION_NOTIFICATION_ID = -553794088
    }
}
