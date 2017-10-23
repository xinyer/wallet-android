package com.mycelium.spvmodule.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.util.Log
import com.mycelium.modularizationtools.CommunicationManager
import com.mycelium.spvmodule.guava.Bip44AccountIdleService
import com.mycelium.spvmodule.providers.TransactionContract.TransactionSummary
import com.mycelium.spvmodule.providers.TransactionContract.TransactionDetails
import com.mycelium.spvmodule.providers.data.TransactionDetailsCursor
import com.mycelium.spvmodule.providers.data.TransactionsSummaryCursor


/**
 * Created by Nelson on 17/10/2017.
 */
class TransactionContentProvider : ContentProvider() {

    var communicationManager: CommunicationManager? = null

    val LOG_TAG = this::class.java.simpleName

    override fun onCreate(): Boolean {
        communicationManager = CommunicationManager.getInstance(context)
        return true
    }

    override fun query(uri: Uri?, projection: Array<out String>?, selection: String?,
                       selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        checkSignature(callingPackage)
        var cursor = MatrixCursor(emptyArray(), 0)
        val match = URI_MATCHER.match(uri)
        when (match) {
            TRANSACTIONS_LIST ->
                if (selection!!.contentEquals(TransactionSummary.SELECTION_ACCOUNT_INDEX)) {
                    Log.d(LOG_TAG, "query, TRANSACTIONS_LIST")
                    val accountIndex = selectionArgs!!.get(0)

                    val transactionsSummary =
                            Bip44AccountIdleService.getInstance().getTransactionsSummary(accountIndex.toInt())
                    cursor = TransactionsSummaryCursor(transactionsSummary.size)

                    for(rowItem in transactionsSummary) {
                        val columnValues = mutableListOf<Any?>()
                        columnValues.add(rowItem.txid.toHex())                          //TransactionContract.TransactionSummary._ID
                        columnValues.add(rowItem.value.value.toPlainString())           //TransactionContract.TransactionSummary.VALUE
                        columnValues.add(if (rowItem.isIncoming) 1 else 0)              //TransactionContract.TransactionSummary.IS_INCOMING
                        columnValues.add(rowItem.time)                                  //TransactionContract.TransactionSummary.TIME
                        columnValues.add(rowItem.height)                                //TransactionContract.TransactionSummary.HEIGHT
                        columnValues.add(rowItem.confirmations)                         //TransactionContract.TransactionSummary.CONFIRMATIONS
                        columnValues.add(if (rowItem.isQueuedOutgoing) 1 else 0)        //TransactionContract.TransactionSummary.IS_QUEUED_OUTGOING
                        if (rowItem.confirmationRiskProfile.isPresent) {
                            val confirmationRiskProfile = rowItem.confirmationRiskProfile.get()
                            columnValues.add(confirmationRiskProfile.unconfirmedChainLength)      //TransactionContract.TransactionSummary.CONFIRMATION_RISK_PROFILE_LENGTH
                            columnValues.add(confirmationRiskProfile.hasRbfRisk)                  //TransactionContract.TransactionSummary.CONFIRMATION_RISK_PROFILE_RBF_RISK
                            columnValues.add(confirmationRiskProfile.isDoubleSpend)               //TransactionContract.TransactionSummary.CONFIRMATION_RISK_PROFILE_DOUBLE_SPEND
                        } else {
                            columnValues.add(-1)
                            columnValues.add(null)
                            columnValues.add(null)
                        }
                        val isDestAddressPresent = rowItem.destinationAddress.isPresent
                        columnValues.add(if (isDestAddressPresent) rowItem.destinationAddress.get().toString() else null) //TransactionContract.TransactionSummary.DESTINATION_ADDRESS
                        val addressesBuilder = StringBuilder()
                        for (addr in rowItem.toAddresses) {
                            if (addressesBuilder.isNotEmpty()) {
                                addressesBuilder.append(",")
                            }
                            addressesBuilder.append(addr.toString())
                        }
                        columnValues.add(addressesBuilder.toString())       //TransactionContract.TransactionSummary.TO_ADDRESSES
                        cursor.addRow(columnValues)
                    }
                    return cursor
                }
            TRANSACTION_DETAILS ->
                if (selection!!.contentEquals(TransactionSummary.SELECTION_ACCOUNT_INDEX)) {
                    cursor = TransactionDetailsCursor()
                    val accountIndex = selectionArgs!!.get(0)
                    val hash = uri!!.lastPathSegment
                    val transactionDetails = Bip44AccountIdleService.getInstance()
                            .getTransactionDetails(accountIndex.toInt(), hash)

                    if (transactionDetails == null) {
                        return cursor
                    }

                    val columnValues = mutableListOf<Any?>()
                    columnValues.add(transactionDetails.hash.toHex())       //TransactionContract.Transaction._ID
                    columnValues.add(transactionDetails.height)             //TransactionContract.Transaction.HEIGHT
                    columnValues.add(transactionDetails.time)               //TransactionContract.Transaction.TIME
                    columnValues.add(transactionDetails.rawSize)            //TransactionContract.Transaction.RAW_SIZE
                    val inputsBuilder = StringBuilder()
                    for (input in transactionDetails.inputs) {
                        if (inputsBuilder.isNotEmpty()) {
                            inputsBuilder.append(",")
                        }
                        inputsBuilder.append("${input.value} BTC")
                        inputsBuilder.append("${input.address}")
                    }
                    columnValues.add(inputsBuilder.toString())              //TransactionContract.Transaction.INPUTS

                    val outputsBuilder = StringBuilder()
                    for (output in transactionDetails.outputs) {
                        if (outputsBuilder.isNotEmpty()) {
                            outputsBuilder.append(",")
                        }
                        outputsBuilder.append("${output.value} BTC")
                        outputsBuilder.append("${output.address}")
                    }
                    columnValues.add(outputsBuilder.toString())             //TransactionContract.Transaction.OUTPUTS
                    cursor.addRow(columnValues)

                }
            else -> {
                // Do nothing.
            }
        }
        return cursor
    }

