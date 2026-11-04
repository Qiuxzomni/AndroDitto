package com.androditto;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

final class TransferHistory {
    static final class Entry {
        final long time;
        final String direction, kind, label;
        Entry(long time, String direction, String kind, String label) {
            this.time = time; this.direction = direction; this.kind = kind; this.label = label;
        }
    }

    static synchronized void add(Context context, String direction, String kind, String label) {
        JSONArray previous;
        try { previous = new JSONArray(context.getSharedPreferences("history", Context.MODE_PRIVATE).getString("entries", "[]")); }
        catch (Exception e) { previous = new JSONArray(); }
        JSONArray next = new JSONArray();
        JSONObject item = new JSONObject();
        try {
            item.put("time", System.currentTimeMillis());
            item.put("direction", direction);
            item.put("kind", kind);
            item.put("label", label);
            next.put(item);
            for (int i = 0; i < previous.length() && i < 99; i++) next.put(previous.getJSONObject(i));
        } catch (Exception ignored) { }
        context.getSharedPreferences("history", Context.MODE_PRIVATE).edit().putString("entries", next.toString()).apply();
    }

    static List<Entry> read(Context context) {
        List<Entry> result = new ArrayList<>();
        try {
            JSONArray entries = new JSONArray(context.getSharedPreferences("history", Context.MODE_PRIVATE).getString("entries", "[]"));
            for (int i = 0; i < entries.length(); i++) {
                JSONObject item = entries.getJSONObject(i);
                result.add(new Entry(item.optLong("time"), item.optString("direction"), item.optString("kind"), item.optString("label")));
            }
        } catch (Exception ignored) { }
        return result;
    }

    private TransferHistory() { }
}
