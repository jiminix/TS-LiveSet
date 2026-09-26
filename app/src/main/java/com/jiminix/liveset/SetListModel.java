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

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("name", name);
        JSONArray a = new JSONArray();
        for (String songId : songIds) a.put(songId);
        o.put("songIds", a);
        return o;
    }

    public static SetListModel fromJson(JSONObject o) {
        SetListModel s = new SetListModel();
        s.id = o.optString("id", s.id);
        s.name = o.optString("name", "Nouvelle setlist");
        JSONArray a = o.optJSONArray("songIds");
        if (a != null) for (int i = 0; i < a.length(); i++) s.songIds.add(a.optString(i));
        return s;
    }
}
