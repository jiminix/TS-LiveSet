package com.jiminix.liveset;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class PlaylistCloudSync {
    // Build Internet sync V0.36
    private static final String PREFS="viewer_cloud_sync";
    private static final String K_BLOB_ID="blob_id";
    private static final String API="https://api.jsonstorage.net/v1/json";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final MediaType JSON=MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT=new OkHttpClient.Builder()
        .connectTimeout(15,TimeUnit.SECONDS)
        .readTimeout(15,TimeUnit.SECONDS)
        .writeTimeout(15,TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build();

    private static Runnable pendingPublish;

    public interface Listener {
        void onSuccess(String code);
        void onError(String message);
    }

    private PlaylistCloudSync(){}

    public static String getCode(Context c){
        String code=prefs(c).getString(K_BLOB_ID,"");
        if(code!=null && !code.isEmpty() && !code.contains("/")){
            prefs(c).edit().remove(K_BLOB_ID).apply();
            return "";
        }
        return code==null?"":code;
    }

    public static boolean isConfigured(Context c){
        return !getCode(c).isEmpty();
    }

    public static void maybePublish(Context c){
        if(!isConfigured(c))return;
        Context app=c.getApplicationContext();
        synchronized(PlaylistCloudSync.class){
            if(pendingPublish!=null)MAIN.removeCallbacks(pendingPublish);
            pendingPublish=()->publishSelected(app,null);
            MAIN.postDelayed(pendingPublish,900);
        }
    }

    public static void publishSelected(Context c, Listener listener){
        Context app=c.getApplicationContext();
        new Thread(()->{
            try{
                JSONObject payload=selectedPlaylistJson(app);
                String code=getCode(app);

                if(code.isEmpty()){
                    JSONObject created=requestJson("POST",API,payload);
                    String uri=created.optString("uri","").trim();
                    if(uri.isEmpty())throw new Exception("Code de synchronisation introuvable");

                    String prefix=API+"/";
                    code=uri.startsWith(prefix)?uri.substring(prefix.length()):uri;
                    while(code.startsWith("/"))code=code.substring(1);
                    while(code.endsWith("/"))code=code.substring(0,code.length()-1);

                    if(code.isEmpty() || !code.contains("/")){
                        throw new Exception("Code Internet invalide");
                    }

                    prefs(app).edit().putString(K_BLOB_ID,code).apply();
                }else{
                    requestJson("PUT",API+"/"+code,payload);
                }

                final String resultCode=code;
                if(listener!=null)MAIN.post(()->listener.onSuccess(resultCode));
            }catch(Exception e){
                if(listener!=null){
                    String msg=e.getMessage();
                    if(msg==null || msg.trim().isEmpty())msg="Erreur de connexion Internet";
                    final String out=msg;
                    MAIN.post(()->listener.onError(out));
                }
            }
        },"TS-Playlist-Cloud").start();
    }

    private static JSONObject requestJson(String method,String url,JSONObject payload) throws Exception{
        Exception last=null;

        for(int attempt=0;attempt<2;attempt++){
            try{
                RequestBody body=RequestBody.create(JSON,payload.toString());
                Request.Builder b=new Request.Builder()
                    .url(url)
                    .header("Accept","application/json")
                    .header("Accept-Encoding","identity")
                    .header("Connection","close")
                    .header("User-Agent","TS-LiveSet/0.36");

                if("POST".equals(method))b.post(body);
                else b.put(body);

                try(Response response=CLIENT.newCall(b.build()).execute()){
                    int status=response.code();
                    String raw=response.body()==null?"":response.body().string();
                    if(status<200 || status>=300){
                        throw new Exception("HTTP "+status+(raw.isEmpty()?"":" · "+shortMessage(raw)));
                    }
                    if(raw.trim().isEmpty())return new JSONObject();
                    return new JSONObject(raw);
                }
            }catch(Exception e){
                last=e;
                if(attempt==0){
                    try{Thread.sleep(700);}catch(InterruptedException ignored){}
                }
            }
        }

        throw last==null?new Exception("Erreur de connexion Internet"):last;
    }

    private static String shortMessage(String raw){
        String s=raw.replaceAll("\\s+"," ").trim();
        return s.length()>100?s.substring(0,100):s;
    }

    private static JSONObject selectedPlaylistJson(Context c) throws Exception{
        JSONObject out=new JSONObject();
        String id=AppStore.getViewerSetlistId(c);
        SetListModel list=(id==null||id.isEmpty())?null:AppStore.findSetlist(c,id);

        out.put("schema",1);
        out.put("managerVersion","0.36");
        out.put("updatedAt",System.currentTimeMillis());

        if(list==null){
            out.put("available",false);
            out.put("songs",new JSONArray());
            return out;
        }

        out.put("available",true);
        out.put("playlist_id",list.id);
        out.put("playlist_name",list.name);

        JSONArray songs=new JSONArray();
        for(String songId:list.songIds){
            Song s=AppStore.findSong(c,songId);
            if(s==null)continue;
            JSONObject o=new JSONObject();
            o.put("id",s.id);
            o.put("title",s.title);
            o.put("bpm",s.bpm);
            o.put("stageNum1",s.stageNum1);
            o.put("stageNum2",s.stageNum2);
            o.put("stageGuitar",s.stageGuitar);
            o.put("stageKeyboard",s.stageKeyboard);
            songs.put(o);
        }

        out.put("songs",songs);
        return out;
    }

    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
}
