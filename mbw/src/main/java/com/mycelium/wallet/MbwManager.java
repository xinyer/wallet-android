package com.mycelium.wallet;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Vibrator;
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
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.mrd.bitlib.crypto.Bip39;
import com.mrd.bitlib.crypto.HdKeyNode;
import com.mrd.bitlib.crypto.InMemoryPrivateKey;
import com.mrd.bitlib.crypto.MrdExport;
import com.mrd.bitlib.crypto.RandomSource;
import com.mrd.bitlib.model.Address;
import com.mrd.bitlib.model.NetworkParameters;
import com.mrd.bitlib.util.BitUtils;
import com.mrd.bitlib.util.CoinUtil;
import com.mrd.bitlib.util.CoinUtil.Denomination;
import com.mrd.bitlib.util.HashUtils;
import com.mycelium.WapiLogger;
import com.mycelium.net.ServerEndpointType;
import com.mycelium.net.TorManager;
import com.mycelium.net.TorManagerOrbot;
import com.mycelium.wallet.activity.util.BlockExplorer;
import com.mycelium.wallet.activity.util.BlockExplorerManager;
import com.mycelium.wallet.api.AndroidAsyncApi;
import com.mycelium.wallet.bitid.ExternalService;
import com.mycelium.wallet.event.EventTranslator;
import com.mycelium.wallet.event.ExtraAccountsChanged;
import com.mycelium.wallet.event.ReceivingAddressChanged;
import com.mycelium.wallet.event.SelectedAccountChanged;
import com.mycelium.wallet.event.SelectedCurrencyChanged;
import com.mycelium.wallet.event.TorStateChanged;
import com.mycelium.wallet.extsig.common.ExternalSignatureDeviceManager;
import com.mycelium.wallet.extsig.trezor.TrezorManager;
import com.mycelium.wallet.persistence.MetadataStorage;
import com.mycelium.wallet.wapi.SqliteWalletManagerBackingWrapper;
import com.mycelium.wapi.api.WapiClient;
import com.mycelium.wapi.wallet.AccountProvider;
import com.mycelium.wapi.wallet.AesKeyCipher;
import com.mycelium.wapi.wallet.IdentityAccountKeyManager;
import com.mycelium.wapi.wallet.KeyCipher;
import com.mycelium.wapi.wallet.SecureKeyValueStore;
import com.mycelium.wapi.wallet.SyncMode;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.WalletManager;
import com.mycelium.wapi.wallet.WalletManagerBacking;
import com.mycelium.wapi.wallet.bip44.Bip44Account;
import com.mycelium.wapi.wallet.bip44.Bip44AccountContext;
import com.mycelium.wapi.wallet.bip44.ExternalSignatureProviderProxy;
import com.mycelium.wapi.wallet.single.SingleAddressAccount;
import com.squareup.otto.Bus;
import com.squareup.otto.Subscribe;

import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;


public class MbwManager {
    private static final String PROXY_HOST = "socksProxyHost";
    private static final String PROXY_PORT = "socksProxyPort";
    private static final String SELECTED_ACCOUNT = "selectedAccount";
    private static volatile MbwManager _instance = null;
    private static final String TAG = "MbwManager";

    /**
     * The root index we use for generating authentication keys.
     * 0x80 makes the number negative == hardened key derivation
     * 0x424944 = "BID"
     */
    private static final int BIP32_ROOT_AUTHENTICATION_INDEX = 0x80424944;

    private final CurrencySwitcher _currencySwitcher;
    private Timer _addressWatchTimer;

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
    private boolean _enableContinuousFocus;
    private MrdExport.V1.EncryptionParameters _cachedEncryptionParameters;
    private final MrdExport.V1.ScryptParameters _deviceScryptParameters;
    private MbwEnvironment _environment;
    private HttpErrorCollector _httpErrorCollector;
    private String _language;
    private final VersionManager _versionManager;
    private final ExchangeRateManager _exchangeRateManager;
    private final WalletManager _walletManager;
    private final RandomSource _randomSource;
    private final EventTranslator _eventTranslator;
    private ServerEndpointType.Types _torMode;
    private TorManager _torManager;
    public final BlockExplorerManager _blockExplorerManager;

