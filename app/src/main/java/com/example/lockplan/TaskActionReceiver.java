package com.example.lockplan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class TaskActionReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        long id = intent.getLongExtra("task_id", -1L);
        AlarmRingingService.stop(context);
        Task t = TaskStore.find(context, id);
        if (t == null) return;
        if ("complete".equals(intent.getAction())) {
            t.done = true;
            TaskStore.upsert(context, t);
            AlarmScheduler.cancel(context, t.id);
            AlarmNotifier.cancel(context, t.id);
            if (TaskStore.wallpaperEnabled(context)) WallpaperHelper.update(context);
        } else if ("snooze".equals(intent.getAction())) {
            AlarmNotifier.cancel(context, t.id);
            AlarmScheduler.snooze(context, t.id, 10);
        }
    }
}
