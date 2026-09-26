package com.jiminix.liveset;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class EditSongActivity extends AppCompatActivity {
    private Song song;
    private EditText title, artist, key, bpm, tuning, capo, duration, singer, guitar, notes, media, lyrics;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id=getIntent().getStringExtra("song_id");
        song=id==null?new Song():AppStore.findSong(this,id);
        if(song==null) song=new Song();
        buildUi(); fill();
    }

    private EditText field(LinearLayout root,String hint){
        EditText e=new EditText(this); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); e.setSingleLine(true);
        root.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)); return e;
    }

    private void buildUi(){
        LinearLayout outer=new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setBackgroundColor(Color.rgb(18,18,18));
        TextView h=Ui.title(this,"Modifier le morceau"); outer.addView(h);
        ScrollView sv=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),Ui.dp(this,30)); sv.addView(root);
        title=field(root,"Titre"); artist=field(root,"Artiste");
        LinearLayout r1=Ui.row(this); key=mini("Tonalité",r1); bpm=mini("BPM",r1); capo=mini("Capo",r1); root.addView(r1);
        tuning=field(root,"Accordage"); duration=field(root,"Durée"); singer=field(root,"Chanteur / chanteuse"); guitar=field(root,"Guitare / instrument");
        notes=field(root,"Notes live : intro, fin, départ…"); media=field(root,"Lien YouTube ou autre média");
        TextView lh=Ui.title(this,"Paroles / structure"); lh.setTextSize(18); root.addView(lh);
        lyrics=new EditText(this); lyrics.setHint("INTRO\n...\n\nCOUPLET 1\n...\n\nREFRAIN\n..."); lyrics.setGravity(android.view.Gravity.TOP); lyrics.setMinLines(14); lyrics.setTextColor(Color.WHITE); lyrics.setHintTextColor(Color.GRAY); lyrics.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(lyrics,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,360)));
        Button arrange=Ui.button(this,"↕ Réarranger les blocs"); arrange.setOnClickListener(v->rearrangeBlocks()); root.addView(arrange);
        outer.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        LinearLayout actions=Ui.row(this); Button cancel=Ui.button(this,"Annuler"); Button save=Ui.button(this,"Enregistrer"); Ui.weight(cancel,1); Ui.weight(save,1); actions.addView(cancel); actions.addView(save); outer.addView(actions);
        cancel.setOnClickListener(v->finish()); save.setOnClickListener(v->save()); Ui.applySafeArea(outer); setContentView(outer);
    }

    private EditText mini(String hint,LinearLayout row){ EditText e=new EditText(this); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); e.setSingleLine(true); Ui.weight(e,1); row.addView(e); return e; }

    private void fill(){ title.setText(song.title); artist.setText(song.artist); key.setText(song.key); bpm.setText(song.bpm); tuning.setText(song.tuning); capo.setText(song.capo); duration.setText(song.duration); singer.setText(song.singer); guitar.setText(song.guitar); notes.setText(song.notes); media.setText(song.mediaUrl); lyrics.setText(song.lyrics); }

    private void save(){
        song.title=title.getText().toString().trim(); song.artist=artist.getText().toString().trim(); song.key=key.getText().toString().trim(); song.bpm=bpm.getText().toString().trim(); song.tuning=tuning.getText().toString().trim(); song.capo=capo.getText().toString().trim(); song.duration=duration.getText().toString().trim(); song.singer=singer.getText().toString().trim(); song.guitar=guitar.getText().toString().trim(); song.notes=notes.getText().toString().trim(); song.mediaUrl=media.getText().toString().trim(); song.lyrics=lyrics.getText().toString();
        if(song.title.isEmpty()){ title.setError("Titre obligatoire"); return; }
        AppStore.upsertSong(this,song); finish();
    }

    private void rearrangeBlocks(){
        String raw=lyrics.getText().toString().trim(); if(raw.isEmpty()) return;
        List<String> blocks=new ArrayList<>(Arrays.asList(raw.split("\\n\\s*\\n")));
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));
        ScrollView scroll=new ScrollView(this); scroll.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Réarranger les paroles").setView(scroll).setPositiveButton("Terminer",null).setNegativeButton("Annuler",null).create();
        Runnable rebuild=new Runnable(){ public void run(){
            box.removeAllViews();
            for(int i=0;i<blocks.size();i++){
                final int pos=i; LinearLayout row=Ui.row(EditSongActivity.this); TextView t=new TextView(EditSongActivity.this); String preview=blocks.get(i).replace('\n',' '); if(preview.length()>55)preview=preview.substring(0,55)+"…"; t.setText((i+1)+". "+preview); t.setTextColor(Color.WHITE); t.setTextSize(16); Ui.weight(t,1);
                Button up=Ui.button(EditSongActivity.this,"↑"); Button down=Ui.button(EditSongActivity.this,"↓"); row.addView(t); row.addView(up); row.addView(down); box.addView(row);
                up.setEnabled(i>0); down.setEnabled(i<blocks.size()-1);
                up.setOnClickListener(v->{ String x=blocks.remove(pos); blocks.add(pos-1,x); run(); });
                down.setOnClickListener(v->{ String x=blocks.remove(pos); blocks.add(pos+1,x); run(); });
                t.setOnClickListener(v->{ EditText e=new EditText(EditSongActivity.this); e.setText(blocks.get(pos)); e.setMinLines(5); new AlertDialog.Builder(EditSongActivity.this).setTitle("Modifier le bloc").setView(e).setPositiveButton("OK",(d,w)->{blocks.set(pos,e.getText().toString()); run();}).setNegativeButton("Annuler",null).show(); });
            }
        }};
        dialog.setOnShowListener(x->{ dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ lyrics.setText(String.join("\n\n",blocks)); dialog.dismiss(); }); });
        rebuild.run(); dialog.show();
    }
}
