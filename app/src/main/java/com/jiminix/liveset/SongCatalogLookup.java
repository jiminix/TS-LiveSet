package com.jiminix.liveset;

import org.json.JSONArray;
import org.json.JSONObject;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public final class SongCatalogLookup {
    private static final OkHttpClient CLIENT=new OkHttpClient();

    public static final class Result {
        public final String title;
        public final String artist;
        public Result(String title,String artist){
            this.title=title==null?"":title.trim();
            this.artist=artist==null?"":artist.trim();
        }
        @Override public String toString(){
            return title+(artist.isEmpty()?"":" — "+artist);
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
            .header("User-Agent","TS-LiveSet/0.47")
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
                out.add(new Result(title,artist));
            }
        }
        return out;
    }
}
