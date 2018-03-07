package com.mycelium.wallet;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.WindowManager;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableList;
import com.google.common.primitives.Ints;
import com.mrd.bitlib.model.Address;
import com.mrd.bitlib.model.NetworkParameters;
import com.mrd.bitlib.util.CoinUtil;
import com.mrd.bitlib.util.CoinUtil.Denomination;
import com.mycelium.WapiLogger;
import com.mycelium.net.ServerEndpointType;
import com.mycelium.wallet.common.AndroidRandomSource;
import com.mycelium.wallet.common.MinerFee;
import com.mycelium.wallet.environment.MbwEnvironment;
import com.mycelium.wallet.event.EventTranslator;
import com.mycelium.wallet.event.ExtraAccountsChanged;
import com.mycelium.wallet.event.ReceivingAddressChanged;
import com.mycelium.wallet.event.SelectedAccountChanged;
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
    private static final String TAG = "MbwManager";

    private static volatile MbwManager instance = null;

    public static synchronized MbwManager getInstance(Context context) {
        if (instance == null) {
            instance = new MbwManager(context);
        }
        return instance;
    }

    private final Bus eventBus;
    private final ExternalSignatureDeviceManager trezorManager;
    private final WapiClient wapi;

    private Context applicationContext;
    private MetadataStorage metadataStorage;

    private MinerFee minerFee;
    private MbwEnvironment environment;
    private String language;
    private final WalletManager walletManager;
    private final EventTranslator eventTranslator;

    private Cache<String, Object> _semiPersistingBackgroundObjects = CacheBuilder.newBuilder().maximumSize(10).build();

    private MbwManager(Context evilContext) {
        applicationContext = Preconditions.checkNotNull(evilContext.getApplicationContext());
        environment = MbwEnvironment.verifyEnvironment(applicationContext);

        // Preferences
        SharedPreferences preferences = getPreferences();

        eventBus = new Bus();
        eventBus.register(this);

        setTorMode(ServerEndpointType.Types.ONLY_HTTPS);

        wapi = initWapi();
        minerFee = MinerFee.fromString(preferences.getString(Constants.MINER_FEE_SETTING, MinerFee.NORMAL.toString()));

        // Get the display metrics of this device
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) applicationContext.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(dm);

        metadataStorage = new MetadataStorage(applicationContext);
        language = preferences.getString(Constants.LANGUAGE_SETTING, Locale.getDefault().getLanguage());

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

        trezorManager = new TrezorManager(applicationContext, getNetwork(), getEventBus());
        walletManager = createWalletManager(applicationContext, environment);
        eventTranslator = new EventTranslator(new Handler(), eventBus);
        walletManager.addObserver(eventTranslator);

    }

    @Subscribe()
    public void onExtraAccountsChanged(ExtraAccountsChanged event) {
        walletManager.refreshExtraAccounts();
    }

    private WapiClient initWapi() {
        String version;
        try {
            PackageInfo packageInfo = applicationContext.getPackageManager().getPackageInfo(applicationContext.getPackageName(), 0);
            if (packageInfo != null) {
                version = String.valueOf(packageInfo.versionCode);
            } else {
                version = "na";
            }
        } catch (PackageManager.NameNotFoundException e) {
            version = "na";
        }

        WapiLogger retainingWapiLogger = new WapiLogger() {
            @Override
            public void logError(String message) {
                Log.e("Wapi", message);
            }

            @Override
            public void logError(String message, Exception e) {
                Log.e("Wapi", message, e);
            }

            @Override
            public void logInfo(String message) {
                Log.i("Wapi", message);
            }
        };

        return new WapiClient(environment.getWapiEndpoints(), retainingWapiLogger, version);
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
                backing, environment.getNetwork(), wapi, externalSignatureProviderProxy);

        // notify the walletManager about the current selected account
        UUID lastSelectedAccountId = getLastSelectedAccountId();
        if (lastSelectedAccountId != null) {
            walletManager.setActiveAccount(lastSelectedAccountId);
        }
        return walletManager;
    }

    private SharedPreferences getPreferences() {
        return applicationContext.getSharedPreferences(Constants.SETTINGS_NAME, Activity.MODE_PRIVATE);
    }

    private SharedPreferences.Editor getEditor() {
        return getPreferences().edit();
    }

    public MinerFee getMinerFee() {
        return minerFee;
    }

    public void setMinerFee(MinerFee minerFee) {
        this.minerFee = minerFee;
        getEditor().putString(Constants.MINER_FEE_SETTING, this.minerFee.toString()).commit();
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
        return eventBus;
    }

    /**
     * Get the Bitcoin network parameters that the wallet operates on
     */
    public NetworkParameters getNetwork() {
        return environment.getNetwork();
    }

    public String getLanguage() {
        return language;
    }

    public Locale getLocale() {
        return new Locale(language);
    }

    public void setLanguage(String _language) {
        this.language = _language;
        SharedPreferences.Editor editor = getEditor();
        editor.putString(Constants.LANGUAGE_SETTING, _language);
        editor.commit();
    }

    private void setTorMode(ServerEndpointType.Types torMode) {
        ServerEndpointType serverEndpointType = ServerEndpointType.fromType(torMode);
        environment.getWapiEndpoints().setAllowedEndpointTypes(serverEndpointType);
        environment.getLtEndpoints().setAllowedEndpointTypes(serverEndpointType);
    }

    public WalletManager getWalletManager() {
        return walletManager;
    }

    public void forgetColdStorageWalletManager() {
        createWalletManager(applicationContext, environment);
    }

    public WalletAccount getSelectedAccount() {
        UUID uuid = getLastSelectedAccountId();

        // If nothing is selected, or selected is archived, pick the first one
        if (uuid == null || !walletManager.hasAccount(uuid) || walletManager.getAccount(uuid).isArchived()) {
            if (walletManager.getActiveAccounts().isEmpty()) {
                // That case should never happen, because we prevent users from archiving all of their
                // accounts.
                // We had a bug that allowed it, and the app will crash always after restart.
                walletManager.activateFirstAccount();
                return null;
            }
            uuid = walletManager.getActiveAccounts().get(0).getId();
            setSelectedAccount(uuid);
        }

        return walletManager.getAccount(uuid);
    }

    public Optional<UUID> getAccountId(Address address, Class accountClass) {
        Optional<UUID> result = Optional.absent();
        for (UUID uuid : walletManager.getAccountIds()) {
            WalletAccount account = walletManager.getAccount(uuid);
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
        account = walletManager.getAccount(uuid);
        Preconditions.checkState(account.isActive());
        getEditor().putString(SELECTED_ACCOUNT, uuid.toString()).commit();
        getEventBus().post(new SelectedAccountChanged(uuid));
        Optional<Address> receivingAddress = account.getReceivingAddress();
        getEventBus().post(new ReceivingAddressChanged(receivingAddress));
        // notify the wallet manager that this is the active account now
        walletManager.setActiveAccount(account.getId());
    }

    public MetadataStorage getMetadataStorage() {
        return metadataStorage;
    }

    public ExternalSignatureDeviceManager getTrezorManager() {
        return trezorManager;
    }

    public WapiClient getWapi() {
        return wapi;
    }

    public Cache<String, Object> getBackgroundObjectsCache() {
        return _semiPersistingBackgroundObjects;
    }

}
