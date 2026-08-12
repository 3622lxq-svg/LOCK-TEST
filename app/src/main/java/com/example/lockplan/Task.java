package com.example.lockplan;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public class Task {
    public long id;
    public String title;
    public String date; // yyyy-MM-dd
    public boolean hasTime;
    public int hour;
    public int minute;
    public boolean reminder;
    public boolean sound;
    public boolean vibrate;
    public boolean wakeScreen;
    public boolean important;
    public boolean done;

    public Task(long id, String title, String date) {
        this.id = id;
        this.title = title;
        this.date = date;
        this.hasTime = true;
        this.hour = 9;
        this.minute = 0;
        this.reminder = false;
        this.sound = true;
        this.vibrate = true;
        this.wakeScreen = true;
        this.important = false;
        this.done = false;
    }

    public long triggerAtMillis() {
        if (!hasTime || date == null || date.isEmpty()) return -1L;
        try {
            LocalDate d = LocalDate.parse(date);
            LocalDateTime dt = d.atTime(hour, minute);
            return dt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return -1L;
        }
    }

    public boolean isToday() {
        try { return LocalDate.parse(date).equals(LocalDate.now()); }
        catch (Exception e) { return false; }
    }
}
