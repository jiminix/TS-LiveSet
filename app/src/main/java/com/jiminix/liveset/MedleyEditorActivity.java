package com.jiminix.liveset;

import android.app.AlertDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MedleyEditorActivity extends AppCompatActivity {
    private static final class Entry {
        String title="";
        String artist="";
        String lyrics="";

        Entry(){}
        Entry(String title,String artist,String lyrics){
            this.title=title==null?"":title;
            this.artist=artist==null?"":artist;
            this.lyrics=lyrics==null?"":lyrics;
        }
    }

    private Song song;
    private String targetSetlistId;
    private boolean newMedley;
    private EditText medleyTitle;
    private EditText search;
    private LinearLayout entriesBox;
    private final ArrayList<Entry> entries=new ArrayList<>();

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);

        String songId=getIntent().getStringExtra("song_id");
        targetSetlistId=getIntent().getStringExtra("target_setlist_id");
        newMedley=getIntent().getBooleanExtra("new_medley",false);

        song=songId==null?null:AppStore.findSong(this,songId);
        if(song==null){
            song=new Song();
            song.title="Medley";
            newMedley=true;
        }

        loadEntries();
        buildUi();
        rebuildEntries();
    }

    private void loadEntries(){
        entries.clear();
        for(int i=0;i<song.medleyItems.size();i++){
            String title=song.medleyItems.get(i);
            String artist=i<song.medleyArtists.size()?song.medleyArtists.get(i):"";
            String lyrics=i<song.medleyLyrics.size()?song.medleyLyrics.get(i):"";
            entries.add(new Entry(title,artist,lyrics));
        }
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(15,15,15));

        LinearLayout head=Ui.row(this);
        head.setBackgroundColor(Color.rgb(105,12,18));

        Button back=Ui.button(this,"‹");
        back.setTextSize(22);
        Ui.compactHeaderButton(back,this,42);

        TextView title=Ui.title(this,newMedley?"Nouveau Medley":"Modifier le Medley");
        Ui.compactHeaderTitle(title,this);

        head.addView(back);
        head.addView(title);
        root.addView(head);

        ScrollView scroll=new ScrollView(this);
        LinearLayout body=new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(Ui.dp(this,10),Ui.dp(this,6),Ui.dp(this,10),Ui.dp(this,24));
        scroll.addView(body);

        medleyTitle=new EditText(this);
        medleyTitle.setHint("Nom : Medley Disco, Medley Rock…");
        medleyTitle.setTextColor(Color.WHITE);
        medleyTitle.setHintTextColor(Color.GRAY);
        medleyTitle.setSingleLine(true);
        medleyTitle.setFilters(new InputFilter[]{new InputFilter.AllCaps()});
        medleyTitle.setText(song.title==null || song.title.trim().isEmpty()?"Medley":song.title);
        body.addView(medleyTitle,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        TextView help=new TextView(this);
        help.setText("Le nom doit commencer par « Medley ». Recherche puis ajoute autant de morceaux que nécessaire.");
        help.setTextColor(Color.LTGRAY);
        help.setTextSize(11);
        help.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),Ui.dp(this,8));
        body.addView(help);

        LinearLayout searchRow=Ui.row(this);
        searchRow.setPadding(0,0,0,Ui.dp(this,4));

        search=new EditText(this);
        search.setHint("Titre + artiste");
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.GRAY);
        search.setLayoutParams(new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));

        Button find=Ui.button(this,"Chercher");
        find.setTextSize(12);
        Ui.compactHeaderButton(find,this,86);

        searchRow.addView(search);
        searchRow.addView(find);
        body.addView(searchRow);

        Button addManual=Ui.button(this,"＋ Ajouter manuellement");
        addManual.setTextSize(13);
        body.addView(addManual,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Ui.dp(this,44)
        ));

        TextView section=new TextView(this);
        section.setText("MORCEAUX DU MEDLEY");
        section.setTextColor(Color.LTGRAY);
        section.setTextSize(12);
        section.setPadding(Ui.dp(this,4),Ui.dp(this,12),Ui.dp(this,4),Ui.dp(this,5));
        body.addView(section);

        entriesBox=new LinearLayout(this);
        entriesBox.setOrientation(LinearLayout.VERTICAL);
        body.addView(entriesBox,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(scroll,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,0,1
        ));

        LinearLayout actions=Ui.row(this);
        actions.setBackgroundColor(Color.rgb(105,12,18));
        Button cancel=Ui.button(this,"Annuler");
        Button save=Ui.button(this,"Enregistrer le Medley");
        Ui.weight(cancel,0.42f);
        Ui.weight(save,1f);
        actions.addView(cancel);
        actions.addView(save);
        root.addView(actions);

        back.setOnClickListener(v->finish());
        cancel.setOnClickListener(v->finish());
        save.setOnClickListener(v->saveMedley());
        find.setOnClickListener(v->searchSongs());
        addManual.setOnClickListener(v->{
            entries.add(new Entry("","",""));
            rebuildEntries();
        });
        search.setOnEditorActionListener((v,actionId,event)->{
            searchSongs();
            return true;
        });

        Ui.applySafeArea(root);
        setContentView(root);
    }

    private void rebuildEntries(){
        if(entriesBox==null)return;
        entriesBox.removeAllViews();

        if(entries.isEmpty()){
            TextView empty=new TextView(this);
            empty.setText("Aucun morceau. Utilise la recherche ou « Ajouter manuellement ».");
            empty.setTextColor(Color.GRAY);
            empty.setTextSize(13);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(this,8),Ui.dp(this,18),Ui.dp(this,8),Ui.dp(this,18));
            entriesBox.addView(empty);
            return;
        }

        for(int i=0;i<entries.size();i++){
            final int index=i;
            final Entry entry=entries.get(i);

            LinearLayout card=new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(Color.rgb(30,30,30));
            card.setPadding(Ui.dp(this,7),Ui.dp(this,6),Ui.dp(this,7),Ui.dp(this,7));

            LinearLayout top=Ui.row(this);
            top.setPadding(0,0,0,0);

            TextView num=new TextView(this);
            num.setText(String.format(Locale.ROOT,"%02d",i+1));
            num.setTextColor(Color.rgb(255,193,7));
            num.setTextSize(15);
            num.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            num.setGravity(Gravity.CENTER);
            top.addView(num,new LinearLayout.LayoutParams(Ui.dp(this,34),Ui.dp(this,42)));

            Button up=Ui.button(this,"↑");
            Button down=Ui.button(this,"↓");
            Button lyricsWeb=Ui.button(this,"🌐");
            Button remove=Ui.button(this,"🗑");
            Ui.compactHeaderButton(up,this,38);
            Ui.compactHeaderButton(down,this,38);
            Ui.compactHeaderButton(lyricsWeb,this,42);
            Ui.compactHeaderButton(remove,this,42);
            up.setEnabled(i>0);
            down.setEnabled(i<entries.size()-1);

            TextView label=new TextView(this);
            label.setText("Morceau "+(i+1));
            label.setTextColor(Color.WHITE);
            label.setTextSize(14);
            label.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
            label.setGravity(Gravity.CENTER_VERTICAL);
            label.setLayoutParams(new LinearLayout.LayoutParams(0,Ui.dp(this,42),1));

            top.addView(label);
            top.addView(up);
            top.addView(down);
            top.addView(lyricsWeb);
            top.addView(remove);
            card.addView(top);

            EditText title=new EditText(this);
            title.setHint("Titre");
            title.setSingleLine(true);
            title.setFilters(new InputFilter[]{new InputFilter.AllCaps()});
            title.setTextColor(Color.WHITE);
            title.setHintTextColor(Color.GRAY);
            title.setText(entry.title);
            card.addView(title,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            EditText artist=new EditText(this);
            artist.setHint("Artiste");
            artist.setSingleLine(true);
            artist.setTextColor(Color.WHITE);
            artist.setHintTextColor(Color.GRAY);
            artist.setText(entry.artist);
            card.addView(artist,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            TextView lyricsLabel=new TextView(this);
            lyricsLabel.setText("Paroles");
            lyricsLabel.setTextColor(Color.LTGRAY);
            lyricsLabel.setTextSize(12);
            lyricsLabel.setPadding(Ui.dp(this,3),Ui.dp(this,5),0,Ui.dp(this,2));
            card.addView(lyricsLabel);

            EditText lyrics=new EditText(this);
            lyrics.setHint("Paroles du morceau…");
            lyrics.setTextColor(Color.WHITE);
            lyrics.setHintTextColor(Color.GRAY);
            lyrics.setGravity(Gravity.TOP);
            lyrics.setMinLines(6);
            lyrics.setText(entry.lyrics);
            lyrics.setInputType(InputType.TYPE_CLASS_TEXT|
                InputType.TYPE_TEXT_FLAG_MULTI_LINE|
                InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            card.addView(lyrics,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                Ui.dp(this,190)
            ));

            title.addTextChangedListener(watcher(v->entry.title=v,title));
            artist.addTextChangedListener(watcher(v->entry.artist=v,artist));
            lyrics.addTextChangedListener(watcher(v->entry.lyrics=v,lyrics));

            up.setOnClickListener(v->{
                if(index<=0)return;
                Collections.swap(entries,index,index-1);
                rebuildEntries();
            });
            down.setOnClickListener(v->{
                if(index>=entries.size()-1)return;
                Collections.swap(entries,index,index+1);
                rebuildEntries();
            });
            remove.setOnClickListener(v->{
                entries.remove(index);
                rebuildEntries();
            });
            lyricsWeb.setOnClickListener(v->fetchLyricsForEntry(entry,true));

            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.bottomMargin=Ui.dp(this,8);
            entriesBox.addView(card,lp);
        }
    }

    private interface TextSetter { void set(String value); }

    private TextWatcher watcher(TextSetter setter,EditText field){
        return new TextWatcher(){
            @Override public void beforeTextChanged(CharSequence s,int start,int count,int after){}
            @Override public void onTextChanged(CharSequence s,int start,int before,int count){
                setter.set(s==null?"":s.toString());
            }
            @Override public void afterTextChanged(Editable s){}
        };
    }

    private void searchSongs(){
        String q=search==null?"":search.getText().toString().trim();
        if(q.isEmpty()){
            if(search!=null)search.setError("Entre un titre et/ou un artiste");
            return;
        }

        Toast.makeText(this,"Recherche du morceau…",Toast.LENGTH_SHORT).show();
        new Thread(()->{
            try{
                List<SongCatalogLookup.Result> results=SongCatalogLookup.search(q,15);
                runOnUiThread(()->showSearchResults(results));
            }catch(Exception e){
                runOnUiThread(()->Toast.makeText(this,
                    "Recherche impossible : "+e.getMessage(),
                    Toast.LENGTH_LONG).show());
            }
        },"TS-Medley-Search").start();
    }

    private void showSearchResults(List<SongCatalogLookup.Result> results){
        if(results==null || results.isEmpty()){
            Toast.makeText(this,"Aucun morceau trouvé.",Toast.LENGTH_LONG).show();
            return;
        }

        String[] labels=new String[results.size()];
        for(int i=0;i<results.size();i++)labels[i]=results.get(i).toString();

        new AlertDialog.Builder(this)
            .setTitle("Ajouter au Medley")
            .setItems(labels,(d,which)->{
                SongCatalogLookup.Result r=results.get(which);
                Entry entry=new Entry(r.title==null?"":r.title.toUpperCase(Locale.ROOT),r.artist,"");
                entries.add(entry);
                rebuildEntries();
                fetchLyricsForEntry(entry,false);
            })
            .setNegativeButton("Fermer",null)
            .show();
    }

    private void fetchLyricsForEntry(Entry entry,boolean announce){
        if(entry==null)return;
        String title=entry.title==null?"":entry.title.trim();
        String artist=entry.artist==null?"":entry.artist.trim();

        if(title.isEmpty() || artist.isEmpty()){
            if(announce)Toast.makeText(this,"Titre et artiste nécessaires pour chercher les paroles.",Toast.LENGTH_LONG).show();
            return;
        }

        if(announce)Toast.makeText(this,"Recherche des paroles…",Toast.LENGTH_SHORT).show();

        new Thread(()->{
            String found=lookupLyrics(title,artist);
            if(found==null || found.trim().isEmpty()){
                if(announce)runOnUiThread(()->Toast.makeText(this,"Paroles non trouvées automatiquement.",Toast.LENGTH_LONG).show());
                return;
            }

            String compact=compactLyrics(found);
            runOnUiThread(()->{
                if(entry.lyrics==null || entry.lyrics.trim().isEmpty()){
                    entry.lyrics=compact;
                    rebuildEntries();
                }
                Toast.makeText(this,"Paroles ajoutées au Medley.",Toast.LENGTH_SHORT).show();
            });
        },"TS-Medley-Lyrics").start();
    }

    private String lookupLyrics(String title,String artist){
        try{
            String q=URLEncoder.encode(artist+" "+title,StandardCharsets.UTF_8.toString());
            URL url=new URL("https://lrclib.net/api/search?q="+q);
            HttpURLConnection con=(HttpURLConnection)url.openConnection();
            con.setConnectTimeout(9000);
            con.setReadTimeout(11000);
            con.setRequestProperty("Accept","application/json");
            con.setRequestProperty("User-Agent","TS-LiveSet/Medley");
            if(con.getResponseCode()>=200 && con.getResponseCode()<300){
                JSONArray arr=new JSONArray(readResponse(con));
                for(int i=0;i<arr.length();i++){
                    JSONObject o=arr.optJSONObject(i);
                    if(o==null)continue;
                    String plain=o.optString("plainLyrics","");
                    if(!plain.trim().isEmpty()){
                        con.disconnect();
                        return plain;
                    }
                }
            }
            con.disconnect();
        }catch(Exception ignored){}

        try{
            String ea=URLEncoder.encode(artist,StandardCharsets.UTF_8.toString()).replace("+","%20");
            String et=URLEncoder.encode(title,StandardCharsets.UTF_8.toString()).replace("+","%20");
            URL url=new URL("https://api.lyrics.ovh/v1/"+ea+"/"+et);
            HttpURLConnection con=(HttpURLConnection)url.openConnection();
            con.setConnectTimeout(9000);
            con.setReadTimeout(11000);
            con.setRequestProperty("Accept","application/json");
            con.setRequestProperty("User-Agent","TS-LiveSet/Medley");
            if(con.getResponseCode()>=200 && con.getResponseCode()<300){
                JSONObject o=new JSONObject(readResponse(con));
                String plain=o.optString("lyrics","");
                con.disconnect();
                return plain;
            }
            con.disconnect();
        }catch(Exception ignored){}

        return "";
    }

    private String readResponse(HttpURLConnection con) throws Exception{
        BufferedReader br=new BufferedReader(new InputStreamReader(con.getInputStream(),StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder();
        String line;
        while((line=br.readLine())!=null)sb.append(line).append('\n');
        br.close();
        return sb.toString();
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

    private void saveMedley(){
        String name=medleyTitle.getText().toString().trim().toUpperCase(Locale.ROOT);
        if(name.toLowerCase(Locale.ROOT).startsWith("meddley")){
            name="Medley"+name.substring(7);
            medleyTitle.setText(name);
        }

        if(!name.toLowerCase(Locale.ROOT).startsWith("medley")){
            medleyTitle.setError("Le nom doit commencer par « Medley »");
            return;
        }

        ArrayList<Entry> clean=new ArrayList<>();
        for(Entry e:entries){
            String t=e.title==null?"":e.title.trim().toUpperCase(Locale.ROOT);
            if(t.isEmpty())continue;
            e.title=t;
            e.artist=e.artist==null?"":e.artist.trim();
            e.lyrics=compactLyrics(e.lyrics);
            clean.add(e);
        }

        if(clean.isEmpty()){
            Toast.makeText(this,"Ajoute au moins un morceau au Medley.",Toast.LENGTH_LONG).show();
            return;
        }

        entries.clear();
        entries.addAll(clean);

        song.title=name;
        song.medleyItems.clear();
        song.medleyArtists.clear();
        song.medleyLyrics.clear();

        StringBuilder combined=new StringBuilder();
        for(Entry e:entries){
            song.medleyItems.add(e.title);
            song.medleyArtists.add(e.artist);
            song.medleyLyrics.add(e.lyrics);

            if(combined.length()>0)combined.append("\n");
            combined.append(e.title);
            if(!e.artist.isEmpty())combined.append(" — ").append(e.artist);
            if(!e.lyrics.isEmpty())combined.append("\n").append(e.lyrics);
            combined.append("\n");
        }
        song.lyrics=combined.toString().trim();

        AppStore.upsertSong(this,song);

        if(targetSetlistId!=null && !targetSetlistId.isEmpty()){
            SetListModel list=AppStore.findSetlist(this,targetSetlistId);
            if(list!=null && !list.songIds.contains(song.id)){
                list.songIds.add(song.id);
                AppStore.upsertSetlist(this,list);
            }
        }

        Toast.makeText(this,"Medley enregistré.",Toast.LENGTH_SHORT).show();
        finish();
    }
}
