package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashSet;
import java.util.Set;
import java.text.Normalizer;
import java.util.Locale;

public class PlaylistOverviewActivity extends AppCompatActivity {
    // Build V0.25
    // Build V0.26
    // Build V0.27
    // Build V0.32
    // Build V0.33
    // Build V0.34
    // Build V0.35
    // Build V0.40 dynamic stage indicators and titles
    // Build V0.41 right-aligned compact metadata and text zoom -3..+2
    // Build V0.42 direct -/+ zoom controls with 6 levels
    // Build V0.43 zoom -5..+2 and compact one-line header
    // Build V0.46 compact one-line bottom controls
    // Build V0.47 BPM edit, online title suggestions and full-row zoom
    // Build V0.49 playlist top line: TS 2026, title count and page arrows
    // Build V0.53 single-line playlist header, no drag-help row
    // Build V0.60 editable En cours playlist access
    // Build V0.61 explicit Google Docs import button in En cours
    // Build V0.63 enrich online song search metadata
    // Build V0.65 red action buttons, black/white zoom and colored header/footer bands
    // Build V0.66 per-playlist disabled songs at 50% opacity
    // Build V0.67 expandable Medley sub-playlists
    // Build V0.70 dedicated Medley creator/editor
    // Build V0.71 self-repair reserved En cours playlist
    // Build V0.72 backup + cloud recovery for En cours
    // Build V0.74 diagnostic + manual En cours rebuild
    // Build V0.79 simplified playlist controls
    private String setlistId;
    private SetListModel setlist;
    private String currentSongId=null;
    private boolean compact=true;
    private int textZoom=0;
    private LinearLayout root;
    private Button modeButton;
    private TextView titleView;
    private RecyclerView recycler;
    private PlaylistAdapter adapter;
    private ItemTouchHelper touchHelper;
    private final Set<String> expandedMedleys=new HashSet<>();
    private boolean enCoursRestoreAttempted=false;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setlistId=getIntent().getStringExtra("setlist_id");
        int currentIndex=getIntent().getIntExtra("current_index",-1);
        setlist=AppStore.isInProgressSetlist(setlistId)
            ? AppStore.getOrCreateInProgressSetlist(this)
            : AppStore.findSetlist(this,setlistId);
        if(setlist==null){ finish(); return; }
        repairMissingLyrics();
        if(currentIndex>=0 && currentIndex<setlist.songIds.size()) currentSongId=setlist.songIds.get(currentIndex);
        compact=true;
        getSharedPreferences("playlist_view",MODE_PRIVATE).edit().putBoolean("compact",true).apply();
        textZoom=Math.max(-5,Math.min(2,getSharedPreferences("playlist_view",MODE_PRIVATE).getInt("text_zoom",0)));
        buildUi();
        maybeRestoreEmptyInProgress();
    }

    @Override protected void onResume(){
        super.onResume();
        SetListModel fresh=AppStore.isInProgressSetlist(setlistId)
            ? AppStore.getOrCreateInProgressSetlist(this)
            : AppStore.findSetlist(this,setlistId);
        if(fresh!=null){
            setlist=fresh;
            repairMissingLyrics();
            if(titleView!=null) titleView.setText(setlist.name+" · "+setlist.songIds.size()+" titres");
            if(adapter!=null) adapter.notifyDataSetChanged();
            maybeRestoreEmptyInProgress();
        }
    }

    private void maybeRestoreEmptyInProgress(){
        if(!AppStore.isInProgressSetlist(setlistId))return;
        if(setlist==null || !setlist.songIds.isEmpty())return;
        if(AppStore.isInProgressIntentionallyEmpty(this))return;
        if(enCoursRestoreAttempted)return;
        enCoursRestoreAttempted=true;

        Toast.makeText(this,"En cours vide · tentative de récupération…",Toast.LENGTH_SHORT).show();
        PlaylistCloudSync.restoreInProgressFromCloud(this,new PlaylistCloudSync.Listener(){
            @Override public void onSuccess(String code){
                SetListModel restored=AppStore.getOrCreateInProgressSetlist(PlaylistOverviewActivity.this);
                if(restored!=null){
                    setlist=restored;
                    if(titleView!=null)titleView.setText(setlist.name+" · "+setlist.songIds.size()+" titres");
                    if(adapter!=null)adapter.notifyDataSetChanged();
                    Toast.makeText(PlaylistOverviewActivity.this,
                        setlist.songIds.size()+" morceau"+(setlist.songIds.size()>1?"x":"")+" récupéré"+(setlist.songIds.size()>1?"s":"")+" dans En cours",
                        Toast.LENGTH_LONG).show();
                }
            }

            @Override public void onError(String message){
                showEnCoursRecoveryFallback(message);
            }
        });
    }

    private void showEnCoursRecoveryFallback(String diagnostic){
        List<Song> library=AppStore.loadSongs(this);

        if(library.isEmpty()){
            new AlertDialog.Builder(this)
                .setTitle("Récupération En cours")
                .setMessage("En cours est vide.\n\n"+diagnostic+
                    "\n\nLa bibliothèque locale est également vide, donc aucun morceau ne peut être reconstruit depuis ce téléphone.")
                .setPositiveButton("OK",null)
                .show();
            return;
        }

        new AlertDialog.Builder(this)
            .setTitle("Récupération En cours")
            .setMessage("La récupération automatique n’a rien retrouvé.\n\n"+diagnostic+
                "\n\nTu peux maintenant reconstruire En cours en choisissant les morceaux dans ta bibliothèque.")
            .setPositiveButton("Choisir les morceaux",(d,w)->showLibraryRecoveryPicker())
            .setNegativeButton("Plus tard",null)
            .show();
    }

    private void showLibraryRecoveryPicker(){
        List<Song> library=AppStore.loadSongs(this);
        if(library.isEmpty())return;

        String[] labels=new String[library.size()];
        boolean[] checked=new boolean[library.size()];

        for(int i=0;i<library.size();i++){
            Song s=library.get(i);
            String artist=s.artist==null?"":s.artist.trim();
            labels[i]=(s.title==null?"":s.title)+(artist.isEmpty()?"":" — "+artist);
            checked[i]=false;
        }

        new AlertDialog.Builder(this)
            .setTitle("Reconstruire « En cours »")
            .setMultiChoiceItems(labels,checked,(dialog,which,isChecked)->checked[which]=isChecked)
            .setPositiveButton("Restaurer",(d,w)->{
                SetListModel progress=AppStore.getOrCreateInProgressSetlist(this);
                progress.songIds.clear();
                progress.disabledSongIds.clear();

                for(int i=0;i<library.size();i++){
                    if(checked[i])progress.songIds.add(library.get(i).id);
                }

                if(progress.songIds.isEmpty()){
                    Toast.makeText(this,"Aucun morceau sélectionné.",Toast.LENGTH_LONG).show();
                    return;
                }

                AppStore.upsertSetlist(this,progress);
                setlist=progress;
                if(titleView!=null)titleView.setText("En cours · "+progress.songIds.size()+" titres");
                if(adapter!=null)adapter.notifyDataSetChanged();

                Toast.makeText(this,
                    progress.songIds.size()+" morceau"+(progress.songIds.size()>1?"x":"")+" restauré"+(progress.songIds.size()>1?"s":"")+" dans En cours",
                    Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void buildUi(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10,10,10));

        LinearLayout head=Ui.row(this);
        head.setPadding(Ui.dp(this,2),Ui.dp(this,2),Ui.dp(this,2),Ui.dp(this,2));
        head.setBackgroundColor(Color.rgb(105,12,18));

        TextView back=new TextView(this);
        back.setText("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(25);
        back.setGravity(Gravity.CENTER);
        back.setClickable(true);
        back.setFocusable(true);
        back.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this,34),Ui.dp(this,46)));

        titleView=new TextView(this);
        titleView.setText(setlist.name+" · "+setlist.songIds.size()+" titres");
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(11);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setPadding(Ui.dp(this,2),0,Ui.dp(this,2),0);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(0,Ui.dp(this,40),1));
        titleView.setClickable(true);

        Button rename=Ui.button(this,"✎");
        rename.setTextSize(14);
        Ui.compactHeaderButton(rename,this,30);

        Button add=Ui.button(this,"＋");
        add.setTextSize(18);
        Ui.compactHeaderButton(add,this,30);

        Button pageUp=Ui.button(this,"↑");
        pageUp.setTextSize(16);
        Ui.compactHeaderButton(pageUp,this,30);

        Button pageDown=Ui.button(this,"↓");
        pageDown.setTextSize(16);
        Ui.compactHeaderButton(pageDown,this,30);

        head.addView(back);
        head.addView(titleView);
        head.addView(pageUp);
        head.addView(pageDown);
        head.addView(rename);
        head.addView(add);
        root.addView(head,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Ui.dp(this,44)
        ));

        recycler=new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setBackgroundColor(Color.rgb(10,10,10));
        recycler.setPadding(Ui.dp(this,3),0,Ui.dp(this,3),Ui.dp(this,8));
        recycler.setClipToPadding(false);

        adapter=new PlaylistAdapter();
        recycler.setAdapter(adapter);
        root.addView(recycler,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        ItemTouchHelper.Callback callback=new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP|ItemTouchHelper.DOWN,0){
            @Override public boolean onMove(RecyclerView rv,RecyclerView.ViewHolder from,RecyclerView.ViewHolder to){
                int a=from.getBindingAdapterPosition();
                int b=to.getBindingAdapterPosition();
                if(a==RecyclerView.NO_POSITION || b==RecyclerView.NO_POSITION)return false;
                Collections.swap(setlist.songIds,a,b);
                adapter.notifyItemMoved(a,b);
                int first=Math.min(a,b);
                int count=Math.abs(a-b)+1;
                adapter.notifyItemRangeChanged(first,count);
                AppStore.upsertSetlist(PlaylistOverviewActivity.this,setlist);
                return true;
            }

            @Override public void onSwiped(RecyclerView.ViewHolder vh,int direction){}

            @Override public boolean isLongPressDragEnabled(){ return true; }

            @Override public void onSelectedChanged(RecyclerView.ViewHolder vh,int actionState){
                super.onSelectedChanged(vh,actionState);
                if(vh!=null && actionState==ItemTouchHelper.ACTION_STATE_DRAG){
                    vh.itemView.setBackgroundColor(Color.rgb(55,55,55));
                }
            }

            @Override public void clearView(RecyclerView rv,RecyclerView.ViewHolder vh){
                super.clearView(rv,vh);
                adapter.notifyDataSetChanged();
                AppStore.upsertSetlist(PlaylistOverviewActivity.this,setlist);
            }
        };
        touchHelper=new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recycler);

        LinearLayout actions=Ui.row(this);
        actions.setPadding(Ui.dp(this,2),Ui.dp(this,1),Ui.dp(this,2),Ui.dp(this,1));
        actions.setBackgroundColor(Color.rgb(105,12,18));
        boolean isInProgress=AppStore.isInProgressSetlist(setlist.id);
        Button importTitles=Ui.button(this,"");
        Button fixTitles=Ui.button(this,"⌕");
        Button viewer=Ui.button(this,"▣");
        Button inProgress=Ui.button(this,"En cours");
        Button zoomMinus=Ui.button(this,"−");
        Button zoomPlus=Ui.button(this,"+");
        importTitles.setTextSize(15);
        importTitles.setCompoundDrawablesWithIntrinsicBounds(getDrawable(R.drawable.ic_google_docs),null,null,null);
        importTitles.setGravity(Gravity.CENTER);
        fixTitles.setTextSize(17);
        viewer.setTextSize(15);
        inProgress.setTextSize(10);
        zoomMinus.setTextSize(16);
        zoomPlus.setTextSize(16);

        // Live UI colors.
        styleButton(inProgress,Color.rgb(198,40,40),Color.WHITE);
        if(isInProgress)styleButton(importTitles,Color.rgb(198,40,40),Color.WHITE);
        styleButton(zoomMinus,Color.BLACK,Color.WHITE);
        styleButton(zoomPlus,Color.WHITE,Color.BLACK);
        Ui.compactHeaderButton(importTitles,this,38);
        Ui.compactHeaderButton(fixTitles,this,34);
        Ui.weight(viewer,1);
        Ui.compactHeaderButton(inProgress,this,66);
        Ui.compactHeaderButton(zoomMinus,this,32);
        Ui.compactHeaderButton(zoomPlus,this,32);
        importTitles.setContentDescription("Importer depuis Google Docs");
        fixTitles.setContentDescription("Trouver les titres et artistes corrects");
        viewer.setContentDescription("Viewer");
        inProgress.setContentDescription("Playlist En cours");
        actions.addView(importTitles);
        actions.addView(fixTitles);
        actions.addView(viewer);
        if(!isInProgress)actions.addView(inProgress);
        actions.addView(zoomMinus);
        actions.addView(zoomPlus);
        root.addView(actions);

        back.setOnClickListener(v->finish());
        titleView.setOnClickListener(v->renameList());
        rename.setOnClickListener(v->renameList());
        add.setOnClickListener(v->showAddSongMenu());
        importTitles.setOnClickListener(v->openImporter());
        fixTitles.setOnClickListener(v->findCorrectTitles());
        viewer.setOnClickListener(v->openViewer());
        inProgress.setOnClickListener(v->openInProgressPlaylist());
        zoomMinus.setOnClickListener(v->changeZoom(-1));
        zoomPlus.setOnClickListener(v->changeZoom(1));
        pageUp.setOnClickListener(v->pageScroll(-1));
        pageDown.setOnClickListener(v->pageScroll(1));

        Ui.applySafeArea(root);
        setContentView(root);
    }

    private void styleButton(Button button,int background,int foreground){
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setTextColor(foreground);
    }

    private void repairMissingLyrics(){
        List<Song> all=AppStore.loadSongs(this);
        if(all.isEmpty())return;

        boolean changed=false;

        for(int i=0;i<setlist.songIds.size();i++){
            String id=setlist.songIds.get(i);
            Song target=AppStore.findSong(this,id);
            if(target==null)continue;

            boolean hasLyrics=target.lyrics!=null && !target.lyrics.trim().isEmpty();
            if(hasLyrics)continue;

            String targetTitle=normalizedTitle(target.title);
            if(targetTitle.isEmpty())continue;

            Song donor=null;
            for(Song candidate:all){
                if(candidate.id.equals(target.id))continue;
                if(candidate.lyrics==null || candidate.lyrics.trim().isEmpty())continue;
                if(targetTitle.equals(normalizedTitle(candidate.title))){
                    donor=candidate;
                    break;
                }
            }

            if(donor!=null){
                // Repoint the playlist to the complete library song instead of
                // keeping a duplicate record with missing lyrics.
                setlist.songIds.set(i,donor.id);
                changed=true;
            }
        }

        if(changed) AppStore.upsertSetlist(this,setlist);
    }

    private String normalizedTitle(String value){
        String s=value==null?"":Normalizer.normalize(value,Normalizer.Form.NFD).replaceAll("\\p{M}+","");
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+"," ").trim().replaceAll("\\s+"," ");
    }

    private void openViewer(){
        boolean progress=AppStore.isInProgressSetlist(setlist.id);
        if(!progress)AppStore.selectViewerSetlist(this,setlist.id);

        String primaryId=AppStore.getViewerSetlistId(this);
        Intent viewer=new Intent(this,IntegratedViewerActivity.class);
        viewer.putExtra("setlist_id",progress?primaryId:setlist.id);
        viewer.putExtra("show_in_progress",progress);
        startActivity(viewer);

        // Keep both Internet playlists updated for the remote Viewer phones.
        PlaylistCloudSync.publishSelected(this,new PlaylistCloudSync.Listener(){
            @Override public void onSuccess(String code){}

            @Override public void onError(String message){
                Toast.makeText(PlaylistOverviewActivity.this,
                    "Viewer local ouvert. Synchronisation Internet indisponible : "+message,
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showViewerInternetDialog(String code){
        new AlertDialog.Builder(this)
            .setTitle("Viewer Internet")
            .setMessage("Playlist publiée.\n\nCode de connexion :\n"+code+
                "\n\nEntre ce code une seule fois dans Viewer sur l’autre téléphone.")
            .setPositiveButton("Ouvrir Viewer",(d,w)->{
                Intent launch=getPackageManager().getLaunchIntentForPackage("com.jiminix.livesetviewer");
                if(launch!=null)startActivity(launch);
                else Toast.makeText(this,"Viewer n’est pas installé sur ce téléphone.",Toast.LENGTH_SHORT).show();
            })
            .setNeutralButton("Partager le code",(d,w)->shareViewerCode(code))
            .setNegativeButton("Fermer",null)
            .show();
    }

    private void shareViewerCode(String code){
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,"TS Playlist Viewer\nCode Internet : "+code);
        startActivity(Intent.createChooser(send,"Partager le code Viewer"));
    }

    private void openInProgressPlaylist(){
        SetListModel progress=AppStore.getOrCreateInProgressSetlist(this);
        if(progress.id.equals(setlist.id)){
            recycler.scrollToPosition(0);
            return;
        }
        Intent i=new Intent(this,PlaylistOverviewActivity.class);
        i.putExtra("setlist_id",progress.id);
        startActivity(i);
    }

    private void openImporter(){
        Intent i=new Intent(this,ImportActivity.class);
        i.putExtra("target_setlist_id",setlist.id);
        startActivity(i);
    }

    private void renameList(){
        if(AppStore.isInProgressSetlist(setlist.id)){
            Toast.makeText(this,"La playlist « En cours » garde ce nom.",Toast.LENGTH_SHORT).show();
            return;
        }
        final android.widget.EditText input=new android.widget.EditText(this);
        input.setSingleLine(true);
        input.setText(setlist.name);
        input.selectAll();
        new AlertDialog.Builder(this)
            .setTitle("Renommer la liste")
            .setView(input)
            .setPositiveButton("Enregistrer",(d,w)->{
                String name=input.getText().toString().trim();
                if(name.isEmpty())return;
                setlist.name=name;
                AppStore.upsertSetlist(this,setlist);
                titleView.setText(name+" · "+setlist.songIds.size()+" titres");
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void toggleMode(){
        compact=!compact;
        getSharedPreferences("playlist_view",MODE_PRIVATE).edit().putBoolean("compact",compact).apply();
        modeButton.setText(compact ? "Détail" : "Compact");
        adapter.notifyDataSetChanged();
    }

    private void changeZoom(int delta){
        int next=Math.max(-5,Math.min(2,textZoom+delta));
        if(next==textZoom){
            Toast.makeText(this,
                delta<0 ? "Zoom minimum" : "Zoom maximum",
                Toast.LENGTH_SHORT).show();
            return;
        }
        textZoom=next;
        getSharedPreferences("playlist_view",MODE_PRIVATE).edit().putInt("text_zoom",textZoom).apply();
        adapter.notifyDataSetChanged();
    }

    private void showAddSongMenu(){
        String[] choices={"Depuis la bibliothèque","Recherche en ligne","Nouveau morceau","Medley"};
        new AlertDialog.Builder(this)
            .setTitle("Ajouter un morceau")
            .setItems(choices,(d,which)->{
                if(which==0) addSong();
                else if(which==1) searchOnlineSongForInsert();
                else if(which==2) createNewSong();
                else createNewMedley();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void createNewMedley(){
        Intent i=new Intent(this,MedleyEditorActivity.class);
        i.putExtra("target_setlist_id",setlist.id);
        i.putExtra("new_medley",true);
        startActivity(i);
    }

    private void createNewSong(){
        Intent i=new Intent(this,EditSongActivity.class);
        i.putExtra("target_setlist_id",setlist.id);
        i.putExtra("new_song",true);
        startActivity(i);
    }

    private void searchOnlineSongForInsert(){
        EditText input=new EditText(this);
        input.setHint("Titre ou artiste");
        input.setSingleLine(true);

        new AlertDialog.Builder(this)
            .setTitle("Rechercher un morceau")
            .setView(input)
            .setPositiveButton("Chercher",(d,w)->{
                String q=input.getText().toString().trim();
                if(q.isEmpty())return;
                Toast.makeText(this,"Recherche en ligne…",Toast.LENGTH_SHORT).show();
                new Thread(()->{
                    try{
                        List<SongCatalogLookup.Result> results=SongCatalogLookup.search(q,12);
                        runOnUiThread(()->showOnlineInsertResults(results));
                    }catch(Exception e){
                        runOnUiThread(()->Toast.makeText(this,"Recherche impossible : "+e.getMessage(),Toast.LENGTH_LONG).show());
                    }
                },"TS-Song-Search").start();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void showOnlineInsertResults(List<SongCatalogLookup.Result> results){
        if(results==null || results.isEmpty()){
            Toast.makeText(this,"Aucun morceau trouvé.",Toast.LENGTH_LONG).show();
            return;
        }

        String[] labels=new String[results.size()];
        for(int i=0;i<results.size();i++)labels[i]=results.get(i).toString();

        new AlertDialog.Builder(this)
            .setTitle("Choisir le morceau")
            .setItems(labels,(d,which)->{
                SongCatalogLookup.Result r=results.get(which);
                Toast.makeText(this,"Récupération des infos du morceau…",Toast.LENGTH_SHORT).show();
                new Thread(()->{
                    SongCatalogLookup.Result enriched=SongCatalogLookup.enrich(r);
                    if(enriched==null)enriched=r;
                    final SongCatalogLookup.Result selected=enriched;
                    runOnUiThread(()->{
                        Intent i=new Intent(this,EditSongActivity.class);
                        i.putExtra("target_setlist_id",setlist.id);
                        i.putExtra("new_song",true);
                        i.putExtra("prefill_title",selected.title);
                        i.putExtra("prefill_artist",selected.artist);
                        i.putExtra("prefill_key",selected.key);
                        i.putExtra("prefill_bpm",selected.bpm);
                        i.putExtra("prefill_duration",selected.duration);
                        startActivity(i);
                    });
                },"TS-Song-Insert-Metadata").start();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private static class TitleCorrection {
        final String songId;
        final String oldTitle;
        final String oldArtist;
        final SongCatalogLookup.Result result;
        TitleCorrection(String songId,String oldTitle,String oldArtist,SongCatalogLookup.Result result){
            this.songId=songId;
            this.oldTitle=oldTitle==null?"":oldTitle;
            this.oldArtist=oldArtist==null?"":oldArtist;
            this.result=result;
        }
    }

    private void findCorrectTitles(){
        if(setlist.songIds.isEmpty()){
            Toast.makeText(this,"Playlist vide.",Toast.LENGTH_SHORT).show();
            return;
        }

        Toast.makeText(this,"Recherche des titres et artistes…",Toast.LENGTH_LONG).show();

        new Thread(()->{
            List<TitleCorrection> corrections=new ArrayList<>();

            for(String id:setlist.songIds){
                Song s=AppStore.findSong(this,id);
                if(s==null || s.title==null || s.title.trim().isEmpty())continue;

                String query=((s.artist==null?"":s.artist)+" "+s.title).trim();
                try{
                    List<SongCatalogLookup.Result> results=SongCatalogLookup.search(query,3);
                    if(results.isEmpty())continue;

                    SongCatalogLookup.Result best=results.get(0);
                    boolean titleChanged=!s.title.trim().equals(best.title);
                    boolean artistChanged=!((s.artist==null?"":s.artist.trim()).equals(best.artist));
                    if(titleChanged || artistChanged){
                        corrections.add(new TitleCorrection(s.id,s.title,s.artist,best));
                    }
                    try{Thread.sleep(120);}catch(InterruptedException ignored){}
                }catch(Exception ignored){}
            }

            runOnUiThread(()->showTitleCorrections(corrections));
        },"TS-Playlist-Title-Fix").start();
    }

    private void showTitleCorrections(List<TitleCorrection> corrections){
        if(corrections==null || corrections.isEmpty()){
            Toast.makeText(this,"Aucune correction à proposer.",Toast.LENGTH_LONG).show();
            return;
        }

        String[] labels=new String[corrections.size()];
        boolean[] checked=new boolean[corrections.size()];
        for(int i=0;i<corrections.size();i++){
            TitleCorrection x=corrections.get(i);
            labels[i]=x.oldTitle+"  →  "+x.result.title+
                (x.result.artist.isEmpty()?"":" — "+x.result.artist);
            checked[i]=true;
        }

        new AlertDialog.Builder(this)
            .setTitle("Corrections proposées")
            .setMultiChoiceItems(labels,checked,(d,which,isChecked)->checked[which]=isChecked)
            .setPositiveButton("Appliquer",(d,w)->{
                int changed=0;
                for(int i=0;i<corrections.size();i++){
                    if(!checked[i])continue;
                    TitleCorrection x=corrections.get(i);
                    Song s=AppStore.findSong(this,x.songId);
                    if(s==null)continue;
                    s.title=x.result.title;
                    s.artist=x.result.artist;
                    if((s.duration==null || s.duration.trim().isEmpty()) && !x.result.duration.isEmpty())s.duration=x.result.duration;
                    if((s.bpm==null || s.bpm.trim().isEmpty()) && !x.result.bpm.isEmpty())s.bpm=x.result.bpm;
                    if((s.key==null || s.key.trim().isEmpty()) && !x.result.key.isEmpty())s.key=x.result.key;
                    AppStore.upsertSong(this,s);
                    changed++;
                }
                adapter.notifyDataSetChanged();
                Toast.makeText(this,changed+" morceau"+(changed>1?"x":"")+" corrigé"+(changed>1?"s":""),Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void editBpm(int pos){
        if(pos<0 || pos>=setlist.songIds.size())return;
        Song s=AppStore.findSong(this,setlist.songIds.get(pos));
        if(s==null)return;

        EditText input=new EditText(this);
        input.setHint("BPM");
        input.setSingleLine(true);
        input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        input.setText(s.bpm==null?"":s.bpm);
        input.selectAll();

        new AlertDialog.Builder(this)
            .setTitle("BPM — "+s.title)
            .setView(input)
            .setPositiveButton("Enregistrer",(d,w)->{
                String value=input.getText().toString().replaceAll("[^0-9]","");
                if(value.length()>3)value=value.substring(0,3);
                s.bpm=value;
                AppStore.upsertSong(this,s);
                adapter.notifyItemChanged(pos);
            })
            .setNeutralButton("Effacer",(d,w)->{
                s.bpm="";
                AppStore.upsertSong(this,s);
                adapter.notifyItemChanged(pos);
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private float zoomScale(){
        return Math.max(0.62f,Math.min(1.18f,1f+(textZoom*0.076f)));
    }

    private int zdp(int base){
        return Ui.dp(this,Math.max(1,Math.round(base*zoomScale())));
    }

    private float zsp(float base){
        return Math.max(7f,base*zoomScale());
    }

    private void addSong(){
        List<Song> all=AppStore.loadSongs(this);
        List<Song> available=new ArrayList<>();
        for(Song s:all){
            if(!setlist.songIds.contains(s.id)) available.add(s);
        }

        if(available.isEmpty()){
            Toast.makeText(this,"Tous les morceaux de la bibliothèque sont déjà dans cette playlist.",Toast.LENGTH_SHORT).show();
            return;
        }

        String[] names=new String[available.size()];
        for(int i=0;i<available.size();i++){
            Song s=available.get(i);
            names[i]=s.title+(s.bpm.isEmpty()?"":"   ·   "+s.bpm+" BPM");
        }

        new AlertDialog.Builder(this)
            .setTitle("Insérer un morceau")
            .setItems(names,(d,which)->{
                Song s=available.get(which);
                setlist.songIds.add(s.id);
                AppStore.upsertSetlist(this,setlist);
                adapter.notifyItemInserted(setlist.songIds.size()-1);
                updateCount();
                recycler.scrollToPosition(setlist.songIds.size()-1);
                Toast.makeText(this,"Ajouté. Maintiens le titre et glisse-le à la bonne place.",Toast.LENGTH_LONG).show();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void pageScroll(int direction){
        if(recycler==null)return;
        int page=Math.max(Ui.dp(this,120),recycler.getHeight()-Ui.dp(this,36));
        recycler.smoothScrollBy(0,direction*page);
    }

    private void updateCount(){
        if(titleView!=null){
            titleView.setText(setlist.name+" · "+setlist.songIds.size()+" titres");
        }
    }

    private String shortTitle(String title){
        if(title==null)return "";
        String t=title.trim();
        return t.length()<=15?t:t.substring(0,15);
    }

    private String twoDigits(String value){
        if(value==null || value.trim().isEmpty())return "--";
        String d=value.replaceAll("[^0-9]","");
        if(d.isEmpty())return "--";
        if(d.length()>2)d=d.substring(0,2);
        return d.length()==1?"0"+d:d;
    }

    private void editStageInfo(int pos){
        if(pos<0 || pos>=setlist.songIds.size())return;
        Song s=AppStore.findSong(this,setlist.songIds.get(pos));
        if(s==null)return;

        LinearLayout box=new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(Ui.dp(this,24),Ui.dp(this,8),Ui.dp(this,24),0);

        EditText n1=new EditText(this);
        n1.setHint("1er nombre (00–99)");
        n1.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        n1.setSingleLine(true);
        n1.setText(s.stageNum1);

        EditText n2=new EditText(this);
        n2.setHint("2e nombre rouge (00–99)");
        n2.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        n2.setSingleLine(true);
        n2.setText(s.stageNum2);

        CheckBox guitar=new CheckBox(this);
        guitar.setText("🎸 Guitare");
        guitar.setChecked(s.stageGuitar);

        CheckBox keyboard=new CheckBox(this);
        keyboard.setText("🎹 Clavier");
        keyboard.setChecked(s.stageKeyboard);

        box.addView(n1);
        box.addView(n2);
        box.addView(guitar);
        box.addView(keyboard);

        new AlertDialog.Builder(this)
            .setTitle(s.title)
            .setView(box)
            .setPositiveButton("Enregistrer",(d,w)->{
                String a=n1.getText().toString().replaceAll("[^0-9]","");
                String b=n2.getText().toString().replaceAll("[^0-9]","");
                if(a.length()>2)a=a.substring(0,2);
                if(b.length()>2)b=b.substring(0,2);
                s.stageNum1=a;
                s.stageNum2=b;
                s.stageGuitar=guitar.isChecked();
                s.stageKeyboard=keyboard.isChecked();
                AppStore.upsertSong(this,s);
                adapter.notifyItemChanged(pos);
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void toggleSongDisabled(int pos){
        if(pos<0 || pos>=setlist.songIds.size())return;
        String id=setlist.songIds.get(pos);
        boolean disabled=setlist.disabledSongIds.contains(id);
        if(disabled){
            setlist.disabledSongIds.remove(id);
            Toast.makeText(this,"Morceau réactivé",Toast.LENGTH_SHORT).show();
        }else{
            setlist.disabledSongIds.add(id);
            Toast.makeText(this,"Morceau désactivé · affiché à 50 %",Toast.LENGTH_SHORT).show();
        }
        AppStore.upsertSetlist(this,setlist);
        adapter.notifyItemChanged(pos);
    }

    private boolean isMedleySong(Song song){
        if(song==null || song.title==null)return false;
        String t=song.title.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("medley") || t.startsWith("meddley");
    }

    private void toggleMedleyExpanded(String songId,int pos){
        if(songId==null || songId.isEmpty())return;
        if(expandedMedleys.contains(songId))expandedMedleys.remove(songId);
        else expandedMedleys.add(songId);
        if(adapter!=null)adapter.notifyItemChanged(pos);
    }

    private void openMedleyEditor(String songId){
        Intent i=new Intent(this,MedleyEditorActivity.class);
        i.putExtra("song_id",songId);
        startActivity(i);
    }

    private void confirmRemoveSong(int pos){
        if(pos<0 || pos>=setlist.songIds.size())return;
        String id=setlist.songIds.get(pos);
        Song s=AppStore.findSong(this,id);
        String name=(s==null || s.title==null || s.title.trim().isEmpty()) ? "ce morceau" : "« "+s.title+" »";

        new AlertDialog.Builder(this)
            .setTitle("Retirer ce morceau ?")
            .setMessage(name+" sera retiré de cette playlist. Il restera dans la bibliothèque avec ses paroles.")
            .setPositiveButton("Retirer",(d,w)->{
                setlist.songIds.remove(pos);
                setlist.disabledSongIds.remove(id);
                AppStore.upsertSetlist(this,setlist);
                if(id.equals(currentSongId))currentSongId=null;
                adapter.notifyItemRemoved(pos);
                if(pos<setlist.songIds.size())adapter.notifyItemRangeChanged(pos,setlist.songIds.size()-pos);
                updateCount();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void openSong(int pos){
        if(pos<0 || pos>=setlist.songIds.size())return;
        Intent i=new Intent(this,LiveSongActivity.class);
        i.putExtra("song_id",setlist.songIds.get(pos));
        i.putExtra("setlist_id",setlist.id);
        i.putExtra("index",pos);
        startActivity(i);
    }

    private class PlaylistAdapter extends RecyclerView.Adapter<PlaylistAdapter.Holder>{
        class Holder extends RecyclerView.ViewHolder{
            final TextView num;
            final TextView song;
            final TextView bpm;
            final LinearLayout stageBox;
            final TextView stage1;
            final TextView stage2;
            final TextView guitarIcon;
            final TextView keyboardIcon;
            final TextView disable;
            final TextView delete;
            final TextView handle;
            final LinearLayout medleyBox;

            Holder(LinearLayout outer,TextView num,TextView song,TextView bpm,LinearLayout stageBox,TextView stage1,TextView stage2,TextView guitarIcon,TextView keyboardIcon,TextView disable,TextView delete,TextView handle,LinearLayout medleyBox){
                super(outer);
                this.num=num;
                this.song=song;
                this.bpm=bpm;
                this.stageBox=stageBox;
                this.stage1=stage1;
                this.stage2=stage2;
                this.guitarIcon=guitarIcon;
                this.keyboardIcon=keyboardIcon;
                this.disable=disable;
                this.delete=delete;
                this.handle=handle;
                this.medleyBox=medleyBox;
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent,int viewType){
            LinearLayout outer=new LinearLayout(PlaylistOverviewActivity.this);
            outer.setOrientation(LinearLayout.VERTICAL);
            outer.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            LinearLayout row=Ui.row(PlaylistOverviewActivity.this);
            row.setPadding(Ui.dp(PlaylistOverviewActivity.this,1),0,0,0);
            row.setMinimumHeight(Ui.dp(PlaylistOverviewActivity.this,32));

            TextView num=new TextView(PlaylistOverviewActivity.this);
            num.setTypeface(Typeface.DEFAULT_BOLD);
            num.setGravity(Gravity.CENTER);
            num.setMinWidth(Ui.dp(PlaylistOverviewActivity.this,28));

            TextView song=new TextView(PlaylistOverviewActivity.this);
            song.setTypeface(Typeface.DEFAULT_BOLD);
            song.setEllipsize(android.text.TextUtils.TruncateAt.END);
            song.setPadding(Ui.dp(PlaylistOverviewActivity.this,2),0,Ui.dp(PlaylistOverviewActivity.this,2),0);
            song.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

            TextView bpm=new TextView(PlaylistOverviewActivity.this);
            bpm.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            bpm.setTypeface(Typeface.DEFAULT_BOLD);
            bpm.setMinWidth(0);
            bpm.setPadding(Ui.dp(PlaylistOverviewActivity.this,2),0,0,0);
            bpm.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            LinearLayout stageBox=new LinearLayout(PlaylistOverviewActivity.this);
            stageBox.setOrientation(LinearLayout.HORIZONTAL);
            stageBox.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            stageBox.setPadding(Ui.dp(PlaylistOverviewActivity.this,2),0,Ui.dp(PlaylistOverviewActivity.this,2),0);
            GradientDrawable stageBg=new GradientDrawable();
            stageBg.setColor(Color.BLACK);
            stageBg.setCornerRadius(Ui.dp(PlaylistOverviewActivity.this,4));
            stageBg.setStroke(Ui.dp(PlaylistOverviewActivity.this,1),Color.rgb(70,70,70));
            stageBox.setBackground(stageBg);
            stageBox.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(PlaylistOverviewActivity.this,116),Ui.dp(PlaylistOverviewActivity.this,28)));

            TextView stage1=new TextView(PlaylistOverviewActivity.this);
            stage1.setTextColor(Color.WHITE);
            stage1.setTextSize(12);
            stage1.setGravity(Gravity.CENTER);
            stage1.setMinWidth(0);
            stage1.setPadding(Ui.dp(PlaylistOverviewActivity.this,2),0,Ui.dp(PlaylistOverviewActivity.this,2),0);

            TextView stage2=new TextView(PlaylistOverviewActivity.this);
            stage2.setTextColor(Color.RED);
            stage2.setTextSize(12);
            stage2.setGravity(Gravity.CENTER);
            stage2.setMinWidth(0);
            stage2.setPadding(Ui.dp(PlaylistOverviewActivity.this,2),0,Ui.dp(PlaylistOverviewActivity.this,2),0);

            TextView guitarIcon=new TextView(PlaylistOverviewActivity.this);
            guitarIcon.setText("🎸");
            guitarIcon.setTextSize(14);
            guitarIcon.setGravity(Gravity.CENTER);
            guitarIcon.setMinWidth(0);
            guitarIcon.setPadding(Ui.dp(PlaylistOverviewActivity.this,1),0,Ui.dp(PlaylistOverviewActivity.this,1),0);

            TextView keyboardIcon=new TextView(PlaylistOverviewActivity.this);
            keyboardIcon.setText("🎹");
            keyboardIcon.setTextSize(14);
            keyboardIcon.setGravity(Gravity.CENTER);
            keyboardIcon.setMinWidth(0);
            keyboardIcon.setPadding(Ui.dp(PlaylistOverviewActivity.this,1),0,Ui.dp(PlaylistOverviewActivity.this,1),0);

            stageBox.addView(stage1);
            stageBox.addView(stage2);
            stageBox.addView(guitarIcon);
            stageBox.addView(keyboardIcon);

            TextView disable=new TextView(PlaylistOverviewActivity.this);
            disable.setText("◐");
            disable.setTextColor(Color.LTGRAY);
            disable.setTextSize(17);
            disable.setGravity(Gravity.CENTER);
            disable.setContentDescription("Désactiver / réactiver le morceau");
            disable.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(PlaylistOverviewActivity.this,30),Ui.dp(PlaylistOverviewActivity.this,32)));

            TextView delete=new TextView(PlaylistOverviewActivity.this);
            delete.setText("🗑");
            delete.setTextColor(Color.LTGRAY);
            delete.setTextSize(16);
            delete.setGravity(Gravity.CENTER);
            delete.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(PlaylistOverviewActivity.this,34),Ui.dp(PlaylistOverviewActivity.this,32)));

            TextView handle=new TextView(PlaylistOverviewActivity.this);
            handle.setText("≡");
            handle.setTextColor(Color.LTGRAY);
            handle.setTextSize(20);
            handle.setGravity(Gravity.CENTER);
            handle.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(PlaylistOverviewActivity.this,30),Ui.dp(PlaylistOverviewActivity.this,32)));

            row.addView(num);
            row.addView(song);
            row.addView(bpm);
            row.addView(stageBox);
            row.addView(disable);
            row.addView(delete);
            row.addView(handle);

            LinearLayout medleyBox=new LinearLayout(PlaylistOverviewActivity.this);
            medleyBox.setOrientation(LinearLayout.VERTICAL);
            medleyBox.setPadding(Ui.dp(PlaylistOverviewActivity.this,38),Ui.dp(PlaylistOverviewActivity.this,3),Ui.dp(PlaylistOverviewActivity.this,8),Ui.dp(PlaylistOverviewActivity.this,5));
            medleyBox.setBackgroundColor(Color.rgb(18,18,18));
            medleyBox.setVisibility(View.GONE);

            outer.addView(row,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
            outer.addView(medleyBox,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));

            Holder h=new Holder(outer,num,song,bpm,stageBox,stage1,stage2,guitarIcon,keyboardIcon,disable,delete,handle,medleyBox);
            row.setOnClickListener(v->{
                int p=h.getBindingAdapterPosition();
                if(p==RecyclerView.NO_POSITION)return;
                Song item=AppStore.findSong(PlaylistOverviewActivity.this,setlist.songIds.get(p));
                if(item!=null && isMedleySong(item) && !item.medleyItems.isEmpty()){
                    toggleMedleyExpanded(item.id,p);
                }else{
                    openSong(p);
                }
            });
            stageBox.setOnClickListener(v->{
                int p=h.getBindingAdapterPosition();
                if(p!=RecyclerView.NO_POSITION)editStageInfo(p);
            });

            bpm.setOnClickListener(v->{
                int p=h.getBindingAdapterPosition();
                if(p!=RecyclerView.NO_POSITION)editBpm(p);
            });

            disable.setOnClickListener(v->{
                int p=h.getBindingAdapterPosition();
                if(p!=RecyclerView.NO_POSITION)toggleSongDisabled(p);
            });

            delete.setOnClickListener(v->{
                int p=h.getBindingAdapterPosition();
                if(p!=RecyclerView.NO_POSITION)confirmRemoveSong(p);
            });

            handle.setOnTouchListener((v,event)->{
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                    touchHelper.startDrag(h);
                    return true;
                }
                return false;
            });
            return h;
        }

        @Override public void onBindViewHolder(Holder h,int position){
            String id=setlist.songIds.get(position);
            Song s=AppStore.findSong(PlaylistOverviewActivity.this,id);

            h.itemView.setMinimumHeight(zdp(compact?32:42));
            h.num.setMinWidth(zdp(28));
            h.num.setText(String.format("%02d",position+1));
            h.num.setTextSize(zsp(compact?12:16));
            h.song.setTextSize(zsp(compact?13:17));
            h.song.setPadding(zdp(2),0,zdp(2),0);
            h.bpm.setTextSize(zsp(compact?12:15));
            h.bpm.setPadding(zdp(2),0,0,0);
            h.song.setSingleLine(compact);

            h.disable.setTextSize(zsp(17));
            h.disable.setLayoutParams(new LinearLayout.LayoutParams(zdp(30),zdp(compact?32:40)));
            h.delete.setTextSize(zsp(16));
            h.delete.setLayoutParams(new LinearLayout.LayoutParams(zdp(34),zdp(compact?32:40)));
            h.handle.setTextSize(zsp(20));
            h.handle.setLayoutParams(new LinearLayout.LayoutParams(zdp(30),zdp(compact?32:40)));

            boolean medley=s!=null && isMedleySong(s) && !s.medleyItems.isEmpty();
            boolean medleyExpanded=medley && expandedMedleys.contains(id);
            String medleyPrefix=medley ? (medleyExpanded?"▾ ":"▸ ") : "";

            boolean hasStageInfo=s!=null && (
                (s.stageNum1!=null && !s.stageNum1.trim().isEmpty()) ||
                (s.stageNum2!=null && !s.stageNum2.trim().isEmpty()) ||
                s.stageGuitar || s.stageKeyboard
            );

            if(s==null){
                h.song.setText("Morceau introuvable");
                h.bpm.setText("");
                h.bpm.setVisibility(View.GONE);
            }else if(compact){
                h.song.setText(medleyPrefix+s.title);
                h.bpm.setText(s.bpm.isEmpty()?"＋":s.bpm);
                h.bpm.setVisibility(View.VISIBLE);
            }else{
                String meta="";
                if(!s.artist.isEmpty())meta=s.artist;
                if(!s.key.isEmpty())meta+=(meta.isEmpty()?"":" · ")+s.key;
                h.song.setText(medleyPrefix+s.title+(meta.isEmpty()?"":"\n"+meta));
                h.bpm.setText(s.bpm.isEmpty()?"＋":s.bpm+" BPM");
                h.bpm.setVisibility(View.VISIBLE);
            }

            if(s!=null && hasStageInfo){
                boolean showNum1=s.stageNum1!=null && !s.stageNum1.trim().isEmpty();
                boolean showNum2=s.stageNum2!=null && !s.stageNum2.trim().isEmpty();
                boolean showGuitar=s.stageGuitar;
                boolean showKeyboard=s.stageKeyboard;

                h.stageBox.setVisibility(View.VISIBLE);
                h.stageBox.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    zdp(28)
                ));
                h.stageBox.setPadding(zdp(2),0,zdp(2),0);

                GradientDrawable stageBg=new GradientDrawable();
                stageBg.setColor(Color.BLACK);
                stageBg.setCornerRadius(zdp(4));
                stageBg.setStroke(Math.max(1,zdp(1)),Color.rgb(70,70,70));
                h.stageBox.setBackground(stageBg);

                h.stage1.setVisibility(showNum1?View.VISIBLE:View.GONE);
                h.stage2.setVisibility(showNum2?View.VISIBLE:View.GONE);
                h.guitarIcon.setVisibility(showGuitar?View.VISIBLE:View.GONE);
                h.keyboardIcon.setVisibility(showKeyboard?View.VISIBLE:View.GONE);

                h.stage1.setTextColor(Color.WHITE);
                h.stage1.setTextSize(zsp(12));
                h.stage1.setGravity(Gravity.CENTER);
                h.stage1.setPadding(zdp(2),0,zdp(2),0);
                h.stage2.setPadding(zdp(2),0,zdp(2),0);
                h.guitarIcon.setPadding(zdp(1),0,zdp(1),0);
                h.keyboardIcon.setPadding(zdp(1),0,zdp(1),0);
                if(showNum1) h.stage1.setText(twoDigits(s.stageNum1));
                if(showNum2) h.stage2.setText(twoDigits(s.stageNum2));
                h.stage2.setTextSize(zsp(12));
                h.guitarIcon.setTextSize(zsp(14));
                h.keyboardIcon.setTextSize(zsp(14));
                h.guitarIcon.setAlpha(1f);
                h.keyboardIcon.setAlpha(1f);
            }else{
                h.stageBox.setVisibility(View.GONE);
            }

            h.medleyBox.removeAllViews();
            if(medley && medleyExpanded){
                h.medleyBox.setVisibility(View.VISIBLE);

                TextView editMedley=new TextView(PlaylistOverviewActivity.this);
                editMedley.setText("✎ Modifier le medley");
                editMedley.setTextColor(Color.rgb(255,193,7));
                editMedley.setTextSize(zsp(12));
                editMedley.setTypeface(Typeface.DEFAULT_BOLD);
                editMedley.setPadding(0,zdp(2),0,zdp(4));
                editMedley.setOnClickListener(v->openMedleyEditor(id));
                h.medleyBox.addView(editMedley,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));

                for(int mi=0;mi<s.medleyItems.size();mi++){
                    TextView item=new TextView(PlaylistOverviewActivity.this);
                    String medleyArtist=mi<s.medleyArtists.size()?s.medleyArtists.get(mi):"";
                    String medleyLabel=s.medleyItems.get(mi)+(medleyArtist==null || medleyArtist.trim().isEmpty()?"":" — "+medleyArtist.trim());
                    item.setText(String.format(Locale.ROOT,"%02d. %s",mi+1,medleyLabel));
                    item.setTextColor(Color.WHITE);
                    item.setTextSize(zsp(12));
                    item.setPadding(zdp(8),zdp(2),0,zdp(2));
                    h.medleyBox.addView(item,new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ));
                }
            }else{
                h.medleyBox.setVisibility(View.GONE);
            }

            boolean disabled=setlist.disabledSongIds.contains(id);
            h.itemView.setAlpha(disabled?0.5f:1f);
            h.disable.setText(disabled?"●":"◐");
            h.disable.setTextColor(disabled?Color.rgb(239,83,80):Color.LTGRAY);
            h.disable.setContentDescription(disabled?"Réactiver le morceau":"Désactiver le morceau");

            boolean current=id.equals(currentSongId);
            int fg=current ? Color.rgb(255,193,7) : Color.WHITE;
            h.song.setTextColor(fg);
            h.bpm.setTextColor(s!=null && (s.bpm==null || s.bpm.isEmpty()) ? Color.GRAY : fg);
            h.num.setTextColor(current ? Color.rgb(255,193,7) : Color.LTGRAY);

            // Odd-numbered titles (1,3,5...) use a dark grey stripe.
            int stripe=(position%2==0) ? Color.rgb(31,31,31) : Color.rgb(10,10,10);
            h.itemView.setBackgroundColor(current ? Color.rgb(55,48,25) : stripe);
        }

        @Override public int getItemCount(){ return setlist.songIds.size(); }
    }
}
