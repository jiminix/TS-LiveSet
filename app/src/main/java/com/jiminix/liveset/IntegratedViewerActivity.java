package com.jiminix.liveset;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.res.ColorStateList;
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
import java.util.HashSet;
import java.util.Set;
import java.util.Locale;

public class IntegratedViewerActivity extends AppCompatActivity {
    // Build V0.38
    // Build V0.39
    // Build V0.41 compact right metadata and text zoom -3..+2
    // Build V0.42 direct -/+ zoom controls with 6 levels
    // Build V0.43 zoom -5..+2, compact top bar and corrected slogan
    // Build V0.44 denser viewer rows
    // Build V0.45 page up/down navigation
    // Build V0.46 TS 2026 info line with page arrows
    // Build V0.48 force TS 2026, title count and page arrows onto one line
    // Build V0.50 remove duplicate playlist title and keep name/count/arrows on one row
    // Build V0.51 show compact 22-character Internet code
    // Build V0.52 show one-letter one-digit Internet code
    // Build V0.47 proportional zoom for stage info and row spacing
    private final Handler handler=new Handler(Looper.getMainLooper());
    private String setlistId;
    private TextView playlistTitle;
    private TextView appTitle;
    private TextView info;
    private Button inProgressButton;
    private LinearLayout songsBox;
    private ScrollView scroll;
    private String lastSignature="";
    private int textZoom=0;
    private boolean showInProgress=false;
    private final Set<String> expandedMedleys=new HashSet<>();

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
        showInProgress=getIntent().getBooleanExtra("show_in_progress",false);
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

    private float zoomScale(){
        return Math.max(0.62f,Math.min(1.18f,1f+(textZoom*0.076f)));
    }

    private int zdp(int base){
        return dp(Math.max(1,Math.round(base*zoomScale())));
    }

    private float zsp(float base){
        return Math.max(7f,base*zoomScale());
    }

    private void buildUi(){
        LinearLayout root=new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.BLACK);
        root.setPadding(dp(6),dp(6),dp(6),dp(8));

        LinearLayout top=new LinearLayout(this);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        top.setBackgroundColor(Color.rgb(105,12,18));

        Button back=Ui.button(this,"‹");
        back.setTextSize(21);
        Ui.compactHeaderButton(back,this,32);

        appTitle=new TextView(this);
        appTitle.setText("Playlist");
        appTitle.setTextColor(Color.rgb(255,196,30));
        appTitle.setTextSize(15);
        appTitle.setSingleLine(true);
        appTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
        appTitle.setGravity(Gravity.CENTER);
        appTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        appTitle.setLayoutParams(new LinearLayout.LayoutParams(0,dp(36),1));

        Button zoomMinus=Ui.button(this,"−");
        zoomMinus.setTextSize(16);
        Ui.compactHeaderButton(zoomMinus,this,30);

        Button zoomPlus=Ui.button(this,"+");
        zoomPlus.setTextSize(16);
        Ui.compactHeaderButton(zoomPlus,this,30);

        Button cloud=Ui.button(this,"☁ Code");
        cloud.setTextSize(9);
        Ui.compactHeaderButton(cloud,this,56);

        inProgressButton=Ui.button(this,showInProgress?"Principal":"En cours");
        inProgressButton.setTextSize(9);
        Ui.compactHeaderButton(inProgressButton,this,58);

        styleButton(inProgressButton,Color.rgb(198,40,40),Color.WHITE);
        styleButton(zoomMinus,Color.BLACK,Color.WHITE);
        styleButton(zoomPlus,Color.WHITE,Color.BLACK);

        top.addView(back);
        top.addView(appTitle);
        top.addView(zoomMinus);
        top.addView(zoomPlus);
        top.addView(cloud);
        top.addView(inProgressButton);
        root.addView(top);

        playlistTitle=new TextView(this);
        playlistTitle.setText("Playlist");

        LinearLayout infoRow=new LinearLayout(this);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setGravity(Gravity.CENTER_VERTICAL);
        infoRow.setPadding(dp(2),0,dp(2),0);
        infoRow.setBackgroundColor(Color.rgb(105,12,18));

        TextView slogan=new TextView(this);
        slogan.setText("Un pour tous, tous pour la même playlist.");
        slogan.setTextColor(Color.WHITE);
        slogan.setTextSize(7.5f);
        slogan.setSingleLine(true);
        slogan.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        slogan.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        slogan.setPadding(dp(1),0,dp(1),0);
        infoRow.addView(slogan,new LinearLayout.LayoutParams(0,dp(26),1.5f));

