package com.mycelium.sporeui.di.component;

/**
 * Created by Nelson on 14/11/2016.
 */

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;

import com.mycelium.sporeui.BitcoinService;
import com.mycelium.sporeui.MainApplication;
import com.mycelium.sporeui.MainView;
import com.mycelium.sporeui.UIThread;
import com.mycelium.sporeui.di.module.ApplicationModule;
import com.mycelium.sporeui.di.scope.ApplicationScope;
import com.mycelium.sporeui.domain.executor.PostExecutionThread;
import com.mycelium.sporeui.domain.repository.CurrencyValueRepository;
import com.mycelium.sporeui.view.ReceiveView;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

import dagger.Component;

@ApplicationScope
@Singleton
@Component(modules = ApplicationModule.class)
public interface ApplicationComponent {

   void inject(MainView mainView);

   void inject(MainApplication mainApplication);
   BitcoinService bitcoinService();
   Context context();
   MainApplication mainApplication();
   SharedPreferences sharedPreferences();
   ContentResolver contentResolver();

  PostExecutionThread postExecutionThread();
  UIThread uiThread();
  CurrencyValueRepository contactsRepository();
  Executor executor();
}
