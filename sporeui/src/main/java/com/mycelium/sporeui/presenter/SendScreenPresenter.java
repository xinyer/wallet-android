package com.mycelium.sporeui.presenter;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.mycelium.sporeui.data.CurrencyValue;
import com.mycelium.sporeui.di.DaggerService;
import com.mycelium.sporeui.di.component.ActivityComponent;
import com.mycelium.sporeui.di.component.DaggerActivityComponent;
import com.mycelium.sporeui.di.scope.DaggerScope;
import com.mycelium.sporeui.domain.interactor.DefaultSubscriber;
import com.mycelium.sporeui.domain.interactor.GetNewCurrencyValue;
import com.mycelium.sporeui.ui.ReceiverRecyclerViewAdapter;
import com.mycelium.sporeui.ui.ReceiverRecyclerViewItem;
import com.mycelium.sporeui.ui.SendRecyclerViewAdapter;
import com.mycelium.sporeui.ui.SenderRecyclerViewItem;
import com.mycelium.sporeui.view.SendView;

import org.jetbrains.annotations.NotNull;
import org.parceler.Parcels;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.inject.Inject;

import mortar.ViewPresenter;
import rx.functions.Action0;

/**
 * Created by Nelson on 05/03/2016.
 */
@DaggerScope(ActivityComponent.class)
public class SendScreenPresenter extends ViewPresenter<SendView> {

  private final GetNewCurrencyValue getNewCurrencyValue;
  private final ActionBarOwner actionBarOwner;


  @Inject
  public SendScreenPresenter(GetNewCurrencyValue getNewCurrencyValue, ActionBarOwner actionBarOwner) {
    this.getNewCurrencyValue = getNewCurrencyValue;
    this.actionBarOwner = actionBarOwner;

  }

  private void initializeInjection(Context context) {
    ((DaggerActivityComponent) DaggerService.getDaggerComponent(context)).inject(this);
  }

  @Override
  protected void onLoad(Bundle savedInstanceState) {
    super.onLoad(savedInstanceState);
    initializeInjection(getView().getContext());
    //this.contact = ((SendScreen) Flow.getKey(getView())).getContact();
    ActionBarOwner.MenuAction menuAction = new ActionBarOwner.MenuAction("Send", new Action0() {
      @Override
      public void call() {
        Toast.makeText(getView().getContext(), "Sending", Toast.LENGTH_LONG).show();
      }
    }, 0); // TODO replace this icon

    ActionBarOwner.Config config = new ActionBarOwner.Config(Arrays.asList(menuAction),
        "Send money", false, true, true);
    this.actionBarOwner.setConfig(config);

    //getView().getToolbarPresenter().setTitle("Send money");
    //getView().toolbarPresenter.setNavigationIcon(R.drawable.oval_40_dp_wallet);
    //getView().getToolbarPresenter().setLogo(R.drawable.ic_clear_black_24_px);
    /* getView().toolbarPresenter.setNavigationClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        //noinspection CheckResult
        Flow.get(getView()).goBack();
      }
    }); */
  }

  @Override
  protected void onExitScope() {
    ActionBarOwner.Config config = new ActionBarOwner.Config(null,
        "", false, false, false);
    this.actionBarOwner.setConfig(config);
    this.getNewCurrencyValue.unsubscribe();
    super.onExitScope();
  }



  public void changeCurrency(@NonNull CURRENCY currencyCode, long value) {
    Bundle bundle = new Bundle(1);
    bundle.putParcelable("currencyValue", Parcels.wrap(
        new CurrencyValue(currencyCode.name(), BigDecimal.valueOf(value))
    ));
    getNewCurrencyValue.execute(new SendSubscriber(), bundle);
  }

  @NotNull
  public List<SenderRecyclerViewItem> getSenderAccountsList() {
    List<SenderRecyclerViewItem> list = new ArrayList<>();
    list.add(new SenderRecyclerViewItem("", "", "", SendRecyclerViewAdapter.VIEW_TYPE_PADDING));
    list.add(new SenderRecyclerViewItem("Add BTC account", "MRD", "0.5556 BTC",
        SendRecyclerViewAdapter.VIEW_TYPE_ITEM));
    list.add(new SenderRecyclerViewItem("My Bitcoin Accounts", "MRD", "0.5556 BTC",
        SendRecyclerViewAdapter.VIEW_TYPE_ITEM));
    list.add(new SenderRecyclerViewItem("Add Debit Card","MRD", "0.5556 BTC",
        SendRecyclerViewAdapter.VIEW_TYPE_ITEM));
    list.add(new SenderRecyclerViewItem("My Debit Cards", "MRD", "0.5556 BTC",
        SendRecyclerViewAdapter.VIEW_TYPE_ITEM));
    //list.add(new SenderRecyclerViewItem("Bank Account", "MRD", "0.5556 BTC",
   //     SendRecyclerViewAdapter.VIEW_TYPE_ITEM));
    list.add(new SenderRecyclerViewItem("", "", "", SendRecyclerViewAdapter.VIEW_TYPE_PADDING));
    return list;
  }

  @NotNull
  public List<ReceiverRecyclerViewItem> getReceiverAccountsList() {
    List<ReceiverRecyclerViewItem> list = new ArrayList<>();
    list.add(new ReceiverRecyclerViewItem("", "", "", ReceiverRecyclerViewAdapter.VIEW_TYPE_PADDING));
    list.add(new ReceiverRecyclerViewItem("My Addresses", "MRD", "0.5556 BTC",
        ReceiverRecyclerViewAdapter.VIEW_TYPE_ITEM));
    list.add(new ReceiverRecyclerViewItem("My Contacts", "MRD", "0.5556 BTC",
        ReceiverRecyclerViewAdapter.VIEW_TYPE_ITEM));
    list.add(new ReceiverRecyclerViewItem("BTC address", "MRD", "0.5556 BTC",
        ReceiverRecyclerViewAdapter.VIEW_TYPE_ITEM));
    list.add(new ReceiverRecyclerViewItem("USD Wire transfer","MRD", "0.5556 BTC",
        ReceiverRecyclerViewAdapter.VIEW_TYPE_ITEM));
    list.add(new ReceiverRecyclerViewItem("", "", "", ReceiverRecyclerViewAdapter.VIEW_TYPE_PADDING));
    return list;
  }

  private class SendSubscriber extends DefaultSubscriber<CurrencyValue> {

    @Override
    public void onNext(CurrencyValue currencyValue) {
      if (getView() != null) {
        //getView().setTelephoneField(contact.getTelephone());
        getView().setNewCurrencyValue(currencyValue);
      }
    }

    @Override
    public void onError(Throwable e) {
      if (getView() != null) {
        Toast.makeText(getView().getContext(), e.getMessage(), Toast.LENGTH_SHORT).show();
      }
    }
  }

  public enum CURRENCY {
    EUR,
    USD,
    XBT,
    ETH
  }
}
