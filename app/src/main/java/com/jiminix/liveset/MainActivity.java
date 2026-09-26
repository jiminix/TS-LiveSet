package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private LinearLayout content;
    private EditText search;
    private Button importButton;
    private Button libraryTab;
    private boolean libraryMode = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        showSetlists();
    }

    @Override protected void onResume() {
        super.onResume();
        if (content != null) { if (libraryMode) showLibrary(); else showSetlists(); }
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18,18,18));

        LinearLayout brand = Ui.row(this);
        TextView head = Ui.title(this, "LIVESET");
        Ui.weight(head,1);
        TextView version = new TextView(this);
        version.setText("v0.9");
        version.setTextColor(Color.LTGRAY);
        version.setTextSize(12);
        version.setPadding(Ui.dp(this,8),Ui.dp(this,6),Ui.dp(this,16),0);
        brand.addView(head);
        brand.addView(version);
        root.addView(brand);

        LinearLayout tabs = Ui.row(this);
        Button setlists = Ui.button(this, "Setlists");
        libraryTab = Ui.button(this, "Bibliothèque");
        Ui.weight(setlists,1); Ui.weight(libraryTab,1);
        tabs.addView(setlists); tabs.addView(libraryTab);
        root.addView(tabs);

        search = new EditText(this);
        search.setHint("Rechercher un morceau…");
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.LTGRAY);
        search.setPadding(Ui.dp(this,16),Ui.dp(this,6),Ui.dp(this,16),Ui.dp(this,6));
        search.setVisibility(View.GONE);
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        ScrollView scroll = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,100));
        scroll.addView(content);
        root.addView(scroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        LinearLayout bottom = Ui.row(this);
        bottom.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8));
        importButton = Ui.button(this,"⇩ Importer");
        Button add = Ui.button(this,"＋");
        add.setTextSize(26);
        add.setMinHeight(Ui.dp(this,56));
        View spacer = new View(this);
        Ui.weight(importButton,1);
        Ui.weight(add,1);
        Ui.weight(spacer,1);
        importButton.setVisibility(View.INVISIBLE);
        bottom.addView(importButton);
        bottom.addView(add);
        bottom.addView(spacer);
        root.addView(bottom,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        Ui.applySafeArea(root);
        setContentView(root);

        setlists.setOnClickListener(v -> {
            libraryMode=false;
            search.setVisibility(View.GONE);
            importButton.setVisibility(View.INVISIBLE);
            showSetlists();
        });
        libraryTab.setOnClickListener(v -> {
            libraryMode=true;
            search.setVisibility(View.VISIBLE);
            importButton.setVisibility(View.VISIBLE);
            showLibrary();
        });
        add.setOnClickListener(v -> {
            if (libraryMode) startActivity(new Intent(this,EditSongActivity.class));
            else createSetlist();
        });
        importButton.setOnClickListener(v -> startActivity(new Intent(this,ImportActivity.class)));
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int st,int c,int a){}
            public void onTextChanged(CharSequence s,int st,int before,int count){ if(libraryMode) showLibrary(); }
            public void afterTextChanged(Editable e){}
        });
    }

    private void showSetlists() {
        content.removeAllViews();
        List<SetListModel> lists = AppStore.loadSetlists(this);
        if (lists.isEmpty()) {
            TextView empty = Ui.title(this,"Aucune setlist\n\nAppuie sur « + Ajouter » pour créer ton premier concert.");
            empty.setTextSize(18); empty.setGravity(Gravity.CENTER); content.addView(empty);
            return;
        }
        for (SetListModel sl : lists) {
            LinearLayout row = Ui.row(this);
            TextView name = new TextView(this);
            name.setText(sl.name + "\n" + sl.songIds.size() + " morceau" + (sl.songIds.size()>1?"x":""));
            name.setTextColor(Color.WHITE); name.setTextSize(18); name.setPadding(Ui.dp(this,12),Ui.dp(this,12),Ui.dp(this,8),Ui.dp(this,12));
            Ui.weight(name,1);
            Button open = Ui.button(this,"Ouvrir");
            row.addView(name); row.addView(open);
            open.setOnClickListener(v -> openSetlist(sl.id));
            name.setOnClickListener(v -> openSetlist(sl.id));
            name.setOnLongClickListener(v -> { setlistMenu(sl); return true; });
            content.addView(row);
        }
    }

    private void showLibrary() {
        content.removeAllViews();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase();
        List<Song> songs=AppStore.loadSongs(this);
        if(libraryTab!=null) libraryTab.setText("Bibliothèque ("+songs.size()+")");
        if(songs.isEmpty()){
            TextView empty=Ui.title(this,"Bibliothèque vide\n\nUtilise « Importer » pour récupérer toute ta playlist d’un coup.");
            empty.setTextSize(18); empty.setGravity(Gravity.CENTER); content.addView(empty);
        }
        for (Song s : songs) {
            if (!q.isEmpty() && !(s.title+" "+s.artist).toLowerCase().contains(q)) continue;
            LinearLayout row = Ui.row(this);
            TextView txt = new TextView(this);
            String meta = s.artist;
            if (!s.key.isEmpty()) meta += (meta.isEmpty()?"":" · ") + s.key;
            if (!s.bpm.isEmpty()) meta += (meta.isEmpty()?"":" · ") + s.bpm + " BPM";
            txt.setText(s.title + (meta.isEmpty()?"":"\n"+meta));
            txt.setTextColor(Color.WHITE); txt.setTextSize(18); txt.setPadding(Ui.dp(this,12),Ui.dp(this,10),Ui.dp(this,6),Ui.dp(this,10));
            Ui.weight(txt,1);
            Button play = Ui.button(this,"▶");
            Button edit = Ui.button(this,"✎");
            row.addView(txt); row.addView(play); row.addView(edit);
            txt.setOnClickListener(v -> openLive(s.id,null,0));
            play.setOnClickListener(v -> openPlayer(s));
            edit.setOnClickListener(v -> editSong(s.id));
            txt.setOnLongClickListener(v -> { songMenu(s); return true; });
            content.addView(row);
        }
    }

    private void createSetlist() {
        EditText input = new EditText(this); input.setHint("Ex. Concert Lyon");
        new AlertDialog.Builder(this).setTitle("Nouvelle setlist").setView(input)
            .setPositiveButton("Créer",(d,w)->{
                String n=input.getText().toString().trim();
                if(n.isEmpty()) n="Nouvelle setlist";
                SetListModel sl=new SetListModel(); sl.name=n; AppStore.upsertSetlist(this,sl); showSetlists();
            }).setNegativeButton("Annuler",null).show();
    }

    private void setlistMenu(SetListModel sl) {
        String[] items={"Renommer","Supprimer"};
        new AlertDialog.Builder(this).setTitle(sl.name).setItems(items,(d,which)->{
            if(which==0){
                EditText e=new EditText(this); e.setText(sl.name); e.selectAll();
                new AlertDialog.Builder(this).setTitle("Renommer").setView(e).setPositiveButton("OK",(x,y)->{ sl.name=e.getText().toString().trim(); AppStore.upsertSetlist(this,sl); showSetlists(); }).setNegativeButton("Annuler",null).show();
            } else {
                List<SetListModel> all=AppStore.loadSetlists(this); all.removeIf(x->x.id.equals(sl.id)); AppStore.saveSetlists(this,all); showSetlists();
            }
        }).show();
    }

    private void songMenu(Song s) {
        String[] items={"Modifier","Supprimer"};
        new AlertDialog.Builder(this).setTitle(s.title).setItems(items,(d,which)->{
            if(which==0) editSong(s.id);
            else new AlertDialog.Builder(this).setTitle("Supprimer ce morceau ?").setMessage(s.title).setPositiveButton("Supprimer",(x,y)->{ AppStore.deleteSong(this,s.id); showLibrary(); }).setNegativeButton("Annuler",null).show();
        }).show();
    }

    private void openSetlist(String id){ Intent i=new Intent(this,SetlistActivity.class); i.putExtra("setlist_id",id); startActivity(i); }
    private void editSong(String id){ Intent i=new Intent(this,EditSongActivity.class); i.putExtra("song_id",id); startActivity(i); }
    private void openLive(String id,String sl,int index){ Intent i=new Intent(this,LiveSongActivity.class); i.putExtra("song_id",id); if(sl!=null)i.putExtra("setlist_id",sl); i.putExtra("index",index); startActivity(i); }
    private void openPlayer(Song s){ if(s.mediaUrl.trim().isEmpty()){ Toast.makeText(this,"Ajoute d’abord un lien YouTube/audio dans la fiche du morceau.",Toast.LENGTH_LONG).show(); return;} Intent i=new Intent(this,PlayerActivity.class); i.putExtra("title",s.title); i.putExtra("url",s.mediaUrl); startActivity(i); }
}
