package com.jiminix.livesetviewer;

import android.app.AlertDialog;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    // Viewer V0.5 Internet sync
    // Viewer V0.6 SuperJSONBlob
    // Viewer V0.7 raw code parsing
    private static final String API="https://superjsonblob.com/api/jsonBlob";
    private static final String PREFS="viewer_cloud";
    private static final String K_CODE="sync_code";
    private static final String K_CACHE="cached_payload";

    private static final OkHttpClient CLIENT=new OkHttpClient.Builder()
        .connectTimeout(15,TimeUnit.SECONDS)
        .readTimeout(15,TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(Collections.singletonList(Protocol.HTTP_1_1))
        .build();

    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView playlistTitle;
    private TextView info;
    private LinearLayout songsBox;
    private String lastSignature="";
    private volatile boolean cloudBusy=false;

    private final Runnable refreshLoop=new Runnable(){
        @Override public void run(){
            refreshPlaylist();
            handler.postDelayed(this,2500);
        }
    };

    @Override protected void onCreate(Bundle b){
        super.onCreate(b);
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
        root.setPadding(dp(10),dp(12),dp(10),dp(10));

        TextView appTitle=new TextView(this);
        appTitle.setText("TS PLAYLIST VIEWER");
        appTitle.setTextColor(Color.rgb(255,196,30));
        appTitle.setTextSize(22);
        appTitle.setGravity(Gravity.CENTER);
        appTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        root.addView(appTitle,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)));

        TextView slogan=new TextView(this);
        slogan.setText("1 pour tous, tous pour la même playlist.");
        slogan.setTextColor(Color.WHITE);
        slogan.setTextSize(13);
        slogan.setGravity(Gravity.CENTER);
        slogan.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        slogan.setPadding(dp(4),0,dp(4),dp(6));
        root.addView(slogan,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT));

        playlistTitle=new TextView(this);
        playlistTitle.setText("Connexion Viewer");
        playlistTitle.setTextColor(Color.WHITE);
        playlistTitle.setTextSize(25);
        playlistTitle.setGravity(Gravity.CENTER);
        playlistTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        playlistTitle.setPadding(dp(6),dp(8),dp(6),dp(4));
        root.addView(playlistTitle);

        info=new TextView(this);
        info.setText("Connexion au Manager…");
        info.setTextColor(Color.LTGRAY);
        info.setTextSize(12);
        info.setGravity(Gravity.CENTER);
        info.setPadding(dp(8),0,dp(8),dp(6));
        root.addView(info);

        Button internet=new Button(this);
        internet.setText("🌐 Connexion Internet");
        internet.setTextSize(13);
        internet.setOnClickListener(v->configureInternet());
        root.addView(internet,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)));

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
        footer.setText("Lecture seule · synchronisation Internet automatique");
        footer.setTextColor(Color.DKGRAY);
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0,dp(5),0,dp(2));
        root.addView(footer);

        setContentView(root);
    }

    private void refreshPlaylist(){
        if(tryLocalManager())return;

        String code=getCode();
        if(code.isEmpty()){
            showEmpty("Connexion Viewer",
                "Appuie sur « Connexion Internet » et entre le code affiché par TS Playlist Manager.");
            return;
        }

        refreshCloud(code);
    }

    private boolean tryLocalManager(){
        try{
            Bundle b=getContentResolver().call(
                Uri.parse("content://com.jiminix.liveset.playlists"),
                "get_selected_playlist",
                null,
                null
            );

            if(b==null || !b.getBoolean("available",false))return false;

            String name=b.getString("playlist_name","Playlist");
            JSONArray songs=new JSONArray(b.getString("songs_json","[]"));
            renderPlaylist(name,songs,"Local · synchronisé avec TS Playlist Manager");
            return true;
        }catch(Exception ignored){
            return false;
        }
    }

    private void refreshCloud(String code){
        if(cloudBusy)return;
        cloudBusy=true;

        new Thread(()->{
            try{
                String raw=getCloudJson(code);
                JSONObject payload=new JSONObject(raw);
                getSharedPreferences(PREFS,MODE_PRIVATE).edit().putString(K_CACHE,raw).apply();
                runOnUiThread(()->renderCloudPayload(payload,false));
            }catch(Exception e){
                String cached=getSharedPreferences(PREFS,MODE_PRIVATE).getString(K_CACHE,"");
                runOnUiThread(()->{
                    if(!cached.isEmpty()){
                        try{
                            renderCloudPayload(new JSONObject(cached),true);
                        }catch(Exception ignored){
                            showEmpty("Connexion Internet impossible","Vérifie le code Viewer et la connexion Internet.");
                        }
                    }else{
                        String msg=e.getMessage();
                        if(msg==null || msg.trim().isEmpty())msg="Erreur Internet";
                        showEmpty("Connexion Internet impossible","Erreur : "+msg);
                    }
                });
            }finally{
                cloudBusy=false;
            }
        },"TS-Viewer-Cloud").start();
    }

    private String getCloudJson(String code) throws Exception{
        Exception last=null;

        for(int attempt=0;attempt<2;attempt++){
            try{
                Request req=new Request.Builder()
                    .url(API+"/"+code)
                    .header("Accept","application/json")
                    .header("Accept-Encoding","identity")
                    .header("Connection","close")
                    .header("User-Agent","TS-Playlist-Viewer/0.6")
                    .get()
                    .build();

                try(Response response=CLIENT.newCall(req).execute()){
                    int status=response.code();
                    String raw=response.body()==null?"":response.body().string();
                    if(status<200 || status>=300)throw new Exception("HTTP "+status);
                    if(raw.trim().isEmpty())throw new Exception("Réponse Internet vide");
                    return raw;
                }
            }catch(Exception e){
                last=e;
                if(attempt==0){
                    try{Thread.sleep(700);}catch(InterruptedException ignored){}
                }
            }
        }

        throw last==null?new Exception("Erreur Internet"):last;
    }

    private void renderCloudPayload(JSONObject payload,boolean cached){
        if(payload==null || !payload.optBoolean("available",false)){
            showEmpty("Aucune playlist publiée",
                "Dans le Manager, ouvre la playlist voulue puis appuie sur « Viewer ».");
            return;
        }

        String name=payload.optString("playlist_name","Playlist");
        JSONArray songs=payload.optJSONArray("songs");
        if(songs==null)songs=new JSONArray();

        renderPlaylist(name,songs,cached?"Internet · dernière copie enregistrée":"Internet · à jour");
    }

    private void renderPlaylist(String name,JSONArray songs,String source){
        String signature=name+"|"+songs.toString()+"|"+source;
        if(signature.equals(lastSignature))return;
        lastSignature=signature;

        playlistTitle.setText(name);
        info.setText(songs.length()+" titre"+(songs.length()>1?"s":"")+" · "+source);
        songsBox.removeAllViews();

        for(int i=0;i<songs.length();i++){
            JSONObject s=songs.optJSONObject(i);
            if(s==null)continue;
            addSongRow(i+1,s.optString("title",""),s.optString("bpm",""));
        }
    }

    private void configureInternet(){
        EditText input=new EditText(this);
        input.setHint("Code Internet du Manager");
        input.setSingleLine(true);
        input.setText(getCode());
        input.selectAll();

        new AlertDialog.Builder(this)
            .setTitle("Connexion Internet")
            .setMessage("Entre le code affiché dans TS Playlist Manager. Les téléphones peuvent être sur des réseaux différents.")
            .setView(input)
            .setPositiveButton("Connecter",(d,w)->{
                String code=cleanCode(input.getText().toString());
                if(code.isEmpty()){
                    showEmpty("Code Internet invalide","Recopie le code complet affiché par le Manager.");
                    return;
                }

                getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                    .putString(K_CODE,code)
                    .remove(K_CACHE)
                    .apply();

                lastSignature="";
                refreshPlaylist();
            })
            .setNeutralButton("Effacer",(d,w)->{
                getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                    .remove(K_CODE)
                    .remove(K_CACHE)
                    .apply();

                lastSignature="";
                showEmpty("Connexion Viewer","Aucun Manager Internet configuré.");
            })
            .setNegativeButton("Annuler",null)
            .show();
    }

    private String cleanCode(String raw){
        if(raw==null)return "";
        String code=raw.trim();

        Matcher uuid=Pattern.compile("(?i)[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}").matcher(code);
        if(uuid.find())return uuid.group();

        String prefix=API+"/";
        if(code.startsWith(prefix))code=code.substring(prefix.length());

        while(code.endsWith("/"))code=code.substring(0,code.length()-1);

        int slash=code.lastIndexOf('/');
        if(slash>=0)code=code.substring(slash+1);

        if(code.contains(":")){
            code=code.substring(code.lastIndexOf(':')+1);
        }

        code=code.trim().replaceAll("[^A-Za-z0-9_-]","");
        return code;
    }

    private String getCode(){
        String code=getSharedPreferences(PREFS,MODE_PRIVATE).getString(K_CODE,"").trim();
        if(code.contains("/")){
            getSharedPreferences(PREFS,MODE_PRIVATE).edit().remove(K_CODE).remove(K_CACHE).apply();
            return "";
        }
        return code;
    }

    private void showEmpty(String title,String message){
        String signature=title+"|"+message;
        if(signature.equals(lastSignature))return;

        lastSignature=signature;
        playlistTitle.setText(title);
        info.setText(message);
        songsBox.removeAllViews();
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
        name.setText(title);
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
