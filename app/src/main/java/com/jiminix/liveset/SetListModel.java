package com.jiminix.liveset;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class SetListModel {
    public String id = UUID.randomUUID().toString();
    public String name = "Nouvelle setlist";
    public final List<String> songIds = new ArrayList<>();
    public final List<String> disabledSongIds = new ArrayList<>();

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        JSONArray a = new JSONArray();
        for (String songId : songIds) a.put(songId);
        o.put("songIds", a);
        JSONArray disabled = new JSONArray();
        for (String songId : disabledSongIds) disabled.put(songId);
        o.put("disabledSongIds", disabled);
        return o;
    }

    public static SetListModel fromJson(JSONObject o) {
        SetListModel s = new SetListModel();
        s.id = o.optString("id", s.id);
        s.name = o.optString("name", "Nouvelle setlist");
        JSONArray a = o.optJSONArray("songIds");
        if (a != null) for (int i = 0; i < a.length(); i++) s.songIds.add(a.optString(i));
        JSONArray disabled = o.optJSONArray("disabledSongIds");
        if (disabled != null) for (int i = 0; i < disabled.length(); i++) {
            String id=disabled.optString(i);
            if(!id.isEmpty() && !s.disabledSongIds.contains(id))s.disabledSongIds.add(id);
        }
        return s;
    }
}
