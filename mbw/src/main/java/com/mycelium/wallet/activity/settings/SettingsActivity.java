/*
 * Copyright 2013, 2014 Megion Research and Development GmbH
 *
 * Licensed under the Microsoft Reference Source License (MS-RSL)
 *
 * This license governs use of the accompanying software. If you use the software, you accept this license.
 * If you do not accept the license, do not use the software.
 *
 * 1. Definitions
 * The terms "reproduce," "reproduction," and "distribution" have the same meaning here as under U.S. copyright law.
 * "You" means the licensee of the software.
 * "Your company" means the company you worked for when you downloaded the software.
 * "Reference use" means use of the software within your company as a reference, in read only form, for the sole purposes
 * of debugging your products, maintaining your products, or enhancing the interoperability of your products with the
 * software, and specifically excludes the right to distribute the software outside of your company.
 * "Licensed patents" means any Licensor patent claims which read directly on the software as distributed by the Licensor
 * under this license.
 *
 * 2. Grant of Rights
 * (A) Copyright Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free copyright license to reproduce the software for reference use.
 * (B) Patent Grant- Subject to the terms of this license, the Licensor grants you a non-transferable, non-exclusive,
 * worldwide, royalty-free patent license under licensed patents for reference use.
 *
 * 3. Limitations
 * (A) No Trademark License- This license does not grant you any rights to use the Licensor’s name, logo, or trademarks.
 * (B) If you begin patent litigation against the Licensor over patents that you think may apply to the software
 * (including a cross-claim or counterclaim in a lawsuit), your license to the software ends automatically.
 * (C) The software is licensed "as-is." You bear the risk of using it. The Licensor gives no express warranties,
 * guarantees or conditions. You may have additional consumer rights under your local laws which this license cannot
 * change. To the extent permitted under your local laws, the Licensor excludes the implied warranties of merchantability,
 * fitness for a particular purpose and non-infringement.
 */

package com.mycelium.wallet.activity.settings;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.ledger.tbase.comm.LedgerTransportTEEProxyFactory;
import com.mrd.bitlib.util.CoinUtil.Denomination;
import com.mrd.bitlib.util.HexUtils;
import com.mycelium.lt.api.model.TraderInfo;
import com.mycelium.net.ServerEndpointType;
import com.mycelium.wallet.Constants;
import com.mycelium.wallet.ExchangeRateManager;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.MinerFee;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.WalletApplication;
import com.mycelium.wallet.activity.export.VerifyBackupActivity;
import com.mycelium.wallet.activity.modern.Toaster;
import com.mycelium.wapi.wallet.WalletAccount;
import com.mycelium.wapi.wallet.single.SingleAddressAccount;

import java.util.List;
import java.util.Locale;

import info.guardianproject.onionkit.ui.OrbotHelper;

/**
 * PreferenceActivity is a built-in Activity for preferences management
 * <p/>
 * To retrieve the values stored by this activity in other activities use the
 * following snippet:
 * <p/>
 * SharedPreferences sharedPreferences =
 * PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
 * <Preference Type> preferenceValue = sharedPreferences.get<Preference
 * Type>("<Preference Key>",<default value>);
 */
