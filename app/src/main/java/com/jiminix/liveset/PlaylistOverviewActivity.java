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

public class PlaylistOverviewActivity extends AppCompatActivity {
    private String setlistId;
    private SetListModel setlist;
    private String currentSongId=null;
    private boolean compact=true;
    private LinearLayout root;
    private Button modeButton;
    private RecyclerView recycler;
    private PlaylistAdapter adapter;
    private ItemTouchHelper touchHelper;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setlistId=getIntent().getStringExtra("setlist_id");
        int currentIndex=getIntent().getIntExtra("current_index",-1);
        setlist=AppStore.findSetlist(this,setlistId);
        if(setlist==null){ finish(); return; }
        if(currentIndex>=0 && currentIndex<setlist.songIds.size()) currentSongId=setlist.songIds.get(currentIndex);
        compact=getSharedPreferences("playlist_view",MODE_PRIVATE).getBoolean("compact",true);
        buildUi();
    }

    @Override protected void onResume(){
        super.onResume();
        SetListModel fresh=AppStore.findSetlist(this,setlistId);
        if(fresh!=null){
            setlist=fresh;
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

        TextView title=new TextView(this);
        title.setText(setlist.name);
        title.setTextColor(Color.WHITE);
        title.setTextSize(17);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setPadding(Ui.dp(this,6),0,Ui.dp(this,6),0);
        title.setLayoutParams(new LinearLayout.LayoutParams(0,Ui.dp(this,52),1));

        Button add=Ui.button(this,"＋");
        add.setTextSize(22);
        Ui.compactHeaderButton(add,this,48);

        modeButton=Ui.button(this, compact ? "Détail" : "Compact");
        modeButton.setTextSize(13);
        Ui.compactHeaderButton(modeButton,this,82);

        TextView count=new TextView(this);
        count.setText(setlist.songIds.size()+" titres");
        count.setTextColor(Color.LTGRAY);
        count.setTextSize(12);
        count.setSingleLine(true);
        count.setGravity(Gravity.CENTER);
        count.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this,68),Ui.dp(this,48)));
        count.setTag("count");

        head.addView(back);
        head.addView(title);
        head.addView(add);
        head.addView(modeButton);
        head.addView(count);
        root.addView(head);

        TextView sub=new TextView(this);
        sub.setText(compact ? "Maintiens et glisse un titre pour le déplacer" : "Maintiens et glisse un titre pour le déplacer");
        sub.setTextColor(Color.LTGRAY);
        sub.setTextSize(12);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),Ui.dp(this,6));
        sub.setTag("sub");
        root.addView(sub);

        recycler=new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setBackgroundColor(Color.rgb(10,10,10));
        recycler.setPadding(Ui.dp(this,6),0,Ui.dp(this,6),Ui.dp(this,16));
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
                adapter.notifyItemChanged(vh.getBindingAdapterPosition());
                AppStore.upsertSetlist(PlaylistOverviewActivity.this,setlist);
            }
        };
        touchHelper=new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recycler);

        back.setOnClickListener(v->finish());
        add.setOnClickListener(v->addSong());
        modeButton.setOnClickListener(v->toggleMode());

        Ui.applySafeArea(root);
        setContentView(root);
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
            final TextView handle;

            Holder(LinearLayout row,TextView num,TextView song,TextView bpm,TextView handle){
                super(row);
                this.num=num;
                this.song=song;
                this.bpm=bpm;
                this.handle=handle;
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent,int viewType){
            LinearLayout row=Ui.row(PlaylistOverviewActivity.this);
            row.setPadding(Ui.dp(PlaylistOverviewActivity.this,4),Ui.dp(PlaylistOverviewActivity.this,1),Ui.dp(PlaylistOverviewActivity.this,2),Ui.dp(PlaylistOverviewActivity.this,1));
            row.setMinimumHeight(Ui.dp(PlaylistOverviewActivity.this,48));

            TextView num=new TextView(PlaylistOverviewActivity.this);
            num.setTypeface(Typeface.DEFAULT_BOLD);
            num.setGravity(Gravity.CENTER);
            num.setMinWidth(Ui.dp(PlaylistOverviewActivity.this,38));

            TextView song=new TextView(PlaylistOverviewActivity.this);
            song.setTypeface(Typeface.DEFAULT_BOLD);
            song.setEllipsize(android.text.TextUtils.TruncateAt.END);
            song.setPadding(Ui.dp(PlaylistOverviewActivity.this,5),Ui.dp(PlaylistOverviewActivity.this,5),Ui.dp(PlaylistOverviewActivity.this,5),Ui.dp(PlaylistOverviewActivity.this,5));
            song.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

            TextView bpm=new TextView(PlaylistOverviewActivity.this);
            bpm.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            bpm.setTypeface(Typeface.DEFAULT_BOLD);
            bpm.setMinWidth(Ui.dp(PlaylistOverviewActivity.this,58));
            bpm.setPadding(Ui.dp(PlaylistOverviewActivity.this,4),0,Ui.dp(PlaylistOverviewActivity.this,4),0);

            TextView handle=new TextView(PlaylistOverviewActivity.this);
            handle.setText("≡");
            handle.setTextColor(Color.LTGRAY);
            handle.setTextSize(26);
            handle.setGravity(Gravity.CENTER);
            handle.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(PlaylistOverviewActivity.this,42),Ui.dp(PlaylistOverviewActivity.this,48)));

            row.addView(num);
            row.addView(song);
            row.addView(bpm);
            row.addView(handle);

            Holder h=new Holder(row,num,song,bpm,handle);
            row.setOnClickListener(v->{
                int p=h.getBindingAdapterPosition();
                if(p!=RecyclerView.NO_POSITION)openSong(p);
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
            h.num.setTextSize(compact?14:16);
            h.song.setTextSize(compact?15:17);
            h.bpm.setTextSize(compact?14:15);
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
            h.itemView.setBackgroundColor(current ? Color.rgb(38,38,38) : Color.rgb(10,10,10));
        }

        @Override public int getItemCount(){ return setlist.songIds.size(); }
    }
}
