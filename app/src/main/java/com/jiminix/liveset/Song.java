package com.jiminix.liveset;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;

public class Song {
    public String id = UUID.randomUUID().toString();
    public String title = "";
    public String artist = "";
    public String key = "";
    public String bpm = "";
    public String tuning = "";
    public String capo = "";
    public String duration = "";
    public String singer = "";
    public String guitar = "";
    public String notes = "";
    public String mediaUrl = "";
    public String lyrics = "";
    public String stageNum1 = "";
    public String stageNum2 = "";
    public boolean stageGuitar = false;
    public boolean stageKeyboard = false;
    public final List<String> medleyItems = new ArrayList<>();

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id);
        o.put("title", title);
        o.put("artist", artist);
        o.put("key", key);
        o.put("bpm", bpm);
        o.put("tuning", tuning);
        o.put("capo", capo);
        o.put("duration", duration);
        o.put("singer", singer);
        o.put("guitar", guitar);
        o.put("notes", notes);
        o.put("mediaUrl", mediaUrl);
        o.put("lyrics", lyrics);
        o.put("stageNum1", stageNum1);
        o.put("stageNum2", stageNum2);
        o.put("stageGuitar", stageGuitar);
        o.put("stageKeyboard", stageKeyboard);
        JSONArray medley = new JSONArray();
        for (String item : medleyItems) medley.put(item);
        o.put("medleyItems", medley);
        return o;
    }

    public static Song fromJson(JSONObject o) {
        Song s = new Song();
        s.id = o.optString("id", s.id);
        s.title = o.optString("title", "");
        s.artist = o.optString("artist", "");
        s.key = o.optString("key", "");
        s.bpm = o.optString("bpm", "");
        s.tuning = o.optString("tuning", "");
        s.capo = o.optString("capo", "");
        s.duration = o.optString("duration", "");
        s.singer = o.optString("singer", "");
        s.guitar = o.optString("guitar", "");
        s.notes = o.optString("notes", "");
        s.mediaUrl = o.optString("mediaUrl", "");
        s.lyrics = o.optString("lyrics", "");
        s.stageNum1 = o.optString("stageNum1", "");
        s.stageNum2 = o.optString("stageNum2", "");
        s.stageGuitar = o.optBoolean("stageGuitar", false);
        s.stageKeyboard = o.optBoolean("stageKeyboard", false);
        JSONArray medley = o.optJSONArray("medleyItems");
        if (medley != null) for (int i = 0; i < medley.length(); i++) {
            String item = medley.optString(i, "").trim();
            if (!item.isEmpty()) s.medleyItems.add(item);
        }
        return s;
    }
}
