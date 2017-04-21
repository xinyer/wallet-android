package com.mycelium.sporeui.di.component;

/**
 * Created by Nelson on 14/11/2016.
 */

import com.mycelium.sporeui.MainActivity;
import com.mycelium.sporeui.di.module.ActivityModule;
import com.mycelium.sporeui.di.scope.DaggerScope;
import com.mycelium.sporeui.presenter.ActionBarOwner;
import com.mycelium.sporeui.presenter.ReceiveScreenPresenter;
import com.mycelium.sporeui.presenter.SendScreenPresenter;
import com.mycelium.sporeui.presenter.ToolbarPresenter;
import com.mycelium.sporeui.view.ReceiveView;
import com.mycelium.sporeui.view.SendView;

import dagger.Component;

@DaggerScope(ActivityComponent.class)
@Component(dependencies = ApplicationComponent.class, modules = ActivityModule.class)
public interface ActivityComponent extends ApplicationComponent {

  void inject(MainActivity mainActivity);
  ActionBarOwner actionBarOwner();
  ToolbarPresenter toolbarPresenter();
  void inject(SendView sendView);
  void inject(SendScreenPresenter sendScreenPresenter);
  void inject(ReceiveView receiveView);
  void inject(ReceiveScreenPresenter sendScreenPresenter);
}
