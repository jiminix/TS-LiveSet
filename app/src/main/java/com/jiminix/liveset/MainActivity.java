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
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    // V0.17 build trigger
    // V0.19 lyrics repair
    // V0.20 relink complete songs
    // build V0.20
    // V0.21 playlist home list
    private LinearLayout content;
    private ScrollView mainScroll;
    private EditText search;
    private Button importButton;
    private Button libraryTab;
    private boolean libraryMode = false;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        buildUi();
        if(getIntent().getBooleanExtra("open_library",false)){
            libraryMode=true;
            search.setVisibility(View.VISIBLE);
            importButton.setVisibility(View.VISIBLE);
            showLibrary();
        }else{
            libraryMode=false;
            search.setVisibility(View.GONE);
            importButton.setVisibility(View.INVISIBLE);
            showSetlists();
        }
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
        Ui.compactHeaderTitle(head,this);
        TextView version = new TextView(this);
        version.setText("v"+installedVersion());
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

        LinearLayout quick = Ui.row(this);
        Button playlistAccess = Ui.button(this,"☰ PLAYLIST");
        Button titlesAccess = Ui.button(this,"≡ TITRES");
        playlistAccess.setTextSize(17);
        titlesAccess.setTextSize(17);
        playlistAccess.setMinHeight(Ui.dp(this,56));
        titlesAccess.setMinHeight(Ui.dp(this,56));
        Ui.weight(playlistAccess,1);
        Ui.weight(titlesAccess,1);
        quick.addView(playlistAccess);
        quick.addView(titlesAccess);
        root.addView(quick);

        search = new EditText(this);
        search.setHint("Rechercher un morceau…");
        search.setSingleLine(true);
        search.setTextColor(Color.WHITE);
        search.setHintTextColor(Color.LTGRAY);
        search.setPadding(Ui.dp(this,16),Ui.dp(this,6),Ui.dp(this,16),Ui.dp(this,6));
        search.setVisibility(View.GONE);
        root.addView(search, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        mainScroll = new ScrollView(this);
        mainScroll.setFillViewport(true);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,8),Ui.dp(this,100));
        mainScroll.addView(content,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(mainScroll, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

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
        playlistAccess.setOnClickListener(v -> openPlaylistAccess());
        titlesAccess.setOnClickListener(v -> {
            libraryMode=true;
            search.setText("");
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

    private String installedVersion(){
        try{
            String version=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
            return version==null || version.trim().isEmpty() ? "?" : version.trim();
        }catch(Exception ignored){
            return "?";
        }
    }

    private void showSetlists() {
        content.removeAllViews();
        List<SetListModel> lists = AppStore.loadSetlists(this);

        TextView section=new TextView(this);
        section.setText("PLAYLISTS");
        section.setTextColor(Color.LTGRAY);
        section.setTextSize(13);
        section.setGravity(Gravity.CENTER_VERTICAL);
        section.setPadding(Ui.dp(this,8),Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,8));
        content.addView(section);

        if (lists.isEmpty()) {
            TextView empty = Ui.title(this,"Aucune playlist");
            empty.setTextSize(20);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(this,12),Ui.dp(this,32),Ui.dp(this,12),Ui.dp(this,18));
            content.addView(empty);

            Button create=Ui.button(this,"＋ Créer une playlist");
            create.setTextSize(17);
            create.setOnClickListener(v->createSetlist());
            content.addView(create,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,58)));

            Button library=Ui.button(this,"Voir la bibliothèque ("+AppStore.loadSongs(this).size()+")");
            library.setTextSize(16);
            library.setOnClickListener(v->{
                libraryMode=true;
                search.setVisibility(View.VISIBLE);
                importButton.setVisibility(View.VISIBLE);
                showLibrary();
            });
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,56));
            lp.topMargin=Ui.dp(this,8);
            content.addView(library,lp);

            if(mainScroll!=null) mainScroll.post(()->mainScroll.scrollTo(0,0));
            return;
        }

        for (SetListModel sl : lists) {
            LinearLayout row=Ui.row(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this,6),Ui.dp(this,2),Ui.dp(this,4),Ui.dp(this,2));
            row.setMinimumHeight(Ui.dp(this,54));

            TextView name=new TextView(this);
            name.setText(sl.name+"\n"+sl.songIds.size()+" titre"+(sl.songIds.size()>1?"s":""));
            name.setTextColor(Color.WHITE);
            name.setTextSize(16);
            name.setSingleLine(false);
            name.setPadding(Ui.dp(this,6),Ui.dp(this,3),Ui.dp(this,6),Ui.dp(this,3));
            Ui.weight(name,1);

            Button open=Ui.button(this,"Ouvrir");
            open.setTextSize(13);
            Ui.compactHeaderButton(open,this,72);

            Button delete=Ui.button(this,"🗑");
            delete.setTextSize(17);
            Ui.compactHeaderButton(delete,this,48);

            row.addView(name);
            row.addView(open);
            row.addView(delete);

            View.OnClickListener opener=v->openPlaylistOverview(sl.id);
            name.setOnClickListener(opener);
            open.setOnClickListener(opener);
            delete.setOnClickListener(v->confirmDeleteSetlist(sl));
            name.setOnLongClickListener(v->{ setlistMenu(sl); return true; });

            content.addView(row);

            View sep=new View(this);
            sep.setBackgroundColor(Color.rgb(38,38,38));
            content.addView(sep,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,1)));
        }

        if(mainScroll!=null) mainScroll.post(()->mainScroll.scrollTo(0,0));
    }

    private void confirmDeleteSetlist(SetListModel sl){
        new AlertDialog.Builder(this)
            .setTitle("Supprimer la playlist ?")
            .setMessage("« "+sl.name+" » sera supprimée. Les morceaux et les paroles resteront dans la bibliothèque.")
            .setPositiveButton("Supprimer",(d,w)->{
                List<SetListModel> all=AppStore.loadSetlists(this);
                all.removeIf(x->x.id.equals(sl.id));
                AppStore.saveSetlists(this,all);
                showSetlists();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void showLibrary() {
        content.removeAllViews();
        String q = search == null ? "" : search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<Song> songs=AppStore.loadSongs(this);
        if(libraryTab!=null) libraryTab.setText("Bibliothèque ("+songs.size()+")");

        int visible=0;
        for (Song s : songs) {
            String title=s.title==null?"":s.title;
            String artist=s.artist==null?"":s.artist;
            String bpm=s.bpm==null?"":s.bpm;
            if (!q.isEmpty() && !(title+" "+artist).toLowerCase(Locale.ROOT).contains(q)) continue;

            final Song song=s;
            LinearLayout row=new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setBackgroundColor(Color.rgb(34,34,34));
            row.setPadding(Ui.dp(this,12),Ui.dp(this,5),Ui.dp(this,8),Ui.dp(this,5));
            row.setMinimumHeight(Ui.dp(this,58));

            TextView txt=new TextView(this);
            String meta="";
            if(!artist.isEmpty())meta=artist;
            if(!bpm.isEmpty())meta+=(meta.isEmpty()?"":" · ")+bpm+" BPM";
            txt.setText(title+(meta.isEmpty()?"":"\n"+meta));
            txt.setTextColor(Color.WHITE);
            txt.setTextSize(18);
            txt.setGravity(Gravity.CENTER_VERTICAL);
            txt.setPadding(Ui.dp(this,4),Ui.dp(this,4),Ui.dp(this,8),Ui.dp(this,4));
            txt.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

            TextView arrow=new TextView(this);
            arrow.setText("›");
            arrow.setTextColor(Color.LTGRAY);
            arrow.setTextSize(30);
            arrow.setGravity(Gravity.CENTER);
            arrow.setMinWidth(Ui.dp(this,38));

            row.addView(txt);
            row.addView(arrow);

            row.setOnClickListener(v->openLive(song.id,null,0));
            row.setOnLongClickListener(v->{ songMenu(song); return true; });
            content.addView(row,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

            View sep=new View(this);
            sep.setBackgroundColor(Color.rgb(18,18,18));
            content.addView(sep,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,4)));
            visible++;
        }

        if(songs.isEmpty()){
            TextView empty=Ui.title(this,"Bibliothèque vide\n\nUtilise « Importer » pour récupérer toute ta playlist d’un coup.");
            empty.setTextSize(18); empty.setGravity(Gravity.CENTER); content.addView(empty);
        }else if(visible==0){
            TextView empty=Ui.title(this,"Aucun résultat pour « "+q+" »");
            empty.setTextSize(18); empty.setGravity(Gravity.CENTER); content.addView(empty);
        }

        if(mainScroll!=null) mainScroll.post(()->mainScroll.scrollTo(0,0));
    }

    private void openPlaylistAccess() {
        List<SetListModel> lists=AppStore.loadSetlists(this);

        if(lists.isEmpty()){
            List<Song> songs=AppStore.loadSongs(this);
            if(songs.isEmpty()){
                Toast.makeText(this,"Aucun morceau dans la bibliothèque.",Toast.LENGTH_LONG).show();
                return;
            }

            SetListModel target=new SetListModel();
            target.name="PLAYLIST 2026";
            for(Song s:songs) target.songIds.add(s.id);
            AppStore.upsertSetlist(this,target);
            openPlaylistOverview(target.id);
            return;
        }

        if(lists.size()==1){
            openPlaylistOverview(lists.get(0).id);
            return;
        }

        String[] names=new String[lists.size()];
        for(int i=0;i<lists.size();i++){
            SetListModel sl=lists.get(i);
            names[i]=sl.name+"   ("+sl.songIds.size()+" titres)";
        }

        new AlertDialog.Builder(this)
            .setTitle("Choisir une playlist")
            .setItems(names,(d,which)->openPlaylistOverview(lists.get(which).id))
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void openPlaylistOverview(String id){
        Intent i=new Intent(this,PlaylistOverviewActivity.class);
        i.putExtra("setlist_id",id);
        startActivity(i);
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
                confirmDeleteSetlist(sl);
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
