package com.mycelium.sporeui.di.module;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;

import com.mycelium.sporeui.BitcoinService;
import com.mycelium.sporeui.MainApplication;
import com.mycelium.sporeui.MockBitcoinService;
import com.mycelium.sporeui.UIThread;
import com.mycelium.sporeui.data.executor.JobExecutor;
import com.mycelium.sporeui.data.repository.CurrencyValueRepositoryMockImpl;
import com.mycelium.sporeui.di.scope.ApplicationScope;
import com.mycelium.sporeui.domain.executor.PostExecutionThread;
import com.mycelium.sporeui.domain.repository.CurrencyValueRepository;

import java.util.concurrent.Executor;

import javax.inject.Singleton;

import dagger.Module;
import dagger.Provides;


@Module
public class ApplicationModule {
  
  private MainApplication mainApplication;
  private String SHARED_NAME = "global_config";

  public ApplicationModule(MainApplication mainApplication) {
    this.mainApplication = mainApplication;
  }


  @Provides
  @Singleton
  Context provideContext() {
    return mainApplication;
  }

  @Provides
  @Singleton
  BitcoinService provideBitcoinService() {
    return new MockBitcoinService();
  }

  @Provides
  @ApplicationScope
  MainApplication provideMainApplication() {
    return mainApplication;
  }



  @Provides
  @ApplicationScope
  public UIThread provideUIThread() {
    return new UIThread();
  }

  @Provides
  @ApplicationScope
  public Executor provideThreadExecutor(JobExecutor jobExecutor) {
    return jobExecutor;
  }


  @Provides
  @ApplicationScope
  public PostExecutionThread providePostExecutionThread(UIThread uiThread) {
    return uiThread;
  }

  @Provides
  @ApplicationScope
  public ContentResolver provideContentResolver() {
    return this.mainApplication.getContentResolver();
  }

  @Provides
  @ApplicationScope
  public SharedPreferences provideSharedPreferences() {
    return this.mainApplication.getSharedPreferences(SHARED_NAME, Context.MODE_PRIVATE);
  }

  @Provides
  @ApplicationScope
  public CurrencyValueRepository providesContactsRepository(ContentResolver contentResolver) {
    return new CurrencyValueRepositoryMockImpl(contentResolver);
  }



}
