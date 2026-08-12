package com.example.lockplan;

import android.Manifest;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.NotificationManager;
import android.app.TimePickerDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final int PICK_WALLPAPER = 88;
    private final List<Task> tasks = new ArrayList<>();
    private LinearLayout taskContainer;
    private TextView stats;
    private ImageView preview;
    private TextView permissionStatus;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestNotificationPermissionIfNeeded();
        buildUi();
        reload();
    }

    @Override protected void onResume() {
        super.onResume();
        AlarmScheduler.rescheduleAll(this);
        if (permissionStatus != null) permissionStatus.setText(permissionText());
        if (taskContainer != null) reload();
    }

    private void reload() {
        tasks.clear(); tasks.addAll(TaskStore.load(this));
        renderTasks(); updatePreview();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(26), dp(20), dp(42));
        root.setBackgroundColor(Color.rgb(247,247,248));
        scroll.addView(root);

        TextView title = tv("LockPlan", 31, true, Color.rgb(22,22,25));
        root.addView(title);
        TextView sub = tv("自己的壁纸 + 今日计划 + 到点闹钟", 15, false, Color.rgb(112,112,120));
        sub.setPadding(0,dp(4),0,dp(22)); root.addView(sub);

        LinearLayout header = new LinearLayout(this); header.setOrientation(LinearLayout.HORIZONTAL); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView today = tv("今日待办", 21, true, Color.rgb(30,30,34));
        header.addView(today, new LinearLayout.LayoutParams(0, dp(54), 1f));
        Button add = new Button(this); add.setText("＋ 新建"); add.setOnClickListener(v -> editTask(null));
        header.addView(add, new LinearLayout.LayoutParams(dp(104), dp(50))); root.addView(header);

        stats = tv("", 14, false, Color.rgb(120,120,128)); root.addView(stats);
        taskContainer = new LinearLayout(this); taskContainer.setOrientation(LinearLayout.VERTICAL); root.addView(taskContainer);

        section(root, "锁屏壁纸");
        TextView wallDesc = tv("选择你的原壁纸，LockPlan 只在上面叠加待办卡片。修改/完成任务后自动刷新锁屏。", 14, false, Color.rgb(105,105,114));
        wallDesc.setPadding(0,0,0,dp(12)); root.addView(wallDesc);

        preview = new ImageView(this); preview.setScaleType(ImageView.ScaleType.CENTER_CROP); preview.setAdjustViewBounds(true);
        root.addView(preview, new LinearLayout.LayoutParams(-1, dp(360)));

        LinearLayout wallButtons = new LinearLayout(this); wallButtons.setOrientation(LinearLayout.HORIZONTAL);
        Button choose = new Button(this); choose.setText("选择我的壁纸"); choose.setOnClickListener(v -> chooseWallpaper());
        wallButtons.addView(choose, new LinearLayout.LayoutParams(0, dp(54), 1f));
        Button apply = new Button(this); apply.setText("应用到锁屏"); apply.setOnClickListener(v -> applyWallpaper());
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, dp(54), 1f); ap.leftMargin=dp(8); wallButtons.addView(apply, ap); root.addView(wallButtons);

        TextView opacityLabel = tv("卡片透明度（当前 " + TaskStore.cardOpacity(this) + "%）", 14, true, Color.rgb(65,65,72)); opacityLabel.setPadding(0,dp(16),0,0); root.addView(opacityLabel);
        SeekBar opacity = new SeekBar(this); opacity.setMax(70); opacity.setProgress(Math.max(0, TaskStore.cardOpacity(this)-10));
        opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s,int p,boolean from){ int val=p+10; TaskStore.setCardOpacity(MainActivity.this,val); opacityLabel.setText("卡片透明度（当前 " + val + "%）"); updatePreview(); }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){ syncWallpaperIfEnabled(); }
        }); root.addView(opacity);

        TextView countLabel = tv("锁屏最多显示 " + TaskStore.maxItems(this) + " 条", 14, true, Color.rgb(65,65,72)); root.addView(countLabel);
        SeekBar count = new SeekBar(this); count.setMax(6); count.setProgress(TaskStore.maxItems(this)-3);
        count.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
            public void onProgressChanged(SeekBar s,int p,boolean from){ int val=p+3; TaskStore.setMaxItems(MainActivity.this,val); countLabel.setText("锁屏最多显示 " + val + " 条"); updatePreview(); }
            public void onStartTrackingTouch(SeekBar s){} public void onStopTrackingTouch(SeekBar s){ syncWallpaperIfEnabled(); }
        }); root.addView(count);

        LinearLayout styleRow = new LinearLayout(this); styleRow.setOrientation(LinearLayout.HORIZONTAL); styleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView styleLab = tv("样式", 15, true, Color.rgb(65,65,72)); styleRow.addView(styleLab, new LinearLayout.LayoutParams(0,dp(54),1f));
        Spinner style = new Spinner(this); String[] styles = {"磨砂卡片", "极简无卡片"}; style.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, styles)); style.setSelection("minimal".equals(TaskStore.cardStyle(this)) ? 1 : 0);
        style.setOnItemSelectedListener(new SimpleItemSelected(pos -> { TaskStore.setCardStyle(this, pos==1?"minimal":"glass"); updatePreview(); syncWallpaperIfEnabled(); }));
        styleRow.addView(style, new LinearLayout.LayoutParams(dp(160),dp(54))); root.addView(styleRow);

        LinearLayout posRow = new LinearLayout(this); posRow.setOrientation(LinearLayout.HORIZONTAL); posRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView posLab = tv("卡片位置", 15, true, Color.rgb(65,65,72)); posRow.addView(posLab, new LinearLayout.LayoutParams(0,dp(54),1f));
        Spinner pos = new Spinner(this); String[] poss = {"偏上", "居中", "靠下"}; pos.setAdapter(new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, poss)); pos.setSelection(TaskStore.cardPosition(this));
        pos.setOnItemSelectedListener(new SimpleItemSelected(p -> { TaskStore.setCardPosition(this,p); updatePreview(); syncWallpaperIfEnabled(); }));
        posRow.addView(pos, new LinearLayout.LayoutParams(dp(140),dp(54))); root.addView(posRow);

        CheckBox showDone = new CheckBox(this); showDone.setText("锁屏显示已完成任务"); showDone.setChecked(TaskStore.showDone(this)); showDone.setOnCheckedChangeListener((b,v)->{TaskStore.setShowDone(this,v);updatePreview();syncWallpaperIfEnabled();}); root.addView(showDone);

        section(root, "提醒与亮屏");
        permissionStatus = tv(permissionText(), 14, false, Color.rgb(102,102,112)); permissionStatus.setPadding(0,0,0,dp(8)); root.addView(permissionStatus);
        Button notify = new Button(this); notify.setText("开启通知与锁屏提醒权限"); notify.setOnClickListener(v -> openNotificationSettings()); root.addView(notify);
        Button exact = new Button(this); exact.setText("开启精确闹钟权限"); exact.setOnClickListener(v -> openExactAlarmSettings()); root.addView(exact);
        if (Build.VERSION.SDK_INT >= 34) {
            Button full = new Button(this); full.setText("允许到点全屏亮起"); full.setOnClickListener(v -> openFullScreenSettings()); root.addView(full);
        }
        Button test = new Button(this); test.setText("20 秒后测试闹钟"); test.setOnClickListener(v -> testAlarm()); root.addView(test);
        TextView alarmNote = tv("V2.1 会用独立的闹钟前台服务持续响铃/震动；“20 秒后测试闹钟”可以先验证系统权限与 ColorOS 后台行为。到点亮屏还需要系统允许全屏提醒。", 13, false, Color.rgb(125,125,134));
        alarmNote.setPadding(0,dp(10),0,0); root.addView(alarmNote);

        setContentView(scroll);
    }

    private void renderTasks() {
        if (taskContainer == null) return;
        taskContainer.removeAllViews();
        int total=0,done=0;
        for (Task t:tasks) if (t.isToday()) { total++; if(t.done) done++; }
        stats.setText("今天完成 " + done + " / " + total);
        boolean any=false;
        for (Task t:tasks) {
            if (!t.isToday()) continue;
            any=true;
            LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL); row.setGravity(Gravity.CENTER_VERTICAL); row.setPadding(0,dp(4),0,dp(4));
            CheckBox cb = new CheckBox(this); cb.setChecked(t.done); cb.setText(""); cb.setOnCheckedChangeListener((b,v)->{ t.done=v; TaskStore.upsert(this,t); if(v) AlarmScheduler.cancel(this,t.id); else AlarmScheduler.schedule(this,t); syncWallpaperIfEnabled(); reload(); });
            row.addView(cb, new LinearLayout.LayoutParams(dp(48),dp(58)));
            LinearLayout textCol = new LinearLayout(this); textCol.setOrientation(LinearLayout.VERTICAL);
            TextView name = tv((t.important?"★ ":"") + t.title, 16, t.important, t.done?Color.rgb(145,145,152):Color.rgb(35,35,40));
            textCol.addView(name);
            String meta = (t.hasTime?String.format(Locale.CHINA,"%02d:%02d",t.hour,t.minute):"无时间") + (t.reminder?" · 已提醒":"") + (t.wakeScreen&&t.reminder?" · 到点亮屏":"");
            TextView m = tv(meta, 12, false, Color.rgb(125,125,134)); textCol.addView(m);
            textCol.setOnClickListener(v -> editTask(t)); row.addView(textCol,new LinearLayout.LayoutParams(0,dp(60),1f));
            Button edit = new Button(this); edit.setText("编辑"); edit.setOnClickListener(v -> editTask(t)); row.addView(edit,new LinearLayout.LayoutParams(dp(72),dp(48)));
            taskContainer.addView(row);
        }
        if(!any){ TextView empty=tv("今天还没有待办。点右上角“新建”。",15,false,Color.rgb(125,125,134)); empty.setPadding(0,dp(14),0,dp(18)); taskContainer.addView(empty); }
    }

    private void editTask(Task existing) {
        final boolean isNew = existing == null;
        Task draft = isNew ? new Task(System.currentTimeMillis(), "", LocalDate.now().toString()) : copy(existing);
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); int pad=dp(18); box.setPadding(pad,dp(4),pad,0);
        EditText title = new EditText(this); title.setHint("待办内容"); title.setSingleLine(true); title.setText(draft.title); box.addView(title);
        Button date = new Button(this); date.setText("日期：" + friendlyDate(draft.date)); date.setOnClickListener(v -> pickDate(draft,date)); box.addView(date);
        CheckBox hasTime = new CheckBox(this); hasTime.setText("设置时间"); hasTime.setChecked(draft.hasTime); box.addView(hasTime);
        Button time = new Button(this); time.setText(String.format(Locale.CHINA,"时间：%02d:%02d",draft.hour,draft.minute)); time.setEnabled(draft.hasTime); time.setOnClickListener(v -> pickTime(draft,time)); box.addView(time);
        hasTime.setOnCheckedChangeListener((b,v)->{draft.hasTime=v;time.setEnabled(v);if(!v)draft.reminder=false;});
        CheckBox reminder = new CheckBox(this); reminder.setText("到点提醒 / 闹钟"); reminder.setChecked(draft.reminder); reminder.setEnabled(draft.hasTime); box.addView(reminder);
        CheckBox sound = new CheckBox(this); sound.setText("响铃"); sound.setChecked(draft.sound); box.addView(sound);
        CheckBox vibrate = new CheckBox(this); vibrate.setText("震动"); vibrate.setChecked(draft.vibrate); box.addView(vibrate);
        CheckBox wake = new CheckBox(this); wake.setText("到点亮屏并显示任务"); wake.setChecked(draft.wakeScreen); box.addView(wake);
        CheckBox important = new CheckBox(this); important.setText("重要任务"); important.setChecked(draft.important); box.addView(important);
        reminder.setOnCheckedChangeListener((b,v)->{draft.reminder=v;sound.setEnabled(v);vibrate.setEnabled(v);wake.setEnabled(v);});
        sound.setEnabled(draft.reminder); vibrate.setEnabled(draft.reminder); wake.setEnabled(draft.reminder);

        AlertDialog.Builder builder = new AlertDialog.Builder(this).setTitle(isNew?"新建待办":"编辑待办").setView(box).setNegativeButton("取消",null);
        if(!isNew) builder.setNeutralButton("删除",(d,w)->{AlarmScheduler.cancel(this,existing.id);TaskStore.delete(this,existing.id);syncWallpaperIfEnabled();reload();});
        builder.setPositiveButton("保存",null);
        AlertDialog dialog=builder.create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String txt=title.getText().toString().trim(); if(txt.isEmpty()){title.setError("请输入待办内容");return;}
            draft.title=txt; draft.hasTime=hasTime.isChecked(); draft.reminder=draft.hasTime && reminder.isChecked(); draft.sound=sound.isChecked(); draft.vibrate=vibrate.isChecked(); draft.wakeScreen=wake.isChecked(); draft.important=important.isChecked();
            TaskStore.upsert(this,draft); AlarmScheduler.schedule(this,draft); syncWallpaperIfEnabled();
            if(draft.reminder && !AlarmScheduler.canScheduleExact(this)) promptExactAlarmPermission();
            if(draft.reminder && draft.wakeScreen && Build.VERSION.SDK_INT>=34 && !getSystemService(NotificationManager.class).canUseFullScreenIntent()) promptFullScreenPermission();
            dialog.dismiss(); reload();
        }));
        dialog.show();
    }

    private void pickDate(Task d, Button b) {
        LocalDate ld; try{ld=LocalDate.parse(d.date);}catch(Exception e){ld=LocalDate.now();}
        new DatePickerDialog(this,(v,y,m,day)->{d.date=LocalDate.of(y,m+1,day).toString();b.setText("日期："+friendlyDate(d.date));},ld.getYear(),ld.getMonthValue()-1,ld.getDayOfMonth()).show();
    }
    private void pickTime(Task d, Button b) { new TimePickerDialog(this,(v,h,m)->{d.hour=h;d.minute=m;b.setText(String.format(Locale.CHINA,"时间：%02d:%02d",h,m));},d.hour,d.minute,true).show(); }

    private void chooseWallpaper() {
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT); i.addCategory(Intent.CATEGORY_OPENABLE); i.setType("image/*"); startActivityForResult(i,PICK_WALLPAPER);
    }
    @Override protected void onActivityResult(int requestCode,int resultCode,Intent data){ super.onActivityResult(requestCode,resultCode,data); if(requestCode==PICK_WALLPAPER&&resultCode==RESULT_OK&&data!=null&&data.getData()!=null){ boolean ok=WallpaperHelper.importSource(this,data.getData()); if(ok){TaskStore.setWallpaperEnabled(this,true); WallpaperHelper.update(this); updatePreview(); Toast.makeText(this,"已使用你的图片作为锁屏底图",Toast.LENGTH_SHORT).show();}else Toast.makeText(this,"读取图片失败",Toast.LENGTH_SHORT).show(); } }
    private void applyWallpaper(){ if(!WallpaperHelper.hasSource(this)){Toast.makeText(this,"请先选择你的壁纸",Toast.LENGTH_SHORT).show();return;} TaskStore.setWallpaperEnabled(this,true); boolean ok=WallpaperHelper.update(this); Toast.makeText(this,ok?"锁屏壁纸已更新":"设置锁屏壁纸失败",Toast.LENGTH_SHORT).show(); updatePreview(); }
    private void syncWallpaperIfEnabled(){ if(TaskStore.wallpaperEnabled(this)) WallpaperHelper.update(this); updatePreview(); }
    private void updatePreview(){ if(preview==null)return; Bitmap bm=WallpaperHelper.preview(this,720,1500); preview.setImageBitmap(bm); }

    private void requestNotificationPermissionIfNeeded(){ if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED) requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},42); }
    private String permissionText(){
        String notify = AlarmNotifier.notificationsAllowed(this) ? "通知权限：已允许" : "通知权限：未允许（锁屏弹窗可能完全不显示）";
        String exact = AlarmScheduler.canScheduleExact(this) ? "精确闹钟：已允许" : "精确闹钟：未允许（可能延迟）";
        String full = ""; if(Build.VERSION.SDK_INT>=34) full="\n到点全屏亮起："+(getSystemService(NotificationManager.class).canUseFullScreenIntent()?"已允许":"未允许");
        return notify + "\n" + exact + full;
    }
    private void openNotificationSettings(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},43);
            return;
        }
        try{ Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()); startActivity(i); }
        catch(Exception e){ startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName()))); }
    }
    private void testAlarm(){
        if(!AlarmScheduler.canScheduleExact(this)){ promptExactAlarmPermission(); return; }
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},43);
            Toast.makeText(this,"先允许通知权限，再点一次测试",Toast.LENGTH_LONG).show();
            return;
        }
        AlarmScheduler.scheduleTest(this,20);
        Toast.makeText(this,"测试闹钟已设置：20 秒后触发。现在可以锁屏等待。",Toast.LENGTH_LONG).show();
    }

    private void promptExactAlarmPermission(){ new AlertDialog.Builder(this).setTitle("允许准时提醒").setMessage("为了让待办在你设定的分钟准时触发，请在系统里允许 LockPlan 设置“闹钟和提醒”。").setNegativeButton("稍后",null).setPositiveButton("去开启",(d,w)->openExactAlarmSettings()).show(); }
    private void promptFullScreenPermission(){ new AlertDialog.Builder(this).setTitle("允许到点亮屏").setMessage("为了在锁屏状态下直接亮起并显示闹钟页面，请允许 LockPlan 使用全屏提醒。若不允许，系统仍可能显示高优先级提醒。").setNegativeButton("稍后",null).setPositiveButton("去开启",(d,w)->openFullScreenSettings()).show(); }
    private void openExactAlarmSettings(){ if(Build.VERSION.SDK_INT>=31){ try{startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));} } else Toast.makeText(this,"你的系统无需单独开启",Toast.LENGTH_SHORT).show(); }
    private void openFullScreenSettings(){ if(Build.VERSION.SDK_INT>=34){ try{startActivity(new Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:"+getPackageName())));}catch(Exception e){startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+getPackageName())));} } }

    private Task copy(Task t){ Task n=new Task(t.id,t.title,t.date); n.hasTime=t.hasTime;n.hour=t.hour;n.minute=t.minute;n.reminder=t.reminder;n.sound=t.sound;n.vibrate=t.vibrate;n.wakeScreen=t.wakeScreen;n.important=t.important;n.done=t.done;return n; }
    private String friendlyDate(String d){ try{LocalDate ld=LocalDate.parse(d);if(ld.equals(LocalDate.now()))return "今天";if(ld.equals(LocalDate.now().plusDays(1)))return "明天";return String.format(Locale.CHINA,"%d月%d日",ld.getMonthValue(),ld.getDayOfMonth());}catch(Exception e){return d;} }
    private void section(LinearLayout root,String s){ TextView v=tv(s,20,true,Color.rgb(30,30,34));v.setPadding(0,dp(30),0,dp(10));root.addView(v); }
    private TextView tv(String s,int sp,boolean bold,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(sp);v.setTextColor(color);if(bold)v.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);return v;}
    private int dp(int v){return Math.round(v*getResources().getDisplayMetrics().density);}

    private static class SimpleItemSelected implements android.widget.AdapterView.OnItemSelectedListener {
        interface Callback { void onSelected(int position); }
        private final Callback cb; SimpleItemSelected(Callback cb){this.cb=cb;}
        public void onItemSelected(android.widget.AdapterView<?> p,View v,int pos,long id){cb.onSelected(pos);} public void onNothingSelected(android.widget.AdapterView<?> p){}
    }
}
