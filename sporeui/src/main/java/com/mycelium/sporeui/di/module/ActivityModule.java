package com.mycelium.sporeui.di.module;

import com.mycelium.sporeui.MainActivity;
import com.mycelium.sporeui.di.component.ActivityComponent;
import com.mycelium.sporeui.di.scope.DaggerScope;
import com.mycelium.sporeui.domain.executor.PostExecutionThread;
import com.mycelium.sporeui.domain.interactor.GetNewCurrencyValue;
import com.mycelium.sporeui.domain.repository.CurrencyValueRepository;
import com.mycelium.sporeui.presenter.ActionBarOwner;
import com.mycelium.sporeui.presenter.ReceiveScreenPresenter;
import com.mycelium.sporeui.presenter.SendScreenPresenter;
import com.mycelium.sporeui.presenter.ToolbarPresenter;

import java.util.concurrent.Executor;

import dagger.Module;
import dagger.Provides;

/**
 * Created by Nelson on 14/11/2016.
 */

@Module
public class ActivityModule {

  @Provides
  @DaggerScope(ActivityComponent.class)
  ActionBarOwner providesActionBarOwner() {
    return new ActionBarOwner();
  }

  @Provides
  @DaggerScope(ActivityComponent.class)
  public GetNewCurrencyValue providesGetDetailedContact(CurrencyValueRepository currencyValueRepository,
                                                        Executor threadExecutor,
                                                        PostExecutionThread postExecutionThread) {
    return new GetNewCurrencyValue(currencyValueRepository, threadExecutor, postExecutionThread);
  }

  @Provides
  @DaggerScope(ActivityComponent.class)
  public SendScreenPresenter providesSendScreenPresenter(GetNewCurrencyValue getNewCurrencyValue,
                                                         ActionBarOwner actionBarOwner) {
    return new SendScreenPresenter(getNewCurrencyValue, actionBarOwner);
  }

  @Provides
  @DaggerScope(ActivityComponent.class)
  public ReceiveScreenPresenter providesReceiveScreenPresenter(GetNewCurrencyValue getNewCurrencyValue,
                                                            ActionBarOwner actionBarOwner) {
    return new ReceiveScreenPresenter(getNewCurrencyValue, actionBarOwner);
  }


  @Provides
  @DaggerScope(ActivityComponent.class)
  ToolbarPresenter providesToolbarPresenter() {
    return new ToolbarPresenter();
  }
}
