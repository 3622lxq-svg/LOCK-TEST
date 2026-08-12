package com.example.lockplan;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import java.util.List;

public class AlarmScheduler {
    private static final int TEST_REQUEST = 987654321;

    private static PendingIntent pending(Context c, long taskId, int flags) {
        Intent i = new Intent(c, AlarmReceiver.class);
        i.putExtra("task_id", taskId);
        return PendingIntent.getBroadcast(c, requestCode(taskId), i, flags | PendingIntent.FLAG_IMMUTABLE);
    }

    public static boolean canScheduleExact(Context c) {
        if (Build.VERSION.SDK_INT < 31) return true;
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        return am.canScheduleExactAlarms();
    }

    private static PendingIntent showAppIntent(Context c, int requestCode) {
        Intent show = new Intent(c, MainActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return PendingIntent.getActivity(c, requestCode, show,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void scheduleAt(Context c, long when, PendingIntent operation, int requestCode) {
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        if (canScheduleExact(c)) {
            // setAlarmClock is intended for user-visible alarms and is allowed to wake the device from idle.
            AlarmManager.AlarmClockInfo info = new AlarmManager.AlarmClockInfo(when, showAppIntent(c, requestCode + 7));
            am.setAlarmClock(info, operation);
        } else {
            // Degraded mode until the user grants exact-alarm access.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, when, operation);
        }
    }

    public static void schedule(Context c, Task t) {
        cancel(c, t.id);
        if (!t.reminder || t.done || !t.hasTime) return;
        long when = t.triggerAtMillis();
        if (when <= System.currentTimeMillis()) return;
        PendingIntent pi = pending(c, t.id, PendingIntent.FLAG_UPDATE_CURRENT);
        scheduleAt(c, when, pi, requestCode(t.id));
    }

    public static void snooze(Context c, long taskId, int minutes) {
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("task_id", taskId).putExtra("snoozed", true);
        PendingIntent pi = PendingIntent.getBroadcast(c, requestCode(taskId) + 100000, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        long when = System.currentTimeMillis() + minutes * 60_000L;
        scheduleAt(c, when, pi, requestCode(taskId) + 100000);
    }

    public static void scheduleTest(Context c, int seconds) {
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("test_alarm", true);
        PendingIntent pi = PendingIntent.getBroadcast(c, TEST_REQUEST, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        scheduleAt(c, System.currentTimeMillis() + seconds * 1000L, pi, TEST_REQUEST);
    }

    public static void cancel(Context c, long taskId) {
        AlarmManager am = (AlarmManager)c.getSystemService(Context.ALARM_SERVICE);
        PendingIntent pi = pending(c, taskId, PendingIntent.FLAG_NO_CREATE);
        if (pi != null) am.cancel(pi);
        Intent i = new Intent(c, AlarmReceiver.class).putExtra("task_id", taskId).putExtra("snoozed", true);
        PendingIntent snooze = PendingIntent.getBroadcast(c, requestCode(taskId) + 100000, i,
                PendingIntent.FLAG_NO_CREATE | PendingIntent.FLAG_IMMUTABLE);
        if (snooze != null) am.cancel(snooze);
    }

    public static void rescheduleAll(Context c) {
        List<Task> tasks = TaskStore.load(c);
        for (Task t : tasks) schedule(c, t);
    }

    public static int requestCode(long id) {
        return (int)(Math.abs(id) % 900000000L) + 1000;
    }
}
