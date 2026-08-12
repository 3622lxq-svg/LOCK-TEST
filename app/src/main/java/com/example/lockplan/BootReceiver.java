package com.example.lockplan;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        AlarmScheduler.rescheduleAll(context);
        if (TaskStore.wallpaperEnabled(context)) WallpaperHelper.update(context);
    }
}
