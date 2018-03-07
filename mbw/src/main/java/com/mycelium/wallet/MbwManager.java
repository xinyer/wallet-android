package com.mycelium.wallet;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.mrd.bitlib.model.Address;
import com.mrd.bitlib.model.NetworkParameters;
import com.mrd.bitlib.util.CoinUtil;
import com.mrd.bitlib.util.CoinUtil.Denomination;
import com.mycelium.WapiLogger;
import com.mycelium.net.ServerEndpointType;
import com.mycelium.net.TorManager;
import com.mycelium.net.TorManagerOrbot;
import com.mycelium.wallet.environment.MbwEnvironment;
import com.mycelium.wallet.event.EventTranslator;
import com.mycelium.wallet.event.ExtraAccountsChanged;
import com.mycelium.wallet.event.ReceivingAddressChanged;
import com.mycelium.wallet.event.SelectedAccountChanged;
import com.mycelium.wallet.event.TorStateChanged;
import com.mycelium.wallet.extsig.common.ExternalSignatureDeviceManager;
import com.mycelium.wallet.extsig.trezor.TrezorManager;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wallet.wapi.SqliteWalletManagerBackingWrapper;
import com.mycelium.wapi.api.WapiClient;
import com.mycelium.wapi.wallet.SecureKeyValueStore;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.WalletManager;
import com.mycelium.wapi.wallet.WalletManagerBacking;
import com.mycelium.wapi.wallet.bip44.ExternalSignatureProviderProxy;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;


public class MbwManager {
    private static final String PROXY_HOST = "socksProxyHost";
    private static final String PROXY_PORT = "socksProxyPort";
    private static final String SELECTED_ACCOUNT = "selectedAccount";
    private static volatile MbwManager _instance = null;
    private static final String TAG = "MbwManager";

    public static synchronized MbwManager getInstance(Context context) {
        if (_instance == null) {
            _instance = new MbwManager(context);
        }
        return _instance;
    }

    private final Bus _eventBus;
    private final ExternalSignatureDeviceManager _trezorManager;
    private final WapiClient _wapi;

    private Handler _torHandler;
    private Context _applicationContext;
    private MetadataStorage _storage;

    private MinerFee _minerFee;
    private MbwEnvironment _environment;
    private String _language;
    private final WalletManager _walletManager;
    private final EventTranslator _eventTranslator;
    private ServerEndpointType.Types _torMode;
    private TorManager _torManager;

    private EvictingQueue<LogEntry> _wapiLogs = EvictingQueue.create(100);
    private Cache<String, Object> _semiPersistingBackgroundObjects = CacheBuilder.newBuilder().maximumSize(10).build();

    private MbwManager(Context evilContext) {
        _applicationContext = Preconditions.checkNotNull(evilContext.getApplicationContext());
        _environment = MbwEnvironment.verifyEnvironment(_applicationContext);

        // Preferences
        SharedPreferences preferences = getPreferences();

        _eventBus = new Bus();
        _eventBus.register(this);

        setTorMode(ServerEndpointType.Types.ONLY_HTTPS);

        _wapi = initWapi();
        _minerFee = MinerFee.fromString(preferences.getString(Constants.MINER_FEE_SETTING, MinerFee.NORMAL.toString()));

        // Get the display metrics of this device
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) _applicationContext.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(dm);

        _storage = new MetadataStorage(_applicationContext);
        _language = preferences.getString(Constants.LANGUAGE_SETTING, Locale.getDefault().getLanguage());

        Set<String> currencyList = getPreferences().getStringSet(Constants.SELECTED_CURRENCIES, null);
        //TODO: get it through coluManager instead ?
        Set<String> fiatCurrencies = new HashSet<>();
        if (currencyList == null) {
            //if there is no list take the default currency
            fiatCurrencies.add(Constants.DEFAULT_CURRENCY);
        } else {
            //else take all dem currencies, yeah
            fiatCurrencies.addAll(currencyList);
        }