        info=new TextView(this);
        info.setText("0 titres");
        info.setTextColor(Color.LTGRAY);
        info.setTextSize(9);
        info.setSingleLine(true);
        info.setHorizontallyScrolling(false);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(1),0,dp(1),0);
        infoRow.addView(info,new LinearLayout.LayoutParams(0,dp(26),0.6f));

        Button pageUp=Ui.button(this,"↑");
        pageUp.setTextSize(14);
        Ui.compactHeaderButton(pageUp,this,26);
        Button pageDown=Ui.button(this,"↓");
        pageDown.setTextSize(14);
        Ui.compactHeaderButton(pageDown,this,26);
        infoRow.addView(pageUp);
        infoRow.addView(pageDown);
        root.addView(infoRow,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(26)
        ));

        scroll=new ScrollView(this);
        scroll.setFillViewport(true);

        songsBox=new LinearLayout(this);
        songsBox.setOrientation(LinearLayout.VERTICAL);
        songsBox.setPadding(0,dp(1),0,dp(10));
        scroll.addView(songsBox,new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        root.addView(scroll,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,0,1
        ));

        TextView footer=new TextView(this);
        footer.setText("Viewer intégré · synchronisation Internet active pour les autres téléphones");
        footer.setTextColor(Color.WHITE);
        footer.setBackgroundColor(Color.rgb(105,12,18));
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0,dp(5),0,dp(2));
        root.addView(footer);

        back.setOnClickListener(v->finish());
        zoomMinus.setOnClickListener(v->changeZoom(-1));
        zoomPlus.setOnClickListener(v->changeZoom(1));
        pageUp.setOnClickListener(v->pageScroll(-1));
        pageDown.setOnClickListener(v->pageScroll(1));
        cloud.setOnClickListener(v->showInternetCode());
        inProgressButton.setOnClickListener(v->toggleInProgress());

        Ui.applySafeArea(root);
        setContentView(root);
    }

    private void styleButton(Button button,int background,int foreground){
        button.setBackgroundTintList(ColorStateList.valueOf(background));
        button.setTextColor(foreground);
    }

    private void toggleInProgress(){
        showInProgress=!showInProgress;
        inProgressButton.setText(showInProgress?"Principal":"En cours");
        lastSignature="";
        if(scroll!=null)scroll.scrollTo(0,0);
        refreshPlaylist();
    }

    private void pageScroll(int direction){
        if(scroll==null)return;
        int page=Math.max(1,scroll.getHeight());
        int max=Math.max(0,songsBox.getHeight()-scroll.getHeight());
        int target=scroll.getScrollY()+(direction*page);
        target=Math.max(0,Math.min(max,target));
        scroll.smoothScrollTo(0,target);
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
        SetListModel list=showInProgress
            ? AppStore.getOrCreateInProgressSetlist(this)
            : AppStore.findSetlist(this,setlistId);
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
                .append(':').append(s.stageGuitar).append(':').append(s.stageKeyboard)
                .append(':').append(list.disabledSongIds.contains(id))
                .append(':').append(s.medleyItems.toString()).append('|');
        }

        String signature=sig.toString();
        if(signature.equals(lastSignature))return;
        lastSignature=signature;

        playlistTitle.setText(list.name);
        appTitle.setText((list.name==null || list.name.trim().isEmpty()) ? "Playlist" : list.name.trim());
        info.setText(songs.size()+" titre"+(songs.size()>1?"s":""));
        songsBox.removeAllViews();

        for(int i=0;i<songs.size();i++){
            Song s=songs.get(i);
            addSongRow(i+1,s,list.disabledSongIds.contains(s.id));
        }
    }

    private void showEmpty(String title,String message){
        String signature=title+"|"+message;
        if(signature.equals(lastSignature))return;
        lastSignature=signature;
        playlistTitle.setText(title);
        appTitle.setText("Meryl");
        info.setText(title);
        songsBox.removeAllViews();
    }

    private void showInternetCode(){
        Toast.makeText(this,"Vérification du code Internet…",Toast.LENGTH_SHORT).show();
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

    private boolean isMedleySong(Song song){
        if(song==null || song.title==null)return false;
        String t=song.title.trim().toLowerCase(Locale.ROOT);
        return t.startsWith("medley") || t.startsWith("meddley");
    }

    private void toggleMedley(String songId){
        if(songId==null || songId.isEmpty())return;
        if(expandedMedleys.contains(songId))expandedMedleys.remove(songId);
        else expandedMedleys.add(songId);
        lastSignature="";
        refreshPlaylist();
    }

    private void addSongRow(int number,Song song,boolean disabled){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(zdp(6),zdp(1),zdp(6),zdp(1));
        row.setMinimumHeight(zdp(36));
        row.setBackgroundColor(number%2==1?Color.rgb(28,28,28):Color.BLACK);
        row.setAlpha(disabled?0.5f:1f);

        boolean medley=isMedleySong(song) && !song.medleyItems.isEmpty();
        boolean medleyExpanded=medley && expandedMedleys.contains(song.id);

        TextView num=new TextView(this);
        num.setText(String.format("%02d",number));
        num.setTextColor(Color.LTGRAY);
        num.setTextSize(zsp(14));
        num.setGravity(Gravity.CENTER);
        num.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(num,new LinearLayout.LayoutParams(zdp(34),ViewGroup.LayoutParams.MATCH_PARENT));

        TextView name=new TextView(this);
        String title=song==null?"":song.title;
        String bpm=song==null?"":song.bpm;
        name.setText((medley?(medleyExpanded?"▾ ":"▸ "):"")+(title==null?"":title));
        name.setTextColor(Color.WHITE);
        name.setTextSize(zsp(20));
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
            stageBox.setPadding(zdp(2),0,zdp(2),0);

            GradientDrawable stageBg=new GradientDrawable();
            stageBg.setColor(Color.BLACK);
            stageBg.setCornerRadius(zdp(4));
            stageBg.setStroke(Math.max(1,zdp(1)),Color.rgb(70,70,70));
            stageBox.setBackground(stageBg);

            if(song.stageNum1!=null && !song.stageNum1.trim().isEmpty()){
                TextView n1=new TextView(this);
                n1.setText(twoDigits(song.stageNum1));
                n1.setTextColor(Color.WHITE);
                n1.setTextSize(zsp(12));
                n1.setGravity(Gravity.CENTER);
                n1.setPadding(zdp(2),0,zdp(2),0);
                stageBox.addView(n1,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,zdp(24)
                ));
            }

            if(song.stageNum2!=null && !song.stageNum2.trim().isEmpty()){
                TextView n2=new TextView(this);
                n2.setText(twoDigits(song.stageNum2));
                n2.setTextColor(Color.RED);
                n2.setTextSize(zsp(12));
                n2.setGravity(Gravity.CENTER);
                n2.setPadding(zdp(2),0,zdp(2),0);
                stageBox.addView(n2,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,zdp(24)
                ));
            }

            if(song.stageGuitar){
                TextView guitar=new TextView(this);
                guitar.setText("🎸");
                guitar.setTextSize(zsp(14));
                guitar.setGravity(Gravity.CENTER);
                guitar.setPadding(zdp(1),0,zdp(1),0);
                stageBox.addView(guitar,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,zdp(24)
                ));
            }

            if(song.stageKeyboard){
                TextView keyboard=new TextView(this);
                keyboard.setText("🎹");
                keyboard.setTextSize(zsp(14));
                keyboard.setGravity(Gravity.CENTER);
                keyboard.setPadding(zdp(1),0,zdp(1),0);
                stageBox.addView(keyboard,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,zdp(24)
                ));
            }

            row.addView(stageBox,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                zdp(26)
            ));
        }

        TextView bpmView=new TextView(this);
        bpmView.setText(bpm==null || bpm.trim().isEmpty() ? "" : bpm.trim());
        bpmView.setTextColor(Color.rgb(255,196,30));
        bpmView.setTextSize(zsp(14));
        bpmView.setGravity(Gravity.RIGHT|Gravity.CENTER_VERTICAL);
        bpmView.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        bpmView.setSingleLine(true);
        bpmView.setPadding(zdp(4),0,0,0);
        if(bpm==null || bpm.trim().isEmpty()){
            bpmView.setVisibility(View.GONE);
        }
        row.addView(bpmView,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ));

        if(medley){
            row.setOnClickListener(v->toggleMedley(song.id));
        }

        songsBox.addView(row,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        if(medley && medleyExpanded){
            LinearLayout medleyBox=new LinearLayout(this);
            medleyBox.setOrientation(LinearLayout.VERTICAL);
            medleyBox.setPadding(zdp(40),zdp(2),zdp(8),zdp(5));
            medleyBox.setBackgroundColor(Color.rgb(18,18,18));
            medleyBox.setAlpha(disabled?0.5f:1f);

            for(int i=0;i<song.medleyItems.size();i++){
                TextView item=new TextView(this);
                item.setText(String.format(Locale.ROOT,"%02d. %s",i+1,song.medleyItems.get(i)));
                item.setTextColor(Color.WHITE);
                item.setTextSize(zsp(13));
                item.setPadding(zdp(6),zdp(2),0,zdp(2));
                medleyBox.addView(item,new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ));
            }

            songsBox.addView(medleyBox,new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ));
        }

        View sep=new View(this);
        sep.setBackgroundColor(Color.rgb(45,45,45));
        songsBox.addView(sep,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,Math.max(1,zdp(1))
        ));
    }
}
