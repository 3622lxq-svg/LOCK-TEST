package com.example.lockplan;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.os.VibrationEffect;
import android.os.Vibrator;

public class AlarmRingingService extends Service {
    private Ringtone ringtone;
    private Vibrator vibrator;
    private PowerManager.WakeLock wakeLock;
    private PowerManager.WakeLock screenWakeLock;
    private long currentTaskId = -1L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable timeout = this::stopSelf;

    @Override public void onCreate() {
        super.onCreate();
        AlarmNotifier.ensureChannel(this);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }

        boolean test = intent != null && intent.getBooleanExtra("test_alarm", false);
        Task t;
        if (test) {
            t = new Task(-20210812L, "LockPlan 测试闹钟", java.time.LocalDate.now().toString());
            t.reminder = true; t.sound = true; t.vibrate = true; t.wakeScreen = true; t.hasTime = true;
            java.time.LocalTime now = java.time.LocalTime.now(); t.hour = now.getHour(); t.minute = now.getMinute();
        } else {
            long id = intent == null ? -1L : intent.getLongExtra("task_id", -1L);
            t = TaskStore.find(this, id);
            if (t == null || t.done || !t.reminder) { stopSelf(); return START_NOT_STICKY; }
        }
        currentTaskId = t.id;

        startForeground(AlarmNotifier.notificationId(t.id), AlarmNotifier.build(this, t));
        acquireWakeLock();
        if (t.wakeScreen) wakeScreenFallback();
        if (t.sound) startRingtone();
        if (t.vibrate) startVibration();

        handler.removeCallbacks(timeout);
        handler.postDelayed(timeout, 10 * 60_000L);
        return START_NOT_STICKY;
    }

    private void acquireWakeLock() {
        try {
            PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LockPlan:AlarmWakeLock");
            wakeLock.acquire(10 * 60_000L);
        } catch (Exception ignored) {}
    }


    @SuppressWarnings("deprecation")
    private void wakeScreenFallback() {
        try {
            PowerManager pm = (PowerManager)getSystemService(POWER_SERVICE);
            if (!pm.isInteractive()) {
                screenWakeLock = pm.newWakeLock(
                        PowerManager.FULL_WAKE_LOCK | PowerManager.ACQUIRE_CAUSES_WAKEUP | PowerManager.ON_AFTER_RELEASE,
                        "LockPlan:ScreenWakeLock");
                screenWakeLock.acquire(30_000L);
            }
        } catch (Exception ignored) {}
    }

    private void startRingtone() {
        try {
            Uri uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
            if (uri == null) uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            ringtone = RingtoneManager.getRingtone(this, uri);
            if (ringtone != null) {
                if (Build.VERSION.SDK_INT >= 21) ringtone.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build());
                if (Build.VERSION.SDK_INT >= 28) ringtone.setLooping(true);
                ringtone.play();
            }
        } catch (Exception ignored) {}
    }

    private void startVibration() {
        try {
            vibrator = (Vibrator)getSystemService(VIBRATOR_SERVICE);
            long[] pattern = new long[]{0, 600, 250, 600, 250, 900};
            if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0));
            else vibrator.vibrate(pattern, 0);
        } catch (Exception ignored) {}
    }

    @Override public void onDestroy() {
        handler.removeCallbacks(timeout);
        try { if (ringtone != null && ringtone.isPlaying()) ringtone.stop(); } catch (Exception ignored) {}
        try { if (vibrator != null) vibrator.cancel(); } catch (Exception ignored) {}
        try { if (wakeLock != null && wakeLock.isHeld()) wakeLock.release(); } catch (Exception ignored) {}
        try { if (screenWakeLock != null && screenWakeLock.isHeld()) screenWakeLock.release(); } catch (Exception ignored) {}
        if (currentTaskId != -1L) AlarmNotifier.cancel(this, currentTaskId);
        stopForeground(true);
        super.onDestroy();
    }

    public static void stop(Context c) {
        try { c.stopService(new Intent(c, AlarmRingingService.class)); } catch (Exception ignored) {}
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
