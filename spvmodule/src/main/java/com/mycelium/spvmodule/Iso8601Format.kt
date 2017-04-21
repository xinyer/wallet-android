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

import android.annotation.SuppressLint

import java.text.DateFormat
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.TimeZone

@SuppressLint("SimpleDateFormat")
class Iso8601Format private constructor(formatString: String) : SimpleDateFormat(formatString) {
    init {
        timeZone = UTC
    }

    companion object {
        private val UTC = TimeZone.getTimeZone("UTC")

        fun newTimeFormat(): DateFormat {
            return Iso8601Format("HH:mm:ss")
        }

        fun newDateFormat(): DateFormat {
            return Iso8601Format("yyyy-MM-dd")
        }

        fun newDateTimeFormat(): DateFormat {
            return Iso8601Format("yyyy-MM-dd HH:mm:ss")
        }

        fun formatDateTime(date: Date): String {
            return newDateTimeFormat().format(date)
        }

        @Throws(ParseException::class)
        fun parseDateTime(source: String): Date {
            return newDateTimeFormat().parse(source)
        }

        fun newDateTimeFormatT(): DateFormat {
            return Iso8601Format("yyyy-MM-dd'T'HH:mm:ss'Z'")
        }

        fun formatDateTimeT(date: Date): String {
            return newDateTimeFormatT().format(date)
        }

        @Throws(ParseException::class)
        fun parseDateTimeT(source: String): Date {
            return newDateTimeFormatT().parse(source)
        }
    }
}
