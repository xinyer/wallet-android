package com.mycelium.sporeui.flow.keys;

import com.mycelium.sporeui.data.CurrencyValue;
import com.mycelium.sporeui.model.Contact;

import flow.ClassKey;

/**
 * Created by Nelson on 08/03/2016.
 */
public abstract class SendKey extends ClassKey {
    protected final CurrencyValue currencyValue;

    protected SendKey(CurrencyValue currencyValue) {
        this.currencyValue = currencyValue;
    }

    @Override
    public boolean equals(Object o) {
        if(this == o) return true;
        if(o == null || getClass() != o.getClass()) return false;

        SendKey screen = (SendKey)o;
        return currencyValue.equals(screen.currencyValue);
    }

    @Override
    public int hashCode() {
        return currencyValue.hashCode();
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + currencyValue.toString();
    }

    public CurrencyValue getCurrencyValue() {
        return currencyValue;
    }
}
