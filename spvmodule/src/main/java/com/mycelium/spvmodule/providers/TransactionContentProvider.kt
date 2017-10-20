package com.mycelium.spvmodule.providers

import android.content.ContentProvider
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.net.Uri
import com.mycelium.modularizationtools.CommunicationManager
import com.mycelium.spvmodule.guava.Bip44AccountIdleService
import com.mycelium.spvmodule.providers.TransactionContract.Transaction
import com.mycelium.spvmodule.providers.data.TransactionCursor


/**
 * Created by Nelson on 17/10/2017.
 */
class TransactionContentProvider : ContentProvider() {

    var communicationManager: CommunicationManager? = null

    override fun onCreate(): Boolean {
        communicationManager = CommunicationManager.getInstance(context)
        return true
    }

    override fun query(uri: Uri?, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        checkSignature(callingPackage)
        var cursor = TransactionCursor(0)
        val match = URI_MATCHER.match(uri)
        when (match) {
            TRANSACTION_LIST ->
                if (selection!!.contentEquals(Transaction.SELECTION_ACCOUNT_INDEX)) {
                    val accountIndex = selectionArgs!!.get(0)

                    val transactionsSummary =
                            Bip44AccountIdleService.getInstance().getTransactionsSummary(accountIndex.toInt())
                    cursor = TransactionCursor(transactionsSummary.size)

                    for(rowItem in transactionsSummary) {
                        val columnValues = mutableListOf<Any?>()
                        columnValues.add(rowItem.txid.toHex())      //TransactionContract.Transaction._ID
                        columnValues.add(rowItem.value.value.toPlainString())   //TransactionContract.Transaction.VALUE
                        columnValues.add(rowItem.isIncoming)        //TransactionContract.Transaction.IS_INCOMING
                        columnValues.add(rowItem.time)              //TransactionContract.Transaction.TIME
                        columnValues.add(rowItem.height)            //TransactionContract.Transaction.HEIGHT
                        columnValues.add(rowItem.confirmations)     //TransactionContract.Transaction.CONFIRMATIONS
                        columnValues.add(rowItem.isQueuedOutgoing)  //TransactionContract.Transaction.IS_QUEUED_OUTGOING
                        columnValues.add(rowItem.confirmationRiskProfile.toString())    //TransactionContract.Transaction.CONFIRMATION_RISK_PROFILE
                        val isDestAddressPresent = rowItem.destinationAddress.isPresent
                        columnValues.add(if (isDestAddressPresent) rowItem.destinationAddress.toString() else null) //TransactionContract.Transaction.DESTINATION_ADDRESS
                        val addressesBuilder = StringBuilder()
                        for (addr in rowItem.toAddresses) {
                            if (addressesBuilder.isNotEmpty()) {
                                addressesBuilder.append(",")
                            }
                            addressesBuilder.append(addr.toString())
                        }
                        columnValues.add(rowItem.toAddresses.toString())    //TransactionContract.Transaction.TO_ADDRESSES
                        cursor.addRow(columnValues)
                    }

                    return cursor
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
            TRANSACTION_LIST -> Transaction.CONTENT_TYPE
            else -> throw IllegalArgumentException("Unknown URI " + uri)
        }
    }

    companion object {

        val URI_MATCHER: UriMatcher

        private val TRANSACTION_LIST = 1

        init {
            URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH)
            URI_MATCHER.addURI("*", Transaction.TABLE_NAME, TRANSACTION_LIST)
        }

        private fun getTableFromMatch(match: Int): String {
            return when (match) {
                TRANSACTION_LIST -> Transaction.TABLE_NAME
                else -> throw IllegalArgumentException("Unknown match " + match)
            }
        }
    }
}