public class SettingsActivity extends PreferenceActivity {
   public static final CharMatcher AMOUNT = CharMatcher.JAVA_DIGIT.or(CharMatcher.anyOf(".,"));
   private final OnPreferenceClickListener localCurrencyClickListener = new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
         SetLocalCurrencyActivity.callMe(SettingsActivity.this);
         return true;
      }
   };
   private final OnPreferenceClickListener setPinClickListener = new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
         _mbwManager.showSetPinDialog(SettingsActivity.this, Optional.<Runnable>of(new Runnable() {
                    @Override
                    public void run() {
                       updateClearPin();
                    }
                 })
         );
         return true;
      }
   };

   private final OnPreferenceChangeListener setPinOnStartupClickListener = new OnPreferenceChangeListener() {
      @Override
      public boolean onPreferenceChange(final Preference preference, Object o) {
         _mbwManager.runPinProtectedFunction(SettingsActivity.this, new Runnable() {
                    @Override
                    public void run() {
                       // toggle it here
                       boolean checked = !((CheckBoxPreference) preference).isChecked();
                       _mbwManager.setPinRequiredOnStartup(checked);
                       ((CheckBoxPreference) preference).setChecked(_mbwManager.getPinRequiredOnStartup());
                    }
                 }
         );

         // dont automatically take the new value, lets to it in our the pin protected runnable
         return false;
      }
   };
   private final OnPreferenceClickListener clearPinClickListener = new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
         _mbwManager.showClearPinDialog(SettingsActivity.this, Optional.<Runnable>of(new Runnable() {
            @Override
            public void run() {
               updateClearPin();
            }
         }));
         return true;
      }
   };
   private final OnPreferenceClickListener legacyBackupClickListener = new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
         Utils.pinProtectedBackup(SettingsActivity.this);
         return true;
      }
   };
   private final OnPreferenceClickListener legacyBackupVerifyClickListener = new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
         VerifyBackupActivity.callMe(SettingsActivity.this);
         return true;
      }
   };

   private final OnPreferenceClickListener showBip44PathClickListener = new OnPreferenceClickListener() {
      public boolean onPreferenceClick(Preference preference) {
         CheckBoxPreference p = (CheckBoxPreference) preference;
         _mbwManager.getMetadataStorage().setShowBip44Path(p.isChecked());
         return true;
      }
   };

   private ListPreference _bitcoinDenomination;
   private Preference _localCurrency;
   private ListPreference _exchangeSource;
   private CheckBoxPreference _ltNotificationSound;
   private CheckBoxPreference _ltMilesKilometers;
   private MbwManager _mbwManager;
   private ListPreference _minerFee;
   private ListPreference _blockExplorer;

   @SuppressWarnings("ResultOfMethodCallIgnored")
   @VisibleForTesting
   static boolean isNumber(String text) {
      try {
         Double.parseDouble(text);
      } catch (NumberFormatException ignore) {
         return false;
      }
      return true;
   }

   @VisibleForTesting
   static String extractAmount(CharSequence s) {
      String amt = AMOUNT.retainFrom(s).replace(",", ".");
      int commaIdx = amt.indexOf(".");
      if (commaIdx > -1) {
         String cents = amt.substring(commaIdx + 1, Math.min(amt.length(), commaIdx + 3));
         String euros = amt.substring(0, commaIdx);
         return euros + "." + cents;
      }
      return amt;
   }

   @SuppressWarnings("deprecation")
   @Override
   public void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      addPreferencesFromResource(R.xml.preferences);
      _mbwManager = MbwManager.getInstance(SettingsActivity.this.getApplication());
      // Bitcoin Denomination
      _bitcoinDenomination = (ListPreference) findPreference("bitcoin_denomination");
      _bitcoinDenomination.setTitle(bitcoinDenominationTitle());
      _bitcoinDenomination.setDefaultValue(_mbwManager.getBitcoinDenomination().toString());
      _bitcoinDenomination.setValue(_mbwManager.getBitcoinDenomination().toString());
      CharSequence[] denominations = new CharSequence[]{Denomination.BTC.toString(), Denomination.mBTC.toString(),
              Denomination.uBTC.toString(), Denomination.BITS.toString()};
      _bitcoinDenomination.setEntries(denominations);
      _bitcoinDenomination.setEntryValues(denominations);
      _bitcoinDenomination.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

         @Override
         public boolean onPreferenceChange(Preference preference, Object newValue) {
            _mbwManager.setBitcoinDenomination(Denomination.fromString(newValue.toString()));
            _bitcoinDenomination.setTitle(bitcoinDenominationTitle());
            return true;
         }
      });

      // Miner Fee
      _minerFee = (ListPreference) findPreference("miner_fee");
      _minerFee.setTitle(getMinerFeeTitle());
      _minerFee.setSummary(getMinerFeeSummary());
      _minerFee.setValue(_mbwManager.getMinerFee().toString());
      CharSequence[] minerFees = new CharSequence[]{
              MinerFee.LOWPRIO.toString(),
              MinerFee.ECONOMIC.toString(),
              MinerFee.NORMAL.toString(),
              MinerFee.PRIORITY.toString()};
      CharSequence[] minerFeeNames = new CharSequence[]{
              getString(R.string.miner_fee_lowprio_name),
              getString(R.string.miner_fee_economic_name),
              getString(R.string.miner_fee_normal_name),
              getString(R.string.miner_fee_priority_name)};
      _minerFee.setEntries(minerFeeNames);
      _minerFee.setEntryValues(minerFees);
      _minerFee.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

         @Override
         public boolean onPreferenceChange(Preference preference, Object newValue) {
            _mbwManager.setMinerFee(MinerFee.fromString(newValue.toString()));
            _minerFee.setTitle(getMinerFeeTitle());
            _minerFee.setSummary(getMinerFeeSummary());
            String description = _mbwManager.getMinerFee().getMinerFeeDescription(SettingsActivity.this);
            Utils.showSimpleMessageDialog(SettingsActivity.this, description);
            return true;
         }
      });


      //Block Explorer
      _blockExplorer = (ListPreference) findPreference("block_explorer");
      _blockExplorer.setTitle(getBlockExplorerTitle());
      _blockExplorer.setSummary(getBlockExplorerSummary());
      _blockExplorer.setValue(_mbwManager._blockExplorerManager.getBlockExplorer().getIdentifier());
      CharSequence[] blockExplorerNames = _mbwManager._blockExplorerManager.getBlockExplorerNames(_mbwManager._blockExplorerManager.getAllBlockExplorer());
      CharSequence[] blockExplorerValues = _mbwManager._blockExplorerManager.getBlockExplorerIds();
      _blockExplorer.setEntries(blockExplorerNames);
      _blockExplorer.setEntryValues(blockExplorerValues);
      _blockExplorer.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

         public boolean onPreferenceChange(Preference preference, Object newValue) {
            _mbwManager.setBlockExplorer(_mbwManager._blockExplorerManager.getBlockExplorerById(newValue.toString()));
            _blockExplorer.setTitle(getBlockExplorerTitle());
            _blockExplorer.setSummary(getBlockExplorerSummary());
            return true;
         }
      });

      //localcurrency
      _localCurrency = findPreference("local_currency");
      _localCurrency.setOnPreferenceClickListener(localCurrencyClickListener);
      _localCurrency.setTitle(localCurrencyTitle());

      // Exchange Source
      _exchangeSource = (ListPreference) findPreference("exchange_source");
      ExchangeRateManager exchangeManager = _mbwManager.getExchangeRateManager();
      List<String> exchangeSourceNamesList = exchangeManager.getExchangeSourceNames();
      CharSequence[] exchangeNames = exchangeSourceNamesList.toArray(new String[exchangeSourceNamesList.size()]);
      _exchangeSource.setEntries(exchangeNames);
      if (exchangeNames.length == 0) {
         _exchangeSource.setEnabled(false);
      } else {
         String currentName = exchangeManager.getCurrentExchangeSourceName();
         if (currentName == null) {
            currentName = "";
         }
         _exchangeSource.setEntries(exchangeNames);
         _exchangeSource.setEntryValues(exchangeNames);
         _exchangeSource.setDefaultValue(currentName);
         _exchangeSource.setValue(currentName);
      }
      _exchangeSource.setTitle(exchangeSourceTitle());
      _exchangeSource.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {

         @Override
         public boolean onPreferenceChange(Preference preference, Object newValue) {
            _mbwManager.getExchangeRateManager().setCurrentExchangeSourceName(newValue.toString());
            _exchangeSource.setTitle(exchangeSourceTitle());
            return true;
         }
      });

      ListPreference language = (ListPreference) findPreference(Constants.LANGUAGE_SETTING);
      language.setTitle(getLanguageSettingTitle());
      language.setDefaultValue(Locale.getDefault().getLanguage());
      language.setSummary(_mbwManager.getLanguage());
      language.setValue(_mbwManager.getLanguage());

      ImmutableMap<String, String> languageLookup = loadLanguageLookups();
      language.setSummary(languageLookup.get(_mbwManager.getLanguage()));

      language.setEntries(R.array.languages_desc);
      language.setEntryValues(R.array.languages);
      language.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
         @Override
         public boolean onPreferenceChange(Preference preference, Object newValue) {
            String lang = newValue.toString();
            _mbwManager.setLanguage(lang);
            WalletApplication app = (WalletApplication) getApplication();
            app.applyLanguageChange(lang);

            restart();

            return true;
         }
      });

      // Set PIN
      Preference setPin = Preconditions.checkNotNull(findPreference("setPin"));
      setPin.setOnPreferenceClickListener(setPinClickListener);

      // Clear PIN
      updateClearPin();

      // PIN required on startup
      CheckBoxPreference setPinRequiredStartup = (CheckBoxPreference) Preconditions.checkNotNull(findPreference("requirePinOnStartup"));
      setPinRequiredStartup.setOnPreferenceChangeListener(setPinOnStartupClickListener);
      setPinRequiredStartup.setChecked(_mbwManager.getPinRequiredOnStartup());

      // Legacy backup function
      Preference legacyBackup = Preconditions.checkNotNull(findPreference("legacyBackup"));
      legacyBackup.setOnPreferenceClickListener(legacyBackupClickListener);

      // Legacy backup function
      Preference legacyBackupVerify = Preconditions.checkNotNull(findPreference("legacyBackupVerify"));
      legacyBackupVerify.setOnPreferenceClickListener(legacyBackupVerifyClickListener);

      // show bip44 path
      CheckBoxPreference showBip44Path = (CheckBoxPreference) findPreference("showBip44Path");
      showBip44Path.setChecked(_mbwManager.getMetadataStorage().getShowBip44Path());
      showBip44Path.setOnPreferenceClickListener(showBip44PathClickListener);


      // Socks Proxy
      final ListPreference useTor = Preconditions.checkNotNull((ListPreference) findPreference("useTor"));
      useTor.setTitle(getUseTorTitle());

      useTor.setEntries(new String[]{
              getString(R.string.use_https),
              getString(R.string.use_external_tor),
//            getString(R.string.both),
      });

      useTor.setEntryValues(new String[]{
              ServerEndpointType.Types.ONLY_HTTPS.toString(),
              ServerEndpointType.Types.ONLY_TOR.toString(),
              //      ServerEndpointType.Types.HTTPS_AND_TOR.toString(),
      });

      useTor.setValue(_mbwManager.getTorMode().toString());

      useTor.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
         @Override
         public boolean onPreferenceChange(Preference preference, Object newValue) {
            if (newValue.equals(ServerEndpointType.Types.ONLY_TOR.toString())) {
               OrbotHelper obh = new OrbotHelper(SettingsActivity.this);
               if (!obh.isOrbotInstalled()) {
                  obh.promptToInstall(SettingsActivity.this);
               }
            }
            _mbwManager.setTorMode(ServerEndpointType.Types.valueOf((String) newValue));
            useTor.setTitle(getUseTorTitle());
            return true;
         }
      });

