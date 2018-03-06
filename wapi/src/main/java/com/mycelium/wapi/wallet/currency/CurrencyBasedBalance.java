package com.mycelium.wapi.wallet.currency;


public class CurrencyBasedBalance {

    public final static CurrencyBasedBalance ZERO_BITCOIN_BALANCE = new CurrencyBasedBalance(
            ExactBitcoinValue.ZERO, ExactBitcoinValue.ZERO, ExactBitcoinValue.ZERO);

    public final ExactCurrencyValue confirmed;
    public final ExactCurrencyValue sending;
    public final ExactCurrencyValue receiving;
    public final boolean isSynchronizing;

    public CurrencyBasedBalance(ExactCurrencyValue confirmed, ExactCurrencyValue sending, ExactCurrencyValue receiving) {
        this(confirmed, sending, receiving, false);
    }

    public CurrencyBasedBalance(ExactCurrencyValue confirmed, ExactCurrencyValue sending, ExactCurrencyValue receiving, boolean isSynchronizing) {
        this.confirmed = confirmed;
        this.sending = sending;
        this.receiving = receiving;
        this.isSynchronizing = isSynchronizing;
    }

    @Override
    public String toString() {
        return "CurrencyBasedBalance{" +
                "confirmed=" + confirmed +
                ", sending=" + sending +
                ", receiving=" + receiving +
                (isSynchronizing ? " [syncing]" : "") +
                '}';
    }
}
