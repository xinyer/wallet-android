/*
 * Copyright 2011-2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mycelium.spvmodule

import android.database.Cursor
import android.net.Uri
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.text.TextUtils
import android.view.ViewGroup.LayoutParams
import android.widget.ScrollView
import com.mrd.bitlib.model.Address
import com.mrd.bitlib.util.Sha256Hash
import com.mycelium.spvmodule.providers.TransactionContract
import com.mycelium.wapi.model.TransactionDetails
import java.util.ArrayList

class PreferenceActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sw = ScrollView(this)
        sw.id = 171415
        setContentView(sw, LayoutParams(MATCH_PARENT, MATCH_PARENT))
        if( savedInstanceState == null) {
            val settingsFragment = SettingsFragment()
            val ft = supportFragmentManager.beginTransaction()
            ft.add(sw.id, settingsFragment)
            ft.commit()
        }
        getTransactionDetails()
    }

    private fun getTransactionDetails(): TransactionDetails? {
        var transactionDetails: TransactionDetails? = null
        val uri = Uri.withAppendedPath(TransactionContract.TransactionDetails.CONTENT_URI("com.mycelium.spvmodule.test"), "bca6c7903fb4bdd5d796501a7f54bed618550dd17e4eb3c9ef2b43214ecc6dc1")
        val selection = TransactionContract.TransactionDetails.SELECTION_ACCOUNT_INDEX
        val accountIndex = 0;
        val selectionArgs = arrayOf(Integer.toString(accountIndex))
        var cursor: Cursor? = null
        val contentResolver = contentResolver
        try {
            cursor = contentResolver.query(uri, null, selection, selectionArgs, null)
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    transactionDetails = from(cursor)
                }
            }
        } finally {
            if (cursor != null) {
                cursor.close()
            }
        }
        return transactionDetails
    }

    private fun from(cursor: Cursor): TransactionDetails {
        val rawTxId = cursor.getString(cursor.getColumnIndex(TransactionContract.TransactionDetails._ID))
        val hash = Sha256Hash.fromString(rawTxId)
        val height = cursor.getInt(cursor.getColumnIndex(TransactionContract.TransactionDetails.HEIGHT))
        val time = cursor.getInt(cursor.getColumnIndex(TransactionContract.TransactionDetails.TIME))
        val rawSize = cursor.getInt(cursor.getColumnIndex(TransactionContract.TransactionDetails.RAW_SIZE))

        val rawInputs = cursor.getString(cursor.getColumnIndex(TransactionContract.TransactionDetails.INPUTS))
        val rawOutputs = cursor.getString(cursor.getColumnIndex(TransactionContract.TransactionDetails.OUTPUTS))

        val inputs = extract(rawInputs)
        val outputs = extract(rawOutputs)
        return TransactionDetails(hash, height, time, inputs, outputs, rawSize)
    }

    private fun extract(data: String): Array<TransactionDetails.Item> {
        val result = ArrayList<TransactionDetails.Item>()
        if (!TextUtils.isEmpty(data)) {
            val dataParts = data.split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            for (input in dataParts) {
                val inputParts = input.split(" BTC".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                val value = java.lang.Long.valueOf(inputParts[0])!!
                val address = Address.fromString(inputParts[1])
                result.add(TransactionDetails.Item(address, value, false))
            }
        }
        return result.toTypedArray()
    }
}
