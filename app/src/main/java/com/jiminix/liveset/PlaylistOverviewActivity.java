package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.Intent;
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

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setlistId=getIntent().getStringExtra("setlist_id");
        int currentIndex=getIntent().getIntExtra("current_index",-1);
        setlist=AppStore.findSetlist(this,setlistId);
        if(setlist==null){ finish(); return; }
        repairMissingLyrics();
        if(currentIndex>=0 && currentIndex<setlist.songIds.size()) currentSongId=setlist.songIds.get(currentIndex);
        compact=getSharedPreferences("playlist_view",MODE_PRIVATE).getBoolean("compact",true);
        textZoom=Math.max(-3,Math.min(2,getSharedPreferences("playlist_view",MODE_PRIVATE).getInt("text_zoom",0)));
        buildUi();
    }

    @Override protected void onResume(){
        super.onResume();
        SetListModel fresh=AppStore.findSetlist(this,setlistId);
        if(fresh!=null){
            setlist=fresh;
            repairMissingLyrics();
            if(adapter!=null) adapter.notifyDataSetChanged();
        }
    }

    private void buildUi(){
        root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(10,10,10));

        LinearLayout head=Ui.row(this);
        head.setPadding(Ui.dp(this,6),Ui.dp(this,4),Ui.dp(this,6),Ui.dp(this,4));

        TextView back=new TextView(this);
        back.setText("‹");
        back.setTextColor(Color.WHITE);
        back.setTextSize(30);
        back.setGravity(Gravity.CENTER);
        back.setClickable(true);
        back.setFocusable(true);
        back.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this,44),Ui.dp(this,52)));

        titleView=new TextView(this);
        titleView.setText(setlist.name);
        titleView.setTextColor(Color.WHITE);
        titleView.setTextSize(16);
        titleView.setTypeface(Typeface.DEFAULT_BOLD);
        titleView.setSingleLine(true);
        titleView.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titleView.setGravity(Gravity.CENTER_VERTICAL);
        titleView.setPadding(Ui.dp(this,5),0,Ui.dp(this,4),0);
        titleView.setLayoutParams(new LinearLayout.LayoutParams(0,Ui.dp(this,48),1));
        titleView.setClickable(true);

        Button rename=Ui.button(this,"✎");
        rename.setTextSize(16);
        Ui.compactHeaderButton(rename,this,42);

        Button add=Ui.button(this,"＋");
        add.setTextSize(20);
        Ui.compactHeaderButton(add,this,44);

        modeButton=Ui.button(this, compact ? "Détail" : "Compact");
        modeButton.setTextSize(13);
        Ui.compactHeaderButton(modeButton,this,74);

        TextView count=new TextView(this);
        count.setText(setlist.songIds.size()+" titres");
        count.setTextColor(Color.LTGRAY);
        count.setTextSize(11);
        count.setSingleLine(true);
        count.setGravity(Gravity.CENTER);
        count.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this,58),Ui.dp(this,44)));
        count.setTag("count");

        head.addView(back);
        head.addView(titleView);
        head.addView(rename);
        head.addView(add);
        head.addView(modeButton);
        head.addView(count);
        root.addView(head);

        TextView sub=new TextView(this);
        sub.setText(compact ? "Maintiens et glisse un titre pour le déplacer" : "Maintiens et glisse un titre pour le déplacer");
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(10);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(Ui.dp(this,6),0,Ui.dp(this,6),Ui.dp(this,2));
        sub.setTag("sub");
        root.addView(sub);

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
        actions.setPadding(Ui.dp(this,4),Ui.dp(this,2),Ui.dp(this,4),Ui.dp(this,2));
        Button addLibrary=Ui.button(this,"＋ Bibliothèque");
        Button importTitles=Ui.button(this,"⇩ Importer");
        Button viewer=Ui.button(this,"▣ Viewer");
        Button zoom=Ui.button(this,zoomLabel());
        addLibrary.setTextSize(13);
        importTitles.setTextSize(13);
        viewer.setTextSize(13);
        zoom.setTextSize(11);
        Ui.weight(addLibrary,1);
        Ui.weight(importTitles,1);
        Ui.weight(viewer,1);
        zoom.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this,66),ViewGroup.LayoutParams.WRAP_CONTENT));
        actions.addView(addLibrary);
        actions.addView(importTitles);
        actions.addView(viewer);
        actions.addView(zoom);
        root.addView(actions);

        back.setOnClickListener(v->finish());
        titleView.setOnClickListener(v->renameList());
        rename.setOnClickListener(v->renameList());
        add.setOnClickListener(v->showAddSongMenu());
        addLibrary.setOnClickListener(v->addSong());
        importTitles.setOnClickListener(v->openImporter());
        viewer.setOnClickListener(v->openViewer());
        zoom.setOnClickListener(v->showZoomDialog(zoom));
        modeButton.setOnClickListener(v->toggleMode());

        Ui.applySafeArea(root);
        setContentView(root);
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
        AppStore.selectViewerSetlist(this,setlist.id);

        Intent viewer=new Intent(this,IntegratedViewerActivity.class);
        viewer.putExtra("setlist_id",setlist.id);
        startActivity(viewer);

        // Keep the Internet copy updated for the remote Viewer phones.
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

    private void openImporter(){
        Intent i=new Intent(this,ImportActivity.class);
        i.putExtra("target_setlist_id",setlist.id);
        startActivity(i);
    }

    private void renameList(){
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
                titleView.setText(name);
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

    private String zoomLabel(){
        return "Zoom "+(textZoom>0?"+":"")+textZoom;
    }

    private void showZoomDialog(Button zoomButton){
        final String[] levels={"−3","−2","−1","0","+1","+2"};
        int checked=textZoom+3;
        new AlertDialog.Builder(this)
            .setTitle("Taille des titres")
            .setSingleChoiceItems(levels,checked,(d,which)->{
                textZoom=which-3;
                getSharedPreferences("playlist_view",MODE_PRIVATE).edit().putInt("text_zoom",textZoom).apply();
                zoomButton.setText(zoomLabel());
                adapter.notifyDataSetChanged();
                d.dismiss();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void showAddSongMenu(){
        String[] choices={"Depuis la bibliothèque","Nouveau morceau"};
        new AlertDialog.Builder(this)
            .setTitle("Ajouter un morceau")
            .setItems(choices,(d,which)->{
                if(which==0) addSong();
                else createNewSong();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void createNewSong(){
        Intent i=new Intent(this,EditSongActivity.class);
        i.putExtra("target_setlist_id",setlist.id);
        i.putExtra("new_song",true);
        startActivity(i);
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

    private void updateCount(){
        TextView count=root.findViewWithTag("count");
        if(count!=null)count.setText(setlist.songIds.size()+" titres");
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
            final TextView delete;
            final TextView handle;

            Holder(LinearLayout row,TextView num,TextView song,TextView bpm,LinearLayout stageBox,TextView stage1,TextView stage2,TextView guitarIcon,TextView keyboardIcon,TextView delete,TextView handle){
                super(row);
                this.num=num;
                this.song=song;
                this.bpm=bpm;
                this.stageBox=stageBox;
                this.stage1=stage1;
                this.stage2=stage2;
                this.guitarIcon=guitarIcon;
                this.keyboardIcon=keyboardIcon;
                this.delete=delete;
                this.handle=handle;
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent,int viewType){
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
            row.addView(delete);
            row.addView(handle);

            Holder h=new Holder(row,num,song,bpm,stageBox,stage1,stage2,guitarIcon,keyboardIcon,delete,handle);
            row.setOnClickListener(v->{
                int p=h.getBindingAdapterPosition();
                if(p!=RecyclerView.NO_POSITION)openSong(p);
            });
            stageBox.setOnClickListener(v->{
                int p=h.getBindingAdapterPosition();
                if(p!=RecyclerView.NO_POSITION)editStageInfo(p);
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

            h.num.setText(String.format("%02d",position+1));
            h.num.setTextSize((compact?12:16)+textZoom);
            h.song.setTextSize((compact?13:17)+textZoom);
            h.bpm.setTextSize((compact?12:15)+textZoom);
            h.song.setSingleLine(compact);

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
                h.song.setText(s.title);
                h.bpm.setText(s.bpm.isEmpty()?"":s.bpm);
                h.bpm.setVisibility(s.bpm.isEmpty()?View.GONE:View.VISIBLE);
            }else{
                String meta="";
                if(!s.artist.isEmpty())meta=s.artist;
                if(!s.key.isEmpty())meta+=(meta.isEmpty()?"":" · ")+s.key;
                h.song.setText(s.title+(meta.isEmpty()?"":"\n"+meta));
                h.bpm.setText(s.bpm.isEmpty()?"":s.bpm+" BPM");
                h.bpm.setVisibility(s.bpm.isEmpty()?View.GONE:View.VISIBLE);
            }

            if(s!=null && hasStageInfo){
                boolean showNum1=s.stageNum1!=null && !s.stageNum1.trim().isEmpty();
                boolean showNum2=s.stageNum2!=null && !s.stageNum2.trim().isEmpty();
                boolean showGuitar=s.stageGuitar;
                boolean showKeyboard=s.stageKeyboard;

                h.stageBox.setVisibility(View.VISIBLE);
                h.stageBox.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    Ui.dp(PlaylistOverviewActivity.this,28)
                ));

                GradientDrawable stageBg=new GradientDrawable();
                stageBg.setColor(Color.BLACK);
                stageBg.setCornerRadius(Ui.dp(PlaylistOverviewActivity.this,4));
                stageBg.setStroke(Ui.dp(PlaylistOverviewActivity.this,1),Color.rgb(70,70,70));
                h.stageBox.setBackground(stageBg);

                h.stage1.setVisibility(showNum1?View.VISIBLE:View.GONE);
                h.stage2.setVisibility(showNum2?View.VISIBLE:View.GONE);
                h.guitarIcon.setVisibility(showGuitar?View.VISIBLE:View.GONE);
                h.keyboardIcon.setVisibility(showKeyboard?View.VISIBLE:View.GONE);

                h.stage1.setTextColor(Color.WHITE);
                h.stage1.setTextSize(12+textZoom);
                h.stage1.setGravity(Gravity.CENTER);
                if(showNum1) h.stage1.setText(twoDigits(s.stageNum1));
                if(showNum2) h.stage2.setText(twoDigits(s.stageNum2));
                h.stage2.setTextSize(12+textZoom);
                h.guitarIcon.setTextSize(14+textZoom);
                h.keyboardIcon.setTextSize(14+textZoom);
                h.guitarIcon.setAlpha(1f);
                h.keyboardIcon.setAlpha(1f);
            }else{
                h.stageBox.setVisibility(View.GONE);
            }

            boolean current=id.equals(currentSongId);
            int fg=current ? Color.rgb(255,193,7) : Color.WHITE;
            h.song.setTextColor(fg);
            h.bpm.setTextColor(fg);
            h.num.setTextColor(current ? Color.rgb(255,193,7) : Color.LTGRAY);

            // Odd-numbered titles (1,3,5...) use a dark grey stripe.
            int stripe=(position%2==0) ? Color.rgb(31,31,31) : Color.rgb(10,10,10);
            h.itemView.setBackgroundColor(current ? Color.rgb(55,48,25) : stripe);
        }

        @Override public int getItemCount(){ return setlist.songIds.size(); }
    }
}
