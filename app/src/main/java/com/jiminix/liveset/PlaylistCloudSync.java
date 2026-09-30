package com.jiminix.liveset;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.concurrent.TimeUnit;
import java.util.Collections;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public final class PlaylistCloudSync {
    // Build Internet sync V0.36
    // Build Internet sync V0.37
    // Build Internet sync V0.51 compact 22-character share code
    // Build Internet sync V0.52 two-character A0-Z9 pairing codes
    // Build Internet sync V0.54 refresh short-code mapping
    // Build Internet sync V0.59 fixed Y6 direct pairing
    // Build Internet sync V0.60 publish main and En cours playlists together
    private static final String PREFS="viewer_cloud_sync";
    private static final String K_BLOB_ID="blob_id";
    private static final String K_SHORT_CODE="short_code";
    private static final String API="https://superjsonblob.com/api/jsonBlob";
    private static final String FIXED_SHARE_CODE="Y6";
    private static final String FIXED_BLOB_ID="d7e6c82a-82e0-4336-8e66-d49b9d013312";
    private static final String SHORT_REGISTRY_ID="95af90a8-317b-4588-b7ab-5153db677527";
    private static final String SHORT_REGISTRY_API=API+"/"+SHORT_REGISTRY_ID;
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final MediaType JSON=MediaType.parse("application/json; charset=utf-8");

    private static final OkHttpClient CLIENT=new OkHttpClient.Builder()
        .connectTimeout(15,TimeUnit.SECONDS)
        .readTimeout(15,TimeUnit.SECONDS)
        .writeTimeout(15,TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .protocols(Collections.singletonList(Protocol.HTTP_1_1))
        .build();

    private static Runnable pendingPublish;

    public interface Listener {
        void onSuccess(String code);
        void onError(String message);
    }

    private PlaylistCloudSync(){}

    public static String getCode(Context c){
        String code=prefs(c).getString(K_BLOB_ID,"");
        code=code==null?"":code.trim();
        if(code.contains("/")){
            prefs(c).edit().remove(K_BLOB_ID).apply();
            return "";
        }
        return code;
    }

    public static String getShareCode(Context c){
        return FIXED_SHARE_CODE;
    }

    private static String codeAt(int index){
        int safe=Math.floorMod(index,260);
        char letter=(char)('A'+(safe/10));
        char digit=(char)('0'+(safe%10));
        return ""+letter+digit;
    }

    private static String ensureShortCode(Context c,String blobId) throws Exception{
        String current=getShareCode(c);

        JSONObject registry=getJson(SHORT_REGISTRY_API);
        JSONObject slots=registry.optJSONObject("slots");
        if(slots==null)slots=new JSONObject();

        if(!current.isEmpty()){
            JSONObject existing=slots.optJSONObject(current);
            String mapped=existing==null?"":existing.optString("blob","").trim();
            if(mapped.isEmpty() || blobId.equals(mapped)){
                JSONObject refreshed=new JSONObject();
                refreshed.put("blob",blobId);
                refreshed.put("updatedAt",System.currentTimeMillis());
                slots.put(current,refreshed);
                registry.put("schema",1);
                registry.put("slots",slots);
                requestJson("PUT",SHORT_REGISTRY_API,registry);
                return current;
            }
            prefs(c).edit().remove(K_SHORT_CODE).apply();
        }

        java.util.Iterator<String> keys=slots.keys();
        while(keys.hasNext()){
            String key=keys.next();
            JSONObject entry=slots.optJSONObject(key);
            if(entry!=null && blobId.equals(entry.optString("blob","")) && key.matches("[A-Z][0-9]")){
                String found=key.toUpperCase();
                prefs(c).edit().putString(K_SHORT_CODE,found).apply();
                return found;
            }
        }

        int start=Math.floorMod(blobId.hashCode(),260);
        String chosen="";
        String oldestCode="";
        long oldestTime=Long.MAX_VALUE;

        for(int i=0;i<260;i++){
            String candidate=codeAt(start+i);
            JSONObject entry=slots.optJSONObject(candidate);
            if(entry==null || entry.optString("blob","").trim().isEmpty()){
                chosen=candidate;
                break;
            }
            long t=entry.optLong("updatedAt",0L);
            if(t<oldestTime){
                oldestTime=t;
                oldestCode=candidate;
            }
        }

        if(chosen.isEmpty())chosen=oldestCode.isEmpty()?codeAt(start):oldestCode;

        JSONObject entry=new JSONObject();
        entry.put("blob",blobId);
        entry.put("updatedAt",System.currentTimeMillis());
        slots.put(chosen,entry);
        registry.put("schema",1);
        registry.put("slots",slots);
        requestJson("PUT",SHORT_REGISTRY_API,registry);

        prefs(c).edit().putString(K_SHORT_CODE,chosen).apply();
        return chosen;
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

    public static void restoreInProgressFromCloud(Context c, Listener listener){
        Context app=c.getApplicationContext();
        new Thread(()->{
            try{
                JSONObject payload=getJson(API+"/"+FIXED_BLOB_ID);
                JSONObject progress=payload.optJSONObject("in_progress");
                JSONArray songs=progress==null?null:progress.optJSONArray("songs");
                if(songs==null || songs.length()==0){
                    if(listener!=null)MAIN.post(()->listener.onError("Aucune sauvegarde En cours trouvée sur Internet"));
                    return;
                }

                SetListModel list=AppStore.getOrCreateInProgressSetlist(app);
                java.util.List<Song> library=AppStore.loadSongs(app);
                java.util.List<String> recovered=new java.util.ArrayList<>();
                java.util.Map<String,String> remoteToLocal=new java.util.HashMap<>();
                int recreated=0;

                for(int i=0;i<songs.length();i++){
                    JSONObject o=songs.optJSONObject(i);
                    if(o==null)continue;

                    String remoteId=o.optString("id","").trim();
                    String title=o.optString("title","").trim();
                    Song local=remoteId.isEmpty()?null:AppStore.findSong(app,remoteId);

                    if(local==null && !title.isEmpty()){
                        for(Song candidate:library){
                            String ct=candidate.title==null?"":candidate.title.trim();
                            if(title.equalsIgnoreCase(ct)){
                                local=candidate;
                                break;
                            }
                        }
                    }

                    if(local==null && !title.isEmpty()){
                        local=new Song();
                        if(!remoteId.isEmpty())local.id=remoteId;
                        local.title=title;
                        local.artist=o.optString("artist","");
                        local.bpm=o.optString("bpm","");
                        local.stageNum1=o.optString("stageNum1","");
                        local.stageNum2=o.optString("stageNum2","");
                        local.stageGuitar=o.optBoolean("stageGuitar",false);
                        local.stageKeyboard=o.optBoolean("stageKeyboard",false);

                        JSONArray medleyItems=o.optJSONArray("medleyItems");
                        if(medleyItems!=null){
                            for(int m=0;m<medleyItems.length();m++){
                                String item=medleyItems.optString(m,"").trim();
                                if(!item.isEmpty())local.medleyItems.add(item);
                            }
                        }

                        JSONArray medleyArtists=o.optJSONArray("medleyArtists");
                        for(int m=0;m<local.medleyItems.size();m++){
                            local.medleyArtists.add(medleyArtists==null?"":medleyArtists.optString(m,""));
                            local.medleyLyrics.add("");
                        }

                        AppStore.upsertSong(app,local);
                        library.add(local);
                        recreated++;
                    }

                    if(local!=null){
                        if(!recovered.contains(local.id))recovered.add(local.id);
                        if(!remoteId.isEmpty())remoteToLocal.put(remoteId,local.id);
                    }
                }

                if(recovered.isEmpty()){
                    if(listener!=null)MAIN.post(()->listener.onError("La sauvegarde Y6 ne contient aucun morceau récupérable"));
                    return;
                }

                list.songIds.clear();
                list.songIds.addAll(recovered);
                list.disabledSongIds.clear();

                for(int i=0;i<songs.length();i++){
                    JSONObject o=songs.optJSONObject(i);
                    if(o==null || !o.optBoolean("disabled",false))continue;
                    String remoteId=o.optString("id","").trim();
                    String localId=remoteToLocal.get(remoteId);
                    if(localId!=null && recovered.contains(localId) && !list.disabledSongIds.contains(localId)){
                        list.disabledSongIds.add(localId);
                    }
                }

                AppStore.upsertSetlist(app,list);
                final int recreatedCount=recreated;
                if(listener!=null)MAIN.post(()->listener.onSuccess(FIXED_SHARE_CODE));
            }catch(Exception e){
                if(listener!=null){
                    String msg=e.getMessage();
                    if(msg==null || msg.trim().isEmpty())msg="Récupération Internet impossible";
                    final String out=msg;
                    MAIN.post(()->listener.onError(out));
                }
            }
        },"TS-EnCours-Restore").start();
    }

    public static void publishSelected(Context c, Listener listener){
        Context app=c.getApplicationContext();
        new Thread(()->{
            try{
                JSONObject payload=selectedPlaylistJson(app);

                // Never overwrite a non-empty remote En cours with an accidental empty local one.
                try{
                    JSONObject localProgress=payload.optJSONObject("in_progress");
                    JSONArray localSongs=localProgress==null?null:localProgress.optJSONArray("songs");
                    boolean localEmpty=localSongs==null || localSongs.length()==0;

                    if(localEmpty){
                        JSONObject remote=getJson(API+"/"+FIXED_BLOB_ID);
                        JSONObject remoteProgress=remote.optJSONObject("in_progress");
                        JSONArray remoteSongs=remoteProgress==null?null:remoteProgress.optJSONArray("songs");
                        if(remoteSongs!=null && remoteSongs.length()>0){
                            payload.put("in_progress",remoteProgress);
                        }
                    }
                }catch(Exception ignored){}

                // Stable direct pairing: Y6 always points to the same Internet blob.
                requestJson("PUT",API+"/"+FIXED_BLOB_ID,payload);
                prefs(app).edit()
                    .putString(K_BLOB_ID,FIXED_BLOB_ID)
                    .putString(K_SHORT_CODE,FIXED_SHARE_CODE)
                    .apply();

                if(listener!=null)MAIN.post(()->listener.onSuccess(FIXED_SHARE_CODE));
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

    private static String createRemote(JSONObject payload) throws Exception{
        Exception last=null;

        for(int attempt=0;attempt<2;attempt++){
            try{
                RequestBody body=RequestBody.create(JSON,payload.toString());
                Request req=new Request.Builder()
                    .url(API)
                    .header("Accept","application/json")
                    .header("Accept-Encoding","identity")
                    .header("Connection","close")
                    .header("User-Agent","TS-LiveSet/0.37")
                    .post(body)
                    .build();

                try(Response response=CLIENT.newCall(req).execute()){
                    int status=response.code();
                    String raw=response.body()==null?"":response.body().string();
                    if(status<200 || status>=300){
                        throw new Exception("HTTP "+status+(raw.isEmpty()?"":" · "+shortMessage(raw)));
                    }

                    String location=response.header("Location","");
                    if(location==null || location.trim().isEmpty()){
                        location=response.header("X-Jsonblob-Id","");
                    }

                    String code=location==null?"":location.trim();
                    if(code.startsWith(API+"/"))code=code.substring((API+"/").length());
                    int slash=code.lastIndexOf('/');
                    if(slash>=0)code=code.substring(slash+1);

                    if(code.isEmpty() && !raw.trim().isEmpty()){
                        try{
                            JSONObject o=new JSONObject(raw);
                            code=o.optString("id",o.optString("_id","")).trim();
                        }catch(Exception ignored){}
                    }

                    if(code.isEmpty())throw new Exception("Code Internet introuvable");
                    return code;
                }
            }catch(Exception e){
                last=e;
                if(attempt==0){
                    try{Thread.sleep(800);}catch(InterruptedException ignored){}
                }
            }
        }

        throw last==null?new Exception("Erreur de connexion Internet"):last;
    }

    private static JSONObject getJson(String url) throws Exception{
        Exception last=null;
        for(int attempt=0;attempt<2;attempt++){
            try{
                Request req=new Request.Builder()
                    .url(url)
                    .header("Accept","application/json")
                    .header("Accept-Encoding","identity")
                    .header("Connection","close")
                    .header("User-Agent","TS-LiveSet/0.52")
                    .get()
                    .build();

                try(Response response=CLIENT.newCall(req).execute()){
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
                    try{Thread.sleep(500);}catch(InterruptedException ignored){}
                }
            }
        }
        throw last==null?new Exception("Erreur registre code"):last;
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
                    .header("User-Agent","TS-LiveSet/0.37");

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
        SetListModel progress=AppStore.getOrCreateInProgressSetlist(c);

        out.put("schema",2);
        out.put("managerVersion","0.60");
        out.put("updatedAt",System.currentTimeMillis());

        if(list==null){
            out.put("available",false);
            out.put("songs",new JSONArray());
        }else{
            out.put("available",true);
            out.put("playlist_id",list.id);
            out.put("playlist_name",list.name);
            out.put("songs",songsJson(c,list));
        }

        JSONObject progressJson=new JSONObject();
        progressJson.put("available",true);
        progressJson.put("playlist_id",progress.id);
        progressJson.put("playlist_name","En cours");
        progressJson.put("songs",songsJson(c,progress));
        out.put("in_progress",progressJson);

        return out;
    }

    private static JSONArray songsJson(Context c,SetListModel list) throws Exception{
        JSONArray songs=new JSONArray();
        if(list==null)return songs;

        for(String songId:list.songIds){
            Song s=AppStore.findSong(c,songId);
            if(s==null)continue;
            JSONObject o=new JSONObject();
            o.put("id",s.id);
            o.put("title",s.title);
            o.put("artist",s.artist);
            o.put("bpm",s.bpm);
            o.put("stageNum1",s.stageNum1);
            o.put("stageNum2",s.stageNum2);
            o.put("stageGuitar",s.stageGuitar);
            o.put("stageKeyboard",s.stageKeyboard);
            JSONArray medleyItems=new JSONArray();
            for(String item:s.medleyItems)medleyItems.put(item);
            o.put("medleyItems",medleyItems);
            JSONArray medleyArtists=new JSONArray();
            for(String item:s.medleyArtists)medleyArtists.put(item);
            o.put("medleyArtists",medleyArtists);
            o.put("disabled",list.disabledSongIds.contains(songId));
            songs.put(o);
        }
        return songs;
    }

    private static SharedPreferences prefs(Context c){
        return c.getSharedPreferences(PREFS,Context.MODE_PRIVATE);
    }
}
