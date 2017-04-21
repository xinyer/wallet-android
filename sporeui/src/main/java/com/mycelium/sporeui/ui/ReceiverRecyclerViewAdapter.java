package com.mycelium.sporeui.ui;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.mycelium.sporeui.R;

/**
 * Created by Nelson on 19/12/2016.
 */

public class ReceiverRecyclerViewAdapter
    extends RecyclerView.Adapter <ReceiverRecyclerViewAdapter.ViewHolder> {

  private ReceiverRecyclerViewItem[] mDataset;

  public static final int VIEW_TYPE_PADDING = 1;
  public static final int VIEW_TYPE_ITEM = 2;
  private int paddingWidth = 0;

  private int selectedItem = -1;

  private ViewHolder.ViewHolderClickListener viewHolderClickListener;


  // Provide a suitable constructor (depends on the kind of dataset)
  public ReceiverRecyclerViewAdapter(ReceiverRecyclerViewItem[] dataset, int paddingWidth,
                                     ViewHolder.ViewHolderClickListener viewHolderClickListener) {
    mDataset = dataset;
    this.paddingWidth = paddingWidth;
    this.viewHolderClickListener = viewHolderClickListener;
  }

  // Create new views (invoked by the layout manager)
  @Override
  public ViewHolder onCreateViewHolder(ViewGroup parent,
                                                 int viewType) {
    if (viewType == VIEW_TYPE_ITEM) {
      // create a new view
      View v = LayoutInflater.from(parent.getContext())
          .inflate(R.layout.receiver_recyclerview_item, parent, false);
      // set the view's size, margins, paddings and layout parameters
      //...
      return new ViewHolder(v, this, viewHolderClickListener);
    } else {
      View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item_padding_receiver,
          parent, false);

      RecyclerView.LayoutParams layoutParams = (RecyclerView.LayoutParams) view.getLayoutParams();
      layoutParams.width = paddingWidth;
      view.setLayoutParams(layoutParams);
      return new ViewHolder(view, this, viewHolderClickListener);
    }
  }

  // Replace the contents of a view (invoked by the layout manager)
  @Override
  public void onBindViewHolder(ViewHolder holder, int position) {
    if (getItemViewType(position) == VIEW_TYPE_ITEM) {
      // - get element from your dataset at this position
      // - replace the contents of the view with that element
      holder.categoryTextView.setText(mDataset[position].getCategory());
      holder.itemTextView.setText(mDataset[position].getItem());
      holder.valueTextView.setText(mDataset[position].getValue());
      if (position == selectedItem) {
        holder.itemView.setActivated(true);
      } else {
        holder.itemView.setActivated(false);
      }
      holder.itemView.setOnClickListener(holder);
    }
  }

  public void setSelecteditem(int selecteditem) {
    int oldSelectedItem = this.selectedItem;
    this.selectedItem = selecteditem;
    notifyItemChanged(oldSelectedItem);
    notifyItemChanged(selecteditem);
  }

  // Return the size of your dataset (invoked by the layout manager)
  @Override
  public int getItemCount() {
    return mDataset.length;
  }

  @Override
  public int getItemViewType(int position) {
    ReceiverRecyclerViewItem item = mDataset[position];
    return item.getType();
  }

  public int getSelectedPosition() {
    return selectedItem;
  }


  // Provide a reference to the views for each data item
  // Complex data items may need more than one view per item, and
  // you provide access to all the views for a data item in a view holder
  public static class ViewHolder extends RecyclerView.ViewHolder
  implements View.OnClickListener, View.OnLongClickListener {
    // each data item is just a string in this case
    public TextView categoryTextView;
    public TextView itemTextView;
    public TextView valueTextView;
    ViewHolderClickListener viewHolderClickListener;
    ReceiverRecyclerViewAdapter adapter;

    public ViewHolder(View v, ReceiverRecyclerViewAdapter adapter,
                      ViewHolderClickListener viewHolderClickListener) {
      super(v);
      categoryTextView = (TextView) v.findViewById(R.id.categorytextView);
      itemTextView = (TextView) v.findViewById(R.id.itemTextView);
      valueTextView = (TextView) v.findViewById(R.id.valueTextView);
      this.adapter = adapter;
      this.viewHolderClickListener = viewHolderClickListener;
    }

    /**
     * Called when a view has been clicked.
     *
     * @param v The view that was clicked.
     */
    @Override
    public void onClick(View v) {
      viewHolderClickListener.onClick(adapter, getAdapterPosition());
    }

    /**
     * Called when a view has been clicked and held.
     *
     * @param v The view that was clicked and held.
     * @return true if the callback consumed the long click, false otherwise.
     */
    @Override
    public boolean onLongClick(View v) {
      //Do nothing.
      return true;
    }

    public static interface ViewHolderClickListener {
      public void onClick(ReceiverRecyclerViewAdapter adapter, int position);
    }
  }
}
