package com.mycelium.wallet.activity.modern.adapter;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;
import com.mycelium.wallet.activity.modern.RecordRowBuilder;
import com.mycelium.wallet.activity.modern.adapter.holder.AccountViewHolder;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.WalletManager;

import java.util.ArrayList;
import java.util.List;

public class AccountListAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private List<WalletAccount> itemList = new ArrayList<>();
    private WalletAccount focusedAccount;

    private ItemClickListener itemClickListener;
    private ItemSelectListener itemSelectListener;

    private MbwManager mbwManager;
    private RecordRowBuilder builder;
    private LayoutInflater layoutInflater;

    public AccountListAdapter(Context context, MbwManager mbwManager) {
        this.mbwManager = mbwManager;
        layoutInflater = LayoutInflater.from(context);
        builder = new RecordRowBuilder(mbwManager, context.getResources(), layoutInflater);
    }

    public void setItemClickListener(ItemClickListener itemClickListener) {
        this.itemClickListener = itemClickListener;
    }

    public void setItemSelectListener(ItemSelectListener itemSelectListener) {
        this.itemSelectListener = itemSelectListener;
    }

    public WalletAccount getFocusedAccount() {
        return focusedAccount;
    }

    public void setFocusedAccount(WalletAccount focusedAccount) {
        notifyItemChanged(findPosition(this.focusedAccount));
        this.focusedAccount = focusedAccount;
        notifyItemChanged(findPosition(this.focusedAccount));
    }

    private int findPosition(WalletAccount account) {
        int position = -1;
        for (int i = 0; i < itemList.size(); i++) {
            WalletAccount item = itemList.get(i);
            if (item == account) {
                position = i;
                break;
            }
        }
        return position;
    }

    public void updateData() {
        itemList.clear();
        WalletManager walletManager = mbwManager.getWalletManager();
        List<WalletAccount> accounts = walletManager.getActiveOtherAccounts();
        itemList.addAll(accounts);
        notifyDataSetChanged();
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        RecyclerView.ViewHolder result = null;
        View view = layoutInflater.inflate(R.layout.record_row, parent, false);
        result = new AccountViewHolder(view);
        return result;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        final WalletAccount account = itemList.get(position);
        AccountViewHolder accountHolder = (AccountViewHolder) holder;
        builder.buildRecordView(null, account, mbwManager.getSelectedAccount() == account, focusedAccount == account, holder.itemView);
        accountHolder.itemView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setFocusedAccount(account);
                if (itemSelectListener != null) {
                    itemSelectListener.onClick(account);
                }

            }
        });
        accountHolder.llAddress.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                setFocusedAccount(account);
                if (itemClickListener != null) {
                    itemClickListener.onItemClick(account);
                }
            }
        });
    }

    @Override
    public int getItemCount() {
        return itemList.size();
    }

    @Override
    public int getItemViewType(int position) {
        return 1;
    }

    public interface ItemClickListener {
        void onItemClick(WalletAccount account);
    }

    public interface ItemSelectListener {
        void onClick(WalletAccount account);
    }
}
