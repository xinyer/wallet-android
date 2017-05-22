package com.mycelium.spvmodule

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
import com.mycelium.spvmodule.Constants.Companion.TAG

import com.mycelium.spvmodule.providers.BlockchainContract

class AddressListFragment : ListFragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {
        val context = activity
        val cr = context.contentResolver
        val cursor = cr.query(BlockchainContract.Address.CONTENT_URI(activity.packageName), AddressesAdapter.fromColumns, null, null, null)
        val adapter = AddressesAdapter(context, cursor)

        /** Setting the list adapter for the ListFragment  */
        listAdapter = adapter

        return super.onCreateView(inflater, container, savedInstanceState)
    }

    private class AddressesAdapter(context: Context, cursor: Cursor) : SimpleCursorAdapter(context, R.layout.address_row, cursor, AddressListFragment.AddressesAdapter.fromColumns, AddressListFragment.AddressesAdapter.toViews, CursorAdapter.FLAG_REGISTER_CONTENT_OBSERVER) {
        private val layoutInflater: LayoutInflater = LayoutInflater.from(context)
        private val addressIndex: Int = cursor.getColumnIndexOrThrow(BlockchainContract.Address.ADDRESS_ID)
        private val creationDateIndex: Int = cursor.getColumnIndexOrThrow(BlockchainContract.Address.CREATION_DATE)

        private val LOG_TAG: String? = this.javaClass.canonicalName

        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View? {
            var cv = convertView
            Log.d(LOG_TAG,"getView at position $position.")
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
                viewHolder.creationDate.text = cursor.getString(creationDateIndex)
            }
            return cv
        }

        private inner class ViewHolder internal constructor(v: View) {
            internal var address: TextView = v.findViewById(R.id.tvAddress) as TextView
            internal var creationDate: TextView = v.findViewById(R.id.tvCreationDate) as TextView
        }

        companion object {
            var fromColumns = arrayOf(BlockchainContract.Address.ADDRESS_ID, BlockchainContract.Address.CREATION_DATE)
            private val toViews = intArrayOf(R.id.tvAddress, R.id.tvCreationDate)
        }
    }
}
