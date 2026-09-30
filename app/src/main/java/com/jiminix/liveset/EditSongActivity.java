package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
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
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.json.JSONArray;
import org.json.JSONObject;

public class EditSongActivity extends AppCompatActivity {
    // V0.62 compact lyrics: remove empty lines on web import, paste and save
    // V0.63 fill key, BPM and duration from song lookup when available
    private Song song;
    private String targetSetlistId;
    private EditText title, artist, key, bpm, tuning, capo, duration, singer, guitar, notes, media, lyrics;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String id=getIntent().getStringExtra("song_id");
        targetSetlistId=getIntent().getStringExtra("target_setlist_id");
        song=id==null?new Song():AppStore.findSong(this,id);
        if(song==null) song=new Song();
        buildUi();
        fill();
        applyPrefill();
    }

    private EditText field(LinearLayout root,String hint){
        EditText e=new EditText(this); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); e.setSingleLine(true);
        root.addView(e,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT)); return e;
    }

    private void buildUi(){
        LinearLayout outer=new LinearLayout(this); outer.setOrientation(LinearLayout.VERTICAL); outer.setBackgroundColor(Color.rgb(18,18,18));
        LinearLayout head=Ui.row(this); Button back=Ui.button(this,"‹"); TextView h=Ui.title(this,getIntent().getBooleanExtra("new_song",false)?"Nouveau morceau":"Modifier le morceau"); Ui.compactHeaderTitle(h,this); Ui.compactHeaderButton(back,this,46); head.addView(back); head.addView(h); outer.addView(head); back.setOnClickListener(v->finish());
        ScrollView sv=new ScrollView(this); LinearLayout root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(Ui.dp(this,14),0,Ui.dp(this,14),Ui.dp(this,30)); sv.addView(root);
        title=field(root,"Titre"); artist=field(root,"Artiste");
        Button findSong=Ui.button(this,"⌕ Trouver titre / artiste");
        findSong.setOnClickListener(v->searchSongIdentity());
        root.addView(findSong);
        LinearLayout r1=Ui.row(this); key=mini("Tonalité",r1); bpm=mini("BPM",r1); capo=mini("Capo",r1); root.addView(r1);
        tuning=field(root,"Accordage"); duration=field(root,"Durée"); singer=field(root,"Chanteur / chanteuse"); guitar=field(root,"Guitare / instrument");
        notes=field(root,"Notes live : intro, fin, départ…"); media=field(root,"Lien YouTube ou autre média");
        TextView lh=Ui.title(this,"Paroles / structure"); lh.setTextSize(18); root.addView(lh);
        Button findLyrics=Ui.button(this,"🌐 Chercher les paroles sur le web");
        findLyrics.setOnClickListener(v->searchLyricsWeb());
        root.addView(findLyrics);
        lyrics=new EditText(this); lyrics.setHint("INTRO\n...\n\nCOUPLET 1\n...\n\nREFRAIN\n..."); lyrics.setGravity(android.view.Gravity.TOP); lyrics.setMinLines(14); lyrics.setTextColor(Color.WHITE); lyrics.setHintTextColor(Color.GRAY); lyrics.setInputType(InputType.TYPE_CLASS_TEXT|InputType.TYPE_TEXT_FLAG_MULTI_LINE|InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        root.addView(lyrics,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,360)));
        Button arrange=Ui.button(this,"↕ Réarranger les blocs"); arrange.setOnClickListener(v->rearrangeBlocks()); root.addView(arrange);
        outer.addView(sv,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));
        LinearLayout actions=Ui.row(this); Button cancel=Ui.button(this,"Annuler"); Button save=Ui.button(this,"Enregistrer"); Ui.weight(cancel,1); Ui.weight(save,1); actions.addView(cancel); actions.addView(save); outer.addView(actions);
        cancel.setOnClickListener(v->finish()); save.setOnClickListener(v->save()); Ui.applySafeArea(outer); setContentView(outer);
    }

    private EditText mini(String hint,LinearLayout row){ EditText e=new EditText(this); e.setHint(hint); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.GRAY); e.setSingleLine(true); Ui.weight(e,1); row.addView(e); return e; }

    private void applyPrefill(){
        if(!getIntent().getBooleanExtra("new_song",false))return;
        String t=getIntent().getStringExtra("prefill_title");
        String a=getIntent().getStringExtra("prefill_artist");
        String k=getIntent().getStringExtra("prefill_key");
        String b=getIntent().getStringExtra("prefill_bpm");
        String d=getIntent().getStringExtra("prefill_duration");
        if(t!=null && !t.trim().isEmpty())title.setText(t.trim());
        if(a!=null && !a.trim().isEmpty())artist.setText(a.trim());
        if(k!=null && !k.trim().isEmpty())key.setText(k.trim());
        if(b!=null && !b.trim().isEmpty())bpm.setText(b.trim());
        if(d!=null && !d.trim().isEmpty())duration.setText(d.trim());
    }

    private void searchSongIdentity(){
        String q=(artist.getText().toString().trim()+" "+title.getText().toString().trim()).trim();
        if(q.isEmpty()){
            title.setError("Entre au moins un titre ou un artiste");
            return;
        }

        android.widget.Toast.makeText(this,"Recherche du morceau…",android.widget.Toast.LENGTH_SHORT).show();

        new Thread(()->{
            try{
                List<SongCatalogLookup.Result> results=SongCatalogLookup.search(q,10);
                runOnUiThread(()->showSongIdentityResults(results));
            }catch(Exception e){
                runOnUiThread(()->android.widget.Toast.makeText(this,
                    "Recherche impossible : "+e.getMessage(),
                    android.widget.Toast.LENGTH_LONG).show());
            }
        },"TS-Song-Identity").start();
    }

    private void showSongIdentityResults(List<SongCatalogLookup.Result> results){
        if(results==null || results.isEmpty()){
            android.widget.Toast.makeText(this,"Aucun résultat trouvé.",android.widget.Toast.LENGTH_LONG).show();
            return;
        }

        String[] labels=new String[results.size()];
        for(int i=0;i<results.size();i++)labels[i]=results.get(i).toString();

        new AlertDialog.Builder(this)
            .setTitle("Choisir le bon morceau")
            .setItems(labels,(d,which)->{
                SongCatalogLookup.Result r=results.get(which);
                applySongLookupResult(r);
                android.widget.Toast.makeText(this,"Récupération BPM / tonalité…",android.widget.Toast.LENGTH_SHORT).show();
                new Thread(()->{
                    SongCatalogLookup.Result enriched=SongCatalogLookup.enrich(r);
                    runOnUiThread(()->{
                        if(enriched!=null){
                            applySongLookupResult(enriched);
                            StringBuilder msg=new StringBuilder("Infos du morceau mises à jour");
                            if(enriched.bpm.isEmpty() && enriched.key.isEmpty()){
                                msg.append(" · BPM/tonalité non disponibles");
                            }
                            android.widget.Toast.makeText(this,msg.toString(),android.widget.Toast.LENGTH_SHORT).show();
                        }
                    });
                },"TS-Song-Metadata").start();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void applySongLookupResult(SongCatalogLookup.Result r){
        if(r==null)return;
        if(!r.title.isEmpty())title.setText(r.title);
        if(!r.artist.isEmpty())artist.setText(r.artist);
        if(!r.key.isEmpty())key.setText(r.key);
        if(!r.bpm.isEmpty())bpm.setText(r.bpm);
        if(!r.duration.isEmpty())duration.setText(r.duration);
    }

    private void fill(){ title.setText(song.title); artist.setText(song.artist); key.setText(song.key); bpm.setText(song.bpm); tuning.setText(song.tuning); capo.setText(song.capo); duration.setText(song.duration); singer.setText(song.singer); guitar.setText(song.guitar); notes.setText(song.notes); media.setText(song.mediaUrl); lyrics.setText(song.lyrics); }

    private void save(){
        song.title=title.getText().toString().trim(); song.artist=artist.getText().toString().trim(); song.key=key.getText().toString().trim(); song.bpm=bpm.getText().toString().trim(); song.tuning=tuning.getText().toString().trim(); song.capo=capo.getText().toString().trim(); song.duration=duration.getText().toString().trim(); song.singer=singer.getText().toString().trim(); song.guitar=guitar.getText().toString().trim(); song.notes=notes.getText().toString().trim(); song.mediaUrl=media.getText().toString().trim(); song.lyrics=compactLyrics(lyrics.getText().toString()); lyrics.setText(song.lyrics);
        if(song.title.isEmpty()){ title.setError("Titre obligatoire"); return; }
        AppStore.upsertSong(this,song);
        if(targetSetlistId!=null && !targetSetlistId.isEmpty()){
            SetListModel sl=AppStore.findSetlist(this,targetSetlistId);
            if(sl!=null && !sl.songIds.contains(song.id)){
                sl.songIds.add(song.id);
                AppStore.upsertSetlist(this,sl);
            }
        }
        finish();
    }

    private void searchLyricsWeb(){
        String t=title.getText().toString().trim();
        String a=artist.getText().toString().trim();

        if(t.isEmpty()){
            title.setError("Titre obligatoire");
            return;
        }
        if(a.isEmpty()){
            artist.setError("Artiste nécessaire pour la recherche");
            return;
        }

        android.widget.Toast.makeText(this,"Recherche des paroles…",android.widget.Toast.LENGTH_SHORT).show();

        new Thread(()->{
            String found=null;
            String source=null;

            try{
                String q=URLEncoder.encode(a+" "+t,StandardCharsets.UTF_8.toString());
                URL url=new URL("https://lrclib.net/api/search?q="+q);
                HttpURLConnection con=(HttpURLConnection)url.openConnection();
                con.setConnectTimeout(10000);
                con.setReadTimeout(12000);
                con.setRequestProperty("Accept","application/json");
                con.setRequestProperty("User-Agent","TS-LiveSet/0.33");
                if(con.getResponseCode()>=200 && con.getResponseCode()<300){
                    String raw=readResponse(con);
                    JSONArray arr=new JSONArray(raw);
                    for(int i=0;i<arr.length();i++){
                        JSONObject o=arr.optJSONObject(i);
                        if(o==null)continue;
                        String plain=o.optString("plainLyrics","");
                        if(plain!=null && !plain.trim().isEmpty()){
                            found=compactLyrics(plain);
                            source="LRCLIB";
                            break;
                        }
                    }
                }
                con.disconnect();
            }catch(Exception ignored){}

            if(found==null || found.isEmpty()){
                try{
                    String ea=URLEncoder.encode(a,StandardCharsets.UTF_8.toString()).replace("+","%20");
                    String et=URLEncoder.encode(t,StandardCharsets.UTF_8.toString()).replace("+","%20");
                    URL url=new URL("https://api.lyrics.ovh/v1/"+ea+"/"+et);
                    HttpURLConnection con=(HttpURLConnection)url.openConnection();
                    con.setConnectTimeout(10000);
                    con.setReadTimeout(12000);
                    con.setRequestProperty("Accept","application/json");
                    con.setRequestProperty("User-Agent","TS-LiveSet/0.33");
                    if(con.getResponseCode()>=200 && con.getResponseCode()<300){
                        JSONObject o=new JSONObject(readResponse(con));
                        String plain=o.optString("lyrics","");
                        if(plain!=null && !plain.trim().isEmpty()){
                            found=compactLyrics(plain);
                            source="lyrics.ovh";
                        }
                    }
                    con.disconnect();
                }catch(Exception ignored){}
            }

            final String result=found;
            final String resultSource=source;
            runOnUiThread(()->{
                if(result!=null && !result.isEmpty()){
                    lyrics.setText(result);
                    android.widget.Toast.makeText(this,"Paroles trouvées via "+resultSource,android.widget.Toast.LENGTH_LONG).show();
                }else{
                    new AlertDialog.Builder(this)
                        .setTitle("Paroles non trouvées automatiquement")
                        .setMessage("Je peux ouvrir une recherche web pour ce titre afin que tu puisses copier les paroles.")
                        .setPositiveButton("Ouvrir le web",(d,w)->openLyricsWebSearch())
                        .setNegativeButton("Fermer",null)
                        .show();
                }
            });
        }).start();
    }

    private String compactLyrics(String raw){
        if(raw==null)return "";
        String normalized=raw.replace("\r\n","\n").replace('\r','\n').replace('\u00A0',' ');
        StringBuilder out=new StringBuilder();
        for(String line:normalized.split("\n",-1)){
            String cleaned=line.replaceAll("[\\t ]+$","");
            if(cleaned.trim().isEmpty())continue;
            if(out.length()>0)out.append('\n');
            out.append(cleaned);
        }
        return out.toString().trim();
    }

    private String readResponse(HttpURLConnection con) throws Exception{
        BufferedReader br=new BufferedReader(new InputStreamReader(con.getInputStream(),StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder();
        String line;
        while((line=br.readLine())!=null) sb.append(line).append('\n');
        br.close();
        return sb.toString();
    }

    private void openLyricsWebSearch(){
        String t=title.getText().toString().trim();
        String a=artist.getText().toString().trim();
        String q=Uri.encode(a+" "+t+" paroles lyrics");
        Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse("https://www.google.com/search?q="+q));
        startActivity(i);
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
        dialog.setOnShowListener(x->{ dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->{ lyrics.setText(compactLyrics(String.join("\n",blocks))); dialog.dismiss(); }); });
        rebuild.run(); dialog.show();
    }
}
