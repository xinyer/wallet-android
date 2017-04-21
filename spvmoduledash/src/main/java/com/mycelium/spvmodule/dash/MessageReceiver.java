package com.mycelium.spvmodule.dash;

import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;

import com.google.common.collect.Lists;
import com.mycelium.spvmodule.dash.providers.DB.Addresses;
import com.mycelium.spvmodule.dash.util.WalletUtils;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Wallet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;

/**
 * This BroadcastReceiver bridges between other apps and SpvModule. It forwards requests to the
 * SpvService.
 */
public class MessageReceiver extends BroadcastReceiver {
    private static final Logger log = LoggerFactory.getLogger(MessageReceiver.class);

    @Override
    public void onReceive(Context context, Intent intent) {
        long tStart = System.currentTimeMillis();
        Intent clone = (Intent) intent.clone();
        clone.setClass(context, SpvService.class);
        switch (intent.getAction()) {
            case "com.mycelium.wallet.broadcastTransaction":
                clone.setAction(SpvService.ACTION_BROADCAST_TRANSACTION);
                Bundle results = getResultExtras(true);
                byte[] txBytes = intent.getByteArrayExtra("TX");
                Transaction tx = new Transaction(Constants.NETWORK_PARAMETERS, txBytes);
                // this assumes the transaction makes it at least into the service' storage
                // TODO: 9/5/16 make it splice in a round trip to the service to get more solid data on broadcastability.
                results.putString("broadcastTX", tx.getHashAsString());
                break;
            case "com.mycelium.wallet.receiveTransactions":
                // parse call
                String[] addressStrings = intent.getStringArrayExtra("ADDRESSES");
                long creationTime = intent.getLongExtra("CREATION_TIME", 0L);
                ContentResolver cr = context.getContentResolver();
                List<Address> addresses = Lists.newArrayListWithCapacity(addressStrings.length);
                for (String addressString : addressStrings) {
                    Address address = WalletUtils.fromBase58(Constants.NETWORK_PARAMETERS, addressString);
                    addresses.add(address);
                    ContentValues values = new ContentValues();
                    String account = null;
                    Cursor cursor = cr.query(Addresses.CONTENT_URI,
                            new String[]{Addresses.ADDRESS},
                            Addresses.ADDRESS + "=?", new String[]{addressString},
                            null);
                    // insert or update
                    if (cursor == null || cursor.getCount() == 0) {
                        values.put(Addresses.ADDRESS, addressString);
                        values.put(Addresses.CREATION_DATE, creationTime);
                        values.put(Addresses.SYNCED_TO_BLOCK, 0);
                        values.put(Addresses.ACCOUNT, account);
                        cr.insert(Addresses.CONTENT_URI, values);
                    } else {
                        values.put(Addresses.ACCOUNT, account);
                        cr.update(Addresses.CONTENT_URI, values, "_id=?",
                                new String[]{addressString});
                    }
                    cursor.close();
                }
                // register addresses as ours
                Wallet wallet = SpvModuleDashApplication.getWallet();
                wallet.addWatchedAddresses(addresses, creationTime);
                // get all known transactions
                Set<Transaction> transactionSet = wallet.getTransactions(false);
                byte[][] transactions = new byte[transactionSet.size()][];
                int i = 0;
                for (Transaction transaction : transactionSet) {
                    log.debug("received transaction: " + transaction);
                    transactions[i++] = transaction.bitcoinSerialize();
                }
                // report back known transactions
                Intent currentTransactionListIntent = new Intent("com.mycelium.wallet.receivedTransactions");
                currentTransactionListIntent.putExtra("TRANSACTIONS", transactions);
                context.sendBroadcast(currentTransactionListIntent, "com.mycelium.wallet.RECEIVE_TRANSACTIONS");
                // check for new transactions (starting service below)
                break;
        }
        context.startService(clone);
        log.debug("Call took {}ms.", System.currentTimeMillis() - tStart);
    }
}
