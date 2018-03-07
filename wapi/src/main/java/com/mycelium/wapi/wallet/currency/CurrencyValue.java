/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wapi.wallet.currency;


import com.google.common.base.Optional;
import com.megiontechnologies.Bitcoins;

import java.io.Serializable;
import java.math.BigDecimal;

public abstract class CurrencyValue implements Serializable {
    public static final String BTC = "BTC";

    public abstract String getCurrency();

    public abstract BigDecimal getValue();

    public boolean isZero() {
        return BigDecimal.ZERO.compareTo(getValue()) == 0;
    }

    @Override
    public String toString() {
        return getValue() + " " + getCurrency();
    }

    public static CurrencyValue fromValue(CurrencyValue currencyValue) {
        return ExactBitcoinValue.from(currencyValue.getValue());
    }

    public static boolean isNullOrZero(CurrencyValue value) {
        return value == null || value.getValue() == null || value.isZero();
    }

    public boolean isBtc() {
        return getCurrency().equals(BTC);
    }

    public boolean isFiat() {
        return !isBtc();
    }

    public BitcoinValue getBitcoinValue() {
        return ((BitcoinValue) this);
    }

    // sum up this+other the other currency and use exchangeRateProvider if other is in
    // another currency as this -> always try to exchange to this
    public abstract CurrencyValue add(CurrencyValue other);

    public Bitcoins getAsBitcoin() {
        return getBitcoinValue().getAsBitcoin();
    }

    public abstract ExactCurrencyValue getExactValue();

    public CurrencyValue getExactValueIfPossible() {
        if (hasExactValue()) {
            return getExactValue();
        } else {
            return this;
        }
    }

    abstract boolean hasExactValue();

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof CurrencyValue)) {
            return false;
        }

        CurrencyValue that = (CurrencyValue) o;

        if (!getCurrency().equals(that.getCurrency())) {
            return false;
        }
        return getValue().compareTo(that.getValue()) == 0;
    }

    @Override
    public int hashCode() {
        int result = getCurrency().hashCode();
        result = 31 * result + getValue().hashCode();
        return result;
    }

    // only return the exact amount, if the input value is already in this currency
    public static Optional<ExactCurrencyValue> checkCurrencyAmount(CurrencyValue amount, String currency) {
        boolean isExact = (amount != null)
                && amount.getExactValueIfPossible() instanceof ExactCurrencyValue
                && amount.getExactValueIfPossible().getCurrency().equals(currency);
        if (isExact) {
            return Optional.of((ExactCurrencyValue) amount.getExactValueIfPossible());
        }
        boolean isSelfExact = (amount != null)
                && amount instanceof ExactCurrencyValue
                && amount.getCurrency().equals(currency);
        if (isSelfExact) {
            return Optional.of((ExactCurrencyValue) amount);
        } else {
            return Optional.absent();
        }
    }
}
