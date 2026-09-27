package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
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
    // Build V0.38
    // Build V0.39
    // Build V0.41 compact right metadata and text zoom -3..+2
    // Build V0.42 direct -/+ zoom controls with 6 levels
    // Build V0.43 zoom -5..+2, compact top bar and corrected slogan
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String setlistId;
    private TextView playlistTitle;
    private TextView info;
    private LinearLayout songsBox;
    private String lastSignature="";
    private int textZoom=0;

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
        textZoom=Math.max(-5,Math.min(2,getSharedPreferences("playlist_view",MODE_PRIVATE).getInt("text_zoom",0)));
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
        root.setPadding(dp(6),dp(6),dp(6),dp(8));

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        Button back=Ui.button(this,"‹");
        back.setTextSize(24);
        Ui.compactHeaderButton(back,this,36);

        TextView appTitle=new TextView(this);
        appTitle.setText("TS PLAYLIST VIEWER");
        appTitle.setTextColor(Color.rgb(255,196,30));
        appTitle.setTextSize(17);
        appTitle.setSingleLine(true);
        appTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        appTitle.setGravity(Gravity.CENTER);
        appTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        appTitle.setLayoutParams(new LinearLayout.LayoutParams(0,dp(40),1));

        Button zoomMinus=Ui.button(this,"−");
        zoomMinus.setTextSize(18);
        Ui.compactHeaderButton(zoomMinus,this,34);

        Button zoomPlus=Ui.button(this,"+");
        zoomPlus.setTextSize(18);
        Ui.compactHeaderButton(zoomPlus,this,34);

        Button cloud=Ui.button(this,"☁ Code");
        cloud.setTextSize(10);
        Ui.compactHeaderButton(cloud,this,62);

        top.addView(back);
        top.addView(appTitle);
        top.addView(zoomMinus);
        top.addView(zoomPlus);
        top.addView(cloud);
        root.addView(top);

        TextView slogan=new TextView(this);
        slogan.setText("Un pour tous, tous pour la même playlist.");
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
        zoomMinus.setOnClickListener(v->changeZoom(-1));
        zoomPlus.setOnClickListener(v->changeZoom(1));
        cloud.setOnClickListener(v->showInternetCode());

        Ui.applySafeArea(root);
        setContentView(root);
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
        lastSignature="";
        refreshPlaylist();
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
            sig.append(s.id).append(':').append(s.title).append(':').append(s.bpm)
                .append(':').append(s.stageNum1).append(':').append(s.stageNum2)
                .append(':').append(s.stageGuitar).append(':').append(s.stageKeyboard).append('|');
        }

        String signature=sig.toString();
        if(signature.equals(lastSignature))return;
        lastSignature=signature;

        playlistTitle.setText(list.name);
        info.setText(songs.size()+" titre"+(songs.size()>1?"s":"")+" · Manager");
        songsBox.removeAllViews();

        for(int i=0;i<songs.size();i++){
            Song s=songs.get(i);
            addSongRow(i+1,s);
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
            .setTitle("Code Internet")
            .setMessage(code)
            .setPositiveButton("Partager",(d,w)->shareCode(code))
            .setNegativeButton("Fermer",null)
            .show();
    }

    private void shareCode(String code){
        Intent send=new Intent(Intent.ACTION_SEND);
        send.setType("text/plain");
        send.putExtra(Intent.EXTRA_TEXT,code);
        startActivity(Intent.createChooser(send,"Partager le code"));
    }

    private String twoDigits(String value){
        if(value==null || value.trim().isEmpty())return "--";
        String d=value.replaceAll("[^0-9]","");
        if(d.isEmpty())return "--";
        if(d.length()>2)d=d.substring(0,2);
        return d.length()==1?"0"+d:d;
    }

    private void addSongRow(int number,Song song){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(8),dp(4),dp(8),dp(4));
        row.setMinimumHeight(dp(46));
        row.setBackgroundColor(number%2==1?Color.rgb(28,28,28):Color.BLACK);

        TextView num=new TextView(this);
        num.setText(String.format("%02d",number));
        num.setTextColor(Color.LTGRAY);
        num.setTextSize(14+textZoom);
        num.setGravity(Gravity.CENTER);
        num.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(num,new LinearLayout.LayoutParams(dp(34),ViewGroup.LayoutParams.MATCH_PARENT));

        TextView name=new TextView(this);
        String title=song==null?"":song.title;
        String bpm=song==null?"":song.bpm;
        name.setText(title==null?"":title);
        name.setTextColor(Color.WHITE);
        name.setTextSize(20+textZoom);
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        row.addView(name,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.MATCH_PARENT,1));

        boolean hasStageInfo=song!=null && (
            (song.stageNum1!=null && !song.stageNum1.trim().isEmpty()) ||
            (song.stageNum2!=null && !song.stageNum2.trim().isEmpty()) ||
            song.stageGuitar || song.stageKeyboard
        );

        if(hasStageInfo){
            LinearLayout stageBox=new LinearLayout(this);
            stageBox.setOrientation(LinearLayout.HORIZONTAL);
            stageBox.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
            stageBox.setPadding(dp(2),0,dp(2),0);

            GradientDrawable stageBg=new GradientDrawable();
            stageBg.setColor(Color.BLACK);
            stageBg.setCornerRadius(dp(4));
            stageBg.setStroke(dp(1),Color.rgb(70,70,70));
            stageBox.setBackground(stageBg);

            if(song.stageNum1!=null && !song.stageNum1.trim().isEmpty()){
                TextView n1=new TextView(this);
                n1.setText(twoDigits(song.stageNum1));
                n1.setTextColor(Color.WHITE);
                n1.setTextSize(12+textZoom);
                n1.setGravity(Gravity.CENTER);
                n1.setPadding(dp(2),0,dp(2),0);
                stageBox.addView(n1,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,dp(28)
                ));
            }

            if(song.stageNum2!=null && !song.stageNum2.trim().isEmpty()){
                TextView n2=new TextView(this);
                n2.setText(twoDigits(song.stageNum2));
                n2.setTextColor(Color.RED);
                n2.setTextSize(12+textZoom);
                n2.setGravity(Gravity.CENTER);
                n2.setPadding(dp(2),0,dp(2),0);
                stageBox.addView(n2,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,dp(28)
                ));
            }

            if(song.stageGuitar){
                TextView guitar=new TextView(this);
                guitar.setText("🎸");
                guitar.setTextSize(14+textZoom);
                guitar.setGravity(Gravity.CENTER);
                guitar.setPadding(dp(1),0,dp(1),0);
                stageBox.addView(guitar,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,dp(28)
                ));
            }

            if(song.stageKeyboard){
                TextView keyboard=new TextView(this);
                keyboard.setText("🎹");
                keyboard.setTextSize(14+textZoom);
                keyboard.setGravity(Gravity.CENTER);
                keyboard.setPadding(dp(1),0,dp(1),0);
                stageBox.addView(keyboard,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,dp(28)
                ));
            }

            row.addView(stageBox,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                dp(30)
            ));
        }

        TextView bpmView=new TextView(this);
        bpmView.setText(bpm==null || bpm.trim().isEmpty() ? "" : bpm.trim());
        bpmView.setTextColor(Color.rgb(255,196,30));
        bpmView.setTextSize(14+textZoom);
        bpmView.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        bpmView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        bpmView.setSingleLine(true);
        bpmView.setPadding(dp(4),0,0,0);
        if(bpm==null || bpm.trim().isEmpty()){
            bpmView.setVisibility(View.GONE);
        }
        row.addView(bpmView,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

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
