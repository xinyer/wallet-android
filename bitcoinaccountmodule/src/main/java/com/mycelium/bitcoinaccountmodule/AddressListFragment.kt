package com.mycelium.bitcoinaccountmodule

import android.app.ListFragment
import android.content.Context
import android.database.Cursor
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CursorAdapter
import android.widget.SimpleCursorAdapter
import android.widget.TextView
import com.mycelium.bitcoinaccountmodule.providers.BitcoinAccountContract.Address

class AddressListFragment : ListFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val context = activity
        val cr = context.contentResolver
        val cursor = cr.query(Address.CONTENT_URI, AddressesAdapter.fromColumns, null, null, null)
        val adapter = AddressesAdapter(context, cursor)

        /** Setting the list adapter for the ListFragment  */
        listAdapter = adapter

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    private class AddressesAdapter(context: Context, cursor: Cursor) : SimpleCursorAdapter(context, R.layout.address_row, cursor, AddressListFragment.AddressesAdapter.fromColumns, AddressListFragment.AddressesAdapter.toViews, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER) {
        private val layoutInflater: LayoutInflater
        private val addressIndex: Int
        private val labelIndex: Int

        init {
            this.layoutInflater = LayoutInflater.from(context)
            this.addressIndex = cursor.getColumnIndexOrThrow(Address.ADDRESS_ID)
            this.labelIndex = cursor.getColumnIndexOrThrow(Address.LABEL)
        }

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
            var cv = convertView
            Log.d(TAG, "getView at position ${position}.")
            if (cursor.moveToPosition(position)) {
                val viewHolder: ViewHolder

                if (cv == null) {
                    cv = layoutInflater.inflate(R.layout.address_row,
                            parent, false)
                    viewHolder = ViewHolder(cv)
                    cv!!.tag = viewHolder
                } else {
                    viewHolder = cv.tag as ViewHolder
                }

                viewHolder.address.text = cursor.getString(addressIndex)
                viewHolder.label.text = cursor.getString(labelIndex)
            }
            return cv
        }

        private inner class ViewHolder internal constructor(v: View) {
            internal var address: TextView
            internal var label: TextView

            init {
                address = v.findViewById(R.id.tvAddress) as TextView
                label = v.findViewById(R.id.tvLabel) as TextView
            }
        }

        companion object {
            var fromColumns = arrayOf(Address.ADDRESS_ID, Address.LABEL)
            private val toViews = intArrayOf(R.id.tvAddress, R.id.tvLabel)
        }
    }
}
