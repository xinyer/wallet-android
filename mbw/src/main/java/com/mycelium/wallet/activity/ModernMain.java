package com.mycelium.wallet.activity;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.view.*;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.mycelium.wallet.*;
import com.mycelium.wallet.activity.accounts.AccountsFragment;
import com.mycelium.wallet.activity.balance.BalanceMasterFragment;
import com.mycelium.wallet.activity.transactions.TransactionHistoryFragment;
import com.mycelium.wallet.activity.send.InstantWalletActivity;
import com.mycelium.wallet.activity.settings.SettingsActivity;
import com.mycelium.wallet.event.*;
import com.mycelium.wallet.widget.Toaster;
import com.mycelium.wapi.wallet.*;
import com.squareup.otto.Subscribe;

import java.util.Date;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

public class ModernMain extends ActionBarActivity {
    private static final int TAB_ID_ACCOUNTS = 0;
    private static final int TAB_ID_BALANCE = 1;
    private static final int TAB_ID_HISTORY = 2;

    private static final int REQUEST_SETTING_CHANGED = 5;
    public static final int GENERIC_SCAN_REQUEST = 4;
    public static final int MIN_AUTOSYNC_INTERVAL = (int) Constants.MS_PR_MINUTE;
    public static final int MIN_FULLSYNC_INTERVAL = (int) (5 * Constants.MS_PR_HOUR);

    public static final String LAST_SYNC = "LAST_SYNC";
    private static final String APP_START = "APP_START";


    ViewPager mViewPager;
    TabsAdapter mTabsAdapter;
    ActionBar.Tab mBalanceTab;
    ActionBar.Tab mAccountsTab;
    private MenuItem refreshItem;
    private Toaster toaster;

    private MbwManager mbwManager;
    private volatile long lastSync = 0;
    private boolean isAppStart = true;

    private Timer balanceRefreshTimer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mbwManager = MbwManager.getInstance(this);
        mViewPager = new ViewPager(this);
        mViewPager.setId(R.id.pager);
        setContentView(mViewPager);
        ActionBar bar = getSupportActionBar();
        bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
        bar.setDisplayShowTitleEnabled(false);

        // Load the theme-background (usually happens in styles.xml) but use a lower
        // pixel format, this saves around 10MB of allocated memory
        // persist the loaded Bitmap in the context of mbw-manager and reuse it every time this activity gets created
        try {
            BitmapDrawable background = (BitmapDrawable) mbwManager.getBackgroundObjectsCache().get("mainBackground", new Callable<BitmapDrawable>() {
                @Override
                public BitmapDrawable call() throws Exception {
                    BitmapFactory.Options options = new BitmapFactory.Options();
                    options.inPreferredConfig = Bitmap.Config.RGB_565;
                    Bitmap background = BitmapFactory.decodeResource(getResources(), R.drawable.background_witherrors_dimmed, options);
                    BitmapDrawable drawable = new BitmapDrawable(getResources(), background);
                    drawable.setGravity(Gravity.CENTER);
                    return drawable;
                }
            });
            getWindow().setBackgroundDrawable(background);
        } catch (ExecutionException ignore) {
        }

        mTabsAdapter = new TabsAdapter(this, mViewPager);
        mAccountsTab = bar.newTab();
        mTabsAdapter.addTab(mAccountsTab.setText(getString(R.string.tab_accounts)), AccountsFragment.class, null);
        mBalanceTab = bar.newTab();
        mTabsAdapter.addTab(mBalanceTab.setText(getString(R.string.tab_balance)), BalanceMasterFragment.class, null);
        mTabsAdapter.addTab(bar.newTab().setText(getString(R.string.tab_transactions)), TransactionHistoryFragment.class, null);
        bar.selectTab(mBalanceTab);
        toaster = new Toaster(this);

        if (savedInstanceState != null) {
            lastSync = savedInstanceState.getLong(LAST_SYNC, 0);
            isAppStart = savedInstanceState.getBoolean(APP_START, true);
        }

        if (isAppStart) {
            isAppStart = false;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(LAST_SYNC, lastSync);
        outState.putBoolean(APP_START, isAppStart);
    }

    protected void stopBalanceRefreshTimer() {
        if (balanceRefreshTimer != null) {
            balanceRefreshTimer.cancel();
        }
    }

