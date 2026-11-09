package com.jiminix.liveset;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

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

    public static void compactHeaderTitle(TextView v, Context c) {
        v.setSingleLine(true);
        v.setEllipsize(android.text.TextUtils.TruncateAt.END);
        v.setGravity(android.view.Gravity.CENTER_VERTICAL);
        v.setTextSize(18);
        v.setPadding(dp(c,6),0,dp(c,6),0);
        v.setLayoutParams(new LinearLayout.LayoutParams(0,dp(c,52),1));
    }

    public static void compactHeaderButton(Button b, Context c, int widthDp) {
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(c,4),0,dp(c,4),0);
        b.setLayoutParams(new LinearLayout.LayoutParams(dp(c,widthDp),dp(c,48)));
    }

    public static void applySafeArea(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, windowInsets) -> {
            Insets bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            int extra = dp(v.getContext(), 8);
            v.setPadding(
                Math.max(v.getPaddingLeft(), bars.left),
                Math.max(v.getPaddingTop(), bars.top + extra),
                Math.max(v.getPaddingRight(), bars.right),
                Math.max(v.getPaddingBottom(), bars.bottom + extra)
            );
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
    }

    // Low-contrast neon watermark used across Manager screens.
    public static void applyTequilaBackground(View view) {
        view.setBackground(new android.graphics.drawable.Drawable() {
            final android.graphics.Paint paint=new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
            @Override public void draw(android.graphics.Canvas canvas) {
                android.graphics.Rect b=getBounds();
                canvas.drawColor(Color.rgb(13,15,21));
                float w=b.width(),h=b.height();
                canvas.save();
                canvas.translate(b.left+w/2f,b.top+h*.49f);
                float scale=Math.min(w/900f,h/1300f);
                canvas.scale(scale,scale);
                paint.setStyle(android.graphics.Paint.Style.STROKE);
                paint.setStrokeWidth(12);
                paint.setColor(Color.argb(75,255,0,222));
                canvas.drawOval(-420,-360,420,300,paint);
                paint.setStrokeWidth(6);
                paint.setColor(Color.argb(82,40,230,255));
                canvas.drawOval(-390,-345,390,285,paint);
                paint.setStyle(android.graphics.Paint.Style.FILL);
                paint.setTextAlign(android.graphics.Paint.Align.CENTER);
                paint.setTypeface(Typeface.create("sans-serif-light",Typeface.NORMAL));
                paint.setTextSize(132);
                paint.setColor(Color.argb(95,255,36,230));
                canvas.drawText("TEQUILA",0,-80,paint);
                paint.setTypeface(Typeface.create("cursive",Typeface.ITALIC));
                paint.setTextSize(115);
                paint.setColor(Color.argb(105,50,225,255));
                canvas.drawText("ungroove",0,55,paint);
                paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
                paint.setTextSize(40);
                canvas.drawText("COVERS 80 | 90 | 00'S",0,150,paint);
                paint.setTypeface(Typeface.create("sans-serif-black",Typeface.BOLD_ITALIC));
                paint.setTextSize(138);
                canvas.drawText("MANAGER",0,490,paint);
                canvas.restore();
            }
            @Override public void setAlpha(int alpha) {}
            @Override public void setColorFilter(android.graphics.ColorFilter filter) {}
            @Override public int getOpacity(){return android.graphics.PixelFormat.OPAQUE;}
        });
    }
}
