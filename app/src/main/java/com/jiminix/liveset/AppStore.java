package com.jiminix.liveset;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import java.util.ArrayList;
import java.util.List;

public class AppStore {
    private static final String PREFS = "liveset_store";
    private static final String K_SONGS = "songs";
    private static final String K_SETLISTS = "setlists";
    private static final String K_VIEWER_SETLIST = "viewer_setlist_id";
    public static final String IN_PROGRESS_SETLIST_ID = "__in_progress__";

    public static List<Song> loadSongs(Context c) {
        List<Song> out = new ArrayList<>();
        try {
            String raw = prefs(c).getString(K_SONGS, "[]");
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) out.add(Song.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    public static void saveSongs(Context c, List<Song> songs) {
        JSONArray a = new JSONArray();
        for (Song s : songs) try { a.put(s.toJson()); } catch (Exception ignored) {}
        prefs(c).edit().putString(K_SONGS, a.toString()).apply();
        PlaylistCloudSync.maybePublish(c);
    }

    public static List<SetListModel> loadSetlists(Context c) {
        List<SetListModel> out = new ArrayList<>();
        try {
            String raw = prefs(c).getString(K_SETLISTS, "[]");
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) out.add(SetListModel.fromJson(a.getJSONObject(i)));
        } catch (Exception ignored) {}
        return out;
    }

    public static void saveSetlists(Context c, List<SetListModel> setlists) {
        JSONArray a = new JSONArray();
        for (SetListModel s : setlists) try { a.put(s.toJson()); } catch (Exception ignored) {}
        prefs(c).edit().putString(K_SETLISTS, a.toString()).apply();
        PlaylistCloudSync.maybePublish(c);
    }

    public static Song findSong(Context c, String id) {
        for (Song s : loadSongs(c)) if (s.id.equals(id)) return s;
        return null;
    }

    public static void upsertSong(Context c, Song song) {
        List<Song> songs = loadSongs(c);
        boolean replaced = false;
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id.equals(song.id)) { songs.set(i, song); replaced = true; break; }
        }
        if (!replaced) songs.add(song);
        saveSongs(c, songs);
    }

    public static void deleteSong(Context c, String id) {
        List<Song> songs = loadSongs(c);
        songs.removeIf(s -> s.id.equals(id));
        saveSongs(c, songs);
        List<SetListModel> setlists = loadSetlists(c);
        for (SetListModel sl : setlists) sl.songIds.removeIf(x -> x.equals(id));
        saveSetlists(c, setlists);
    }

    public static SetListModel findSetlist(Context c, String id) {
        for (SetListModel s : loadSetlists(c)) if (s.id.equals(id)) return s;
        return null;
    }

    public static void upsertSetlist(Context c, SetListModel setlist) {
        List<SetListModel> all = loadSetlists(c);
        boolean replaced = false;
        for (int i = 0; i < all.size(); i++) {
            if (all.get(i).id.equals(setlist.id)) { all.set(i, setlist); replaced = true; break; }
        }
        if (!replaced) all.add(setlist);
        saveSetlists(c, all);
    }

    public static SetListModel getOrCreateInProgressSetlist(Context c) {
        SetListModel existing = findSetlist(c, IN_PROGRESS_SETLIST_ID);
        if (existing != null) {
            if (!"En cours".equals(existing.name)) {
                existing.name = "En cours";
                upsertSetlist(c, existing);
            }
            return existing;
        }

        SetListModel list = new SetListModel();
        list.id = IN_PROGRESS_SETLIST_ID;
        list.name = "En cours";
        upsertSetlist(c, list);
        return list;
    }

    public static boolean isInProgressSetlist(String id) {
        return IN_PROGRESS_SETLIST_ID.equals(id);
    }

    public static void selectViewerSetlist(Context c, String id) {
        prefs(c).edit().putString(K_VIEWER_SETLIST, id == null ? "" : id).apply();
    }

    public static String getViewerSetlistId(Context c) {
        return prefs(c).getString(K_VIEWER_SETLIST, "");
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