    @Override
    protected void onResume() {
        mbwManager.getEventBus().register(this);
        stopBalanceRefreshTimer();
        balanceRefreshTimer = new Timer();
        balanceRefreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                // if the last full sync is too old (or not known), start a full sync for _all_ accounts
                // otherwise just run a normal sync for the current account
                final Optional<Long> lastFullSync = mbwManager.getMetadataStorage().getLastFullSync();
                if (lastFullSync.isPresent()
                        && (new Date().getTime() - lastFullSync.get() < MIN_FULLSYNC_INTERVAL)) {
                    mbwManager.getWalletManager().startSynchronization();
                } else {
                    mbwManager.getWalletManager().startSynchronization(SyncMode.FULL_SYNC_ALL_ACCOUNTS);
                    mbwManager.getMetadataStorage().setLastFullSync(new Date().getTime());
                }

                lastSync = new Date().getTime();
            }
        }, 100, MIN_AUTOSYNC_INTERVAL);

        supportInvalidateOptionsMenu();
        super.onResume();
    }

    @Override
    protected void onPause() {
        stopBalanceRefreshTimer();
        mbwManager.getEventBus().unregister(this);
        super.onPause();
    }

    @Override
    public void onBackPressed() {
        ActionBar bar = getSupportActionBar();
        if (bar.getSelectedTab() == mBalanceTab) {
//         if(Build.VERSION.SDK_INT >= 21) {
//            finishAndRemoveTask();
//         } else {
//            finish();
//         }
            // this is not finishing on Android 6 LG G4, so the pin on startup is not requested.
            // commented out code above doesn't do the trick, neither.
//         mbwManager.setStartUpPinUnlocked(false);
            super.onBackPressed();
        } else {
            bar.selectTab(mBalanceTab);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.transaction_history_options_global, menu);
        inflater.inflate(R.menu.main_activity_options_menu, menu);
        addEnglishSetting(menu.findItem(R.id.miSettings));
        inflater.inflate(R.menu.refresh, menu);
        inflater.inflate(R.menu.record_options_menu_global, menu);
        return true;
    }

    private void addEnglishSetting(MenuItem settingsItem) {
        String displayed = getResources().getString(R.string.settings);
        String settingsEn = Utils.loadEnglish(R.string.settings);
        if (!settingsEn.equals(displayed)) {
            settingsItem.setTitle(settingsItem.getTitle() + " (" + settingsEn + ")");
        }
    }

    // controlling the behavior here is the safe but slightly slower responding
    // way of doing this.
    // controlling the visibility from the individual fragments is a bug-ridden
    // nightmare.
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        final int tabIdx = mViewPager.getCurrentItem();

        // Add Record menu
        final boolean isAccountTab = tabIdx == TAB_ID_ACCOUNTS;
        Preconditions.checkNotNull(menu.findItem(R.id.miAddRecord)).setVisible(isAccountTab);

        // Refresh menu
        final boolean isBalanceTab = tabIdx == TAB_ID_BALANCE;
        final boolean isHistoryTab = tabIdx == TAB_ID_HISTORY;
        refreshItem = Preconditions.checkNotNull(menu.findItem(R.id.miRefresh));
        refreshItem.setVisible(isBalanceTab || isHistoryTab || isAccountTab);
        setRefreshAnimation();

        Preconditions.checkNotNull(menu.findItem(R.id.miRescanTransactions)).setVisible(isHistoryTab);

        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        final int itemId = item.getItemId();
        switch (itemId) {
            case R.id.miColdStorage:
                InstantWalletActivity.callMe(this);
                return true;
            case R.id.miSettings: {
                Intent intent = new Intent(this, SettingsActivity.class);
                startActivityForResult(intent, REQUEST_SETTING_CHANGED);
                return true;
            }
            case R.id.miRefresh:
                // default only sync the current account
                SyncMode syncMode = SyncMode.NORMAL_FORCED;
                // every 5th manual refresh make a full scan
                if (new Random().nextInt(5) == 0) {
                    syncMode = SyncMode.FULL_SYNC_CURRENT_ACCOUNT_FORCED;
                } else if (mViewPager.getCurrentItem() == TAB_ID_ACCOUNTS) {
                    // if we are in the accounts tab, sync all accounts if the users forces a sync
                    syncMode = SyncMode.NORMAL_ALL_ACCOUNTS_FORCED;
                }
                mbwManager.getWalletManager().startSynchronization(syncMode);
                // also fetch a new exchange rate, if necessary
                return true;
            case R.id.miRescanTransactions:
                mbwManager.getSelectedAccount().dropCachedData();
                mbwManager.getWalletManager().startSynchronization(SyncMode.FULL_SYNC_CURRENT_ACCOUNT_FORCED);
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_SETTING_CHANGED) {
            // restart activity if language changes
            // or anything else in settings. this makes some of the listeners
            // obsolete
            Intent running = getIntent();
            finish();
            startActivity(running);
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private WalletManager.State commonSyncState;

    public void setRefreshAnimation() {
        if (refreshItem != null) {
            if (mbwManager.getWalletManager().getState() == WalletManager.State.SYNCHRONIZING) {
                if (commonSyncState != WalletManager.State.SYNCHRONIZING) {
                    commonSyncState = WalletManager.State.SYNCHRONIZING;
                    MenuItemCompat.setActionView(refreshItem, R.layout.actionbar_indeterminate_progress);
                }
            } else {
                commonSyncState = WalletManager.State.READY;
                MenuItemCompat.setActionView(refreshItem, null);
            }
        }
    }

    @Subscribe
    public void syncStarted(SyncStarted event) {
        setRefreshAnimation();
    }

    @Subscribe
    public void syncStopped(SyncStopped event) {
        setRefreshAnimation();
    }

    @Subscribe
    public void torState(TorStateChanged event) {
        setRefreshAnimation();
    }

    @Subscribe
    public void synchronizationFailed(SyncFailed event) {
        toaster.toastConnectionError();
    }

    @Subscribe
    public void transactionBroadcasted(TransactionBroadcasted event) {
        toaster.toast(R.string.transaction_sent, false);
    }

}
