/*
 * Copyright 2011-2015 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.mycelium.spvmodule.dash;

import android.os.Build;
import android.os.Environment;
import android.text.format.DateUtils;

import com.google.common.io.BaseEncoding;

import org.bitcoinj.core.CoinDefinition;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.params.TestNet3Params;
import org.bitcoinj.utils.MonetaryFormat;

import java.io.File;

/**
 * @author Andreas Schildbach
 */
public interface Constants {
    boolean TEST = BuildConfig.APPLICATION_ID.contains("_test");

    /**
     * Network this wallet is on (e.g. testnet or mainnet).
     */
    NetworkParameters NETWORK_PARAMETERS = TEST ? TestNet3Params.get() : MainNetParams.get();

    interface Files {
        String _FILENAME_NETWORK_SUFFIX = NETWORK_PARAMETERS.getId().equals(NetworkParameters.ID_MAINNET) ? "" : "-testnet";

        /**
         * Filename of the wallet.
         */
        String WALLET_FILENAME_PROTOBUF = "wallet-protobuf" + _FILENAME_NETWORK_SUFFIX;

        /**
         * Filename of the automatic key backup (old format, can only be read).
         */
        String WALLET_KEY_BACKUP_BASE58 = "key-backup-base58" + _FILENAME_NETWORK_SUFFIX;

        /**
         * Filename of the automatic wallet backup.
         */
        String WALLET_KEY_BACKUP_PROTOBUF = "key-backup-protobuf" + _FILENAME_NETWORK_SUFFIX;

        /**
         * Path to external storage
         */
        File EXTERNAL_STORAGE_DIR = Environment.getExternalStorageDirectory();

        /**
         * Manual backups go here.
         */
        File EXTERNAL_WALLET_BACKUP_DIR = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);

        /**
         * Filename of the manual key backup (old format, can only be read).
         */
        String EXTERNAL_WALLET_KEY_BACKUP = "dash-wallet-keys" + _FILENAME_NETWORK_SUFFIX;

        /**
         * Filename of the manual wallet backup.
         */
        String EXTERNAL_WALLET_BACKUP = "dash-wallet-backup" + _FILENAME_NETWORK_SUFFIX;

        /**
         * Filename of the block store for storing the chain.
         */
        String BLOCKCHAIN_FILENAME = "blockchain" + _FILENAME_NETWORK_SUFFIX;

        /**
         * Filename of the block checkpoints file.
         */
        String CHECKPOINTS_FILENAME = "checkpoints" + _FILENAME_NETWORK_SUFFIX + ".txt";
    }

    /**
     * Maximum size of backups. Files larger will be rejected.
     */
    long BACKUP_MAX_CHARS = 10000000;

    /**
     * MIME type used for transmitting single transactions.
     */
    String MIMETYPE_TRANSACTION = "application/x-dashtx";

    /**
     * User-agent to use for network access.
     */
    String USER_AGENT = "Mycelium SPV Module Dash";

    char CHAR_THIN_SPACE = '\u2009';

    int HTTP_TIMEOUT_MS = 15 * (int) DateUtils.SECOND_IN_MILLIS;
    int PEER_TIMEOUT_MS = 15 * (int) DateUtils.SECOND_IN_MILLIS;

    long LAST_USAGE_THRESHOLD_JUST_MS = DateUtils.HOUR_IN_MILLIS;
    long LAST_USAGE_THRESHOLD_RECENTLY_MS = 2 * DateUtils.DAY_IN_MILLIS;

    int MEMORY_CLASS_LOWEND = 48;


}
