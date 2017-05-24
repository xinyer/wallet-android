package com.mycelium.spvmodule.providers

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri

import com.mycelium.spvmodule.providers.data.DatabaseHelper
import com.mycelium.modularizationtools.CommunicationManager
import com.mycelium.spvmodule.providers.BlockchainContract.Address
import com.mycelium.spvmodule.providers.BlockchainContract.Transaction
import com.mycelium.spvmodule.providers.BlockchainContract.TransactionOutput

class BlockchainContentProvider : ContentProvider() {
    private var dbHelper: DatabaseHelper? = null
    var communicationManager: CommunicationManager? = null

    override fun onCreate(): Boolean {
        dbHelper = DatabaseHelper(context)
        communicationManager = CommunicationManager.getInstance(context)
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        var sel = selection
        checkSignature(callingPackage)
        val qb = SQLiteQueryBuilder()
        val match = URI_MATCHER.match(uri)
        when (match) {
            ADDRESS_LIST, TRANSACTION_LIST -> {
            }
            ADDRESS_ID, TRANSACTION_ID -> sel = sel + " _id=" + uri.lastPathSegment
            else -> throw IllegalArgumentException("Unknown URI " + uri) as Throwable
        }
        qb.tables = getTableFromMatch(match)
        val db = dbHelper!!.readableDatabase
        val cursor = qb.query(db, projection, sel, selectionArgs, null, null, sortOrder)
        //noinspection ConstantConditions
        cursor.setNotificationUri(context!!.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        checkSignature(callingPackage)
        return when (URI_MATCHER.match(uri)) {
            ADDRESS_LIST -> Address.CONTENT_TYPE
            ADDRESS_ID -> Address.CONTENT_ITEM_TYPE
            TRANSACTION_LIST -> Transaction.CONTENT_TYPE
            TRANSACTION_ID -> Transaction.CONTENT_ITEM_TYPE
            TRANSACTION_OUTPUT_LIST -> TransactionOutput.CONTENT_TYPE
            TRANSACTION_OUTPUT_ID -> TransactionOutput.CONTENT_ITEM_TYPE
            else -> throw IllegalArgumentException("Unknown URI " + uri)
        }
    }

    /**
     * Unconfirmed transactions can be inserted for broadcast using the TRANSACTION_LIST uri.
     *
     * Unmonitored addresses can be inserted for monitoring using the ADDRESS_LIST uri.
     */
    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        checkSignature(callingPackage)
        // TODO: 9/14/16 validate data here
        if (values == null || values.size() < 1) {
            throw IllegalArgumentException("Can't insert empty row!")
        }
        val match = URI_MATCHER.match(uri)
        val contentUri = when (match) {
            ADDRESS_LIST -> Address.CONTENT_URI(context.packageName)
            TRANSACTION_LIST -> Transaction.CONTENT_URI(context.packageName)
            TRANSACTION_OUTPUT_LIST -> TransactionOutput.CONTENT_URI(context.packageName)
            else -> throw IllegalArgumentException("Unknown URI " + uri)
        }
        val db = dbHelper!!.writableDatabase
        val rowId = db.insert(getTableFromMatch(match), null, values)
        if (rowId > 0) {
            val noteUri = ContentUris.withAppendedId(contentUri, rowId)
            notifyChange(noteUri)
            return noteUri
        }
        throw SQLException("Failed to insert row into " + uri)
    }

    override fun delete(uri: Uri, where: String?, whereArgs: Array<String>?): Int {
        var sel = where
        checkSignature(callingPackage)
        // TODO: 9/14/16 delete should not allow to delete transactions of watched addresses
        // TODO: 9/14/16 deleting addresses should delete their transactions
        val db = dbHelper!!.writableDatabase
        val match = URI_MATCHER.match(uri)
        when (match) {
            ADDRESS_LIST, TRANSACTION_LIST -> {
            }
            ADDRESS_ID, TRANSACTION_ID -> sel = sel + " _id=" + uri.lastPathSegment
            else -> throw IllegalArgumentException("Unknown URI " + uri)
        }
        val count = db.delete(getTableFromMatch(match), sel, whereArgs)
        if (count > 0) {
            notifyChange(uri)
        }
        return count
    }

    override fun update(uri: Uri, values: ContentValues?, where: String?, whereArgs: Array<String>?): Int {
        checkSignature(callingPackage)
        // TODO: 9/14/16 updates should be limited to very few things
        val db = dbHelper!!.writableDatabase
        val match = URI_MATCHER.match(uri)
        val count = db.update(getTableFromMatch(match), values, where, whereArgs)
        if (count > 0) {
            notifyChange(uri)
        }
        return count
    }

    private fun notifyChange(uri: Uri) {
        //crashing if context or contentResolver are null is appropriate here
        //noinspection ConstantConditions
        context!!.contentResolver.notifyChange(uri, null)
    }

    private fun  checkSignature(callingPackage: String) {
        communicationManager!!.checkSignature(callingPackage)
    }

    companion object {
        val URI_MATCHER: UriMatcher

        private val ADDRESS_LIST = 1
        private val ADDRESS_ID = 2
        private val TRANSACTION_LIST = 3
        private val TRANSACTION_ID = 4
        private val TRANSACTION_OUTPUT_LIST = 5
        private val TRANSACTION_OUTPUT_ID = 6

        init {
            URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH)
            URI_MATCHER.addURI("*", Address.TABLE_NAME, ADDRESS_LIST)
            URI_MATCHER.addURI("*", Address.TABLE_NAME + "/#", ADDRESS_ID)
            URI_MATCHER.addURI("*", Transaction.TABLE_NAME, TRANSACTION_LIST)
            URI_MATCHER.addURI("*", Transaction.TABLE_NAME + "/#", TRANSACTION_ID)
            URI_MATCHER.addURI("*", TransactionOutput.TABLE_NAME, TRANSACTION_OUTPUT_LIST)
            URI_MATCHER.addURI("*", TransactionOutput.TABLE_NAME + "/#", TRANSACTION_OUTPUT_ID)
        }

        private fun getTableFromMatch(match: Int): String {
            return when (match) {
                ADDRESS_LIST, ADDRESS_ID -> Address.TABLE_NAME
                TRANSACTION_LIST, TRANSACTION_ID -> Transaction.TABLE_NAME
                TRANSACTION_OUTPUT_LIST, TRANSACTION_OUTPUT_ID -> TransactionOutput.TABLE_NAME
                else -> throw IllegalArgumentException("Unknown match " + match)
            }
        }
    }
}
