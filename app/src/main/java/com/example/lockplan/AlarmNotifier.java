package com.example.lockplan;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import java.util.Locale;

public class AlarmNotifier {
    public static final String CHANNEL_ID = "lockplan_alarm_v21";

    public static void ensureChannel(Context c) {
        if (Build.VERSION.SDK_INT < 26) return;
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel ch = nm.getNotificationChannel(CHANNEL_ID);
        if (ch == null) {
            ch = new NotificationChannel(CHANNEL_ID, "LockPlan 闹钟", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("用户设置的待办闹钟与锁屏提醒");
            ch.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
            ch.enableVibration(false); // Ringing service handles vibration reliably.
            ch.setSound(null, null);   // Ringing service handles alarm audio reliably.
            nm.createNotificationChannel(ch);
        }
    }

    public static boolean notificationsAllowed(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        if (Build.VERSION.SDK_INT >= 33 && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        return nm == null || nm.areNotificationsEnabled();
    }

    public static Notification build(Context c, Task t) {
        ensureChannel(c);
        Intent open = new Intent(c, AlarmActivity.class).putExtra("task_id", t.id)
                .putExtra("test_alarm", t.id == -20210812L)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent full = PendingIntent.getActivity(c, AlarmScheduler.requestCode(t.id), open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent complete = new Intent(c, TaskActionReceiver.class).setAction("complete").putExtra("task_id", t.id);
        PendingIntent completePi = PendingIntent.getBroadcast(c, AlarmScheduler.requestCode(t.id)+1, complete,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Intent snooze = new Intent(c, TaskActionReceiver.class).setAction("snooze").putExtra("task_id", t.id);
        PendingIntent snoozePi = PendingIntent.getBroadcast(c, AlarmScheduler.requestCode(t.id)+2, snooze,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        String time = t.hasTime ? String.format(Locale.CHINA, "%02d:%02d", t.hour, t.minute) : "现在";
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(c, CHANNEL_ID)
                : new Notification.Builder(c);
        b.setSmallIcon(R.drawable.ic_plan)
                .setContentTitle(t.title)
                .setContentText(time + " · 计划时间到了")
                .setCategory(Notification.CATEGORY_ALARM)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setPriority(Notification.PRIORITY_MAX)
                .setOngoing(true)
                .setAutoCancel(false)
                .setContentIntent(full)
                .addAction(new Notification.Action.Builder(null, "完成", completePi).build())
                .addAction(new Notification.Action.Builder(null, "10分钟后", snoozePi).build());
        if (t.wakeScreen) b.setFullScreenIntent(full, true);
        return b.build();
    }

    public static void show(Context c, Task t) {
        if (!notificationsAllowed(c)) return;
        c.getSystemService(NotificationManager.class).notify(notificationId(t.id), build(c, t));
    }

    public static void cancel(Context c, long taskId) {
        c.getSystemService(NotificationManager.class).cancel(notificationId(taskId));
    }

    public static int notificationId(long id) { return AlarmScheduler.requestCode(id) + 500; }
}
