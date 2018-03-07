package com.mycelium.wapi.wallet.currency;

import com.megiontechnologies.Bitcoins;

import java.math.BigDecimal;

public class ExactBitcoinValue extends ExactCurrencyValue implements BitcoinValue {
    private final Bitcoins value;
    public static final ExactCurrencyValue ZERO = from(0L);

    public static ExactBitcoinValue from(BigDecimal value) {
        return new ExactBitcoinValue(value);
    }

    public static ExactBitcoinValue from(Long value) {
        return new ExactBitcoinValue(value);
    }

    public static ExactBitcoinValue from(Bitcoins value) {
        return new ExactBitcoinValue(value);
    }

    protected ExactBitcoinValue(Long satoshis) {
        if (satoshis != null) {
            value = Bitcoins.valueOf(satoshis);
        } else {
            value = null;
        }
    }

    protected ExactBitcoinValue(Bitcoins bitcoins) {
        value = bitcoins;
    }


    protected ExactBitcoinValue(BigDecimal bitcoins) {
        if (bitcoins != null) {
            value = Bitcoins.nearestValue(bitcoins);
        } else {
            value = null;
        }
    }

    @Override
    public CurrencyValue add(CurrencyValue other) {
        if (other == null || other.getValue() == null) {
            return ExactBitcoinValue.from(null, this.getCurrency());
        }

        if (other instanceof ExactBitcoinValue) {
            return ExactBitcoinValue.from(this.getValue().add(other.getValue()));
        } else {
            return ExactBitcoinValue.from(null, this.getCurrency());
        }
    }

    @Override
    public long getLongValue() {
        return getAsBitcoin().getLongValue();
    }

    @Override
    public Bitcoins getAsBitcoin() {
        return value;
    }

    @Override
    public String getCurrency() {
        return CurrencyValue.BTC;
    }

    @Override
    public BigDecimal getValue() {
        if (value != null) {
            return value.toBigDecimal();
        } else {
            return null;
        }
    }
}
