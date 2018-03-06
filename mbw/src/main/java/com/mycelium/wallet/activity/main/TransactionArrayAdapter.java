package com.mycelium.wallet.activity.main;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

import com.google.common.base.Preconditions;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.activity.util.AdaptiveDateFormat;
import com.mycelium.wallet.activity.util.TransactionConfirmationsDisplay;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wapi.model.TransactionSummary;

import java.text.DateFormat;
import java.util.Date;
import java.util.List;


public class TransactionArrayAdapter extends ArrayAdapter<TransactionSummary> {

    protected Context context;
    private MbwManager mbwManager;
    private final MetadataStorage metadataStorage;

    private DateFormat dateFormat;
    private Fragment containerFragment;

    public TransactionArrayAdapter(Context context, List<TransactionSummary> transactions) {
        this(context, transactions, null);
    }

    public TransactionArrayAdapter(Context context,
                                   List<TransactionSummary> transactions,
                                   Fragment containerFragment) {
        super(context, R.layout.transaction_row, transactions);
        this.context = context;
        this.dateFormat = new AdaptiveDateFormat(context);
        this.mbwManager = MbwManager.getInstance(context);
        this.containerFragment = containerFragment;
        this.metadataStorage = mbwManager.getMetadataStorage();
    }

    @NonNull
    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        // Only inflate a new view if we are not reusing an old one
        View rowView = convertView;
        if (rowView == null) {
            LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            rowView = Preconditions.checkNotNull(inflater.inflate(R.layout.transaction_row, parent, false));
        }

        // Make sure we are still added
        if (containerFragment != null && !containerFragment.isAdded()) {
            // We have observed that the fragment can be disconnected at this
            // point
            return rowView;
        }

        final TransactionSummary record = getItem(position);

        // Determine Color
        int color;
        if (record.isIncoming) {
            color = context.getResources().getColor(R.color.green);
        } else {
            color = context.getResources().getColor(R.color.red);
        }

        // Set Date
        Date date = new Date(record.time * 1000L);
        TextView tvDate = rowView.findViewById(R.id.tvDate);
        tvDate.setText(dateFormat.format(date));

        // Set value
        TextView tvAmount = rowView.findViewById(R.id.tvAmount);
        tvAmount.setText(Utils.getFormattedValueWithUnit(record.value, mbwManager.getBitcoinDenomination()));
        tvAmount.setTextColor(color);

        // Show confirmations indicator
        int confirmations = record.confirmations;
        TransactionConfirmationsDisplay tcdConfirmations = rowView.findViewById(R.id.tcdConfirmations);
        if (record.isQueuedOutgoing) {
            tcdConfirmations.setNeedsBroadcast();
        } else {
            tcdConfirmations.setConfirmations(confirmations);
        }

        // Show label or confirmations
        TextView tvLabel = rowView.findViewById(R.id.tvTransactionLabel);
        String label = metadataStorage.getLabelByTransaction(record.txid);
        if (label.length() == 0) {
            String confirmationsText;
            if (record.isQueuedOutgoing) {
                confirmationsText = context.getResources().getString(R.string.transaction_not_broadcasted_info);
            } else {
                if (confirmations > 6) {
                    confirmationsText = context.getResources().getString(R.string.confirmed);
                } else {
                    confirmationsText = context.getResources().getString(R.string.confirmations, confirmations);
                }
            }
            tvLabel.setText(confirmationsText);
        } else {
            tvLabel.setText(label);
        }
        rowView.setTag(record);
        return rowView;
    }
}
