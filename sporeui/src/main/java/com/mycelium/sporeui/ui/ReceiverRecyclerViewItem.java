package com.mycelium.sporeui.ui;

import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.mycelium.sporeui.R;

import java.util.List;
import java.util.Objects;

import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;
import eu.davidea.viewholders.FlexibleViewHolder;

/**
 * Created by Nelson on 19/12/2016.
 */
public class ReceiverRecyclerViewItem
    extends AbstractFlexibleItem<ReceiverRecyclerViewItem.ViewHolder> {
  private String category;
  private String item;
  private String value;
  private int type;

  public ReceiverRecyclerViewItem(String category, String item, String value, int type) {
    this.category = category;
    this.item = item;
    this.value = value;
    this.type = type;
  }

  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ReceiverRecyclerViewItem that = (ReceiverRecyclerViewItem) o;
    return Objects.equals(category, that.category) &&
        Objects.equals(item, that.item) &&
        Objects.equals(value, that.value);
  }

  public int hashCode() {
    return Objects.hash(category.hashCode(), category, item, value);
  }

  /**
   * For the item type we need an int value: the layoutResID is sufficient.
   */
  @Override
  public int getLayoutRes() {
    if(type == ReceiverRecyclerViewAdapter.VIEW_TYPE_ITEM) {
      return R.layout.receiver_recyclerview_item;
    } else {
      return R.layout.list_item_padding_receiver;
    }
  }


  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getItem() {
    return item;
  }

  public void setItem(String item) {
    this.item = item;
  }

  public String getValue() {
    return value;
  }

  public void setValue(String value) {
    this.value = value;
  }

  public int getType() {
    return type;
  }

  /**
   * The Adapter is provided to be forwarded to the MyViewHolder.
   * The unique instance of the LayoutInflater is also provided to simplify the
   * creation of the VH.
   */
  @Override
  public ViewHolder createViewHolder(FlexibleAdapter adapter, LayoutInflater inflater,
                                       ViewGroup parent) {
    return new ViewHolder(inflater.inflate(getLayoutRes(), parent, false), adapter);
  }

  /**
   * The Adapter and the Payload are provided to get more specific information from it.
   */
  @Override
  public void bindViewHolder(FlexibleAdapter adapter, ViewHolder holder, int position,
                             List payloads) {
    holder.categoryTextView.setText(category);
    holder.itemTextView.setText(item);
    holder.valueTextView.setText(value);

    //Text appears disabled if item is disabled
    holder.categoryTextView.setEnabled(isEnabled());
    holder.itemTextView.setEnabled(isEnabled());
    holder.valueTextView.setEnabled(isEnabled());
  }

  /**
   * The ViewHolder used by this item.
   * Extending from FlexibleViewHolder is recommended especially when you will use
   * more advanced features.
   */
  public class ViewHolder extends FlexibleViewHolder {

    // each data item is just a string in this case
    public TextView categoryTextView;
    public TextView itemTextView;
    public TextView valueTextView;
    public View rectangle;

    public ViewHolder(View view, FlexibleAdapter adapter) {
      super(view, adapter);
      categoryTextView = (TextView) view.findViewById(R.id.categorytextView);
      itemTextView = (TextView) view.findViewById(R.id.itemTextView);
      valueTextView = (TextView) view.findViewById(R.id.valueTextView);
      rectangle = view.findViewById(R.id.rectangle);
    }
  }
}
