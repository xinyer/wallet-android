package com.mycelium.spvmodule.providers.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log
import com.mycelium.spvmodule.Constants.Companion.TAG

import com.mycelium.spvmodule.providers.data.DatabaseContract.Address
import com.mycelium.spvmodule.providers.data.DatabaseContract.Transaction
import com.mycelium.spvmodule.providers.data.DatabaseContract.TransactionOutput

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DatabaseHelper.DATABASE_NAME,
        null, DatabaseHelper.DATABASE_VERSION) {

    private val LOG_TAG: String? = this::class.java.canonicalName

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS ${Address.TABLE_NAME};")
        db.execSQL("""
                CREATE TABLE ${Address.TABLE_NAME} (
                ${Address.ADDRESS_ID} TEXT PRIMARY KEY,
                ${Address.CREATION_DATE} INTEGER,
                ${Address.SYNCED_TO_BLOCK} INTEGER);
                """.trimIndent())
        db.execSQL("DROP TABLE IF EXISTS ${Transaction.TABLE_NAME};")
        db.execSQL("""
                CREATE TABLE ${Transaction.TABLE_NAME} (
                ${Transaction.TRANSACTION_ID} TEXT PRIMARY KEY,
                ${Transaction.TRANSACTION} BLOB,
                ${Transaction.INCLUDED_IN_BLOCK} INTEGER);
                """.trimIndent())
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(LOG_TAG, "Upgrading database from version $oldVersion to $newVersion.")
        if (oldVersion < 4) {
            onCreate(db)
        }
        if (oldVersion < 6) {
            db.execSQL("DROP TABLE IF EXISTS ${TransactionOutput.TABLE_NAME};")
            db.execSQL("""
                CREATE TABLE ${TransactionOutput.TABLE_NAME} (
                ${TransactionOutput.TRANSACTION_OUTPUT_ID} TEXT PRIMARY KEY,
                ${TransactionOutput.TRANSACTION_OUTPUT} BLOB);
                """.trimIndent())
        }
    }

    companion object {
        private val DATABASE_NAME = "spvModule.db"
        private val DATABASE_VERSION = 6
    }
}