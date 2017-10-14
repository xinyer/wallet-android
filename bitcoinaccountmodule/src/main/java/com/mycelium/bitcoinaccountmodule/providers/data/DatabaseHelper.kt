package com.mycelium.bitcoinaccountmodule.providers.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.util.Log

import com.mycelium.bitcoinaccountmodule.providers.data.DatabaseContract.Address
import com.mycelium.bitcoinaccountmodule.providers.data.DatabaseContract.Transaction

class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DatabaseHelper.DATABASE_NAME, null, DatabaseHelper.DATABASE_VERSION) {
    override fun onCreate(db: SQLiteDatabase) {
        arrayOf("DROP TABLE IF EXISTS ${Address.TABLE_NAME};",
                "CREATE TABLE ${Address.TABLE_NAME} (${Address.ADDRESS_ID} TEXT PRIMARY KEY, ${Address.LABEL} TEXT);",
                "DROP TABLE IF EXISTS ${Transaction.TABLE_NAME};",
                "CREATE TABLE ${Transaction.TABLE_NAME} (${Transaction.TRANSACTION_ID} TEXT PRIMARY KEY, ${Transaction.LABEL} TEXT);")
                .forEach {
                    Log.d(TAG, it)
                    db.execSQL(it)
                }
        onUpgrade(db, 0, DATABASE_VERSION)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        Log.w(TAG, "Upgrading database from version $oldVersion to $newVersion")
        if (oldVersion < 5) {
            Log.d(TAG, "inserting some dummy data")
            arrayOf("mwme6EEHxZvUafsadc4H1oDBhkpaF8ocTd", "mudqLdo16aCoKYdddNZyzDYzUJ7gkCJbB9", "mmdVEwUDGQtoutXKYhug3vMd7xqBDFp5BK", "n4eD671ibA7qyG2RVjiPL19SxWgCs7vPRf", "mkQ12ENFJXWx3tKwaMwspSzagWMfJsXYgv", "no Real Address")
                    .forEach {
                        val contentValues = ContentValues()
                        contentValues.put(Address.ADDRESS_ID, it)
                        contentValues.put(Address.LABEL, "The label of $it")
                        db.insert(Address.TABLE_NAME, null, contentValues)
                    }
        }
    }

    companion object {
        private val DATABASE_NAME = "bitcoinAccountModule.db"
        private val DATABASE_VERSION = 5
    }
}