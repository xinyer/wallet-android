package com.mycelium.wallet.widget;

import android.app.Activity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import com.mycelium.wallet.R;

import java.math.BigDecimal;

public class NumberEntry {

   private static final int DEL = -1;
   private static final int DOT = -2;
   private static final int MAX_DIGITS_BEFORE_DOT = 9;

   public interface NumberEntryListener {
      void onEntryChanged(String entry, boolean wasSet);
   }

   private NumberEntryListener _listener;
   private LinearLayout _llNumberEntry;
   private String _entry;
   private int _maxDecimals;

   public NumberEntry(int maxDecimals, NumberEntryListener listener, Activity parent) {
      this(maxDecimals, listener, parent, "");
   }

   public NumberEntry(int maxDecimals, NumberEntryListener listener, Activity parent, String text) {
      if (text.length() != 0) {
         try {
            text = new BigDecimal(text).toPlainString();
         } catch (Exception e) {
            text = "";
         }
      }
      _entry = text;
      _maxDecimals = maxDecimals;
      _listener = listener;
      _llNumberEntry = (LinearLayout) parent.findViewById(R.id.llNumberEntry);
      setClickListener((Button) _llNumberEntry.findViewById(R.id.btOne), 1);
      setClickListener((Button) _llNumberEntry.findViewById(R.id.btTwo), 2);
      setClickListener((Button) _llNumberEntry.findViewById(R.id.btThree), 3);
      setClickListener((Button) _llNumberEntry.findViewById(R.id.btFour), 4);
      setClickListener((Button) _llNumberEntry.findViewById(R.id.btFive), 5);
      setClickListener((Button) _llNumberEntry.findViewById(R.id.btSix), 6);
      setClickListener((Button) _llNumberEntry.findViewById(R.id.btSeven), 7);
      setClickListener((Button) _llNumberEntry.findViewById(R.id.btEight), 8);
      setClickListener((Button) _llNumberEntry.findViewById(R.id.btNine), 9);
      if (_maxDecimals > 0) {
         setClickListener((Button) _llNumberEntry.findViewById(R.id.btDot), DOT);
      } else{
         ((Button) _llNumberEntry.findViewById(R.id.btDot)).setText("");
      }
      setClickListener((Button) _llNumberEntry.findViewById(R.id.btZero), 0);
      setClickListener((Button) _llNumberEntry.findViewById(R.id.btDel), DEL);

      _llNumberEntry.findViewById(R.id.btDel).setOnLongClickListener(new View.OnLongClickListener() {
         @Override
         public boolean onLongClick(View v) {
            _entry = "";
            _listener.onEntryChanged(_entry, false);
            return true;
         }
      });
   }

   private void clicked(int digit) {
      if (digit == DEL) {
         // Delete Digit
         if (_entry.length() == 0) {
            return;
         }
         _entry = _entry.substring(0, _entry.length() - 1);
      } else if (digit == DOT) {
         // Do we already have a dot?
         if (hasDot()) {
            return;
         }
         if (_maxDecimals == 0) {
            return;
         }
         if (_entry.length() == 0) {
            _entry = "0.";
         } else {
            _entry = _entry + '.';
         }
      } else {
         // Append Digit
         if (digit == 0 && _entry.equals("0")) {
            // Only one leading zero
            return;
         }
         if (hasDot()) {
            if (decimalsAfterDot() >= _maxDecimals) {
               // too many decimals
               return;
            }
         } else {
            if (decimalsBeforeDot() >= MAX_DIGITS_BEFORE_DOT) {
               return;
            }
         }
         _entry = _entry + (digit);
      }
      _listener.onEntryChanged(_entry, false);
   }

   private boolean hasDot() {
      return _entry.indexOf('.') != -1;
   }

   private int decimalsAfterDot() {
      int dotIndex = _entry.indexOf('.');
      if (dotIndex == -1) {
         return 0;
      }
      return _entry.length() - dotIndex - 1;
   }

   private int decimalsBeforeDot() {
      int dotIndex = _entry.indexOf('.');
      if (dotIndex == -1) {
         return _entry.length();
      }
      return dotIndex;
   }

   public String getEntry() {
      return _entry;
   }

   public void setEntry(BigDecimal number, int maxDecimals) {
      _maxDecimals = maxDecimals;
      if (number == null || number.compareTo(BigDecimal.ZERO) == 0) {
         _entry = "";
      } else {
         _entry = number.setScale(_maxDecimals, BigDecimal.ROUND_HALF_DOWN).stripTrailingZeros().toPlainString();
      }
      _listener.onEntryChanged(_entry, true);
   }

   public BigDecimal getEntryAsBigDecimal() {
      if (_entry.length() == 0) {
         return BigDecimal.ZERO;
      }
      if (_entry.equals("0.")) {
         return BigDecimal.ZERO;
      }
      try {
         return new BigDecimal(_entry);
      } catch (NumberFormatException e) {
         return BigDecimal.ZERO;
      }
   }

   private void setClickListener(Button button, final int digit) {
      button.setOnClickListener(new android.view.View.OnClickListener() {

         @Override
         public void onClick(View v) {
            clicked(digit);
         }
      });

   }

   public void setEnabled(boolean enabled) {
      _llNumberEntry.findViewById(R.id.btOne).setEnabled(enabled);
      _llNumberEntry.findViewById(R.id.btTwo).setEnabled(enabled);
      _llNumberEntry.findViewById(R.id.btThree).setEnabled(enabled);
      _llNumberEntry.findViewById(R.id.btFour).setEnabled(enabled);
      _llNumberEntry.findViewById(R.id.btFive).setEnabled(enabled);
      _llNumberEntry.findViewById(R.id.btSix).setEnabled(enabled);
      _llNumberEntry.findViewById(R.id.btSeven).setEnabled(enabled);
      _llNumberEntry.findViewById(R.id.btEight).setEnabled(enabled);
      _llNumberEntry.findViewById(R.id.btNine).setEnabled(enabled);
      _llNumberEntry.findViewById(R.id.btDot).setEnabled(enabled);
      _llNumberEntry.findViewById(R.id.btZero).setEnabled(enabled);
      _llNumberEntry.findViewById(R.id.btDel).setEnabled(enabled);
   }

}
