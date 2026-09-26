package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.List;

public class HomePlaylistsActivity extends AppCompatActivity {
    // V0.22 launcher build
    private LinearLayout content;

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        buildUi();
        render();
    }

    @Override protected void onResume(){
        super.onResume();
        if(content!=null) render();
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(18,18,18));

        LinearLayout head=Ui.row(this);
        TextView title=Ui.title(this,"PLAYLISTS");
        Ui.compactHeaderTitle(title,this);

        Button library=Ui.button(this,"Titres");
        library.setTextSize(13);
        Ui.compactHeaderButton(library,this,70);

        Button add=Ui.button(this,"＋");
        add.setTextSize(24);
        Ui.compactHeaderButton(add,this,50);

        head.addView(title);
        head.addView(library);
        head.addView(add);
        root.addView(head);

        TextView version=new TextView(this);
        version.setText("LiveSet v0.23");
        version.setTextColor(Color.LTGRAY);
        version.setTextSize(11);
        version.setGravity(Gravity.RIGHT);
        version.setPadding(0,0,Ui.dp(this,12),Ui.dp(this,4));
        root.addView(version);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);
        content=new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(Ui.dp(this,6),Ui.dp(this,4),Ui.dp(this,6),Ui.dp(this,80));
        scroll.addView(content,new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));
        root.addView(scroll,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,0,1));

        library.setOnClickListener(v->startActivity(new Intent(this,MainActivity.class).putExtra("open_library",true)));
        add.setOnClickListener(v->createPlaylist());

        Ui.applySafeArea(root);
        setContentView(root);
    }

    private void render(){
        content.removeAllViews();
        List<SetListModel> lists=AppStore.loadSetlists(this);

        if(lists.isEmpty()){
            TextView empty=Ui.title(this,"Aucune playlist");
            empty.setTextSize(20);
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(Ui.dp(this,8),Ui.dp(this,40),Ui.dp(this,8),Ui.dp(this,18));
            content.addView(empty);

            Button create=Ui.button(this,"＋ Créer une playlist");
            create.setTextSize(17);
            create.setOnClickListener(v->createPlaylist());
            content.addView(create,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,58)));
            return;
        }

        for(SetListModel sl:lists){
            LinearLayout row=Ui.row(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(Ui.dp(this,4),Ui.dp(this,2),Ui.dp(this,4),Ui.dp(this,2));
            row.setMinimumHeight(Ui.dp(this,52));

            TextView name=new TextView(this);
            name.setText(sl.name+"\n"+sl.songIds.size()+" titre"+(sl.songIds.size()>1?"s":""));
            name.setTextColor(Color.WHITE);
            name.setTextSize(16);
            name.setPadding(Ui.dp(this,6),Ui.dp(this,2),Ui.dp(this,6),Ui.dp(this,2));
            Ui.weight(name,1);

            Button open=Ui.button(this,"Ouvrir");
            open.setTextSize(13);
            Ui.compactHeaderButton(open,this,72);

            Button del=Ui.button(this,"🗑");
            del.setTextSize(17);
            Ui.compactHeaderButton(del,this,48);

            row.addView(name);
            row.addView(open);
            row.addView(del);

            name.setOnClickListener(v->openPlaylist(sl.id));
            open.setOnClickListener(v->openPlaylist(sl.id));
            del.setOnClickListener(v->confirmDelete(sl));

            content.addView(row);

            android.view.View sep=new android.view.View(this);
            sep.setBackgroundColor(Color.rgb(40,40,40));
            content.addView(sep,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,Ui.dp(this,1)));
        }
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
                render();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private void confirmDelete(SetListModel sl){
        new AlertDialog.Builder(this)
            .setTitle("Supprimer la playlist ?")
            .setMessage("« "+sl.name+" » sera supprimée. Les morceaux et leurs paroles resteront dans la bibliothèque.")
            .setPositiveButton("Supprimer",(d,w)->{
                List<SetListModel> all=AppStore.loadSetlists(this);
                all.removeIf(x->x.id.equals(sl.id));
                AppStore.saveSetlists(this,all);
                render();
            })
            .setNegativeButton("Annuler",null)
            .show();
    }
}
