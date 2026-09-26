package com.jiminix.liveset;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.View;

public class PlaylistBannerView extends View {
    private final Paint p=new Paint(Paint.ANTI_ALIAS_FLAG);

    public PlaylistBannerView(Context c){
        super(c);
        setBackgroundColor(Color.BLACK);
    }

    @Override protected void onMeasure(int widthMeasureSpec,int heightMeasureSpec){
        int w=MeasureSpec.getSize(widthMeasureSpec);
        int h=Math.max(Ui.dp(getContext(),92),Math.round(w*0.22f));
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

        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD_ITALIC));
        String main="Ts PLAYLIST manager";
        float mainSize=fitText(main,w-pad*2,h*0.38f);

        float baseY=h*0.53f;

        // Blue offset/shadow for the 3D look.
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(20,20,180));
        p.setTextSize(mainSize);
        canvas.drawText(main,pad+3,baseY+5,p);

        // Dark blue outline.
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(2f,mainSize*0.055f));
        p.setColor(Color.rgb(20,35,170));
        canvas.drawText(main,pad,baseY,p);

        // Yellow/orange face.
        p.setStyle(Paint.Style.FILL);
        p.setColor(Color.rgb(255,196,30));
        canvas.drawText(main,pad,baseY,p);

        // Small white signature on the right.
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setColor(Color.WHITE);
        String signature="made with ChatGPT";
        float sigSize=fitText(signature,w*0.34f,h*0.115f);
        p.setTextSize(sigSize);
        float sigX=pad+Math.max(2f,w*0.02f);
        canvas.drawText(signature,sigX,h*0.69f,p);

        // Subtitle.
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD_ITALIC));
        p.setColor(Color.WHITE);
        String sub="La playlist préférée des grands-pères";
        float subSize=fitText(sub,w-pad*2,h*0.23f);
        p.setTextSize(subSize);
        canvas.drawText(sub,pad,h*0.88f,p);
    }
}
