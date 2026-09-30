package com.jiminix.liveset;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

public class PlaylistBannerView extends View {
    // Build V0.29
    // Build V0.30
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);

    public PlaylistBannerView(Context c){
        super(c);
        setBackgroundColor(Color.rgb(105,12,18));
    }

    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        int w=MeasureSpec.getSize(widthMeasureSpec);
        int h=Ui.dp(getContext(),42);
        setMeasuredDimension(w,h);
    }

    private float fitText(String text,float maxWidth,float startSize){
        p.setTextSize(startSize);
        while(p.measureText(text)>maxWidth && p.getTextSize()>10f){
            p.setTextSize(p.getTextSize()-1f);
        }
        return p.getTextSize();
    }

    @Override protected void onDraw(Canvas canvas){
        super.onDraw(canvas);
        float w=getWidth();
        float h=getHeight();
        float pad=Math.max(8f,w*0.02f);

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextAlign(Paint.Align.CENTER);

        String main="TS Playlist 2026";
        p.setColor(Color.rgb(255,196,30));
        p.setTextSize(h*0.43f);
        canvas.drawText(main,w/2f,h*0.43f,p);

        String sub="Un pour tous, tous pour la même playlist.";
        p.setColor(Color.WHITE);
        p.setTextSize(h*0.24f);
        canvas.drawText(sub,w/2f,h*0.78f,p);

        p.setTextAlign(Paint.Align.LEFT);
    }
}
