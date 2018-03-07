package com.mycelium.wallet.widget;

import android.content.Context;
import android.util.AttributeSet;

import com.mrd.bitlib.model.Address;
import com.mycelium.wallet.Utils;

public class AddressLabel extends GenericBlockExplorerLabel {
    private Address address;

    public AddressLabel(Context context) {
        super(context);
    }

    public AddressLabel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public AddressLabel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected String getLinkText() {
        return address.toString();
    }

    @Override
    protected String getFormattedLinkText() {
        return Utils.stringChopper(address.toString(), 12, "\n");
    }

    public void setAddress(final Address address) {
        this.address = address;
        update_ui();
        setHandler();
    }

    public Address getAddress() {
        return address;
    }

}

