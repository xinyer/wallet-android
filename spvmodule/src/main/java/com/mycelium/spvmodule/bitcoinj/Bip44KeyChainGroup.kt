package com.mycelium.spvmodule.bitcoinj

import com.google.common.collect.ImmutableList
import org.bitcoinj.core.NetworkParameters
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed
import org.bitcoinj.wallet.KeyChainGroup

/**
 * Created by Nelson on 06/09/2017.
 */
class Bip44KeyChainGroup : KeyChainGroup {
    constructor(params: NetworkParameters?, seed: DeterministicSeed?) :
            super(params) {
        addAndActivateHDChain(Bip44DeterministicKeyChain(seed))
    }
}