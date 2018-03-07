package com.mycelium.wallet.activity.util;

import android.content.Context;
import android.util.AttributeSet;

import com.mycelium.net.ServerEndpointType;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.Utils;
import com.mycelium.wapi.model.TransactionDetails;

public class TransactionDetailsLabel extends GenericBlockExplorerLabel {
    private TransactionDetails transaction;

    public TransactionDetailsLabel(Context context) {
        super(context);
    }

    public TransactionDetailsLabel(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public TransactionDetailsLabel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected String getLinkText() {
        return transaction.hash.toString();
    }

    @Override
    protected String getFormattedLinkText() {
        return Utils.stringChopper(transaction.hash.toString(), 4, " ");
    }

    public void setTransaction(final TransactionDetails tx) {
        this.transaction = tx;
        update_ui();
        setHandler();
    }

    public TransactionDetails getAddress() {
        return transaction;
    }

}
