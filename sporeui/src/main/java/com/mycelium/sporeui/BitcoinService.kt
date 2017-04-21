package com.mycelium.sporeui

interface BitcoinService {
    fun getBitcoinAddress(): String
    fun getBitcoinBalance(): Long
}

class MockBitcoinService() : BitcoinService {
    override fun getBitcoinAddress(): String = "mz5ydTb59nk3AMPrpVtDQi2WGy7F26b8aY"
    override fun getBitcoinBalance(): Long = 123450000L
}
