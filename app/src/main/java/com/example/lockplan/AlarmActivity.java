package com.example.lockplan;

import android.app.Activity;
import android.app.NotificationManager;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Locale;

public class AlarmActivity extends Activity {
    private long taskId;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON);
        taskId = getIntent().getLongExtra("task_id", -1L);
        boolean test = getIntent().getBooleanExtra("test_alarm", false);
        Task t;
        if (test || taskId == -20210812L) {
            t = new Task(-20210812L, "LockPlan 测试闹钟", java.time.LocalDate.now().toString());
            t.hasTime = true; t.reminder = true; t.sound = true; t.vibrate = true; t.wakeScreen = true;
            java.time.LocalTime now = java.time.LocalTime.now(); t.hour = now.getHour(); t.minute = now.getMinute();
        } else {
            t = TaskStore.find(this, taskId);
        }
        if (t == null) { finish(); return; }
        buildUi(t);
    }

    private void buildUi(Task t) {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(28), dp(70), dp(28), dp(34));
        root.setBackgroundColor(Color.rgb(247,247,248));

        TextView time = tv(t.hasTime ? String.format(Locale.CHINA, "%02d:%02d", t.hour, t.minute) : "提醒", 46, true, Color.rgb(26,26,30));
        root.addView(time);
        TextView label = tv("计划时间到了", 15, false, Color.rgb(118,118,126));
        label.setPadding(0, dp(8), 0, dp(28));
        root.addView(label);
        TextView title = tv(t.title, 29, true, Color.rgb(20,20,24));
        title.setGravity(Gravity.CENTER);
        title.setPadding(dp(10),0,dp(10),dp(48));
        root.addView(title);

        Button done = new Button(this);
        done.setText("完成");
        done.setTextSize(18);
        done.setOnClickListener(v -> complete());
        root.addView(done, new LinearLayout.LayoutParams(-1, dp(58)));

        Button snooze = new Button(this);
        snooze.setText("10 分钟后提醒");
        snooze.setTextSize(17);
        snooze.setOnClickListener(v -> snooze());
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, dp(58));
        sp.topMargin = dp(12);
        root.addView(snooze, sp);

        Button close = new Button(this);
        close.setText("关闭");
        close.setOnClickListener(v -> { AlarmRingingService.stop(this); AlarmNotifier.cancel(this, taskId); finish(); });
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(52));
        cp.topMargin = dp(22);
        root.addView(close, cp);
        setContentView(root);
    }

    private void complete() {
        AlarmRingingService.stop(this);
        if (taskId == -20210812L) { finish(); return; }
        Task t = TaskStore.find(this, taskId);
        if (t != null) {
            t.done = true;
            TaskStore.upsert(this, t);
            AlarmScheduler.cancel(this, taskId);
            if (TaskStore.wallpaperEnabled(this)) WallpaperHelper.update(this);
        }
        AlarmNotifier.cancel(this, taskId);
        finish();
    }

    private void snooze() {
        AlarmRingingService.stop(this);
        AlarmNotifier.cancel(this, taskId);
        if (taskId != -20210812L) AlarmScheduler.snooze(this, taskId, 10);
        finish();
    }

    private TextView tv(String s, int sp, boolean bold, int color) {
        TextView v = new TextView(this); v.setText(s); v.setTextSize(sp); v.setTextColor(color);
        if (bold) v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD); return v;
    }
    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
