package com.example.lockplan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        Task t;
        if (intent.getBooleanExtra("test_alarm", false)) {
            t = new Task(-20210812L, "LockPlan 测试闹钟", java.time.LocalDate.now().toString());
            t.reminder = true;
            t.sound = true;
            t.vibrate = true;
            t.wakeScreen = true;
            t.hasTime = true;
            java.time.LocalTime now = java.time.LocalTime.now();
            t.hour = now.getHour();
            t.minute = now.getMinute();
        } else {
            long id = intent.getLongExtra("task_id", -1L);
            t = TaskStore.find(context, id);
            if (t == null || t.done || !t.reminder) return;
        }

        Intent service = new Intent(context, AlarmRingingService.class)
                .putExtra("task_id", t.id)
                .putExtra("test_alarm", intent.getBooleanExtra("test_alarm", false));
        try {
            if (Build.VERSION.SDK_INT >= 26) context.startForegroundService(service);
            else context.startService(service);
        } catch (Exception e) {
            // If an OEM blocks the foreground service, still try the normal alarm notification path.
            AlarmNotifier.show(context, t);
        }
    }
}