    private EvictingQueue<LogEntry> _wapiLogs = EvictingQueue.create(100);
    private Cache<String, Object> _semiPersistingBackgroundObjects = CacheBuilder.newBuilder().maximumSize(10).build();

    private MbwManager(Context evilContext) {
        _applicationContext = Preconditions.checkNotNull(evilContext.getApplicationContext());
        _environment = MbwEnvironment.verifyEnvironment(_applicationContext);
        String version = VersionManager.determineVersion(_applicationContext);

        // Preferences
        SharedPreferences preferences = getPreferences();

        _eventBus = new Bus();
        _eventBus.register(this);

        setTorMode(ServerEndpointType.Types.ONLY_HTTPS);

        _wapi = initWapi();
        _httpErrorCollector = HttpErrorCollector.registerInVM(_applicationContext, _wapi);

        _randomSource = new AndroidRandomSource();

        _minerFee = MinerFee.fromString(preferences.getString(Constants.MINER_FEE_SETTING, MinerFee.NORMAL.toString()));
        _enableContinuousFocus = preferences.getBoolean(Constants.ENABLE_CONTINUOUS_FOCUS_SETTING, false);

        // Get the display metrics of this device
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager windowManager = (WindowManager) _applicationContext.getSystemService(Context.WINDOW_SERVICE);
        windowManager.getDefaultDisplay().getMetrics(dm);

        _storage = new MetadataStorage(_applicationContext);
        _language = preferences.getString(Constants.LANGUAGE_SETTING, Locale.getDefault().getLanguage());
        _versionManager = new VersionManager(_applicationContext, _language, new AndroidAsyncApi(_wapi, _eventBus), version, _eventBus);

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

        _exchangeRateManager = new ExchangeRateManager(_applicationContext, _wapi, getNetwork(), getMetadataStorage());
        _currencySwitcher = new CurrencySwitcher(
                _exchangeRateManager,
                fiatCurrencies,
                getPreferences().getString(Constants.FIAT_CURRENCY_SETTING, Constants.DEFAULT_CURRENCY),
                Denomination.fromString(preferences.getString(Constants.BITCOIN_DENOMINATION_SETTING, Denomination.BTC.toString()))
        );

        // Check the device MemoryClass and set the scrypt-parameters for the PDF backup
        ActivityManager am = (ActivityManager) _applicationContext.getSystemService(Context.ACTIVITY_SERVICE);
        int memoryClass = am.getMemoryClass();

        _deviceScryptParameters = memoryClass > 20
                ? MrdExport.V1.ScryptParameters.DEFAULT_PARAMS
                : MrdExport.V1.ScryptParameters.LOW_MEM_PARAMS;

        _trezorManager = new TrezorManager(_applicationContext, getNetwork(), getEventBus());
        _walletManager = createWalletManager(_applicationContext, _environment);

        _eventTranslator = new EventTranslator(new Handler(), _eventBus);
        _exchangeRateManager.subscribe(_eventTranslator);

        _walletManager.addObserver(_eventTranslator);

        // set the currency-list after we added all extra accounts, they may provide
        // additional needed fiat currencies
        setCurrencyList(fiatCurrencies);

        migrateOldKeys();

        _versionManager.initBackgroundVersionChecker();
        _blockExplorerManager = new BlockExplorerManager(this,
                _environment.getBlockExplorerList(),
                getPreferences().getString(Constants.BLOCK_EXPLORER,
                        _environment.getBlockExplorerList().get(0).getIdentifier()));
    }

