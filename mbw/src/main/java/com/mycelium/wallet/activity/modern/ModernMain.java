package com.mycelium.wallet.activity.modern;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ShareCompat;
import android.support.v4.content.FileProvider;
import android.support.v4.view.MenuItemCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.util.Log;
import android.view.*;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Joiner;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.mrd.bitlib.model.Address;
import com.mycelium.net.ServerEndpointType;
import com.mycelium.wallet.*;
import com.mycelium.wallet.activity.AboutActivity;
import com.mycelium.wallet.activity.MessageVerifyActivity;
import com.mycelium.wallet.activity.ScanActivity;
import com.mycelium.wallet.activity.main.BalanceMasterFragment;
import com.mycelium.wallet.activity.main.TransactionHistoryFragment;
import com.mycelium.wallet.activity.modern.adapter.TabsAdapter;
import com.mycelium.wallet.activity.send.InstantWalletActivity;
import com.mycelium.wallet.activity.settings.SettingsActivity;
import com.mycelium.wallet.event.*;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wapi.api.response.Feature;
import com.mycelium.wapi.wallet.*;
import com.squareup.otto.Subscribe;

import de.cketti.library.changelog.ChangeLog;

import java.io.*;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
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
    private MbwManager _mbwManager;

    ViewPager mViewPager;
    TabsAdapter mTabsAdapter;
    ActionBar.Tab mBalanceTab;
    ActionBar.Tab mAccountsTab;
    private MenuItem refreshItem;
    private Toaster _toaster;
    private volatile long _lastSync = 0;
    private boolean _isAppStart = true;

    private Timer balanceRefreshTimer;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        _mbwManager = MbwManager.getInstance(this);
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
            BitmapDrawable background = (BitmapDrawable) _mbwManager.getBackgroundObjectsCache().get("mainBackground", new Callable<BitmapDrawable>() {
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

        mTabsAdapter = new TabsAdapter(this, mViewPager, _mbwManager);
        mAccountsTab = bar.newTab();
        mTabsAdapter.addTab(mAccountsTab.setText(getString(R.string.tab_accounts)), AccountsFragment.class, null);
        mBalanceTab = bar.newTab();
        mTabsAdapter.addTab(mBalanceTab.setText(getString(R.string.tab_balance)), BalanceMasterFragment.class, null);
        mTabsAdapter.addTab(bar.newTab().setText(getString(R.string.tab_transactions)), TransactionHistoryFragment.class, null);
        bar.selectTab(mBalanceTab);
        _toaster = new Toaster(this);

        ChangeLog cl = new DarkThemeChangeLog(this);
        if (cl.isFirstRun() && cl.getChangeLog(false).size() > 0 && !cl.isFirstRunEver()) {
            cl.getLogDialog().show();
        }

        if (savedInstanceState != null) {
            _lastSync = savedInstanceState.getLong(LAST_SYNC, 0);
            _isAppStart = savedInstanceState.getBoolean(APP_START, true);
        }

        if (_isAppStart) {
            _mbwManager.getVersionManager().showFeatureWarningIfNeeded(this, Feature.APP_START);
            checkGapBug();
            _isAppStart = false;
        }
    }

    private void checkGapBug() {
        final WalletManager walletManager = _mbwManager.getWalletManager();
        final List<Integer> gaps = walletManager.getGapsBug();
        if (!gaps.isEmpty()) {
            try {
                final List<Address> gapAddresses = walletManager.getGapAddresses(AesKeyCipher.defaultKeyCipher());
                final String gapsString = Joiner.on(", ").join(gapAddresses);
                Log.d("Gaps", gapsString);

                final SpannableString s = new SpannableString(
                        "Sorry to interrupt you... \n \nWe discovered a bug in the account logic that will make problems if you someday need to restore from your 12 word backup.\n\nFor further information see here: https://wallet.mycelium.com/info/gaps \n\nMay we try to resolve it for you? Press OK, to share one address per affected account with us."
                );
                Linkify.addLinks(s, Linkify.ALL);

                final AlertDialog d = new AlertDialog.Builder(this)
                        .setTitle("Account Gap")
                        .setMessage(s)

                        .setPositiveButton("OK", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                createPlaceHolderAccounts(gaps);
                                _mbwManager.reportIgnoredException(new RuntimeException("Address gaps: " + gapsString));
                            }
                        })
                        .setNegativeButton("Ignore", new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {

                            }
                        })
                        .show();

                // Make the textview clickable. Must be called after show()
                ((TextView) d.findViewById(android.R.id.message)).setMovementMethod(LinkMovementMethod.getInstance());

            } catch (KeyCipher.InvalidKeyCipher invalidKeyCipher) {
                throw new RuntimeException(invalidKeyCipher);
            }
        }
    }

    private void createPlaceHolderAccounts(List<Integer> gapIndex) {
        final WalletManager walletManager = _mbwManager.getWalletManager();
        for (Integer index : gapIndex) {
            try {
                final UUID newAccount = walletManager.createArchivedGapFiller(AesKeyCipher.defaultKeyCipher(), index);
                _mbwManager.getMetadataStorage().storeAccountLabel(newAccount, "Gap Account " + (index + 1));
            } catch (KeyCipher.InvalidKeyCipher invalidKeyCipher) {
                throw new RuntimeException(invalidKeyCipher);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(LAST_SYNC, _lastSync);
        outState.putBoolean(APP_START, _isAppStart);
    }

    protected void stopBalanceRefreshTimer() {
        if (balanceRefreshTimer != null) {
            balanceRefreshTimer.cancel();
        }
    }

    @Override
    protected void onResume() {
        _mbwManager.getEventBus().register(this);

        long curTime = new Date().getTime();
        if (_lastSync == 0 || curTime - _lastSync > MIN_AUTOSYNC_INTERVAL) {
            Handler h = new Handler();
            h.postDelayed(new Runnable() {
                @Override
                public void run() {
                    _mbwManager.getVersionManager().checkForUpdate();
                }
            }, 50);
        }

        stopBalanceRefreshTimer();
        balanceRefreshTimer = new Timer();
        balanceRefreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                _mbwManager.getExchangeRateManager().requestRefresh();

                // if the last full sync is too old (or not known), start a full sync for _all_ accounts
                // otherwise just run a normal sync for the current account
                final Optional<Long> lastFullSync = _mbwManager.getMetadataStorage().getLastFullSync();
                if (lastFullSync.isPresent()
                        && (new Date().getTime() - lastFullSync.get() < MIN_FULLSYNC_INTERVAL)) {
                    _mbwManager.getWalletManager().startSynchronization();
                } else {
                    _mbwManager.getWalletManager().startSynchronization(SyncMode.FULL_SYNC_ALL_ACCOUNTS);
                    _mbwManager.getMetadataStorage().setLastFullSync(new Date().getTime());
                }

                _lastSync = new Date().getTime();
            }
        }, 100, MIN_AUTOSYNC_INTERVAL);

        supportInvalidateOptionsMenu();
        super.onResume();
    }

    @Override
    protected void onPause() {
        stopBalanceRefreshTimer();
        _mbwManager.getEventBus().unregister(this);
        _mbwManager.getVersionManager().closeDialog();
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
//         _mbwManager.setStartUpPinUnlocked(false);
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
        inflater.inflate(R.menu.export_history, menu);
        inflater.inflate(R.menu.record_options_menu_global, menu);
        inflater.inflate(R.menu.verify_message, menu);
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

        // at the moment, we allow to make backups multiple times
        Preconditions.checkNotNull(menu.findItem(R.id.miBackup)).setVisible(true);

        // Add Record menu
        final boolean isAccountTab = tabIdx == TAB_ID_ACCOUNTS;
        Preconditions.checkNotNull(menu.findItem(R.id.miAddRecord)).setVisible(isAccountTab);

        // Lock menu
//      final boolean hasPin = _mbwManager.isPinProtected();
//      Preconditions.checkNotNull(menu.findItem(R.id.miLockKeys)).setVisible(isAccountTab && hasPin);

        // Refresh menu
        final boolean isBalanceTab = tabIdx == TAB_ID_BALANCE;
        final boolean isHistoryTab = tabIdx == TAB_ID_HISTORY;
        refreshItem = Preconditions.checkNotNull(menu.findItem(R.id.miRefresh));
        refreshItem.setVisible(isBalanceTab || isHistoryTab || isAccountTab);
        setRefreshAnimation();

        //export tx history
        Preconditions.checkNotNull(menu.findItem(R.id.miExportHistory)).setVisible(isHistoryTab);

        Preconditions.checkNotNull(menu.findItem(R.id.miRescanTransactions)).setVisible(isHistoryTab);

//      final boolean isAddressBook = tabIdx == addressBookTabIndex;
//      Preconditions.checkNotNull(menu.findItem(R.id.miAddAddress)).setVisible(isAddressBook);

        return super.onPrepareOptionsMenu(menu);
    }

    @SuppressWarnings("unused")
    private boolean canObtainLocation() {
        final boolean hasFeature = getPackageManager().hasSystemFeature("android.hardware.location.network");
        if (!hasFeature) {
            return false;
        }
        String permission = "android.permission.ACCESS_COARSE_LOCATION";
        int res = checkCallingOrSelfPermission(permission);
        return (res == PackageManager.PERMISSION_GRANTED);
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
            case R.id.miBackup:
                Utils.pinProtectedWordlistBackup(this);
                return true;
            //with wordlists, we just need to backup and verify in one step
            //} else if (itemId == R.id.miVerifyBackup) {
            //   VerifyBackupActivity.callMe(this);
            //   return true;
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
                _mbwManager.getWalletManager().startSynchronization(syncMode);
                // also fetch a new exchange rate, if necessary
                _mbwManager.getExchangeRateManager().requestOptionalRefresh();
                return true;
            case R.id.miHelp:
                openMyceliumHelp();
                break;
            case R.id.miAbout: {
                Intent intent = new Intent(this, AboutActivity.class);
                startActivity(intent);
                break;
            }
            case R.id.miRescanTransactions:
                _mbwManager.getSelectedAccount().dropCachedData();
                _mbwManager.getWalletManager().startSynchronization(SyncMode.FULL_SYNC_CURRENT_ACCOUNT_FORCED);
                break;
            case R.id.miExportHistory:
                shareTransactionHistory();
                break;
            case R.id.miVerifyMessage:
                startActivity(new Intent(this, MessageVerifyActivity.class));
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void shareTransactionHistory() {
        WalletAccount account = _mbwManager.getSelectedAccount();
        MetadataStorage metaData = _mbwManager.getMetadataStorage();
        try {
            String fileName = "MyceliumExport_" + System.currentTimeMillis() + ".csv";
            File historyData = DataExport.getTxHistoryCsv(account, metaData, getFileStreamPath(fileName));
            PackageManager packageManager = Preconditions.checkNotNull(getPackageManager());
            PackageInfo packageInfo = packageManager.getPackageInfo(getPackageName(), PackageManager.GET_PROVIDERS);
            for (ProviderInfo info : packageInfo.providers) {
                if (info.name.equals("android.support.v4.content.FileProvider")) {
                    String authority = info.authority;
                    Uri uri = FileProvider.getUriForFile(this, authority, historyData);
                    Intent intent = ShareCompat.IntentBuilder.from(this)
                            .setStream(uri)  // uri from FileProvider
                            .setType("text/plain")
                            .setSubject(getResources().getString(R.string.transaction_history_title))
                            .setText(getResources().getString(R.string.transaction_history_title))
                            .getIntent()
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    List<ResolveInfo> resInfoList = packageManager.queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY);
                    for (ResolveInfo resolveInfo : resInfoList) {
                        String packageName = resolveInfo.activityInfo.packageName;
                        grantUriPermission(packageName, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    }
                    startActivity(Intent.createChooser(intent, getResources().getString(R.string.share_transaction_history)));
                }
            }
        } catch (IOException | PackageManager.NameNotFoundException e) {
            _toaster.toast("Export failed. Check your logs", false);
            e.printStackTrace();
        }
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
        } else if (requestCode == GENERIC_SCAN_REQUEST) {
            if (resultCode != RESULT_OK) {
                //report to user in case of error
                //if no scan handlers match successfully, this is the last resort to display an error msg
                ScanActivity.toastScanError(resultCode, data, this);
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void openMyceliumHelp() {
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setData(Uri.parse(Constants.MYCELIUM_WALLET_HELP_URL));
        startActivity(intent);
        Toast.makeText(this, R.string.going_to_mycelium_com_help, Toast.LENGTH_LONG).show();
    }

    private WalletManager.State commonSyncState;

    public void setRefreshAnimation() {
        if (refreshItem != null) {
            if (_mbwManager.getWalletManager().getState() == WalletManager.State.SYNCHRONIZING) {
                if (commonSyncState != WalletManager.State.SYNCHRONIZING) {
                    commonSyncState = WalletManager.State.SYNCHRONIZING;
                    MenuItem menuItem = MenuItemCompat.setActionView(refreshItem, R.layout.actionbar_indeterminate_progress);
                    ImageView ivTorIcon = (ImageView) menuItem.getActionView().findViewById(R.id.ivTorIcon);

                    if (_mbwManager.getTorMode() == ServerEndpointType.Types.ONLY_TOR && _mbwManager.getTorManager() != null) {
                        ivTorIcon.setVisibility(View.VISIBLE);
                        if (_mbwManager.getTorManager().getInitState() == 100) {
                            ivTorIcon.setImageResource(R.drawable.tor);
                        } else {
                            ivTorIcon.setImageResource(R.drawable.tor_gray);
                        }
                    } else {
                        ivTorIcon.setVisibility(View.GONE);
                    }
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
        _toaster.toastConnectionError();
    }

    @Subscribe
    public void transactionBroadcasted(TransactionBroadcasted event) {
        _toaster.toast(R.string.transaction_sent, false);
    }

    @Subscribe
    public void onNewFeatureWarnings(final FeatureWarningsAvailable event) {
        _mbwManager.getVersionManager().showFeatureWarningIfNeeded(this, Feature.MAIN_SCREEN);
    }

    @Subscribe
    public void onNewVersion(final NewWalletVersionAvailable event) {
        _mbwManager.getVersionManager().showIfRelevant(event.versionInfo, this);
    }
}
