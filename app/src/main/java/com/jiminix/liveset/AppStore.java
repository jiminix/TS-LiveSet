package com.jiminix.liveset;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class AppStore {
    private static final String PREFS = "liveset_store";
    private static final String K_SONGS = "songs";
    private static final String K_SETLISTS = "setlists";
    private static final String K_VIEWER_SETLIST = "viewer_setlist_id";
    private static final String K_IN_PROGRESS_BACKUP = "in_progress_song_ids_backup";
    private static final String K_IN_PROGRESS_DISABLED_BACKUP = "in_progress_disabled_song_ids_backup";
    private static final String K_IN_PROGRESS_INTENTIONALLY_EMPTY = "in_progress_intentionally_empty";
    private static final String K_FULL_BACKUP = "full_backup_json";
    private static final String K_FULL_BACKUP_TIME = "full_backup_time";
    public static final String IN_PROGRESS_SETLIST_ID = "__in_progress__";

    public static List<Song> loadSongs(Context c) {
        List<Song> out = new ArrayList<>();
        try {
            String raw = prefs(c).getString(K_SONGS, "[]");
            JSONArray a = new JSONArray(raw);
            for (int i = 0; i < a.length(); i++) {
                Song s=Song.fromJson(a.getJSONObject(i));
                normalizeSongTitles(s);
                out.add(s);
            }
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
        normalizeSongTitles(song);
        List<Song> songs = loadSongs(c);
        boolean replaced = false;
        for (int i = 0; i < songs.size(); i++) {
            if (songs.get(i).id.equals(song.id)) { songs.set(i, song); replaced = true; break; }
        }
        if (!replaced) songs.add(song);
        saveSongs(c, songs);
    }

    private static void normalizeSongTitles(Song song) {
        if (song == null) return;
        song.title = song.title == null ? "" : song.title.toUpperCase(Locale.ROOT);

        for (int i = 0; i < song.medleyItems.size(); i++) {
            String item = song.medleyItems.get(i);
            song.medleyItems.set(i, item == null ? "" : item.toUpperCase(Locale.ROOT));
        }
    }

    public static void deleteSong(Context c, String id) {
        List<Song> songs = loadSongs(c);
        songs.removeIf(s -> s.id.equals(id));
        saveSongs(c, songs);
        List<SetListModel> setlists = loadSetlists(c);
        for (SetListModel sl : setlists) {
            sl.songIds.removeIf(x -> x.equals(id));
            sl.disabledSongIds.removeIf(x -> x.equals(id));
        }
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

        if (isInProgressSetlist(setlist.id)) {
            if (!setlist.songIds.isEmpty()) {
                prefs(c).edit().putBoolean(K_IN_PROGRESS_INTENTIONALLY_EMPTY, false).apply();
            }
            saveInProgressBackup(c, setlist);
        }

        saveSetlists(c, all);
    }

    public static SetListModel getOrCreateInProgressSetlist(Context c) {
        List<SetListModel> all = loadSetlists(c);
        SetListModel canonical = null;
        boolean changed = false;

        for (SetListModel s : all) {
            if (IN_PROGRESS_SETLIST_ID.equals(s.id)) {
                canonical = s;
                break;
            }
        }

        if (canonical == null) {
            // Recover older/duplicate "En cours" playlists created before the reserved ID existed.
            for (SetListModel s : all) {
                if (s.name != null && "en cours".equalsIgnoreCase(s.name.trim())) {
                    canonical = s;
                    canonical.id = IN_PROGRESS_SETLIST_ID;
                    canonical.name = "En cours";
                    changed = true;
                    break;
                }
            }
        }

        if (canonical == null) {
            canonical = new SetListModel();
            canonical.id = IN_PROGRESS_SETLIST_ID;
            canonical.name = "En cours";
            all.add(canonical);
            changed = true;
        }

        if (!"En cours".equals(canonical.name)) {
            canonical.name = "En cours";
            changed = true;
        }

        // Restore from the dedicated backup only when the empty state was not intentional.
        boolean intentionallyEmpty=prefs(c).getBoolean(K_IN_PROGRESS_INTENTIONALLY_EMPTY,false);
        if (canonical.songIds.isEmpty() && !intentionallyEmpty) {
            List<String> backupIds = loadStringListBackup(c, K_IN_PROGRESS_BACKUP);
            if (!backupIds.isEmpty()) {
                for (String id : backupIds) {
                    if (id != null && !id.isEmpty() && findSong(c,id) != null && !canonical.songIds.contains(id)) {
                        canonical.songIds.add(id);
                        changed = true;
                    }
                }
            }
        }

        if (canonical.disabledSongIds.isEmpty()) {
            List<String> backupDisabled = loadStringListBackup(c, K_IN_PROGRESS_DISABLED_BACKUP);
            for (String id : backupDisabled) {
                if (canonical.songIds.contains(id) && !canonical.disabledSongIds.contains(id)) {
                    canonical.disabledSongIds.add(id);
                    changed = true;
                }
            }
        }

        // Merge any duplicate playlist also named "En cours" into the reserved one.
        List<SetListModel> duplicates = new ArrayList<>();
        for (SetListModel s : all) {
            if (s == canonical) continue;
            if (s.name == null || !"en cours".equalsIgnoreCase(s.name.trim())) continue;

            for (String songId : s.songIds) {
                if (songId != null && !songId.isEmpty() && !canonical.songIds.contains(songId)) {
                    canonical.songIds.add(songId);
                    changed = true;
                }
            }
            for (String songId : s.disabledSongIds) {
                if (songId != null && !songId.isEmpty() && !canonical.disabledSongIds.contains(songId)) {
                    canonical.disabledSongIds.add(songId);
                    changed = true;
                }
            }
            duplicates.add(s);
        }

        if (!duplicates.isEmpty()) {
            all.removeAll(duplicates);
            changed = true;
        }

        // If we migrated an old named playlist, ensure no old object with its previous ID survives.
        boolean hasCanonical = false;
        for (int i = 0; i < all.size(); i++) {
            SetListModel s = all.get(i);
            if (IN_PROGRESS_SETLIST_ID.equals(s.id)) {
                if (!hasCanonical) {
                    all.set(i, canonical);
                    hasCanonical = true;
                } else if (s != canonical) {
                    for (String songId : s.songIds) {
                        if (!canonical.songIds.contains(songId)) canonical.songIds.add(songId);
                    }
                    all.remove(i--);
                    changed = true;
                }
            }
        }
        if (!hasCanonical) {
            all.add(canonical);
            changed = true;
        }

        if (changed) saveSetlists(c, all);
        saveInProgressBackup(c, canonical);
        return canonical;
    }

    private static void saveInProgressBackup(Context c, SetListModel list) {
        if (list == null) return;

        // Never destroy a non-empty rescue copy with an accidental empty playlist.
        if (list.songIds.isEmpty()) {
            List<String> existing = loadStringListBackup(c, K_IN_PROGRESS_BACKUP);
            if (!existing.isEmpty()) return;
        }

        JSONArray ids = new JSONArray();
        for (String id : list.songIds) ids.put(id);
        JSONArray disabled = new JSONArray();
        for (String id : list.disabledSongIds) disabled.put(id);
        prefs(c).edit()
            .putString(K_IN_PROGRESS_BACKUP, ids.toString())
            .putString(K_IN_PROGRESS_DISABLED_BACKUP, disabled.toString())
            .apply();
    }

    public static boolean isInProgressIntentionallyEmpty(Context c) {
        return prefs(c).getBoolean(K_IN_PROGRESS_INTENTIONALLY_EMPTY, false);
    }

    public static void clearInProgressSetlist(Context c) {
        List<SetListModel> all=loadSetlists(c);
        SetListModel progress=null;

        for(SetListModel s:all){
            if(isInProgressSetlist(s.id)){
                progress=s;
                break;
            }
        }

        if(progress==null){
            progress=new SetListModel();
            progress.id=IN_PROGRESS_SETLIST_ID;
            progress.name="En cours";
            all.add(progress);
        }

        progress.songIds.clear();
        progress.disabledSongIds.clear();

        JSONArray a=new JSONArray();
        for(SetListModel s:all){
            try{a.put(s.toJson());}catch(Exception ignored){}
        }

        prefs(c).edit()
            .putString(K_SETLISTS,a.toString())
            .putString(K_IN_PROGRESS_BACKUP,"[]")
            .putString(K_IN_PROGRESS_DISABLED_BACKUP,"[]")
            .putBoolean(K_IN_PROGRESS_INTENTIONALLY_EMPTY,true)
            .commit();

        PlaylistCloudSync.maybePublish(c);
    }

    public static int getInProgressBackupCount(Context c) {
        return loadStringListBackup(c, K_IN_PROGRESS_BACKUP).size();
    }

    private static List<String> loadStringListBackup(Context c, String key) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs(c).getString(key, "[]"));
            for (int i = 0; i < a.length(); i++) {
                String id = a.optString(i, "");
                if (!id.isEmpty() && !out.contains(id)) out.add(id);
            }
        } catch (Exception ignored) {}
        return out;
    }

    public static boolean isInProgressSetlist(String id) {
        return IN_PROGRESS_SETLIST_ID.equals(id);
    }

    public static JSONObject createFullBackup(Context c) throws Exception {
        JSONObject backup = new JSONObject();
        long now = System.currentTimeMillis();

        backup.put("schema", 1);
        backup.put("savedAt", now);
        backup.put("songs", new JSONArray(prefs(c).getString(K_SONGS, "[]")));
        backup.put("setlists", new JSONArray(prefs(c).getString(K_SETLISTS, "[]")));
        backup.put("viewerSetlistId", prefs(c).getString(K_VIEWER_SETLIST, ""));

        SharedPreferences view = c.getSharedPreferences("playlist_view", Context.MODE_PRIVATE);
        JSONObject viewSettings = new JSONObject();
        viewSettings.put("compact", view.getBoolean("compact", true));
        viewSettings.put("textZoom", view.getInt("text_zoom", 0));
        backup.put("playlistView", viewSettings);

        return backup;
    }

    public static long saveFullBackupLocal(Context c, JSONObject backup) {
        if (backup == null) return 0L;
        long savedAt = backup.optLong("savedAt", System.currentTimeMillis());
        prefs(c).edit()
            .putString(K_FULL_BACKUP, backup.toString())
            .putLong(K_FULL_BACKUP_TIME, savedAt)
            .commit();
        return savedAt;
    }

    public static JSONObject getFullBackupLocal(Context c) {
        try {
            String raw = prefs(c).getString(K_FULL_BACKUP, "");
            if (raw == null || raw.trim().isEmpty()) return null;
            return new JSONObject(raw);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static long getFullBackupTimestamp(Context c) {
        return prefs(c).getLong(K_FULL_BACKUP_TIME, 0L);
    }

    public static boolean restoreFullBackup(Context c, JSONObject backup) {
        if (backup == null) return false;
        JSONArray songs = backup.optJSONArray("songs");
        JSONArray setlists = backup.optJSONArray("setlists");
        if (songs == null || setlists == null) return false;

        String viewerId = backup.optString("viewerSetlistId", "");

        boolean ok = prefs(c).edit()
            .putString(K_SONGS, songs.toString())
            .putString(K_SETLISTS, setlists.toString())
            .putString(K_VIEWER_SETLIST, viewerId)
            .commit();

        JSONObject viewSettings = backup.optJSONObject("playlistView");
        if (viewSettings != null) {
            c.getSharedPreferences("playlist_view", Context.MODE_PRIVATE).edit()
                .putBoolean("compact", viewSettings.optBoolean("compact", true))
                .putInt("text_zoom", viewSettings.optInt("textZoom", 0))
                .commit();
        }

        if (!ok) return false;

        saveFullBackupLocal(c, backup);
        getOrCreateInProgressSetlist(c);
        return true;
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
