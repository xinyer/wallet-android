package com.mycelium.wallet.widget;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.text.SpannableString;
import android.text.style.UnderlineSpan;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.common.base.Strings;
import com.mycelium.wallet.R;
import com.mycelium.wallet.Utils;

//TODO: upgrade to android support v7 >>19.1.0
@SuppressLint("AppCompatCustomView")
public abstract class GenericBlockExplorerLabel extends TextView {
    private void init() {
        this.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        this.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        this.setTypeface(Typeface.MONOSPACE);
    }

    public GenericBlockExplorerLabel(Context context) {
        super(context);
        init();
    }

    public GenericBlockExplorerLabel(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public GenericBlockExplorerLabel(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    abstract protected String getLinkText();

    abstract protected String getFormattedLinkText();

    void update_ui() {
        if (Strings.isNullOrEmpty(getLinkText())) {
            super.setText("");
        } else {
            SpannableString link = new SpannableString(getFormattedLinkText());
            link.setSpan(new UnderlineSpan(), 0, link.length(), 0);
            this.setText(link);
            this.setTextColor(getResources().getColor(R.color.brightblue));
        }
    }

    protected void setHandler() {
        if (!Strings.isNullOrEmpty(getLinkText())) {
            setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    Utils.setClipboardString(getLinkText(), GenericBlockExplorerLabel.this.getContext());
                    Toast.makeText(GenericBlockExplorerLabel.this.getContext(), R.string.copied_to_clipboard, Toast.LENGTH_SHORT).show();
                    return true;
                }
            });

        }
    }
}
