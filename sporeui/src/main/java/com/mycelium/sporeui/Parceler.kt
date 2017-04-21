package com.mycelium.sporeui

import android.os.Parcelable
import flow.KeyParceler
import org.parceler.Parcels

class KeyParceler() : KeyParceler {

    override fun toParcelable(key: Any): Parcelable = Parcels.wrap(key)

    override fun toKey(parcelable: Parcelable): Any = Parcels.unwrap(parcelable)
}