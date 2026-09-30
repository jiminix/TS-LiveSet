package com.jiminix.liveset;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import org.json.JSONArray;
import org.json.JSONObject;

public class PlaylistShareProvider extends ContentProvider {
    public static final String AUTHORITY="com.jiminix.liveset.playlists";

    @Override public boolean onCreate(){ return true; }

    @Override public Bundle call(String method,String arg,Bundle extras){
        Bundle out=new Bundle();
        SetListModel list;
        if("get_in_progress_playlist".equals(method)){
            list=AppStore.getOrCreateInProgressSetlist(getContext());
        }else if("get_selected_playlist".equals(method)){
            String id=AppStore.getViewerSetlistId(getContext());
            list=id==null || id.isEmpty()?null:AppStore.findSetlist(getContext(),id);
        }else{
            out.putBoolean("available",false);
            return out;
        }
        if(list==null){
            out.putBoolean("available",false);
            return out;
        }

        JSONArray songs=new JSONArray();
        for(String songId:list.songIds){
            Song s=AppStore.findSong(getContext(),songId);
            if(s==null)continue;
            try{
                JSONObject o=new JSONObject();
                o.put("id",s.id);
                o.put("title",s.title);
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
            }catch(Exception ignored){}
        }

        out.putBoolean("available",true);
        out.putString("playlist_id",list.id);
        out.putString("playlist_name",list.name);
        out.putString("songs_json",songs.toString());
        return out;
    }

    @Override public Cursor query(Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder){return null;}
    @Override public String getType(Uri uri){return null;}
    @Override public Uri insert(Uri uri,ContentValues values){return null;}
    @Override public int delete(Uri uri,String selection,String[] selectionArgs){return 0;}
    @Override public int update(Uri uri,ContentValues values,String selection,String[] selectionArgs){return 0;}
}