    public void addExtraAccounts(AccountProvider accounts) {
        _walletManager.addExtraAccounts(accounts);
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


    private void migrateOldKeys() {
        // We only migrate old keys if we don't have any accounts yet - otherwise, migration has already taken place
        if (!_walletManager.getAccountIds().isEmpty()) {
            return;
        }

        //check which address was the last recently selected one
        SharedPreferences prefs = _applicationContext.getSharedPreferences("selected", Context.MODE_PRIVATE);
        String lastAddress = prefs.getString("last", null);

        // Migrate all existing records to accounts
        List<Record> records = loadClassicRecords();
        for (Record record : records) {

            // Create an account from this record
            UUID account;
            if (record.hasPrivateKey()) {
                try {
                    account = _walletManager.createSingleAddressAccount(record.key, AesKeyCipher.defaultKeyCipher());
                } catch (KeyCipher.InvalidKeyCipher invalidKeyCipher) {
                    throw new RuntimeException(invalidKeyCipher);
                }
            } else {
                account = _walletManager.createSingleAddressAccount(record.address);
            }

            //check whether this was the selected record
            if (record.address.toString().equals(lastAddress)) {
                setSelectedAccount(account);
            }

            //check whether the record was archived
            if (record.tag.equals(Record.Tag.ARCHIVE)) {
                _walletManager.getAccount(account).archiveAccount();
            }

        }
    }

    private List<Record> loadClassicRecords() {
        SharedPreferences prefs = _applicationContext.getSharedPreferences("data", Context.MODE_PRIVATE);
        List<Record> recordList = new LinkedList<>();

        // Load records
        String records = prefs.getString("records", "");
        for (String one : records.split(",")) {
            one = one.trim();
            if (one.length() == 0) {
                continue;
            }
            Record record = Record.fromSerializedString(one);
            if (record != null) {
                recordList.add(record);
            }
        }

        // Sort all records
        Collections.sort(recordList);
        return recordList;
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

    public String getFiatCurrency() {
        return _currencySwitcher.getCurrentFiatCurrency();
    }

    public boolean hasFiatCurrency() {
        return !getCurrencyList().isEmpty();
    }

    private SharedPreferences getPreferences() {
        return _applicationContext.getSharedPreferences(Constants.SETTINGS_NAME, Activity.MODE_PRIVATE);
    }

    public List<String> getCurrencyList() {
        return _currencySwitcher.getCurrencyList();
    }

    public void setCurrencyList(Set<String> currencies) {
        Set<String> allActiveFiatCurrencies = _walletManager.getAllActiveFiatCurrencies();
        // let the exchange-rate manager fetch all currencies, that we might need
        _exchangeRateManager.setCurrencyList(Sets.union(currencies, allActiveFiatCurrencies));

        // but tell the currency-switcher only to switch over the user selected currencies
        _currencySwitcher.setCurrencyList(currencies);

        SharedPreferences.Editor editor = getEditor();
        editor.putStringSet(Constants.SELECTED_CURRENCIES, new HashSet<>(currencies));
        editor.commit();
    }

    public String getNextCurrency(boolean includeBitcoin) {
        return _currencySwitcher.getNextCurrency(includeBitcoin);
    }

    private SharedPreferences.Editor getEditor() {
        return getPreferences().edit();
    }

    public ExchangeRateManager getExchangeRateManager() {
        return _exchangeRateManager;
    }

    public CurrencySwitcher getCurrencySwitcher() {
        return _currencySwitcher;
    }

    // returns the age of the PIN in blocks (~10min)
    public Optional<Integer> getRemainingPinLockdownDuration() {
        Optional<Integer> pinSetHeight = getMetadataStorage().getLastPinSetBlockheight();
        int blockHeight = getSelectedAccount().getBlockChainHeight();

        if (!pinSetHeight.isPresent() || blockHeight < pinSetHeight.get()) {
            return Optional.absent();
        }

        int pinAge = blockHeight - pinSetHeight.get();
        if (pinAge > Constants.MIN_PIN_BLOCKHEIGHT_AGE_ADDITIONAL_BACKUP) {
            return Optional.of(0);
        } else {
            return Optional.of(Constants.MIN_PIN_BLOCKHEIGHT_AGE_ADDITIONAL_BACKUP - pinAge);
        }
    }

    public void vibrate() {
        vibrate(500);
    }

    private void vibrate(int milliseconds) {
        Vibrator v = (Vibrator) _applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) {
            v.vibrate(milliseconds);
        }
    }

    public MinerFee getMinerFee() {
        return _minerFee;
    }

    public void setMinerFee(MinerFee minerFee) {
        _minerFee = minerFee;
        getEditor().putString(Constants.MINER_FEE_SETTING, _minerFee.toString()).commit();
    }

    public void setBlockExplorer(BlockExplorer blockExplorer) {
        _blockExplorerManager.setBlockExplorer(blockExplorer);
        getEditor().putString(Constants.BLOCK_EXPLORER, blockExplorer.getIdentifier()).commit();
    }


    public CoinUtil.Denomination getBitcoinDenomination() {
        return _currencySwitcher.getBitcoinDenomination();
    }

    public void setBitcoinDenomination(CoinUtil.Denomination denomination) {
        _currencySwitcher.setBitcoinDenomination(denomination);
        getEditor().putString(Constants.BITCOIN_DENOMINATION_SETTING, denomination.toString()).commit();
    }

    public String getBtcValueString(long satoshis) {
        return _currencySwitcher.getBtcValueString(satoshis);
    }

    public boolean getContinuousFocus() {
        return _enableContinuousFocus;
    }

    public void setContinuousFocus(boolean enableContinuousFocus) {
        _enableContinuousFocus = enableContinuousFocus;
        getEditor().putBoolean(Constants.ENABLE_CONTINUOUS_FOCUS_SETTING, _enableContinuousFocus).commit();
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

    public MrdExport.V1.EncryptionParameters getCachedEncryptionParameters() {
        return _cachedEncryptionParameters;
    }

    public void setCachedEncryptionParameters(MrdExport.V1.EncryptionParameters cachedEncryptionParameters) {
        _cachedEncryptionParameters = cachedEncryptionParameters;
    }

    public void clearCachedEncryptionParameters() {
        _cachedEncryptionParameters = null;
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

    public MbwEnvironment getEnvironmentSettings() {
        return _environment;
    }

    /**
     * Get the brand of the wallet. This allows us to behave differently
     * depending on the brand of the wallet.
     */
    public String getBrand() {
        return _environment.getBrand();
    }

    public void reportIgnoredException(Throwable e) {
        reportIgnoredException(null, e);
    }

    public void reportIgnoredException(String message, Throwable e) {
        if (_httpErrorCollector != null) {
            if (null != message && message.length() > 0) {
                message += "\n";
            } else {
                message = "";
            }
            RuntimeException msg = new RuntimeException("We caught an exception that we chose to ignore.\n" + message, e);
            _httpErrorCollector.reportErrorToServer(msg);
        }
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

    public VersionManager getVersionManager() {
        return _versionManager;
    }

    public MrdExport.V1.ScryptParameters getDeviceScryptParameters() {
        return _deviceScryptParameters;
    }

    public WalletManager getWalletManager() {
        return _walletManager;
    }

    public UUID createOnTheFlyAccount(Address address) {
        UUID accountId = _walletManager.createSingleAddressAccount(address);
        _walletManager.getAccount(accountId).setAllowZeroConfSpending(true);
        _walletManager.setActiveAccount(accountId);  // this also starts a sync
        return accountId;
    }

    public UUID createOnTheFlyAccount(InMemoryPrivateKey privateKey) {
        UUID accountId;
        try {
            accountId = _walletManager.createSingleAddressAccount(privateKey, AesKeyCipher.defaultKeyCipher());
        } catch (KeyCipher.InvalidKeyCipher invalidKeyCipher) {
            throw new RuntimeException(invalidKeyCipher);
        }
        _walletManager.getAccount(accountId).setAllowZeroConfSpending(true);
        _walletManager.setActiveAccount(accountId); // this also starts a sync
        return accountId;
    }


    public void forgetColdStorageWalletManager() {
        createWalletManager(_applicationContext, _environment);

    }

    public int getBitcoinBlockheight() {
        return _walletManager.getBlockheight();
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

    public InMemoryPrivateKey obtainPrivateKeyForAccount(WalletAccount account, String website, KeyCipher cipher) {
        if (account instanceof SingleAddressAccount) {
            // For single address accounts we use the private key directly
            try {
                return ((SingleAddressAccount) account).getPrivateKey(cipher);
            } catch (KeyCipher.InvalidKeyCipher invalidKeyCipher) {
                throw new RuntimeException();
            }
        } else if (account instanceof Bip44Account && ((Bip44Account) account).getAccountType() == Bip44AccountContext.ACCOUNT_TYPE_FROM_MASTERSEED) {
            // For BIP44 accounts we derive a private key from the BIP32 hierarchy
            try {
                Bip39.MasterSeed masterSeed = _walletManager.getMasterSeed(cipher);
                int accountIndex = ((Bip44Account) account).getAccountIndex();
                return createBip32WebsitePrivateKey(masterSeed.getBip32Seed(), accountIndex, website);
            } catch (KeyCipher.InvalidKeyCipher invalidKeyCipher) {
                throw new RuntimeException(invalidKeyCipher);
            }
        } else {
            throw new RuntimeException("Invalid account type");
        }
    }

    public InMemoryPrivateKey getBitIdKeyForWebsite(String website) {
        try {
            IdentityAccountKeyManager identity = _walletManager.getIdentityAccountKeyManager(AesKeyCipher.defaultKeyCipher());
            return identity.getPrivateKeyForWebsite(website, AesKeyCipher.defaultKeyCipher());
        } catch (KeyCipher.InvalidKeyCipher invalidKeyCipher) {
            throw new RuntimeException(invalidKeyCipher);
        }
    }

    private InMemoryPrivateKey createBip32WebsitePrivateKey(byte[] masterSeed, int accountIndex, String site) {
        // Create BIP32 root node
        HdKeyNode rootNode = HdKeyNode.fromSeed(masterSeed);
        // Create bit id node
        HdKeyNode bidNode = rootNode.createChildNode(BIP32_ROOT_AUTHENTICATION_INDEX);
        // Create the private key for the specified account
        InMemoryPrivateKey accountPriv = bidNode.createChildPrivateKey(accountIndex);
        // Concatenate the private key bytes with the site name
        byte[] sitePrivateKeySeed;
        try {
            sitePrivateKeySeed = BitUtils.concatenate(accountPriv.getPrivateKeyBytes(), site.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            // Does not happen
            throw new RuntimeException(e);
        }
        // Hash the seed and create a new private key from that which uses compressed public keys
        byte[] sitePrivateKeyBytes = HashUtils.doubleSha256(sitePrivateKeySeed).getBytes();
        return new InMemoryPrivateKey(sitePrivateKeyBytes, true);
    }

    public boolean isWalletPaired(ExternalService service) {
        return getMetadataStorage().isPairedService(service.getHost(getNetwork()));
    }

    public MetadataStorage getMetadataStorage() {
        return _storage;
    }

    public RandomSource getRandomSource() {
        return _randomSource;
    }

    public ExternalSignatureDeviceManager getTrezorManager() {
        return _trezorManager;
    }

    public WapiClient getWapi() {
        return _wapi;
    }

    public EvictingQueue<LogEntry> getWapiLogs() {
        return _wapiLogs;
    }

    public TorManager getTorManager() {
        return _torManager;
    }

    @Subscribe
    public void onSelectedCurrencyChanged(SelectedCurrencyChanged event) {
        SharedPreferences.Editor editor = getEditor();
        editor.putString(Constants.FIAT_CURRENCY_SETTING, _currencySwitcher.getCurrentFiatCurrency());
        editor.commit();
    }

    public Cache<String, Object> getBackgroundObjectsCache() {
        return _semiPersistingBackgroundObjects;
    }

    private void switchServer() {
        _environment.getWapiEndpoints().switchToNextEndpoint();
    }

    public void stopWatchingAddress() {
        if (_addressWatchTimer != null) {
            _addressWatchTimer.cancel();
        }
    }

    public void watchAddress(final Address address) {
        stopWatchingAddress();
        _addressWatchTimer = new Timer();
        _addressWatchTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                getWalletManager().startSynchronization(new SyncMode(address));
            }
        }, 1000, 5 * 1000);
    }

}