        _trezorManager = new TrezorManager(_applicationContext, getNetwork(), getEventBus());
        _walletManager = createWalletManager(_applicationContext, _environment);
        _eventTranslator = new EventTranslator(new Handler(), _eventBus);
        _walletManager.addObserver(_eventTranslator);

//        migrateOldKeys();
    }

    @Subscribe()
    public void onExtraAccountsChanged(ExtraAccountsChanged event) {
        _walletManager.refreshExtraAccounts();
    }

    private synchronized void retainLog(Level level, String message) {
        _wapiLogs.add(new LogEntry(message, level, new Date()));
    }

    public WapiLogger retainingWapiLogger = new WapiLogger() {
        @Override
        public void logError(String message) {
            Log.e("Wapi", message);
            retainLog(Level.SEVERE, message);
        }

        @Override
        public void logError(String message, Exception e) {
            Log.e("Wapi", message, e);
            retainLog(Level.SEVERE, message);
        }

        @Override
        public void logInfo(String message) {
            Log.i("Wapi", message);
            retainLog(Level.INFO, message);
        }
    };

    private WapiClient initWapi() {
        String version;
        try {
            PackageInfo packageInfo = _applicationContext.getPackageManager().getPackageInfo(_applicationContext.getPackageName(), 0);
            if (packageInfo != null) {
                version = String.valueOf(packageInfo.versionCode);
            } else {
                version = "na";
            }
        } catch (PackageManager.NameNotFoundException e) {
            version = "na";
        }

        return new WapiClient(_environment.getWapiEndpoints(), retainingWapiLogger, version);
    }

    private void initTor() {
        _torHandler = new Handler(Looper.getMainLooper());

        if (_torMode == ServerEndpointType.Types.ONLY_TOR) {
            this._torManager = new TorManagerOrbot();
        } else {
            throw new IllegalArgumentException();
        }

        _torManager.setStateListener(new TorManager.TorState() {
            @Override
            public void onStateChange(String status, final int percentage) {
                Log.i("Tor init", status + ", " + String.valueOf(percentage));
                retainLog(Level.INFO, "Tor: " + status + ", " + String.valueOf(percentage));
                _torHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        _eventBus.post(new TorStateChanged(percentage));
                    }
                });
            }
        });

        _environment.getWapiEndpoints().setTorManager(this._torManager);
        _environment.getLtEndpoints().setTorManager(this._torManager);
    }

    /**
     * Create a Wallet Manager instance
     *
     * @param context     the application context
     * @param environment the Mycelium environment
     * @return a new wallet manager instance
     */
    private WalletManager createWalletManager(final Context context, MbwEnvironment environment) {
        // Create persisted account backing
        WalletManagerBacking backing = new SqliteWalletManagerBackingWrapper(context);

        // Create persisted secure storage instance
        SecureKeyValueStore secureKeyValueStore = new SecureKeyValueStore(backing,
                new AndroidRandomSource());

        ExternalSignatureProviderProxy externalSignatureProviderProxy = new ExternalSignatureProviderProxy(
                getTrezorManager()
        );

        // Create and return wallet manager
        WalletManager walletManager = new WalletManager(secureKeyValueStore,
                backing, environment.getNetwork(), _wapi, externalSignatureProviderProxy);

        // notify the walletManager about the current selected account
        UUID lastSelectedAccountId = getLastSelectedAccountId();
        if (lastSelectedAccountId != null) {
            walletManager.setActiveAccount(lastSelectedAccountId);
        }
        return walletManager;
    }

    private SharedPreferences getPreferences() {
        return _applicationContext.getSharedPreferences(Constants.SETTINGS_NAME, Activity.MODE_PRIVATE);
    }

    private SharedPreferences.Editor getEditor() {
        return getPreferences().edit();
    }

    public MinerFee getMinerFee() {
        return _minerFee;
    }

    public void setMinerFee(MinerFee minerFee) {
        _minerFee = minerFee;
        getEditor().putString(Constants.MINER_FEE_SETTING, _minerFee.toString()).commit();
    }

    public CoinUtil.Denomination getBitcoinDenomination() {
        String coinDenomination = getPreferences().getString(Constants.BITCOIN_DENOMINATION_SETTING, Denomination.BTC.toString());
        return Denomination.fromString(coinDenomination);
    }

    public void setBitcoinDenomination(CoinUtil.Denomination denomination) {
        getEditor().putString(Constants.BITCOIN_DENOMINATION_SETTING, denomination.toString()).commit();
    }

    public String getBtcValueString(long satoshis) {
        CoinUtil.Denomination d = getBitcoinDenomination();
        String valueString = CoinUtil.valueString(satoshis, d, true);
        return valueString + " " + d.getUnicodeName();
    }

    public void setProxy(String proxy) {
        getEditor().putString(Constants.PROXY_SETTING, proxy).commit();
        ImmutableList<String> vals = ImmutableList.copyOf(Splitter.on(":").split(proxy));
        if (vals.size() != 2) {
            noProxy();
            return;
        }
        Integer portNumber = Ints.tryParse(vals.get(1));
        if (portNumber == null || portNumber < 1 || portNumber > 65535) {
            noProxy();
            return;
        }
        String hostname = vals.get(0);
        System.setProperty(PROXY_HOST, hostname);
        System.setProperty(PROXY_PORT, portNumber.toString());
    }

    private void noProxy() {
        System.clearProperty(PROXY_HOST);
        System.clearProperty(PROXY_PORT);
    }

    public Bus getEventBus() {
        return _eventBus;
    }

    /**
     * Get the Bitcoin network parameters that the wallet operates on
     */
    public NetworkParameters getNetwork() {
        return _environment.getNetwork();
    }

    public String getLanguage() {
        return _language;
    }

    public Locale getLocale() {
        return new Locale(_language);
    }

    public void setLanguage(String _language) {
        this._language = _language;
        SharedPreferences.Editor editor = getEditor();
        editor.putString(Constants.LANGUAGE_SETTING, _language);
        editor.commit();
    }

    public void setTorMode(ServerEndpointType.Types torMode) {
        this._torMode = torMode;

        ServerEndpointType serverEndpointType = ServerEndpointType.fromType(torMode);
        if (serverEndpointType.mightUseTor()) {
            initTor();
        } else {
            if (_torManager != null) {
                _torManager.stopClient();
            }
        }

        _environment.getWapiEndpoints().setAllowedEndpointTypes(serverEndpointType);
        _environment.getLtEndpoints().setAllowedEndpointTypes(serverEndpointType);
    }

    public ServerEndpointType.Types getTorMode() {
        return _torMode;
    }

    public WalletManager getWalletManager() {
        return _walletManager;
    }

    public void forgetColdStorageWalletManager() {
        createWalletManager(_applicationContext, _environment);

    }

    public WalletAccount getSelectedAccount() {
        UUID uuid = getLastSelectedAccountId();

        // If nothing is selected, or selected is archived, pick the first one
        if (uuid == null || !_walletManager.hasAccount(uuid) || _walletManager.getAccount(uuid).isArchived()) {
            if (_walletManager.getActiveAccounts().isEmpty()) {
                // That case should never happen, because we prevent users from archiving all of their
                // accounts.
                // We had a bug that allowed it, and the app will crash always after restart.
                _walletManager.activateFirstAccount();
                return null;
            }
            uuid = _walletManager.getActiveAccounts().get(0).getId();
            setSelectedAccount(uuid);
        }

        return _walletManager.getAccount(uuid);
    }


    public Optional<UUID> getAccountId(Address address, Class accountClass) {
        Optional<UUID> result = Optional.absent();
        for (UUID uuid : _walletManager.getAccountIds()) {
            WalletAccount account = _walletManager.getAccount(uuid);
            if ((accountClass == null || accountClass.isAssignableFrom(account.getClass()))
                    && account.isMine(address)) {
                result = Optional.of(uuid);
                break;
            }
        }
        return result;
    }

    @Nullable
    private UUID getLastSelectedAccountId() {
        // Get the selected account ID
        String uuidStr = getPreferences().getString(SELECTED_ACCOUNT, "");
        UUID uuid = null;
        if (uuidStr.length() != 0) {
            try {
                uuid = UUID.fromString(uuidStr);
            } catch (IllegalArgumentException e) {
                // default to null and select another account below
            }
        }
        return uuid;
    }

    public void setSelectedAccount(UUID uuid) {
        final WalletAccount account;
        account = _walletManager.getAccount(uuid);
        Preconditions.checkState(account.isActive());
        getEditor().putString(SELECTED_ACCOUNT, uuid.toString()).commit();
        getEventBus().post(new SelectedAccountChanged(uuid));
        Optional<Address> receivingAddress = account.getReceivingAddress();
        getEventBus().post(new ReceivingAddressChanged(receivingAddress));
        // notify the wallet manager that this is the active account now
        _walletManager.setActiveAccount(account.getId());
    }

    public MetadataStorage getMetadataStorage() {
        return _storage;
    }

    public ExternalSignatureDeviceManager getTrezorManager() {
        return _trezorManager;
    }

    public WapiClient getWapi() {
        return _wapi;
    }

    public TorManager getTorManager() {
        return _torManager;
    }

    public Cache<String, Object> getBackgroundObjectsCache() {
        return _semiPersistingBackgroundObjects;
    }

}
