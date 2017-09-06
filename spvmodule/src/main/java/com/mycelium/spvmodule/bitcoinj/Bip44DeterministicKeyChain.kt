package com.mycelium.spvmodule.bitcoinj

import com.google.common.collect.ImmutableList
import org.bitcoinj.crypto.ChildNumber
import org.bitcoinj.wallet.DeterministicKeyChain
import org.bitcoinj.wallet.DeterministicSeed

/**
 * Created by Nelson on 06/09/2017.
 */
class Bip44DeterministicKeyChain(seed: DeterministicSeed?) : DeterministicKeyChain(seed) {

    /** Override in subclasses to use a different account derivation path  */
    override fun getAccountPath(): ImmutableList<ChildNumber> {
        return ImmutableList.of(ChildNumber(44, true),
                ChildNumber(1, true), ChildNumber(0, true));
    }
}