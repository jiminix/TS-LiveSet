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
    private static final String K_IN_PROGRESS_BACKUP = "in_progress_song_ids_backup";
    private static final String K_IN_PROGRESS_DISABLED_BACKUP = "in_progress_disabled_song_ids_backup";
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

        // Restore from the dedicated backup if the canonical playlist is unexpectedly empty.
        if (canonical.songIds.isEmpty()) {
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
        JSONArray ids = new JSONArray();
        for (String id : list.songIds) ids.put(id);
        JSONArray disabled = new JSONArray();
        for (String id : list.disabledSongIds) disabled.put(id);
        prefs(c).edit()
            .putString(K_IN_PROGRESS_BACKUP, ids.toString())
            .putString(K_IN_PROGRESS_DISABLED_BACKUP, disabled.toString())
            .apply();
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
