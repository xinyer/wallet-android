package com.mycelium.wallet.activity.receive;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.common.base.Preconditions;
import com.mrd.bitlib.model.Address;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;
import com.mycelium.wallet.activity.util.QrImageView;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class ReceiveCoinsActivity extends Activity {

    @BindView(R.id.ivQrCode)
    QrImageView ivQrCode;

    private Address address;

    public static void callMe(Activity currentActivity, Address address) {
        Intent intent = new Intent(currentActivity, ReceiveCoinsActivity.class);
        intent.putExtra("address", address);
        currentActivity.startActivity(intent);
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        this.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.receive_coins_activity);
        ButterKnife.bind(this);

        address = Preconditions.checkNotNull((Address) getIntent().getSerializableExtra("address"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateUi();
    }

    private void updateUi() {
        final String qrText = getBitcoinAddress();
        ivQrCode.setQrCode(qrText);
    }

    private String getBitcoinAddress() {
        return address.toString();
    }

    @OnClick(R.id.btShare)
    public void shareRequest(View view) {
        Intent s = new Intent(android.content.Intent.ACTION_SEND);
        s.setType("text/plain");
        s.putExtra(Intent.EXTRA_SUBJECT, getResources().getString(R.string.bitcoin_address_title));
        s.putExtra(Intent.EXTRA_TEXT, getBitcoinAddress());
        startActivity(Intent.createChooser(s, getResources().getString(R.string.share_bitcoin_address)));
    }

    @OnClick(R.id.btnClipboard)
    public void copyToClipboard(View view) {
        String text = getBitcoinAddress();
        Utils.setClipboardString(text, this);
        Toast.makeText(this, R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
    }
}
