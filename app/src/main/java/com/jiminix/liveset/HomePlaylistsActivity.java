package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class HomePlaylistsActivity extends AppCompatActivity {
    // V0.24 draggable playlists
    // Build V0.24
    // Build V0.28
    // Build V0.76 show reserved En cours below its principal playlist
    private RecyclerView recycler;
    private PlaylistHomeAdapter adapter;
    private List<SetListModel> lists;
    private final List<SetListModel> displayLists=new ArrayList<>();
    private SetListModel inProgress;
    private ItemTouchHelper touchHelper;
    private TextView empty;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        buildUi();
        loadLists();
    }

    @Override protected void onResume(){
        super.onResume();
        if(recycler!=null) loadLists();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18,18,18));

        PlaylistBannerView banner=new PlaylistBannerView(this);
        root.addView(banner,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout head=Ui.row(this);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView spacer=new TextView(this);
        spacer.setText("");
        Ui.weight(spacer,1);

        Button library=Ui.button(this,"Titres");
        library.setTextSize(13);
        Ui.compactHeaderButton(library,this,82);

        Button add=Ui.button(this,"＋");
        add.setTextSize(24);
        Ui.compactHeaderButton(add,this,54);

        head.addView(spacer);
        head.addView(library);
        head.addView(add);
        root.addView(head);

        TextView version=new TextView(this);
        version.setText("LiveSet v"+installedVersion());
        version.setTextColor(Color.LTGRAY);
        version.setTextSize(11);
        version.setGravity(Gravity.RIGHT);
        version.setPadding(0,0,Ui.dp(this,12),Ui.dp(this,2));
        root.addView(version);

        TextView hint=new TextView(this);
        hint.setText("Maintiens ≡ et glisse pour classer les playlists");
        hint.setTextColor(Color.LTGRAY);
        hint.setTextSize(11);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(Ui.dp(this,8),0,Ui.dp(this,8),Ui.dp(this,5));
        root.addView(hint);

        empty=Ui.title(this,"Aucune playlist");
        empty.setTextSize(20);
        empty.setGravity(Gravity.CENTER);
        empty.setVisibility(View.GONE);
        root.addView(empty,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        recycler=new RecyclerView(this);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        recycler.setBackgroundColor(Color.rgb(18,18,18));
        recycler.setPadding(Ui.dp(this,4),0,Ui.dp(this,4),Ui.dp(this,70));
        recycler.setClipToPadding(false);
        adapter=new PlaylistHomeAdapter();
        recycler.setAdapter(adapter);
        root.addView(recycler,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        ItemTouchHelper.Callback callback=new ItemTouchHelper.SimpleCallback(ItemTouchHelper.UP|ItemTouchHelper.DOWN,0){
            @Override public boolean onMove(RecyclerView rv,RecyclerView.ViewHolder from,RecyclerView.ViewHolder to){
                int a=from.getBindingAdapterPosition();
                int b=to.getBindingAdapterPosition();
                if(a==RecyclerView.NO_POSITION || b==RecyclerView.NO_POSITION)return false;
                if(a<0 || b<0 || a>=displayLists.size() || b>=displayLists.size())return false;

                SetListModel fromList=displayLists.get(a);
                SetListModel toList=displayLists.get(b);
                if(isProgressRow(fromList) || isProgressRow(toList))return false;

                int fromRegular=indexOfRegular(fromList.id);
                int toRegular=indexOfRegular(toList.id);
                if(fromRegular<0 || toRegular<0)return false;

                Collections.swap(lists,fromRegular,toRegular);
                saveRegularPlaylistOrder();
                rebuildDisplayLists();
                adapter.notifyDataSetChanged();
                return true;
            }

            @Override public void onSwiped(RecyclerView.ViewHolder vh,int direction){}

            @Override public boolean isLongPressDragEnabled(){return false;}

            @Override public void onSelectedChanged(RecyclerView.ViewHolder vh,int actionState){
                super.onSelectedChanged(vh,actionState);
                if(vh!=null && actionState==ItemTouchHelper.ACTION_STATE_DRAG){
                    vh.itemView.setBackgroundColor(Color.rgb(48,48,48));
                }
            }

            @Override public void clearView(RecyclerView rv,RecyclerView.ViewHolder vh){
                super.clearView(rv,vh);
                saveRegularPlaylistOrder();
                rebuildDisplayLists();
                adapter.notifyDataSetChanged();
            }
        };
        touchHelper=new ItemTouchHelper(callback);
        touchHelper.attachToRecyclerView(recycler);

        library.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).putExtra("open_library",true)));
        add.setOnClickListener(v->createPlaylist());

        Ui.applySafeArea(root);
        setContentView(root);
    }

    private String installedVersion(){
        try{
            String v=getPackageManager().getPackageInfo(getPackageName(),0).versionName;
            return v==null || v.trim().isEmpty() ? "?" : v.trim();
        }catch(Exception ignored){
            return "?";
        }
    }

    private void loadLists(){
        List<SetListModel> all=AppStore.loadSetlists(this);
        inProgress=AppStore.getOrCreateInProgressSetlist(this);

        lists=new ArrayList<>();
        for(SetListModel sl:all){
            if(sl==null)continue;
            if(AppStore.isInProgressSetlist(sl.id))continue;
            lists.add(sl);
        }

        rebuildDisplayLists();

        boolean isEmpty=displayLists.isEmpty();
        empty.setVisibility(isEmpty?View.VISIBLE:View.GONE);
        recycler.setVisibility(isEmpty?View.GONE:View.VISIBLE);
        adapter.notifyDataSetChanged();
    }

    private void rebuildDisplayLists(){
        displayLists.clear();

        String principalId=AppStore.getViewerSetlistId(this);
        boolean progressInserted=false;

        for(int i=0;i<lists.size();i++){
            SetListModel sl=lists.get(i);
            displayLists.add(sl);

            if(inProgress!=null && !progressInserted &&
                principalId!=null && !principalId.isEmpty() &&
                principalId.equals(sl.id)){
                displayLists.add(inProgress);
                progressInserted=true;
            }
        }

        if(inProgress!=null && !progressInserted){
            if(displayLists.isEmpty()){
                displayLists.add(inProgress);
            }else{
                displayLists.add(1,inProgress);
            }
        }
    }

    private boolean isProgressRow(SetListModel sl){
        return sl!=null && AppStore.isInProgressSetlist(sl.id);
    }

    private void saveRegularPlaylistOrder(){
        List<SetListModel> persisted=new ArrayList<>(lists);
        if(inProgress!=null)persisted.add(inProgress);
        AppStore.saveSetlists(this,persisted);
    }

    private int indexOfRegular(String id){
        if(id==null)return -1;
        for(int i=0;i<lists.size();i++){
            if(id.equals(lists.get(i).id))return i;
        }
        return -1;
    }

    private class PlaylistHomeAdapter extends RecyclerView.Adapter<PlaylistHomeAdapter.Holder>{
        class Holder extends RecyclerView.ViewHolder{
            final TextView name;
            final Button open;
            final Button del;
            final TextView handle;

            Holder(LinearLayout row,TextView name,Button open,Button del,TextView handle){
                super(row);
                this.name=name;
                this.open=open;
                this.del=del;
                this.handle=handle;
            }
        }

        @Override public Holder onCreateViewHolder(ViewGroup parent,int viewType){
            LinearLayout row=Ui.row(HomePlaylistsActivity.this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(HomePlaylistsActivity.this,3),Ui.dp(HomePlaylistsActivity.this,1),Ui.dp(HomePlaylistsActivity.this,2),Ui.dp(HomePlaylistsActivity.this,1));
            row.setMinimumHeight(Ui.dp(HomePlaylistsActivity.this,50));

            TextView name=new TextView(HomePlaylistsActivity.this);
            name.setTextColor(Color.WHITE);
            name.setTextSize(16);
            name.setPadding(Ui.dp(HomePlaylistsActivity.this,6),Ui.dp(HomePlaylistsActivity.this,2),Ui.dp(HomePlaylistsActivity.this,6),Ui.dp(HomePlaylistsActivity.this,2));
            name.setLayoutParams(new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1));

            Button open=Ui.button(HomePlaylistsActivity.this,"Ouvrir");
            open.setTextSize(12);
            Ui.compactHeaderButton(open,HomePlaylistsActivity.this,68);

            Button del=Ui.button(HomePlaylistsActivity.this,"🗑");
            del.setTextSize(16);
            Ui.compactHeaderButton(del,HomePlaylistsActivity.this,44);

            TextView handle=new TextView(HomePlaylistsActivity.this);
            handle.setText("≡");
            handle.setTextColor(Color.LTGRAY);
            handle.setTextSize(24);
            handle.setGravity(Gravity.CENTER);
            handle.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(HomePlaylistsActivity.this,40),Ui.dp(HomePlaylistsActivity.this,48)));

            row.addView(name);
            row.addView(open);
            row.addView(del);
            row.addView(handle);

            Holder h=new Holder(row,name,open,del,handle);

            handle.setOnTouchListener((v,event)->{
                int p=h.getBindingAdapterPosition();
                if(p==RecyclerView.NO_POSITION || p<0 || p>=displayLists.size())return false;
                if(isProgressRow(displayLists.get(p)))return false;
                if(event.getActionMasked()==MotionEvent.ACTION_DOWN){
                    touchHelper.startDrag(h);
                    return true;
                }
                return false;
            });

            return h;
        }

        @Override public void onBindViewHolder(Holder h,int position){
            SetListModel sl=displayLists.get(position);
            boolean progress=isProgressRow(sl);

            if(progress){
                h.name.setText("↳  EN COURS\n"+sl.songIds.size()+" titre"+(sl.songIds.size()>1?"s":""));
                h.name.setTextColor(Color.rgb(255,193,7));
                h.itemView.setBackgroundColor(Color.rgb(55,20,20));
                h.del.setVisibility(View.GONE);
                h.handle.setVisibility(View.GONE);
                h.open.setText("Ouvrir");
            }else{
                int regularIndex=indexOfRegular(sl.id);
                h.name.setText((regularIndex+1)+".  "+sl.name+"\n"+sl.songIds.size()+" titre"+(sl.songIds.size()>1?"s":""));
                h.name.setTextColor(Color.WHITE);
                h.itemView.setBackgroundColor(regularIndex%2==0?Color.rgb(30,30,30):Color.rgb(18,18,18));
                h.del.setVisibility(View.VISIBLE);
                h.handle.setVisibility(View.VISIBLE);
                h.open.setText("Ouvrir");
            }

            h.name.setOnClickListener(v->openPlaylist(sl.id));
            h.open.setOnClickListener(v->openPlaylist(sl.id));
            h.del.setOnClickListener(progress?null:v->confirmDelete(sl));
        }

        @Override public int getItemCount(){return displayLists.size();}
    }

    private void openPlaylist(String id){
        Intent i=new Intent(this,PlaylistOverviewActivity.class);
        i.putExtra("setlist_id",id);
        startActivity(i);
    }

    private void createPlaylist(){
        EditText input=new EditText(this);
        input.setHint("Nom de la playlist");
        new AlertDialog.Builder(this)
            .setTitle("Nouvelle playlist")
            .setView(input)
            .setPositiveButton("Créer",(d,w)->{
                String n=input.getText().toString().trim();
                if(n.isEmpty()) n="Nouvelle playlist";
                SetListModel sl=new SetListModel();
                sl.name=n;
                AppStore.upsertSetlist(this,sl);
                loadLists();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void confirmDelete(SetListModel sl){
        new AlertDialog.Builder(this)
            .setTitle("Supprimer la playlist ?")
            .setMessage("« "+sl.name+" » sera supprimée. Les morceaux et leurs paroles resteront dans la bibliothèque.")
            .setPositiveButton("Supprimer",(d,w)->{
                lists.removeIf(x->x.id.equals(sl.id));
                saveRegularPlaylistOrder();
                loadLists();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }
}
