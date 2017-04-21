package com.mycelium.sporeui.listener

import flow.Direction

/**
 * Created by elvis on 26.01.17.
 */
interface KeyChangeListener {
    fun isFake():Boolean

    fun outgoing(direction: Direction)

    fun incoming(direction: Direction)
}