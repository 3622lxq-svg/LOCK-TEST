package com.example.lockplan;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class TaskStore {
    private static final String PREFS = "lockplan_v2";
    private static final String KEY_TASKS = "tasks";
    private static final String KEY_WALLPAPER_ENABLED = "wallpaper_enabled";
    private static final String KEY_CARD_OPACITY = "card_opacity";
    private static final String KEY_MAX_ITEMS = "max_items";
    private static final String KEY_POSITION = "card_position";
    private static final String KEY_STYLE = "card_style";
    private static final String KEY_SHOW_DONE = "show_done";

    public static List<Task> load(Context context) {
        List<Task> out = new ArrayList<>();
        String raw = prefs(context).getString(KEY_TASKS, "[]");
        try {
            JSONArray array = new JSONArray(raw);
            for (int i = 0; i < array.length(); i++) {
                JSONObject o = array.getJSONObject(i);
                Task t = new Task(o.optLong("id", System.currentTimeMillis() + i),
                        o.optString("title", ""), o.optString("date", java.time.LocalDate.now().toString()));
                t.hasTime = o.optBoolean("hasTime", true);
                t.hour = o.optInt("hour", 9);
                t.minute = o.optInt("minute", 0);
                t.reminder = o.optBoolean("reminder", false);
                t.sound = o.optBoolean("sound", true);
                t.vibrate = o.optBoolean("vibrate", true);
                t.wakeScreen = o.optBoolean("wakeScreen", true);
                t.important = o.optBoolean("important", false);
                t.done = o.optBoolean("done", false);
                if (!t.title.trim().isEmpty()) out.add(t);
            }
        } catch (Exception ignored) {}
        sort(out);
        return out;
    }

    public static void save(Context context, List<Task> tasks) {
        JSONArray array = new JSONArray();
        try {
            for (Task t : tasks) {
                JSONObject o = new JSONObject();
                o.put("id", t.id);
                o.put("title", t.title);
                o.put("date", t.date);
                o.put("hasTime", t.hasTime);
                o.put("hour", t.hour);
                o.put("minute", t.minute);
                o.put("reminder", t.reminder);
                o.put("sound", t.sound);
                o.put("vibrate", t.vibrate);
                o.put("wakeScreen", t.wakeScreen);
                o.put("important", t.important);
                o.put("done", t.done);
                array.put(o);
            }
        } catch (Exception ignored) {}
        prefs(context).edit().putString(KEY_TASKS, array.toString()).apply();
    }

    public static Task find(Context context, long id) {
        for (Task t : load(context)) if (t.id == id) return t;
        return null;
    }

    public static void upsert(Context context, Task task) {
        List<Task> tasks = load(context);
        boolean replaced = false;
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id == task.id) {
                tasks.set(i, task);
                replaced = true;
                break;
            }
        }
        if (!replaced) tasks.add(task);
        sort(tasks);
        save(context, tasks);
    }

    public static void delete(Context context, long id) {
        List<Task> tasks = load(context);
        tasks.removeIf(t -> t.id == id);
        save(context, tasks);
    }

    public static List<Task> today(Context context) {
        List<Task> result = new ArrayList<>();
        for (Task t : load(context)) if (t.isToday()) result.add(t);
        sort(result);
        return result;
    }

    public static void sort(List<Task> tasks) {
        Collections.sort(tasks, Comparator
                .comparing((Task t) -> t.date == null ? "" : t.date)
                .thenComparingInt(t -> t.hasTime ? t.hour : 99)
                .thenComparingInt(t -> t.hasTime ? t.minute : 99)
                .thenComparingLong(t -> t.id));
    }

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public static boolean wallpaperEnabled(Context c) { return prefs(c).getBoolean(KEY_WALLPAPER_ENABLED, false); }
    public static void setWallpaperEnabled(Context c, boolean value) { prefs(c).edit().putBoolean(KEY_WALLPAPER_ENABLED, value).apply(); }
    public static int cardOpacity(Context c) { return prefs(c).getInt(KEY_CARD_OPACITY, 38); }
    public static void setCardOpacity(Context c, int value) { prefs(c).edit().putInt(KEY_CARD_OPACITY, value).apply(); }
    public static int maxItems(Context c) { return prefs(c).getInt(KEY_MAX_ITEMS, 6); }
    public static void setMaxItems(Context c, int value) { prefs(c).edit().putInt(KEY_MAX_ITEMS, value).apply(); }
    public static int cardPosition(Context c) { return prefs(c).getInt(KEY_POSITION, 2); }
    public static void setCardPosition(Context c, int value) { prefs(c).edit().putInt(KEY_POSITION, value).apply(); }
    public static String cardStyle(Context c) { return prefs(c).getString(KEY_STYLE, "glass"); }
    public static void setCardStyle(Context c, String value) { prefs(c).edit().putString(KEY_STYLE, value).apply(); }
    public static boolean showDone(Context c) { return prefs(c).getBoolean(KEY_SHOW_DONE, false); }
    public static void setShowDone(Context c, boolean value) { prefs(c).edit().putBoolean(KEY_SHOW_DONE, value).apply(); }
}
