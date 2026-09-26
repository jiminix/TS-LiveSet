package com.jiminix.liveset;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class Ui {
    public static int dp(Context c, int v) { return (int)(v * c.getResources().getDisplayMetrics().density + 0.5f); }

    public static TextView title(Context c, String t) {
        TextView v = new TextView(c);
        v.setText(t);
        v.setTextSize(24);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setTextColor(Color.WHITE);
        v.setPadding(dp(c,16), dp(c,14), dp(c,16), dp(c,10));
        return v;
    }

    public static Button button(Context c, String t) {
        Button b = new Button(c);
        b.setText(t);
        b.setAllCaps(false);
        b.setMinHeight(dp(c,48));
        return b;
    }

    public static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        r.setPadding(dp(c,8), dp(c,4), dp(c,8), dp(c,4));
        r.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return r;
    }

    public static void weight(View v, float w) {
        v.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w));
    }
}
