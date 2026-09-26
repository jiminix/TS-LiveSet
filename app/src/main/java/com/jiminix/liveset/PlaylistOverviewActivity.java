package com.jiminix.liveset;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
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

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setlistId=getIntent().getStringExtra("setlist_id");
        currentIndex=getIntent().getIntExtra("current_index",-1);
        setlist=AppStore.findSetlist(this,setlistId);
        if(setlist==null){ finish(); return; }
        buildUi();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(12,12,12));

        LinearLayout head=Ui.row(this);
        Button back=Ui.button(this,"‹");
        TextView title=Ui.title(this,setlist.name);
        title.setTextSize(21);
        Ui.weight(title,1);
        TextView count=new TextView(this);
        count.setText(setlist.songIds.size()+" titres");
        count.setTextColor(Color.LTGRAY);
        count.setTextSize(14);
        count.setPadding(Ui.dp(this,8),0,Ui.dp(this,12),0);
        head.addView(back);
        head.addView(title);
        head.addView(count);
        root.addView(head);

        TextView sub=new TextView(this);
        sub.setText("PLAYLIST COMPLÈTE");
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(13);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(Ui.dp(this,12),0,Ui.dp(this,12),Ui.dp(this,8));
        root.addView(sub);

        ScrollView sv=new ScrollView(this);
        LinearLayout list=new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),Ui.dp(this,20));

        for(int i=0;i<setlist.songIds.size();i++){
            final int pos=i;
            Song s=AppStore.findSong(this,setlist.songIds.get(i));
            if(s==null)continue;

            LinearLayout row=Ui.row(this);
            row.setPadding(Ui.dp(this,6),Ui.dp(this,4),Ui.dp(this,6),Ui.dp(this,4));

            TextView num=new TextView(this);
            num.setText(String.format("%02d",i+1));
            num.setTextSize(16);
            num.setTypeface(Typeface.DEFAULT_BOLD);
            num.setGravity(Gravity.CENTER);
            num.setMinWidth(Ui.dp(this,44));

            TextView song=new TextView(this);
            StringBuilder text=new StringBuilder(s.title);
            String meta="";
            if(!s.key.isEmpty()) meta=s.key;
            if(!s.bpm.isEmpty()) meta+=(meta.isEmpty()?"":" · ")+s.bpm+" BPM";
            if(!meta.isEmpty()) text.append("\n").append(meta);
            song.setText(text.toString());
            song.setTextSize(17);
            song.setPadding(Ui.dp(this,8),Ui.dp(this,9),Ui.dp(this,8),Ui.dp(this,9));
            Ui.weight(song,1);

            if(i==currentIndex){
                num.setTextColor(Color.rgb(255,193,7));
                song.setTextColor(Color.rgb(255,193,7));
                row.setBackgroundColor(Color.rgb(38,38,38));
            }else{
                num.setTextColor(Color.LTGRAY);
                song.setTextColor(Color.WHITE);
            }

            row.addView(num);
            row.addView(song);
            row.setOnClickListener(v->openSong(pos));
            list.addView(row);

            TextView sep=new TextView(this);
            sep.setBackgroundColor(Color.rgb(45,45,45));
            list.addView(sep,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,1)));
        }

        sv.addView(list);
        root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        back.setOnClickListener(v->finish());
        Ui.applySafeArea(root);
        setContentView(root);
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
