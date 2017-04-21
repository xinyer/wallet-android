package com.mycelium.sporeui.screen;


import android.support.annotation.NonNull;

import com.mycelium.sporeui.Layout;
import com.mycelium.sporeui.R;
import com.mycelium.sporeui.data.CurrencyValue;
import com.mycelium.sporeui.di.component.ApplicationComponent;
import com.mycelium.sporeui.di.component.DaggerActivityComponent;
import com.mycelium.sporeui.di.module.ActivityModule;
import com.mycelium.sporeui.flow.keys.ReceiveKey;
import com.mycelium.sporeui.flow.servicefactory.InjectionComponent;

import org.parceler.Parcel;
import org.parceler.ParcelConstructor;

import flow.TreeKey;

@Parcel
@Layout(R.layout.screen_receive)
public final class ReceiveScreen extends ReceiveKey
    implements InjectionComponent<ApplicationComponent>, TreeKey {

    @ParcelConstructor
    public ReceiveScreen(CurrencyValue currencyValue) {
        super(currencyValue);
    }

    @Override
    public Object createComponent(ApplicationComponent parent) {
        return DaggerActivityComponent
                .builder()
                .applicationComponent(parent)
                .activityModule(new ActivityModule())
                .build();
    }

    @NonNull
    @Override
    public Object getParentKey() {
        //return new EditContactKey(contact);
        return null;
    }
}
