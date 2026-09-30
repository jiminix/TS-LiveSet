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
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HomePlaylistsActivity extends AppCompatActivity {
    // V0.24 draggable playlists
    // Build V0.24
    // Build V0.28
    // Build V0.76 show reserved En cours below its principal playlist
    // Build V0.77 global SAVE / RESTORE with backup timestamp
    // Build V0.79 simplified home controls
    // Build V0.81 global multi-step undo
    private RecyclerView recycler;
    private PlaylistHomeAdapter adapter;
    private List<SetListModel> lists;
    private final List<SetListModel> displayLists=new ArrayList<>();
    private SetListModel inProgress;
    private ItemTouchHelper touchHelper;
    private TextView empty;
    private TextView backupInfo;
    private boolean homeDragUndoRecorded=false;

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

        TextView version=new TextView(this);
        version.setText("LiveSet v"+installedVersion());
        version.setTextColor(Color.LTGRAY);
        version.setTextSize(11);
        version.setGravity(Gravity.RIGHT);
        version.setPadding(0,0,Ui.dp(this,12),Ui.dp(this,2));
        root.addView(version);

        LinearLayout backupRow=Ui.row(this);
        backupRow.setPadding(Ui.dp(this,6),Ui.dp(this,2),Ui.dp(this,6),Ui.dp(this,2));

        Button saveAll=Ui.button(this,"SAVE TOUT");
        saveAll.setTextSize(12);
        Button undo=Ui.button(this,"↶ ANNULER");
        undo.setTextSize(12);
        Button restoreAll=Ui.button(this,"↻ RESTAURER");
        restoreAll.setTextSize(12);
        Ui.weight(saveAll,1);
        Ui.weight(undo,1);
        Ui.weight(restoreAll,1);
        backupRow.addView(saveAll);
        backupRow.addView(undo);
        backupRow.addView(restoreAll);
        root.addView(backupRow);

        backupInfo=new TextView(this);
        backupInfo.setText("Dernière sauvegarde : vérification…");
        backupInfo.setTextColor(Color.LTGRAY);
        backupInfo.setTextSize(11);
        backupInfo.setGravity(Gravity.CENTER);
        backupInfo.setPadding(Ui.dp(this,6),0,Ui.dp(this,6),Ui.dp(this,4));
        root.addView(backupInfo);

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

        Button addBottom=Ui.button(this,"＋");
        addBottom.setTextSize(36);
        addBottom.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        addBottom.setContentDescription("Créer une nouvelle playlist");
        root.addView(addBottom,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            Ui.dp(this,58)
        ));

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
                homeDragUndoRecorded=false;
                return true;
            }

            @Override public void onSwiped(RecyclerView.ViewHolder vh,int direction){}

            @Override public boolean isLongPressDragEnabled(){return false;}

            @Override public void onSelectedChanged(RecyclerView.ViewHolder vh,int actionState){
                super.onSelectedChanged(vh,actionState);
                if(vh!=null && actionState==ItemTouchHelper.ACTION_STATE_DRAG){
                    if(!homeDragUndoRecorded){
                        AppStore.recordUndoSnapshot(HomePlaylistsActivity.this,"Déplacement playlist");
                        homeDragUndoRecorded=true;
                    }
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

        addBottom.setOnClickListener(v->createPlaylist());
        saveAll.setOnClickListener(v->saveEverything());
        undo.setOnClickListener(v->undoLastAction());
        restoreAll.setOnClickListener(v->confirmRestoreEverything());

        Ui.applySafeArea(root);
        setContentView(root);
        refreshBackupInfo();
    }

    private void undoLastAction(){
        String label=AppStore.undoLast(this);
        if(label==null || label.trim().isEmpty()){
            Toast.makeText(this,"Aucune action à annuler",Toast.LENGTH_SHORT).show();
            return;
        }
        loadLists();
        Toast.makeText(this,"Annulé : "+label,Toast.LENGTH_LONG).show();
    }

    private String formatBackupDate(long time){
        if(time<=0L)return "Aucune sauvegarde";
        return new SimpleDateFormat("dd/MM/yyyy HH:mm",Locale.getDefault()).format(new Date(time));
    }

    private long parseTime(String value){
        try{return Long.parseLong(value==null?"0":value.trim());}
        catch(Exception ignored){return 0L;}
    }

    private void refreshBackupInfo(){
        long local=AppStore.getFullBackupTimestamp(this);
        if(backupInfo!=null){
            backupInfo.setText(local>0L
                ? "Dernière sauvegarde : "+formatBackupDate(local)+" · vérification Internet…"
                : "Dernière sauvegarde : vérification Internet…");
        }

        PlaylistCloudSync.getGlobalBackupTimestamp(this,new PlaylistCloudSync.Listener(){
            @Override public void onSuccess(String value){
                long time=parseTime(value);
                if(backupInfo!=null)backupInfo.setText("Dernière sauvegarde : "+formatBackupDate(time));
            }

            @Override public void onError(String message){
                long fallback=AppStore.getFullBackupTimestamp(HomePlaylistsActivity.this);
                if(backupInfo!=null){
                    backupInfo.setText(fallback>0L
                        ? "Dernière sauvegarde : "+formatBackupDate(fallback)+" · locale"
                        : "Dernière sauvegarde : aucune");
                }
            }
        });
    }

    private void saveEverything(){
        Toast.makeText(this,"Sauvegarde complète en cours…",Toast.LENGTH_SHORT).show();

        PlaylistCloudSync.saveGlobalBackup(this,new PlaylistCloudSync.Listener(){
            @Override public void onSuccess(String value){
                long time=parseTime(value);
                if(backupInfo!=null)backupInfo.setText("Dernière sauvegarde : "+formatBackupDate(time));
                Toast.makeText(HomePlaylistsActivity.this,
                    "SAVE terminé · toutes les données sont sauvegardées",
                    Toast.LENGTH_LONG).show();
            }

            @Override public void onError(String message){
                long local=AppStore.getFullBackupTimestamp(HomePlaylistsActivity.this);
                if(backupInfo!=null && local>0L){
                    backupInfo.setText("Dernière sauvegarde : "+formatBackupDate(local)+" · locale");
                }
                Toast.makeText(HomePlaylistsActivity.this,
                    "Sauvegarde locale créée, mais Internet : "+message,
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    private void confirmRestoreEverything(){
        long time=AppStore.getFullBackupTimestamp(this);
        String date=time>0L?formatBackupDate(time):"la dernière sauvegarde disponible";

        new AlertDialog.Builder(this)
            .setTitle("Restaurer la sauvegarde ?")
            .setMessage("Toutes les données actuelles seront remplacées par "+date+
                ".\n\nMorceaux, paroles, playlists, En cours et Medleys seront restaurés.")
            .setPositiveButton("RESTAURER",(d,w)->restoreEverything())
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void restoreEverything(){
        Toast.makeText(this,"Restauration de la sauvegarde…",Toast.LENGTH_SHORT).show();

        PlaylistCloudSync.restoreGlobalBackup(this,new PlaylistCloudSync.Listener(){
            @Override public void onSuccess(String value){
                long time=parseTime(value);
                loadLists();
                if(backupInfo!=null)backupInfo.setText("Dernière sauvegarde : "+formatBackupDate(time));
                Toast.makeText(HomePlaylistsActivity.this,
                    "Sauvegarde restaurée · "+formatBackupDate(time),
                    Toast.LENGTH_LONG).show();
            }

            @Override public void onError(String message){
                Toast.makeText(HomePlaylistsActivity.this,
                    "Restauration impossible : "+message,
                    Toast.LENGTH_LONG).show();
            }
        });
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
                h.del.setVisibility(View.VISIBLE);
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
            h.del.setOnClickListener(progress?v->confirmClearInProgress():v->confirmDelete(sl));
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
                AppStore.recordUndoSnapshot(this,"Création playlist");
                SetListModel sl=new SetListModel();
                sl.name=n;
                AppStore.upsertSetlist(this,sl);
                loadLists();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void confirmClearInProgress(){
        int count=inProgress==null?0:inProgress.songIds.size();

        new AlertDialog.Builder(this)
            .setTitle("Vider « EN COURS » ?")
            .setMessage(count==0
                ? "La playlist EN COURS est déjà vide."
                : "Les "+count+" titre"+(count>1?"s":"")+" seront retirés de EN COURS. Les morceaux resteront dans la bibliothèque.")
            .setPositiveButton("Vider",(d,w)->{
                AppStore.recordUndoSnapshot(this,"Vidage EN COURS");
                AppStore.clearInProgressSetlist(this);
                loadLists();
                Toast.makeText(this,"EN COURS vidée",Toast.LENGTH_SHORT).show();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void confirmDelete(SetListModel sl){
        new AlertDialog.Builder(this)
            .setTitle("Supprimer la playlist ?")
            .setMessage("« "+sl.name+" » sera supprimée. Les morceaux et leurs paroles resteront dans la bibliothèque.")
            .setPositiveButton("Supprimer",(d,w)->{
                AppStore.recordUndoSnapshot(this,"Suppression playlist");
                lists.removeIf(x->x.id.equals(sl.id));
                saveRegularPlaylistOrder();
                loadLists();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }
}
