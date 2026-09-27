package com.jiminix.liveset;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public final class PlaylistCloudSync {
    // Build Internet sync V0.34
    // Build Internet sync V0.36
    private static final String PREFS="viewer_cloud_sync";
    private static final String K_BLOB_ID="blob_id";
    private static final String API="https://api.jsonstorage.net/v1/json";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static Runnable pendingPublish;

    public interface Listener {
        void onSuccess(String code);
        void onError(String message);
    }

    private PlaylistCloudSync(){}

    public static String getCode(Context c){
        return prefs(c).getString(K_BLOB_ID,"");
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
                String id=getCode(app);

                if(id.isEmpty()){
                    HttpURLConnection con=open(API,"POST");
                    writeJson(con,payload);
                    int status=con.getResponseCode();
                    if(status<200 || status>=300)throw new Exception("HTTP "+status);

                    String raw=readResponse(con);
                    JSONObject created=new JSONObject(raw);
                    String uri=created.optString("uri","").trim();
                    if(uri.isEmpty())throw new Exception("Code de synchronisation introuvable");

                    String prefix=API+"/";
                    id=uri.startsWith(prefix)?uri.substring(prefix.length()):uri;
                    while(id.startsWith("/"))id=id.substring(1);
                    while(id.endsWith("/"))id=id.substring(0,id.length()-1);
                    if(id.isEmpty() || !id.contains("/"))throw new Exception("Code Internet invalide");

                    prefs(app).edit().putString(K_BLOB_ID,id).apply();
                    con.disconnect();
                }else{
                    HttpURLConnection con=open(API+"/"+id,"PUT");
                    writeJson(con,payload);
                    int status=con.getResponseCode();
                    if(status<200 || status>=300)throw new Exception("HTTP "+status);
                    con.disconnect();
                }

                final String code=id;
                if(listener!=null)MAIN.post(()->listener.onSuccess(code));
            }catch(Exception e){
                if(listener!=null){
                    String msg=e.getMessage()==null?"Erreur Internet":e.getMessage();
                    MAIN.post(()->listener.onError(msg));
                }
            }
        },"TS-Playlist-Cloud").start();
    }

    private static HttpURLConnection open(String url,String method) throws Exception{
        HttpURLConnection con=(HttpURLConnection)new URL(url).openConnection();
        con.setRequestMethod(method);
        con.setConnectTimeout(10000);
        con.setReadTimeout(12000);
        con.setRequestProperty("Content-Type","application/json");
        con.setRequestProperty("Accept","application/json");
        con.setRequestProperty("User-Agent","TS-LiveSet/0.36");
        con.setDoInput(true);
        if("POST".equals(method)||"PUT".equals(method))con.setDoOutput(true);
        return con;
    }

    private static void writeJson(HttpURLConnection con,JSONObject payload) throws Exception{
        byte[] data=payload.toString().getBytes(StandardCharsets.UTF_8);
        con.setFixedLengthStreamingMode(data.length);
        try(OutputStream os=con.getOutputStream()){
            os.write(data);
        }
    }

    private static String readResponse(HttpURLConnection con) throws Exception{
        BufferedReader br=new BufferedReader(new InputStreamReader(con.getInputStream(),StandardCharsets.UTF_8));
        StringBuilder sb=new StringBuilder();
        String line;
        while((line=br.readLine())!=null)sb.append(line).append('\n');
        br.close();
        return sb.toString();
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
