package com.jiminix.liveset;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class LiveSongActivity extends AppCompatActivity {
    private Song song; private String setlistId; private int index; private TextView lyrics; private float fontSize=24f;

    @Override protected void onCreate(Bundle b){ super.onCreate(b); getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); fontSize=getSharedPreferences("live_ui",MODE_PRIVATE).getFloat("font",24f); load(); buildUi(); }
    @Override protected void onResume(){ super.onResume(); load(); if(lyrics!=null) lyrics.setText(song==null?"":song.lyrics); }

    private void load(){ setlistId=getIntent().getStringExtra("setlist_id"); index=getIntent().getIntExtra("index",0); song=AppStore.findSong(this,getIntent().getStringExtra("song_id")); if(song==null)finish(); }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.BLACK);
        LinearLayout top=Ui.row(this); Button back=Ui.button(this,"‹"); TextView info=new TextView(this); String meta=song.artist; if(!song.key.isEmpty())meta+=(meta.isEmpty()?"":" · ")+song.key; if(!song.bpm.isEmpty())meta+=(meta.isEmpty()?"":" · ")+song.bpm+" BPM"; if(!song.tuning.isEmpty())meta+=(meta.isEmpty()?"":" · ")+song.tuning; info.setText(song.title+(meta.isEmpty()?"":" · "+meta)); info.setTextColor(Color.WHITE); info.setTypeface(Typeface.DEFAULT_BOLD); Ui.compactHeaderTitle(info,this); Button overview=Ui.button(this,"☰"); Button edit=Ui.button(this,"✎"); Ui.compactHeaderButton(back,this,46); Ui.compactHeaderButton(overview,this,52); Ui.compactHeaderButton(edit,this,52); top.addView(back); top.addView(info); top.addView(overview); top.addView(edit); root.addView(top);
        if(!song.notes.isEmpty()){ TextView notes=new TextView(this); notes.setText("⚡ "+song.notes); notes.setTextColor(Color.rgb(255,193,7)); notes.setTextSize(17); notes.setPadding(Ui.dp(this,16),Ui.dp(this,6),Ui.dp(this,16),Ui.dp(this,10)); root.addView(notes); }
        ScrollView sv=new ScrollView(this); lyrics=new TextView(this); lyrics.setText(song.lyrics.isEmpty()?"Aucune parole enregistrée.":song.lyrics); lyrics.setTextColor(Color.WHITE); lyrics.setTextSize(fontSize); lyrics.setLineSpacing(Ui.dp(this,4),1.08f); lyrics.setPadding(Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,18),Ui.dp(this,80)); lyrics.setTextIsSelectable(true); sv.addView(lyrics); root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        LinearLayout zoom=Ui.row(this); Button minus=Ui.button(this,"A−"); Button play=Ui.button(this,"▶ Écouter"); Button plus=Ui.button(this,"A+"); Ui.weight(minus,1);Ui.weight(play,2);Ui.weight(plus,1);zoom.addView(minus);zoom.addView(play);zoom.addView(plus);root.addView(zoom);
        LinearLayout nav=Ui.row(this); Button prev=Ui.button(this,"◀ Précédent"); Button next=Ui.button(this,"Suivant ▶"); Ui.weight(prev,1);Ui.weight(next,1);nav.addView(prev);nav.addView(next);root.addView(nav);
        back.setOnClickListener(v->finish()); overview.setEnabled(setlistId!=null); overview.setOnClickListener(v->openOverview()); edit.setOnClickListener(v->{Intent i=new Intent(this,EditSongActivity.class);i.putExtra("song_id",song.id);startActivity(i);}); minus.setOnClickListener(v->font(-2)); plus.setOnClickListener(v->font(2)); play.setOnClickListener(v->player()); prev.setOnClickListener(v->navigate(-1)); next.setOnClickListener(v->navigate(1));
        if(setlistId==null){prev.setEnabled(false);next.setEnabled(false);} else { SetListModel sl=AppStore.findSetlist(this,setlistId); if(sl!=null){prev.setEnabled(index>0);next.setEnabled(index<sl.songIds.size()-1);} }
        Ui.applySafeArea(root); setContentView(root);
    }

    private void openOverview(){
        if(setlistId==null)return;
        Intent i=new Intent(this,PlaylistOverviewActivity.class);
        i.putExtra("setlist_id",setlistId);
        i.putExtra("current_index",index);
        startActivity(i);
    }

    private void font(float d){fontSize=Math.max(12f,Math.min(52f,fontSize+d));lyrics.setTextSize(fontSize);getSharedPreferences("live_ui",MODE_PRIVATE).edit().putFloat("font",fontSize).apply();}
    private void player(){if(song.mediaUrl.trim().isEmpty()){Toast.makeText(this,"Ajoute un lien média dans la fiche du morceau.",Toast.LENGTH_SHORT).show();return;}Intent i=new Intent(this,PlayerActivity.class);i.putExtra("title",song.title);i.putExtra("url",song.mediaUrl);startActivity(i);}
    private void navigate(int delta){SetListModel sl=AppStore.findSetlist(this,setlistId);if(sl==null)return;int n=index+delta;if(n<0||n>=sl.songIds.size())return;Intent i=new Intent(this,LiveSongActivity.class);i.putExtra("song_id",sl.songIds.get(n));i.putExtra("setlist_id",sl.id);i.putExtra("index",n);startActivity(i);finish();}
}
