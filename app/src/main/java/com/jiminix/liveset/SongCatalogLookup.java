package com.jiminix.liveset;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class SongCatalogLookup {
    // V0.63 enrich selected songs with duration, BPM and musical key when available.
    private static final OkHttpClient CLIENT=new OkHttpClient.Builder()
        .connectTimeout(8,TimeUnit.SECONDS)
        .readTimeout(10,TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build();

    public static final class Result {
        public final String title;
        public final String artist;
        public final String duration;
        public final String bpm;
        public final String key;

        public Result(String title,String artist){
            this(title,artist,"","","");
        }

        public Result(String title,String artist,String duration,String bpm,String key){
            this.title=clean(title);
            this.artist=clean(artist);
            this.duration=clean(duration);
            this.bpm=clean(bpm);
            this.key=clean(key);
        }

        public Result withMeta(String newDuration,String newBpm,String newKey){
            return new Result(
                title,
                artist,
                firstNonEmpty(newDuration,duration),
                firstNonEmpty(newBpm,bpm),
                firstNonEmpty(newKey,key)
            );
        }

        @Override public String toString(){
            StringBuilder s=new StringBuilder(title);
            if(!artist.isEmpty())s.append(" — ").append(artist);
            if(!duration.isEmpty())s.append(" · ").append(duration);
            if(!bpm.isEmpty())s.append(" · ").append(bpm).append(" BPM");
            if(!key.isEmpty())s.append(" · ").append(key);
            return s.toString();
        }
    }

    private SongCatalogLookup(){}

    public static List<Result> search(String query,int limit) throws Exception{
        List<Result> out=new ArrayList<>();
        String q=query==null?"":query.trim();
        if(q.isEmpty())return out;

        int safeLimit=Math.max(1,Math.min(20,limit));
        String encoded=URLEncoder.encode(q,StandardCharsets.UTF_8.toString());
        String url="https://itunes.apple.com/search?entity=song&country=FR&limit="+safeLimit+"&term="+encoded;

        Request req=new Request.Builder()
            .url(url)
            .header("Accept","application/json")
            .header("User-Agent","TS-LiveSet/0.63")
            .get()
            .build();

        try(Response response=CLIENT.newCall(req).execute()){
            if(!response.isSuccessful())throw new Exception("HTTP "+response.code());
            String raw=response.body()==null?"":response.body().string();
            JSONObject root=new JSONObject(raw);
            JSONArray results=root.optJSONArray("results");
            if(results==null)return out;

            for(int i=0;i<results.length();i++){
                JSONObject item=results.optJSONObject(i);
                if(item==null)continue;
                String title=item.optString("trackName","").trim();
                String artist=item.optString("artistName","").trim();
                if(title.isEmpty())continue;
                String duration=formatMillis(item.optLong("trackTimeMillis",0L));
                out.add(new Result(title,artist,duration,"",""));
            }
        }
        return out;
    }

    public static Result enrich(Result base){
        if(base==null)return null;

        String duration=base.duration;
        String bpm=base.bpm;
        String key=base.key;

        try{
            Result deezer=lookupDeezer(base.title,base.artist);
            if(deezer!=null){
                duration=firstNonEmpty(deezer.duration,duration);
                bpm=firstNonEmpty(deezer.bpm,bpm);
            }
        }catch(Exception ignored){}

        // Best effort only: AcousticBrainz has key/BPM metadata for some MusicBrainz recordings.
        // If that source has no analysis for the track, the fields simply remain unchanged.
        if(key.isEmpty() || bpm.isEmpty()){
            try{
                Result acoustic=lookupAcousticBrainz(base.title,base.artist);
                if(acoustic!=null){
                    key=firstNonEmpty(acoustic.key,key);
                    bpm=firstNonEmpty(acoustic.bpm,bpm);
                }
            }catch(Exception ignored){}
        }

        return base.withMeta(duration,bpm,key);
    }

    private static Result lookupDeezer(String title,String artist) throws Exception{
        String query=(artist+" "+title).trim();
        String encoded=URLEncoder.encode(query,StandardCharsets.UTF_8.toString());
        JSONObject search=getJson("https://api.deezer.com/search?q="+encoded+"&limit=5");
        JSONArray data=search.optJSONArray("data");
        if(data==null || data.length()==0)return null;

        JSONObject best=data.optJSONObject(0);
        if(best==null)return null;
        long id=best.optLong("id",0L);
        String duration=formatSeconds(best.optLong("duration",0L));
        String bpm="";

        if(id>0){
            try{
                JSONObject track=getJson("https://api.deezer.com/track/"+id);
                duration=firstNonEmpty(formatSeconds(track.optLong("duration",0L)),duration);
                double value=track.optDouble("bpm",0.0);
                if(value>0.0)bpm=String.valueOf(Math.round(value));
            }catch(Exception ignored){}
        }

        return new Result(title,artist,duration,bpm,"");
    }

    private static Result lookupAcousticBrainz(String title,String artist) throws Exception{
        String query="recording:\""+title+"\"";
        if(artist!=null && !artist.trim().isEmpty())query+=" AND artist:\""+artist+"\"";
        String encoded=URLEncoder.encode(query,StandardCharsets.UTF_8.toString());

        JSONObject mb=getJson(
            "https://musicbrainz.org/ws/2/recording/?fmt=json&limit=3&query="+encoded,
            "TS-LiveSet/0.63 (Android playlist manager)"
        );
        JSONArray recordings=mb.optJSONArray("recordings");
        if(recordings==null || recordings.length()==0)return null;

        String mbid=recordings.optJSONObject(0)==null?"":recordings.optJSONObject(0).optString("id","");
        if(mbid.isEmpty())return null;

        JSONObject low=getJson("https://acousticbrainz.org/api/v1/"+mbid+"/low-level");
        JSONObject rhythm=low.optJSONObject("rhythm");
        JSONObject tonal=low.optJSONObject("tonal");

        String bpm="";
        if(rhythm!=null){
            double value=rhythm.optDouble("bpm",0.0);
            if(value>0.0)bpm=String.valueOf(Math.round(value));
        }

        String key="";
        if(tonal!=null){
            String note=tonal.optString("key_key","").trim();
            String scale=tonal.optString("key_scale","").trim();
            if(!note.isEmpty()){
                key=prettyKey(note,scale);
            }
        }

        return new Result(title,artist,"",bpm,key);
    }

    private static JSONObject getJson(String url) throws Exception{
        return getJson(url,"TS-LiveSet/0.63");
    }

    private static JSONObject getJson(String url,String userAgent) throws Exception{
        Request req=new Request.Builder()
            .url(url)
            .header("Accept","application/json")
            .header("User-Agent",userAgent)
            .get()
            .build();

        try(Response response=CLIENT.newCall(req).execute()){
            if(!response.isSuccessful())throw new Exception("HTTP "+response.code());
            String raw=response.body()==null?"":response.body().string();
            if(raw.trim().isEmpty())throw new Exception("Réponse vide");
            return new JSONObject(raw);
        }
    }

    private static String prettyKey(String raw,String scale){
        String n=raw==null?"":raw.trim().toUpperCase();
        if(n.isEmpty())return "";
        n=n.replace("_SHARP","#").replace("_FLAT","b");
        if(n.length()>1 && !n.contains("#") && !n.contains("b")){
            n=n.substring(0,1)+n.substring(1).toLowerCase();
        }
        String s=scale==null?"":scale.trim().toLowerCase();
        if("minor".equals(s))return n+"m";
        return n;
    }

    private static String formatMillis(long ms){
        return ms<=0?"":formatSeconds(Math.round(ms/1000.0));
    }

    private static String formatSeconds(long total){
        if(total<=0)return "";
        long min=total/60;
        long sec=total%60;
        return min+":"+String.format(java.util.Locale.ROOT,"%02d",sec);
    }

    private static String clean(String s){
        return s==null?"":s.trim();
    }

    private static String firstNonEmpty(String preferred,String fallback){
        String p=clean(preferred);
        return p.isEmpty()?clean(fallback):p;
    }
}
