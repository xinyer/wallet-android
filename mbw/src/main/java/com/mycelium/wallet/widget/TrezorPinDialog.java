package com.mycelium.wallet.widget;

import android.content.Context;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.google.common.base.Strings;
import com.mycelium.wallet.R;


public class TrezorPinDialog extends PinDialog {
    private TextView pinDisp;

    public void setOnPinValid(OnPinEntered _onPinValid) {
        this.onPinValid = _onPinValid;
    }

    public TrezorPinDialog(Context context, boolean hidden) {
        super(context, hidden, true);
    }

    @Override
    protected void loadLayout() {
        setContentView(R.layout.enter_trezor_pin_dialog);
    }

    @Override
    protected void initPinPad() {
        pinDisp = (TextView) findViewById(R.id.pin_display);

        findViewById(R.id.pin_button0).setVisibility(View.INVISIBLE);

        // reorder the Buttons for the trezor PIN-entry (like a NUM-Pad)
        buttons.add(((Button) findViewById(R.id.pin_button7)));
        buttons.add(((Button) findViewById(R.id.pin_button8)));
        buttons.add(((Button) findViewById(R.id.pin_button9)));
        buttons.add(((Button) findViewById(R.id.pin_button4)));
        buttons.add(((Button) findViewById(R.id.pin_button5)));
        buttons.add(((Button) findViewById(R.id.pin_button6)));
        buttons.add(((Button) findViewById(R.id.pin_button1)));
        buttons.add(((Button) findViewById(R.id.pin_button2)));
        buttons.add(((Button) findViewById(R.id.pin_button3)));

        btnClear = (Button) findViewById(R.id.pin_clr);
        btnBack = (Button) findViewById(R.id.pin_back);
        btnBack.setText("OK");

        enteredPin = "";

        int cnt = 0;
        for (Button b : buttons) {
            final int akCnt = cnt;
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    addDigit(String.valueOf(akCnt + 1));
                }
            });
            b.setText("\u2022");  // unicode "bullet"
            cnt++;
        }

        btnBack.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                acceptPin();
            }
        });

        btnClear.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearDigits();
                updatePinDisplay();
            }
        });
    }

    @Override
    protected void updatePinDisplay() {
        pinDisp.setText(Strings.repeat("\u25CF  ", enteredPin.length())); // Unicode Character 'BLACK CIRCLE'
        checkPin();
    }


    @Override
    protected void checkPin() {
        if (enteredPin.length() >= 9) {
            acceptPin();
        }
    }
}