    override fun insert(uri: Uri?, values: ContentValues?): Uri {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun update(uri: Uri?, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    override fun delete(uri: Uri?, selection: String?, selectionArgs: Array<out String>?): Int {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
    }

    private fun checkSignature(callingPackage: String) {
        communicationManager!!.checkSignature(callingPackage)
    }

    override fun getType(uri: Uri): String? {
        checkSignature(callingPackage)
        return when (URI_MATCHER.match(uri)) {
            TRANSACTIONS_LIST -> TransactionSummary.CONTENT_TYPE
            TRANSACTION_DETAILS -> TransactionDetails.CONTENT_TYPE
            else -> throw IllegalArgumentException("Unknown URI " + uri)
        }
    }

    companion object {

        val URI_MATCHER: UriMatcher = UriMatcher(UriMatcher.NO_MATCH)

        private val TRANSACTIONS_LIST = 1
        private val TRANSACTION_DETAILS = 2

        init {
            URI_MATCHER.addURI(TransactionContract.AUTHORITY("com.mycelium.spvmodule.test"),
                    TransactionSummary.TABLE_NAME, TRANSACTIONS_LIST)
            URI_MATCHER.addURI(TransactionContract.AUTHORITY("com.mycelium.spvmodule.test"),
                    "${TransactionDetails.TABLE_NAME}/*", TRANSACTION_DETAILS)
        }

        private fun getTableFromMatch(match: Int): String = when (match) {
            TRANSACTIONS_LIST -> TransactionSummary.TABLE_NAME
            TRANSACTION_DETAILS -> TransactionDetails.TABLE_NAME
            else -> throw IllegalArgumentException("Unknown match " + match)
        }
    }
}