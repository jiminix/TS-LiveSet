package com.jiminix.liveset;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class PlaylistOverviewActivity extends AppCompatActivity {
    private String setlistId;
    private SetListModel setlist;
    private int currentIndex=-1;
    private boolean compact=true;
    private LinearLayout root;
    private LinearLayout list;
    private Button modeButton;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setlistId=getIntent().getStringExtra("setlist_id");
        currentIndex=getIntent().getIntExtra("current_index",-1);
        setlist=AppStore.findSetlist(this,setlistId);
        if(setlist==null){ finish(); return; }
        compact=getSharedPreferences("playlist_view",MODE_PRIVATE).getBoolean("compact",true);
        buildUi();
    }

    private void buildUi(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10,10,10));

        LinearLayout head=Ui.row(this);
        Button back=Ui.button(this,"‹");
        TextView title=Ui.title(this,setlist.name);
        title.setTextSize(20);
        Ui.weight(title,1);

        modeButton=Ui.button(this, compact ? "Détaillé" : "Compact");
        TextView count=new TextView(this);
        count.setText(setlist.songIds.size()+" titres");
        count.setTextColor(Color.LTGRAY);
        count.setTextSize(13);
        count.setPadding(Ui.dp(this,8),0,Ui.dp(this,10),0);

        head.addView(back);
        head.addView(title);
        head.addView(modeButton);
        head.addView(count);
        root.addView(head);

        TextView sub=new TextView(this);
        sub.setText(compact ? "PLAYLIST — AFFICHAGE COMPACT" : "PLAYLIST — AFFICHAGE DÉTAILLÉ");
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),Ui.dp(this,5));
        sub.setTag("sub");
        root.addView(sub);

        ScrollView sv=new ScrollView(this);
        list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this,6),0,Ui.dp(this,6),Ui.dp(this,16));
        sv.addView(list);
        root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        back.setOnClickListener(v->finish());
        modeButton.setOnClickListener(v->toggleMode());

        renderList();
        Ui.applySafeArea(root);
        setContentView(root);
    }

    private void toggleMode(){
        compact=!compact;
        getSharedPreferences("playlist_view",MODE_PRIVATE).edit().putBoolean("compact",compact).apply();
        modeButton.setText(compact ? "Détaillé" : "Compact");
        TextView sub=root.findViewWithTag("sub");
        if(sub!=null)sub.setText(compact ? "PLAYLIST — AFFICHAGE COMPACT" : "PLAYLIST — AFFICHAGE DÉTAILLÉ");
        renderList();
    }

    private void renderList(){
        list.removeAllViews();

        for(int i=0;i<setlist.songIds.size();i++){
            final int pos=i;
            Song s=AppStore.findSong(this,setlist.songIds.get(i));
            if(s==null)continue;

            LinearLayout row=Ui.row(this);
            row.setPadding(Ui.dp(this,4),compact?Ui.dp(this,1):Ui.dp(this,4),Ui.dp(this,4),compact?Ui.dp(this,1):Ui.dp(this,4));

            TextView num=new TextView(this);
            num.setText(String.format("%02d",i+1));
            num.setTextSize(compact?14:16);
            num.setTypeface(Typeface.DEFAULT_BOLD);
            num.setGravity(Gravity.CENTER);
            num.setMinWidth(Ui.dp(this,38));

            TextView song=new TextView(this);
            song.setTextSize(compact?15:17);
            song.setTypeface(Typeface.DEFAULT_BOLD);
            song.setSingleLine(compact);
            song.setEllipsize(compact ? android.text.TextUtils.TruncateAt.END : null);
            song.setPadding(Ui.dp(this,5),compact?Ui.dp(this,5):Ui.dp(this,9),Ui.dp(this,5),compact?Ui.dp(this,5):Ui.dp(this,9));
            Ui.weight(song,1);

            TextView bpm=new TextView(this);
            bpm.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            bpm.setTextSize(compact?14:15);
            bpm.setTypeface(Typeface.DEFAULT_BOLD);
            bpm.setMinWidth(Ui.dp(this,68));
            bpm.setPadding(Ui.dp(this,4),0,Ui.dp(this,8),0);

            if(compact){
                song.setText(s.title);
                bpm.setText(s.bpm.isEmpty() ? "" : s.bpm);
            }else{
                StringBuilder text=new StringBuilder(s.title);
                String meta="";
                if(!s.artist.isEmpty())meta=s.artist;
                if(!s.key.isEmpty())meta+=(meta.isEmpty()?"":" · ")+s.key;
                if(!s.tuning.isEmpty())meta+=(meta.isEmpty()?"":" · ")+s.tuning;
                song.setText(text.toString()+(meta.isEmpty()?"":"\n"+meta));
                bpm.setText(s.bpm.isEmpty() ? "" : s.bpm+" BPM");
            }

            if(i==currentIndex){
                num.setTextColor(Color.rgb(255,193,7));
                song.setTextColor(Color.rgb(255,193,7));
                bpm.setTextColor(Color.rgb(255,193,7));
                row.setBackgroundColor(Color.rgb(38,38,38));
            }else{
                num.setTextColor(Color.LTGRAY);
                song.setTextColor(Color.WHITE);
                bpm.setTextColor(Color.WHITE);
            }

            row.addView(num);
            row.addView(song);
            row.addView(bpm);
            row.setOnClickListener(v->openSong(pos));
            list.addView(row);

            View sep=new View(this);
            sep.setBackgroundColor(Color.rgb(38,38,38));
            list.addView(sep,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,1)));
        }
    }

    private void openSong(int pos){
        if(pos<0 || pos>=setlist.songIds.size())return;
        Intent i=new Intent(this,LiveSongActivity.class);
        i.putExtra("song_id",setlist.songIds.get(pos));
        i.putExtra("setlist_id",setlist.id);
        i.putExtra("index",pos);
        startActivity(i);
        finish();
    }
}
