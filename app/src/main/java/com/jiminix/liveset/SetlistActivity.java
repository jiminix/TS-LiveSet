package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class SetlistActivity extends AppCompatActivity {
    private SetListModel setlist;
    private LinearLayout content;
    private TextView title;

    @Override protected void onCreate(Bundle b){ super.onCreate(b); reloadModel(); buildUi(); render(); }
    @Override protected void onResume(){ super.onResume(); reloadModel(); if(content!=null) render(); }

    private void reloadModel(){ setlist=AppStore.findSetlist(this,getIntent().getStringExtra("setlist_id")); if(setlist==null){ finish(); } }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(Color.rgb(18,18,18));
        LinearLayout head=Ui.row(this); Button back=Ui.button(this,"‹"); title=Ui.title(this,setlist.name); title.setTextSize(21); Ui.weight(title,1); Button menu=Ui.button(this,"⋮"); head.addView(back); head.addView(title); head.addView(menu); root.addView(head);
        ScrollView sv=new ScrollView(this); content=new LinearLayout(this); content.setOrientation(LinearLayout.VERTICAL); content.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,80)); sv.addView(content); root.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        LinearLayout bottom=Ui.row(this); Button add=Ui.button(this,"＋ Morceau"); Button live=Ui.button(this,"▶ MODE LIVE"); Ui.weight(add,1); Ui.weight(live,1); bottom.addView(add); bottom.addView(live); root.addView(bottom);
        back.setOnClickListener(v->finish()); add.setOnClickListener(v->chooseSong()); live.setOnClickListener(v->startLive()); menu.setOnClickListener(v->setlistMenu()); setContentView(root);
    }

    private void render(){
        if(setlist==null)return; title.setText(setlist.name); content.removeAllViews();
        if(setlist.songIds.isEmpty()){ TextView e=Ui.title(this,"Setlist vide\n\nAjoute des morceaux depuis ta bibliothèque."); e.setTextSize(17); e.setGravity(Gravity.CENTER); content.addView(e); return; }
        for(int i=0;i<setlist.songIds.size();i++){
            final int pos=i; Song s=AppStore.findSong(this,setlist.songIds.get(i)); if(s==null)continue;
            LinearLayout row=Ui.row(this);
            TextView number=new TextView(this); number.setText(String.format("%02d",i+1)); number.setTextColor(Color.LTGRAY); number.setTextSize(16); number.setGravity(Gravity.CENTER); number.setMinWidth(Ui.dp(this,42)); row.addView(number);
            TextView txt=new TextView(this); String meta=s.artist; if(!s.key.isEmpty())meta+=(meta.isEmpty()?"":" · ")+s.key; if(!s.bpm.isEmpty())meta+=(meta.isEmpty()?"":" · ")+s.bpm+" BPM"; txt.setText(s.title+(meta.isEmpty()?"":"\n"+meta)); txt.setTextColor(Color.WHITE); txt.setTextSize(18); txt.setPadding(Ui.dp(this,6),Ui.dp(this,10),Ui.dp(this,4),Ui.dp(this,10)); Ui.weight(txt,1); row.addView(txt);
            Button play=Ui.button(this,"▶"); Button move=Ui.button(this,"↕"); row.addView(play); row.addView(move); content.addView(row);
            txt.setOnClickListener(v->openLive(pos)); play.setOnClickListener(v->openPlayer(s)); move.setOnClickListener(v->moveMenu(pos,s));
            row.setOnLongClickListener(v->{moveMenu(pos,s);return true;});
        }
    }

    private void chooseSong(){
        List<Song> songs=AppStore.loadSongs(this); if(songs.isEmpty()){ Toast.makeText(this,"Crée d’abord un morceau dans la Bibliothèque.",Toast.LENGTH_LONG).show(); return; }
        String[] names=new String[songs.size()]; for(int i=0;i<songs.size();i++) names[i]=songs.get(i).title+(songs.get(i).artist.isEmpty()?"":" — "+songs.get(i).artist);
        new AlertDialog.Builder(this).setTitle("Ajouter un morceau").setItems(names,(d,which)->{ setlist.songIds.add(songs.get(which).id); AppStore.upsertSetlist(this,setlist); render(); }).show();
    }

    private void moveMenu(int pos,Song song){
        String[] options={"Monter","Descendre","Déplacer à la position…","Retirer de cette setlist"};
        new AlertDialog.Builder(this).setTitle(song.title).setItems(options,(d,w)->{
            if(w==0 && pos>0){ String id=setlist.songIds.remove(pos); setlist.songIds.add(pos-1,id); saveRender(); }
            else if(w==1 && pos<setlist.songIds.size()-1){ String id=setlist.songIds.remove(pos); setlist.songIds.add(pos+1,id); saveRender(); }
            else if(w==2){ EditText e=new EditText(this); e.setInputType(InputType.TYPE_CLASS_NUMBER); e.setHint("1 à "+setlist.songIds.size()); new AlertDialog.Builder(this).setTitle("Nouvelle position").setView(e).setPositiveButton("Déplacer",(x,y)->{ try{ int n=Integer.parseInt(e.getText().toString())-1; n=Math.max(0,Math.min(n,setlist.songIds.size()-1)); String id=setlist.songIds.remove(pos); setlist.songIds.add(n,id); saveRender(); }catch(Exception ignored){} }).setNegativeButton("Annuler",null).show(); }
            else if(w==3){ setlist.songIds.remove(pos); saveRender(); }
        }).show();
    }

    private void setlistMenu(){
        String[] opts={"Renommer","Vider la setlist"}; new AlertDialog.Builder(this).setTitle(setlist.name).setItems(opts,(d,w)->{
            if(w==0){ EditText e=new EditText(this); e.setText(setlist.name); e.selectAll(); new AlertDialog.Builder(this).setTitle("Renommer").setView(e).setPositiveButton("OK",(x,y)->{setlist.name=e.getText().toString().trim();saveRender();}).setNegativeButton("Annuler",null).show(); }
            else new AlertDialog.Builder(this).setTitle("Vider la setlist ?").setPositiveButton("Vider",(x,y)->{setlist.songIds.clear();saveRender();}).setNegativeButton("Annuler",null).show();
        }).show();
    }

    private void saveRender(){ AppStore.upsertSetlist(this,setlist); render(); }
    private void startLive(){ if(setlist.songIds.isEmpty())return; openLive(0); }
    private void openLive(int pos){ Intent i=new Intent(this,LiveSongActivity.class); i.putExtra("song_id",setlist.songIds.get(pos)); i.putExtra("setlist_id",setlist.id); i.putExtra("index",pos); startActivity(i); }
    private void openPlayer(Song s){ if(s.mediaUrl.trim().isEmpty()){Toast.makeText(this,"Aucun lien média pour ce morceau.",Toast.LENGTH_SHORT).show();return;} Intent i=new Intent(this,PlayerActivity.class);i.putExtra("title",s.title);i.putExtra("url",s.mediaUrl);startActivity(i); }
}
