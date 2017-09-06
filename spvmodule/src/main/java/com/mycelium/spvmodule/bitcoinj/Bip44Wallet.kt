package com.mycelium.spvmodule.bitcoinj

import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.wallet.Wallet
import org.bitcoinj.wallet.KeyChainGroup
import org.bitcoinj.wallet.DeterministicSeed



/**
 * Created by Nelson on 06/09/2017.
 */
class Bip44Wallet {

    companion object Factory {
        fun fromSeed(params: NetworkParameters, seed: DeterministicSeed): Wallet {
            return Wallet(params, Bip44KeyChainGroup(params, seed))
        }
    }


}