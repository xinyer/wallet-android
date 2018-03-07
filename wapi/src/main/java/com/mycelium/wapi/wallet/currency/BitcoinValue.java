package com.mycelium.wapi.wallet.currency;

import com.megiontechnologies.Bitcoins;

import java.math.BigDecimal;

public interface BitcoinValue {

    Bitcoins getAsBitcoin();

    long getLongValue();

    BigDecimal getValue();

    String getCurrency();

}
