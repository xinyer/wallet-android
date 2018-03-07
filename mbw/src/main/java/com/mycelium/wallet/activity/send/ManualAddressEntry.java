package com.mycelium.wallet.activity.send;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.widget.EditText;

import com.mrd.bitlib.model.Address;
import com.mycelium.wallet.MbwManager;
import com.mycelium.wallet.R;

public class ManualAddressEntry extends Activity {

    public static final String ADDRESS_RESULT_NAME = "address";
    private Address address;
    private String entered;
    private MbwManager mbwManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        this.requestWindowFeature(Window.FEATURE_NO_TITLE);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.manual_entry);

        mbwManager = MbwManager.getInstance(this);
        ((EditText) findViewById(R.id.etAddress)).addTextChangedListener(textWatcher);
        findViewById(R.id.btOk).setOnClickListener(okClickListener);
        ((EditText) findViewById(R.id.etAddress)).setInputType(InputType.TYPE_TEXT_FLAG_AUTO_COMPLETE);

        // Load saved state
        if (savedInstanceState != null) {
            entered = savedInstanceState.getString("entered");
        } else {
            entered = "";
        }

    }

    @Override
    protected void onResume() {
        ((EditText) findViewById(R.id.etAddress)).setText(entered);
        super.onResume();
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putSerializable("entered", findViewById(R.id.etAddress).toString());
    }

    OnClickListener okClickListener = new OnClickListener() {

        @Override
        public void onClick(View arg0) {
            Intent result = new Intent();
            result.putExtra(ADDRESS_RESULT_NAME, address);
            ManualAddressEntry.this.setResult(RESULT_OK, result);
            ManualAddressEntry.this.finish();
        }
    };
    TextWatcher textWatcher = new TextWatcher() {

        @Override
        public void onTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
        }

        @Override
        public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
        }

        @Override
        public void afterTextChanged(Editable editable) {
            entered = editable.toString();
            address = Address.fromString(entered.trim(), mbwManager.getNetwork());

            findViewById(R.id.btOk).setEnabled(address != null);
            boolean addressValid = address != null;
            findViewById(R.id.tvBitcoinAddressInvalid).setVisibility(!addressValid ? View.VISIBLE : View.GONE);
            findViewById(R.id.tvBitcoinAddressValid).setVisibility(addressValid ? View.VISIBLE : View.GONE);
            findViewById(R.id.btOk).setEnabled(addressValid);
        }
    };

}
