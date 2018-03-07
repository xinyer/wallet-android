package com.mycelium.wallet.activity.settings;

import android.content.Intent;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import com.mrd.bitlib.util.CoinUtil.Denomination;
import com.mycelium.wallet.Constants;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.common.MinerFee;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.WalletApplication;

import java.util.Locale;

public class SettingsActivity extends PreferenceActivity {

    public static final CharMatcher AMOUNT = CharMatcher.JAVA_DIGIT.or(CharMatcher.anyOf(".,"));

    private final OnPreferenceClickListener showBip44PathClickListener = new OnPreferenceClickListener() {
        public boolean onPreferenceClick(Preference preference) {
            CheckBoxPreference p = (CheckBoxPreference) preference;
            _mbwManager.getMetadataStorage().setShowBip44Path(p.isChecked());
            return true;
        }
    };

    private ListPreference _bitcoinDenomination;
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

        // show bip44 path
        CheckBoxPreference showBip44Path = (CheckBoxPreference) findPreference("showBip44Path");
        showBip44Path.setChecked(_mbwManager.getMetadataStorage().getShowBip44Path());
        showBip44Path.setOnPreferenceClickListener(showBip44PathClickListener);
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

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

}
