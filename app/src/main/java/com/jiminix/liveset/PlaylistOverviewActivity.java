package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
    private String setlistId;
    private SetListModel setlist;
    private String currentSongId=null;
    private boolean compact=true;
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
        addLibrary.setTextSize(14);
        importTitles.setTextSize(14);
        Ui.weight(addLibrary,1);
        Ui.weight(importTitles,1);
        actions.addView(addLibrary);
        actions.addView(importTitles);
        root.addView(actions);

        back.setOnClickListener(v->finish());
        titleView.setOnClickListener(v->renameList());
        rename.setOnClickListener(v->renameList());
        add.setOnClickListener(v->addSong());
        addLibrary.setOnClickListener(v->addSong());
        importTitles.setOnClickListener(v->openImporter());
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
            final TextView delete;
            final TextView handle;

            Holder(LinearLayout row,TextView num,TextView song,TextView bpm,TextView delete,TextView handle){
                super(row);
                this.num=num;
                this.song=song;
                this.bpm=bpm;
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
            bpm.setMinWidth(Ui.dp(PlaylistOverviewActivity.this,44));
            bpm.setPadding(Ui.dp(PlaylistOverviewActivity.this,2),0,Ui.dp(PlaylistOverviewActivity.this,2),0);

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
            row.addView(delete);
            row.addView(handle);

            Holder h=new Holder(row,num,song,bpm,delete,handle);
            row.setOnClickListener(v->{
                int p=h.getBindingAdapterPosition();
                if(p!=RecyclerView.NO_POSITION)openSong(p);
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
            h.num.setTextSize(compact?12:16);
            h.song.setTextSize(compact?13:17);
            h.bpm.setTextSize(compact?12:15);
            h.song.setSingleLine(compact);

            if(s==null){
                h.song.setText("Morceau introuvable");
                h.bpm.setText("");
            }else if(compact){
                h.song.setText(s.title);
                h.bpm.setText(s.bpm.isEmpty()?"":s.bpm);
            }else{
                String meta="";
                if(!s.artist.isEmpty())meta=s.artist;
                if(!s.key.isEmpty())meta+=(meta.isEmpty()?"":" · ")+s.key;
                h.song.setText(s.title+(meta.isEmpty()?"":"\n"+meta));
                h.bpm.setText(s.bpm.isEmpty()?"":s.bpm+" BPM");
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
