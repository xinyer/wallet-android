package com.mycelium.spvmodule.dash;

import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.mycelium.spvmodule.dash.util.WalletUtils;

import org.bitcoinj.core.Address;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.InsufficientMoneyException;
import org.bitcoinj.core.Wallet;

public class MainActivity extends AppCompatActivity implements View.OnClickListener, View.OnLongClickListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                // delayed start so that UI has enough time to initialize
                ((SpvModuleDashApplication) getApplication()).startBlockchainService(true);
            }
        }, 1000);

        init();
    }

    private void init() {
        findViewById(R.id.btn1).setOnClickListener(this);
        findViewById(R.id.btn2).setOnLongClickListener(this);
    }

    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.btn1:
                showInfo();
                break;
        }
    }

    @Override
    public boolean onLongClick(View view) {
        int id = view.getId();
        switch (id) {
            case R.id.btn2:
                sendSomeCoins();
                return true;
        }
        return false;
    }

    private void showInfo() {
        Wallet wallet = SpvModuleDashApplication.getWallet();
        Log.i(TAG, "description: " + wallet.getDescription());
        Log.i(TAG, "balance: " + wallet.getBalance());
        Log.i(TAG, "fresh receive address: " + wallet.freshReceiveAddress());
        Log.i(TAG, "watched addresses: " + wallet.getWatchedAddresses().size());
        Log.i(TAG, "issued receive addresses: " + wallet.getIssuedReceiveAddresses().size());
    }

    private void sendSomeCoins() {
        Wallet wallet = SpvModuleDashApplication.getWallet();
        Address address = WalletUtils.fromBase58(Constants.NETWORK_PARAMETERS, "Xs7Vpu7qQsTsWaJSPzvxhit48GueyBQ2xB");
        Coin value = Coin.valueOf(100000);
        Wallet.SendRequest sendRequest = Wallet.SendRequest.to(address, value);
        Log.i(TAG, "sending " + value);
        try {
            wallet.sendCoins(sendRequest);
        } catch (InsufficientMoneyException e) {
            Log.w(TAG, e.getMessage());
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }
}
