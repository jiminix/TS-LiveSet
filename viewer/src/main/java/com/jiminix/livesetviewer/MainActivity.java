package com.jiminix.livesetviewer;

import android.app.AlertDialog;
import android.graphics.Color;
import android.content.res.ColorStateList;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Base64;
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
import java.nio.ByteBuffer;
import java.util.UUID;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {
    // Build V0.8 denser viewer rows
    // Build V0.9 page up/down navigation
    // Build V0.10 TS 2026 info line with page arrows
    // Build V0.11 force TS 2026, title count and page arrows onto one line
    // Build V0.12 accept compact 22-character connection codes
    // Build V0.13 accept one-letter one-digit A0-Z9 pairing codes
    // Build V0.14 retry and cache short-code resolution
    // Build V0.15 compact slogan/info on one row
    // Build V0.16 fit full slogan on one line
    // Build V0.17 Meryl title and count-only info line
    // Build V0.18 playlist name in top header and centered title count
    // Build V0.19 direct Y6 pairing without registry lookup
    // Build V0.20 read-only En cours playlist toggle
    // Build V0.21 red En cours and colored Viewer bands
    // Viewer V0.5 Internet sync
    // Viewer V0.6 SuperJSONBlob
    // Viewer V0.7 raw code parsing
    private static final String API="https://superjsonblob.com/api/jsonBlob";
    private static final String SHORT_REGISTRY_ID="95af90a8-317b-4588-b7ab-5153db677527";
    private static final String SHORT_REGISTRY_API=API+"/"+SHORT_REGISTRY_ID;
    private static final String PREFS="viewer_cloud";
    private static final String K_CODE="sync_code";
    private static final String K_CACHE="cached_payload";
    private static final String K_RESOLVED_CODE="resolved_pair_code";
    private static final String K_RESOLVED_BLOB="resolved_blob_id";
    private static final String BOOTSTRAP_CODE="Y6";
    private static final String BOOTSTRAP_BLOB="d7e6c82a-82e0-4336-8e66-d49b9d013312";

    private static final OkHttpClient CLIENT=new OkHttpClient.Builder()
        .connectTimeout(15,TimeUnit.SECONDS)
        .readTimeout(15,TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(Collections.singletonList(Protocol.HTTP_1_1))
        .build();

    private final Handler handler=new Handler(Looper.getMainLooper());
    private TextView playlistTitle;
    private TextView appTitle;
    private TextView info;
    private Button inProgressButton;
    private LinearLayout songsBox;
    private ScrollView scroll;
    private String lastSignature="";
    private volatile boolean cloudBusy=false;
    private boolean showInProgress=false;

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

        appTitle=new TextView(this);
        appTitle.setText("Playlist");
        appTitle.setTextColor(Color.rgb(255,196,30));
        appTitle.setTextSize(22);
        appTitle.setGravity(Gravity.CENTER);
        appTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        appTitle.setBackgroundColor(Color.rgb(105,12,18));
        root.addView(appTitle,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(44)));

        playlistTitle=new TextView(this);
        playlistTitle.setText("Connexion Viewer");
        playlistTitle.setTextColor(Color.WHITE);
        playlistTitle.setTextSize(25);
        playlistTitle.setGravity(Gravity.CENTER);
        playlistTitle.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        playlistTitle.setPadding(dp(6),dp(8),dp(6),dp(4));
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

        Button pageUp=new Button(this);
        pageUp.setText("↑");
        pageUp.setTextSize(14);
        pageUp.setMinWidth(0);
        pageUp.setMinimumWidth(0);
        pageUp.setPadding(0,0,0,0);
        infoRow.addView(pageUp,new LinearLayout.LayoutParams(dp(26),dp(26)));

        Button pageDown=new Button(this);
        pageDown.setText("↓");
        pageDown.setTextSize(14);
        pageDown.setMinWidth(0);
        pageDown.setMinimumWidth(0);
        pageDown.setPadding(0,0,0,0);
        infoRow.addView(pageDown,new LinearLayout.LayoutParams(dp(26),dp(26)));

        root.addView(infoRow,new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,dp(26)));

        LinearLayout viewerActions=new LinearLayout(this);
        viewerActions.setOrientation(LinearLayout.HORIZONTAL);
        viewerActions.setGravity(Gravity.CENTER_VERTICAL);

        inProgressButton=new Button(this);
        inProgressButton.setText("En cours");
        inProgressButton.setTextSize(11);
        inProgressButton.setMinWidth(0);
        inProgressButton.setMinimumWidth(0);
        inProgressButton.setBackgroundTintList(ColorStateList.valueOf(Color.rgb(198,40,40)));
        inProgressButton.setTextColor(Color.WHITE);

        Button internet=new Button(this);
        internet.setText("🌐 Connexion");
        internet.setTextSize(11);
        internet.setMinWidth(0);
        internet.setMinimumWidth(0);

        viewerActions.addView(inProgressButton,new LinearLayout.LayoutParams(0,dp(36),1));
        viewerActions.addView(internet,new LinearLayout.LayoutParams(0,dp(36),1));
        root.addView(viewerActions,new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,dp(36)
        ));

        internet.setOnClickListener(v->configureInternet());
        inProgressButton.setOnClickListener(v->toggleInProgress());

        scroll=new ScrollView(this);
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
        footer.setTextColor(Color.WHITE);
        footer.setBackgroundColor(Color.rgb(105,12,18));
        footer.setTextSize(10);
        footer.setGravity(Gravity.CENTER);
        footer.setPadding(0,dp(5),0,dp(2));
        root.addView(footer);

        pageUp.setOnClickListener(v->pageScroll(-1));
        pageDown.setOnClickListener(v->pageScroll(1));

        setContentView(root);
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
                showInProgress?"get_in_progress_playlist":"get_selected_playlist",
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
                            String msg=e.getMessage();
                            if(msg==null || msg.trim().isEmpty())msg="Erreur Internet";
                            showEmpty("Connexion Internet impossible","Erreur : "+msg);
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
        String resolved=resolvePairCode(code);
        Exception last=null;

        for(int attempt=0;attempt<2;attempt++){
            try{
                Request req=new Request.Builder()
                    .url(API+"/"+resolved)
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
        if(payload==null){
            showEmpty("Aucune playlist publiée","Aucune donnée reçue du Manager.");
            return;
        }

        JSONObject sourcePayload=payload;
        if(showInProgress){
            sourcePayload=payload.optJSONObject("in_progress");
            if(sourcePayload==null){
                showEmpty("En cours","Cette version du Manager n’a pas encore publié la playlist En cours.");
                return;
            }
        }

        if(!sourcePayload.optBoolean("available",false)){
            showEmpty(showInProgress?"En cours":"Aucune playlist publiée",
                showInProgress
                    ? "La playlist En cours est vide ou indisponible."
                    : "Dans le Manager, ouvre la playlist voulue puis appuie sur « Viewer ».");
            return;
        }

        String name=sourcePayload.optString("playlist_name",showInProgress?"En cours":"Playlist");
        JSONArray songs=sourcePayload.optJSONArray("songs");
        if(songs==null)songs=new JSONArray();

        renderPlaylist(name,songs,cached?"Internet · dernière copie enregistrée":"Internet · à jour");
    }

    private void renderPlaylist(String name,JSONArray songs,String source){
        String signature=name+"|"+songs.toString()+"|"+source;
        if(signature.equals(lastSignature))return;
        lastSignature=signature;

        playlistTitle.setText(name);
        appTitle.setText((name==null || name.trim().isEmpty()) ? "Playlist" : name.trim());
        info.setText(songs.length()+" titre"+(songs.length()>1?"s":""));
        songsBox.removeAllViews();

        for(int i=0;i<songs.length();i++){
            JSONObject s=songs.optJSONObject(i);
            if(s==null)continue;
            addSongRow(i+1,s.optString("title",""),s.optString("bpm",""));
        }
    }

    private void configureInternet(){
        EditText input=new EditText(this);
        input.setHint("Exemple : A7");
        input.setSingleLine(true);
        input.setText(shortCode(getCode()));
        input.selectAll();

        new AlertDialog.Builder(this)
            .setTitle("Connexion Internet")
            .setMessage("Entre le code à 2 caractères affiché dans TS Playlist Manager, par exemple A7.")
            .setView(input)
            .setPositiveButton("Connecter",(d,w)->{
                String code=cleanCode(input.getText().toString());
                if(code.isEmpty()){
                    showEmpty("Code Internet invalide","Entre le code à 2 caractères affiché par le Manager.");
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
        if(code.matches("(?i)[A-Z][0-9]"))return code.toUpperCase();
        String expanded=expandShortCode(code);
        return expanded.isEmpty()?code:expanded;
    }

    private String resolvePairCode(String raw) throws Exception{
        if(raw==null)return "";
        String code=raw.trim().toUpperCase();
        if(!code.matches("[A-Z][0-9]"))return raw.trim();

        // Y6 is the permanent direct pairing code for this playlist.
        if(BOOTSTRAP_CODE.equals(code)){
            return BOOTSTRAP_BLOB;
        }

        Exception last=null;
        for(int attempt=0;attempt<3;attempt++){
            try{
                Request req=new Request.Builder()
                    .url(SHORT_REGISTRY_API)
                    .header("Accept","application/json")
                    .header("Accept-Encoding","identity")
                    .header("Connection","close")
                    .header("User-Agent","TS-Playlist-Viewer/0.14")
                    .get()
                    .build();

                try(Response response=CLIENT.newCall(req).execute()){
                    int status=response.code();
                    String body=response.body()==null?"":response.body().string();
                    if(status<200 || status>=300)throw new Exception("Registre HTTP "+status);
                    JSONObject registry=new JSONObject(body);
                    JSONObject slots=registry.optJSONObject("slots");
                    JSONObject entry=slots==null?null:slots.optJSONObject(code);
                    String blob=entry==null?"":entry.optString("blob","").trim();
                    if(blob.isEmpty())throw new Exception("Code "+code+" introuvable");

                    getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                        .putString(K_RESOLVED_CODE,code)
                        .putString(K_RESOLVED_BLOB,blob)
                        .apply();
                    return blob;
                }
            }catch(Exception e){
                last=e;
                if(attempt<2){
                    try{Thread.sleep(650);}catch(InterruptedException ignored){}
                }
            }
        }

        String savedCode=getSharedPreferences(PREFS,MODE_PRIVATE).getString(K_RESOLVED_CODE,"");
        String savedBlob=getSharedPreferences(PREFS,MODE_PRIVATE).getString(K_RESOLVED_BLOB,"");
        if(code.equalsIgnoreCase(savedCode) && savedBlob!=null && !savedBlob.trim().isEmpty()){
            return savedBlob.trim();
        }

        // Emergency bootstrap for the current TS 2026 pairing.
        if(BOOTSTRAP_CODE.equals(code)){
            getSharedPreferences(PREFS,MODE_PRIVATE).edit()
                .putString(K_RESOLVED_CODE,code)
                .putString(K_RESOLVED_BLOB,BOOTSTRAP_BLOB)
                .apply();
            return BOOTSTRAP_BLOB;
        }

        throw last==null?new Exception("Code "+code+" introuvable"):last;
    }

    private String shortCode(String raw){
        if(raw==null || raw.trim().isEmpty())return "";
        String trimmed=raw.trim();
        if(trimmed.matches("(?i)[A-Z][0-9]"))return trimmed.toUpperCase();
        try{
            UUID uuid=UUID.fromString(trimmed);
            ByteBuffer b=ByteBuffer.allocate(16);
            b.putLong(uuid.getMostSignificantBits());
            b.putLong(uuid.getLeastSignificantBits());
            return Base64.encodeToString(
                b.array(),
                Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING
            );
        }catch(Exception ignored){
            return trimmed;
        }
    }

    private String expandShortCode(String raw){
        if(raw==null)return "";
        String code=raw.trim();
        if(!code.matches("[A-Za-z0-9_-]{22}"))return "";
        try{
            byte[] bytes=Base64.decode(code+"==",Base64.URL_SAFE|Base64.NO_WRAP);
            if(bytes.length!=16)return "";
            ByteBuffer b=ByteBuffer.wrap(bytes);
            return new UUID(b.getLong(),b.getLong()).toString();
        }catch(Exception ignored){
            return "";
        }
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
        appTitle.setText("Meryl");
        info.setText(message);
        songsBox.removeAllViews();
    }

    private void addSongRow(int number,String title,String bpm){
        LinearLayout row=new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6),dp(1),dp(6),dp(1));
        row.setMinimumHeight(dp(36));
        row.setBackgroundColor(number%2==1?Color.rgb(28,28,28):Color.BLACK);

        TextView num=new TextView(this);
        num.setText(String.format("%02d",number));
        num.setTextColor(Color.LTGRAY);
        num.setTextSize(14);
        num.setGravity(Gravity.CENTER);
        num.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        row.addView(num,new LinearLayout.LayoutParams(dp(36),ViewGroup.LayoutParams.MATCH_PARENT));

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
        bpmView.setPadding(dp(4),0,0,0);
        row.addView(bpmView,new LinearLayout.LayoutParams(dp(62),ViewGroup.LayoutParams.MATCH_PARENT));

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
