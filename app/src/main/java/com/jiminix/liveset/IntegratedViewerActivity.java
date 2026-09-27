package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.ArrayList;
import java.util.List;

public class IntegratedViewerActivity extends AppCompatActivity {
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String setlistId;
    private TextView playlistTitle;
    private TextView info;
    private LinearLayout songsBox;
    private String lastSignature="";

    private final Runnable refreshLoop=new Runnable(){
        @Override public void run(){
            refreshPlaylist();
            handler.postDelayed(this,1200);
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
        setlistId=getIntent().getStringExtra("setlist_id");
        if(setlistId==null || setlistId.isEmpty())setlistId=AppStore.getViewerSetlistId(this);
        buildUi();
    }

    @Override protected void onResume(){
        super.onResume();
        handler.removeCallbacks(refreshLoop);
        refreshLoop.run();
    }

    @Override protected void onPause(){
        handler.removeCallbacks(refreshLoop);
        super.onPause();
    }

    private int dp(int v){
        return Math.round(v*getResources().getDisplayMetrics().density);
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(10),dp(8),dp(10),dp(8));

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Button back=Ui.button(this,"‹");
        back.setTextSize(28);
        Ui.compactHeaderButton(back,this,46);

        TextView appTitle=new TextView(this);
        appTitle.setText("TS PLAYLIST VIEWER");
        appTitle.setTextColor(Color.rgb(255,196,30));
        appTitle.setTextSize(22);
        appTitle.setGravity(Gravity.CENTER);
        appTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        appTitle.setLayoutParams(new LinearLayout.LayoutParams(0,dp(44),1));

        Button cloud=Ui.button(this,"☁ Code");
        cloud.setTextSize(12);
        Ui.compactHeaderButton(cloud,this,76);

        top.addView(back);
        top.addView(appTitle);
        top.addView(cloud);
        root.addView(top);

        TextView slogan=new TextView(this);
        slogan.setText("1 pour tous, tous pour la même playlist.");
        slogan.setTextColor(Color.WHITE);
        slogan.setTextSize(13);
        slogan.setGravity(Gravity.CENTER);
        slogan.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        slogan.setPadding(dp(4),0,dp(4),dp(6));
        root.addView(slogan);

        playlistTitle=new TextView(this);
        playlistTitle.setText("Playlist");
        playlistTitle.setTextColor(Color.WHITE);
        playlistTitle.setTextSize(25);
        playlistTitle.setGravity(Gravity.CENTER);
        playlistTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        playlistTitle.setPadding(dp(6),dp(8),dp(6),dp(4));
        root.addView(playlistTitle);

        info=new TextView(this);
        info.setText("Lecture locale");
        info.setTextColor(Color.LTGRAY);
        info.setTextSize(12);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(8),0,dp(8),dp(8));
        root.addView(info);

        ScrollView scroll=new ScrollView(this);
        scroll.setFillViewport(true);

        songsBox=new LinearLayout(this);
        songsBox.setOrientation(LinearLayout.VERTICAL);
        songsBox.setPadding(0,dp(4),0,dp(24));
        scroll.addView(songsBox,new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(scroll,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,0,1
        ));

        TextView footer=new TextView(this);
        footer.setText("Viewer intégré · synchronisation Internet active pour les autres téléphones");
        footer.setTextColor(Color.DKGRAY);
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0,dp(5),0,dp(2));
        root.addView(footer);

        back.setOnClickListener(v->finish());
        cloud.setOnClickListener(v->showInternetCode());

        Ui.applySafeArea(root);
        setContentView(root);
    }

    private void refreshPlaylist(){
        SetListModel list=AppStore.findSetlist(this,setlistId);
        if(list==null){
            showEmpty("Playlist introuvable","Retourne dans le Manager et sélectionne une playlist.");
            return;
        }

        List<Song> songs=new ArrayList<>();
        StringBuilder sig=new StringBuilder(list.name).append('|');
        for(String id:list.songIds){
            Song s=AppStore.findSong(this,id);
            if(s==null)continue;
            songs.add(s);
            sig.append(s.id).append(':').append(s.title).append(':').append(s.bpm).append('|');
        }

        String signature=sig.toString();
        if(signature.equals(lastSignature))return;
        lastSignature=signature;

        playlistTitle.setText(list.name);
        info.setText(songs.size()+" titre"+(songs.size()>1?"s":"")+" · Manager");
        songsBox.removeAllViews();

        for(int i=0;i<songs.size();i++){
            Song s=songs.get(i);
            addSongRow(i+1,s.title,s.bpm);
        }
    }

    private void showEmpty(String title,String message){
        String signature=title+"|"+message;
        if(signature.equals(lastSignature))return;
        lastSignature=signature;
        playlistTitle.setText(title);
        info.setText(message);
        songsBox.removeAllViews();
    }

    private void showInternetCode(){
        String code=PlaylistCloudSync.getCode(this);
        if(code!=null && !code.isEmpty()){
            showCodeDialog(code);
            return;
        }

        Toast.makeText(this,"Création du code Internet…",Toast.LENGTH_SHORT).show();
        PlaylistCloudSync.publishSelected(this,new PlaylistCloudSync.Listener(){
            @Override public void onSuccess(String newCode){
                showCodeDialog(newCode);
            }

            @Override public void onError(String message){
                Toast.makeText(IntegratedViewerActivity.this,
                    "Impossible de créer le code : "+message,
                    Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showCodeDialog(String code){
        new AlertDialog.Builder(this)
            .setTitle("Viewer Internet")
            .setMessage("Code pour les autres téléphones :\n\n"+code+
                "\n\nIls entrent ce code une seule fois dans leur TS Playlist Viewer.")
            .setPositiveButton("Partager",(d,w)->shareCode(code))
            .setNegativeButton("Fermer",null)
            .show();
    }

    private void shareCode(String code){
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,"TS Playlist Viewer\nCode Internet : "+code);
        startActivity(Intent.createChooser(send,"Partager le code Viewer"));
    }

    private void addSongRow(int number,String title,String bpm){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8),dp(4),dp(8),dp(4));
        row.setMinimumHeight(dp(46));
        row.setBackgroundColor(number%2==1?Color.rgb(28,28,28):Color.BLACK);

        TextView num=new TextView(this);
        num.setText(String.format("%02d",number));
        num.setTextColor(Color.LTGRAY);
        num.setTextSize(14);
        num.setGravity(Gravity.CENTER);
        num.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(num,new LinearLayout.LayoutParams(dp(42),ViewGroup.LayoutParams.MATCH_PARENT));

        TextView name=new TextView(this);
        name.setText(title==null?"":title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(20);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1));

        TextView bpmView=new TextView(this);
        bpmView.setText(bpm==null || bpm.trim().isEmpty() ? "" : bpm.trim());
        bpmView.setTextColor(Color.rgb(255,196,30));
        bpmView.setTextSize(14);
        bpmView.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        bpmView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        bpmView.setSingleLine(true);
        bpmView.setPadding(dp(8),0,dp(2),0);
        row.addView(bpmView,new LinearLayout.LayoutParams(dp(74),ViewGroup.LayoutParams.MATCH_PARENT));

        songsBox.addView(row,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        View sep=new View(this);
        sep.setBackgroundColor(Color.rgb(45,45,45));
        songsBox.addView(sep,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(1)
        ));
    }
}
