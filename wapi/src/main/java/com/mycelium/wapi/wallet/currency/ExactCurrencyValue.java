package com.mycelium.wapi.wallet.currency;

import java.math.BigDecimal;

public abstract class ExactCurrencyValue extends CurrencyValue {

    public static ExactCurrencyValue from(BigDecimal value, String currency) {
        if (currency.equals(CurrencyValue.BTC)) {
            return new ExactBitcoinValue(value);
        } else {
            throw new IllegalStateException("currency only support BTC.");
        }
    }

    @Override
    public ExactCurrencyValue getExactValue() {
        return this;
    }

    @Override
    boolean hasExactValue() {
        return true;
    }
}