//      CheckBoxPreference ledgerDisableTee = (CheckBoxPreference) findPreference("ledgerDisableTee");
//      Preference ledgerSetUnpluggedAID = findPreference("ledgerUnpluggedAID");

//      boolean isTeeAvailable = LedgerTransportTEEProxyFactory.isServiceAvailable(this);
//      if (isTeeAvailable) {
//         ledgerDisableTee.setChecked(_mbwManager.getLedgerManager().getDisableTEE());
//         ledgerDisableTee.setOnPreferenceClickListener(onClickLedgerNotificationDisableTee);
//      } else {
//         PreferenceCategory ledger = (PreferenceCategory) findPreference("ledger");
//         ledger.removePreference(ledgerDisableTee);
//      }

//      ledgerSetUnpluggedAID.setOnPreferenceClickListener(onClickLedgerSetUnpluggedAID);

   }

   @Override
   protected void onResume() {
      showOrHideLegacyBackup();
      _localCurrency.setTitle(localCurrencyTitle());
      super.onResume();
   }

   private ProgressDialog pleaseWait;

   @SuppressWarnings("deprecation")
   private void showOrHideLegacyBackup() {
      List<WalletAccount> accounts = _mbwManager.getWalletManager(false).getSpendingAccounts();
      Preference legacyPref = findPreference("legacyBackup");
      if (legacyPref == null) {
         return; // it was already removed, don't remove it again.
      }

      PreferenceCategory legacyCat = (PreferenceCategory) findPreference("legacy");
      for (WalletAccount account : accounts) {
         if (account instanceof SingleAddressAccount) {
            return; //we have a single address account with priv key, so its fine to show the setting
         }
      }
      //no matching account, hide setting
      legacyCat.removePreference(legacyPref);
   }

   private String getLanguageSettingTitle() {
      String displayed = getResources().getString(R.string.pref_change_language);
      String english = Utils.loadEnglish(R.string.pref_change_language);
      return english.equals(displayed) ? displayed : displayed + " / " + english;
   }

   private ImmutableMap<String, String> loadLanguageLookups() {
      String[] langDesc = getResources().getStringArray(R.array.languages_desc);
      String[] langs = getResources().getStringArray(R.array.languages);

      ImmutableMap.Builder<String, String> b = ImmutableMap.builder();
      for (int i = 0; i < langs.length; i++) {
         String lang = langs[i];
         String desc = langDesc[i];
         b.put(lang, desc);
      }
      return b.build();
   }

   private void restart() {
      Intent running = getIntent();
      finish();
      startActivity(running);
   }

   private String getUseTorTitle() {
      if (_mbwManager.getTorMode() == ServerEndpointType.Types.ONLY_HTTPS) {
         return getResources().getString(R.string.useTorOnlyHttps);
      } else if (_mbwManager.getTorMode() == ServerEndpointType.Types.ONLY_TOR) {
         return getResources().getString(R.string.useTorOnlyExternalTor);
      } else {
         return getResources().getString(R.string.useTorBoth);
      }
   }

   private String localCurrencyTitle() {
      if (_mbwManager.hasFiatCurrency()) {
         String currency = _mbwManager.getFiatCurrency();
         if (_mbwManager.getCurrencyList().size() > 1) {
            //multiple selected, add ...
            currency = currency + "...";
         }
         return getResources().getString(R.string.pref_local_currency_with_currency, currency);
      } else {
         //nothing selected
         return getResources().getString(R.string.pref_no_fiat_selected);
      }
   }

   private String exchangeSourceTitle() {
      String name = _mbwManager.getExchangeRateManager().getCurrentExchangeSourceName();
      if (name == null) {
         name = "";
      }
      return getResources().getString(R.string.pref_exchange_source_with_value, name);
   }

   private String bitcoinDenominationTitle() {
      return getResources().getString(R.string.pref_bitcoin_denomination_with_denomination,
              _mbwManager.getBitcoinDenomination().getAsciiName());
   }

   private String getMinerFeeTitle() {
      return getResources().getString(R.string.pref_miner_fee_title,
              _mbwManager.getMinerFee().getMinerFeeName(this));
   }

   private String getMinerFeeSummary() {
      return getResources().getString(R.string.pref_miner_fee_block_summary,
              Integer.toString(_mbwManager.getMinerFee().getNBlocks()));
   }

   private String getBlockExplorerTitle() {
      return getResources().getString(R.string.block_explorer_title,
              _mbwManager._blockExplorerManager.getBlockExplorer().getTitle());
   }

   private String getBlockExplorerSummary() {
      return getResources().getString(R.string.block_explorer_summary,
              _mbwManager._blockExplorerManager.getBlockExplorer().getTitle());
   }

   @SuppressWarnings("deprecation")
   private void updateClearPin() {
      Preference clearPin = findPreference("clearPin");
      clearPin.setEnabled(_mbwManager.isPinProtected());
      clearPin.setOnPreferenceClickListener(clearPinClickListener);
   }

   @Override
   protected void onDestroy() {
      super.onDestroy();
   }

}
