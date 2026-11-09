package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Private, on-device stage annotations. Never written into Song or cloud sync. */
public final class PrivateStageIcons {
    private PrivateStageIcons() {}
    private static SharedPreferences prefs(Context c) { return c.getSharedPreferences("private_stage_icons_v1", Context.MODE_PRIVATE); }
    private static String key(String listId, String songId, int part) { return listId + "/" + songId + "/" + part; }
    public static int flags(Context c, String listId, String songId, int part, int position) {
        String k=key(listId,songId,part);
        SharedPreferences p=prefs(c);
        if(!p.contains(k) && listId!=null && songId!=null) {
            int v=0;
            // Default assignments are based on playlist positions, and stored by song ID thereafter.
            if(part==0) {
                int n=position+1;
                if(n==3 || n==10 || n==15 || n==19 || n==22 || n==27) v|=1;
                if(n==16) v|=2;
            } else if(position==30) {
                if(part==5) v|=1;
                if(part==1 || part==3 || part==6) v|=2;
            }
            // Seed only the TS 2026 playlist, not unrelated playlists.
            // The caller passes position=-1 when automatic assignments are inappropriate.
            if(position>=0 && v!=0) p.edit().putInt(k,v).apply();
            return v;
        }
        return p.getInt(k,0);
    }
    public static void show(Context c, LinearLayout target, int flags, int size) {
        if((flags&1)!=0) {
            ImageView acoustic=new ImageView(c);
            acoustic.setImageResource(R.drawable.ic_acoustic_000);
            acoustic.setAdjustViewBounds(true);
            target.addView(acoustic,new LinearLayout.LayoutParams(dp(c,size),dp(c,size)));
        }
        if((flags&2)!=0) addText(c,target,"🎸",size);
        if((flags&4)!=0) addText(c,target,"🎹",size);
        if((flags&8)!=0) addText(c,target,prefs(c).getString("num1","12"),size);
        if((flags&16)!=0) {
            TextView n=addText(c,target,prefs(c).getString("num2","03"),size);
            n.setTextColor(Color.RED);
        }
    }
    private static TextView addText(Context c,LinearLayout row,String s,int size) {
        TextView t=new TextView(c);
        t.setText(s); t.setTextSize(Math.max(10,size-2)); t.setGravity(Gravity.CENTER);
        t.setTextColor(Color.WHITE); t.setTypeface(Typeface.DEFAULT_BOLD);
        row.addView(t,new LinearLayout.LayoutParams(-2,dp(c,size)));
        return t;
    }
    private static int dp(Context c,int v) { return Math.round(v*c.getResources().getDisplayMetrics().density); }
    public static void edit(Context c,String listId,String songId,int part,int position,Runnable refresh) {
        int f=flags(c,listId,songId,part,position);
        String[] labels={"Acoustique Martin 000","Guitare électrique","Clavier","Chiffre blanc","Chiffre rouge"};
        boolean[] checked=new boolean[5];
        for(int i=0;i<5;i++) checked[i]=(f&(1<<i))!=0;
        LinearLayout numbers=new LinearLayout(c);
        numbers.setOrientation(LinearLayout.VERTICAL);
        numbers.setPadding(dp(c,16),0,dp(c,16),0);
        EditText first=new EditText(c);
        first.setHint("Chiffre blanc (00–99)");
        first.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        first.setText(prefs(c).getString("num1","12"));
        EditText second=new EditText(c);
        second.setHint("Chiffre rouge (00–99)");
        second.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        second.setText(prefs(c).getString("num2","03"));
        numbers.addView(first); numbers.addView(second);
        new AlertDialog.Builder(c).setTitle("Icônes du titre")
            .setView(numbers)
            .setMultiChoiceItems(labels,checked,(dialog,which,on)->checked[which]=on)
            .setPositiveButton("Enregistrer",(dialog,which)->{
                int next=0; for(int i=0;i<5;i++) if(checked[i])next|=1<<i;
                String a=first.getText().toString().replaceAll("[^0-9]","");
                String b=second.getText().toString().replaceAll("[^0-9]","");
                if(a.length()>2)a=a.substring(0,2);
                if(b.length()>2)b=b.substring(0,2);
                prefs(c).edit().putInt(key(listId,songId,part),next)
                    .putString("num1",a).putString("num2",b).apply();
                refresh.run();
            }).setNegativeButton("Annuler",null).show();
    }
}
