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

import android.content.Intent
import android.os.Bundle
import android.preference.PreferenceActivity
import android.view.MenuItem

import java.util.HashSet

class PreferenceActivity : PreferenceActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        //startService(Intent(this, SpvService::class.java))
    }

    override fun onBuildHeaders(target: List<PreferenceActivity.Header>) {
        loadHeadersFromResource(R.xml.preference_headers, target)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
        }

        return super.onOptionsItemSelected(item)
    }

    override fun isValidFragment(fragmentName: String): Boolean {
        return VALID_FRAGMENT_NAMES.contains(fragmentName)
    }

    companion object {
        private val VALID_FRAGMENT_NAMES = HashSet<String>()

        init {
            VALID_FRAGMENT_NAMES.addAll(arrayOf(
                    SettingsFragment::class,
                    AboutFragment::class,
                    AddressListFragment::class,
                    TransactionListFragment::class).map {it.java.name})
        }
    }
}
