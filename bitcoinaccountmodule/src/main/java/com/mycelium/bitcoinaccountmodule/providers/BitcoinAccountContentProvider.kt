package com.mycelium.bitcoinaccountmodule.providers

import android.content.ContentProvider
import android.content.ContentUris
import android.content.ContentValues
import android.content.UriMatcher
import android.database.Cursor
import android.database.SQLException
import android.database.sqlite.SQLiteQueryBuilder
import android.net.Uri

import com.mycelium.bitcoinaccountmodule.providers.data.DatabaseHelper
import com.mycelium.bitcoinaccountmodule.providers.BitcoinAccountContract.Address
import com.mycelium.bitcoinaccountmodule.providers.BitcoinAccountContract.Transaction

import com.mycelium.bitcoinaccountmodule.providers.BitcoinAccountContract.Companion.AUTHORITY
import com.mycelium.modularizationtools.CommunicationManager


class BitcoinAccountContentProvider : ContentProvider() {
    private var dbHelper: DatabaseHelper? = null
    private var signatureChecker: CommunicationManager? = null

    override fun onCreate(): Boolean {
        signatureChecker = CommunicationManager.Companion.getInstance(context)
        dbHelper = DatabaseHelper(context!!)
        return true
    }

    override fun query(uri: Uri, projection: Array<String>?, selection: String?, selectionArgs: Array<String>?, sortOrder: String?): Cursor? {
        var sel = selection
        checkSignature()
        val qb = SQLiteQueryBuilder()
        val match = URI_MATCHER.match(uri)
        when (match) {
            ADDRESS_LIST, TRANSACTION_LIST -> {
            }
            ADDRESS_ID, TRANSACTION_ID -> sel = sel + " _id=" + uri.lastPathSegment
            else -> throw IllegalArgumentException("Unknown URI " + uri)
        }
        qb.tables = getTableFromMatch(match)
        val db = dbHelper!!.readableDatabase

        val cursor = qb.query(db, projection, sel, selectionArgs, null, null, sortOrder)
        //noinspection ConstantConditions
        cursor.setNotificationUri(context!!.contentResolver, uri)
        return cursor
    }

    override fun getType(uri: Uri): String? {
        checkSignature()
        when (URI_MATCHER.match(uri)) {
            ADDRESS_LIST -> return Address.CONTENT_TYPE
            ADDRESS_ID -> return Address.CONTENT_ITEM_TYPE
            TRANSACTION_LIST -> return Transaction.CONTENT_TYPE
            TRANSACTION_ID -> return Transaction.CONTENT_ITEM_TYPE
            else -> throw IllegalArgumentException("Unknown URI " + uri)
        }
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        checkSignature()
        // TODO: 9/14/16 validate data here
        if (values == null || values.size() < 1) {
            throw IllegalArgumentException("Can't insert empty row!")
        }
        val contentUri: Uri
        val match = URI_MATCHER.match(uri)
        when (match) {
            ADDRESS_LIST -> contentUri = Address.CONTENT_URI
            TRANSACTION_LIST -> contentUri = Transaction.CONTENT_URI
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

    override fun delete(uri: Uri, selection: String?, whereArgs: Array<String>?): Int {
        var sel = selection
        checkSignature()
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
        checkSignature()
        val db = dbHelper!!.writableDatabase
        val match = URI_MATCHER.match(uri)
        val count = db.update(getTableFromMatch(match), values, where, whereArgs)
        if (count > 0) {
            notifyChange(uri)
        }
        return count
    }

    private fun checkSignature() {
        signatureChecker!!.checkSignature(callingPackage)
    }

    private fun notifyChange(uri: Uri) {
        //crashing if context or contentResolver are null is appropriate here
        //noinspection ConstantConditions
        context!!.contentResolver.notifyChange(uri, null)
    }

    companion object {
        // private val log = LoggerFactory.getLogger(BitcoinAccountContentProvider::class.java)
        private val URI_MATCHER: UriMatcher

        private val ADDRESS_LIST = 1
        private val ADDRESS_ID = 2
        private val TRANSACTION_LIST = 3
        private val TRANSACTION_ID = 4

        init {
            URI_MATCHER = UriMatcher(UriMatcher.NO_MATCH)
            URI_MATCHER.addURI(AUTHORITY, Address.TABLE_NAME, ADDRESS_LIST)
            URI_MATCHER.addURI(AUTHORITY, Address.TABLE_NAME + "/#", ADDRESS_ID)
            URI_MATCHER.addURI(AUTHORITY, Transaction.TABLE_NAME, TRANSACTION_LIST)
            URI_MATCHER.addURI(AUTHORITY, Transaction.TABLE_NAME + "/#", TRANSACTION_ID)
        }

        private fun getTableFromMatch(match: Int): String {
            when (match) {
                ADDRESS_LIST, ADDRESS_ID -> return Address.TABLE_NAME
                TRANSACTION_LIST, TRANSACTION_ID -> return Transaction.TABLE_NAME
                else -> throw IllegalArgumentException("Unknown match " + match)
            }
        }
    }
}
