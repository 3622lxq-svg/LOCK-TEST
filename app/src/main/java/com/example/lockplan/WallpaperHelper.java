package com.example.lockplan;

import android.app.WallpaperManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.net.Uri;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class WallpaperHelper {
    private static final String SOURCE_FILE = "lockplan_wallpaper_source.jpg";

    public static boolean hasSource(Context context) {
        return new File(context.getFilesDir(), SOURCE_FILE).exists();
    }

    public static boolean importSource(Context context, Uri uri) {
        Bitmap decoded = null;
        try (InputStream in = context.getContentResolver().openInputStream(uri)) {
            if (in == null) return false;
            decoded = BitmapFactory.decodeStream(in);
            if (decoded == null) return false;
            Bitmap scaled = scaleDown(decoded, 4096);
            File file = new File(context.getFilesDir(), SOURCE_FILE);
            try (FileOutputStream out = new FileOutputStream(file)) {
                scaled.compress(Bitmap.CompressFormat.JPEG, 94, out);
            }
            if (scaled != decoded) scaled.recycle();
            decoded.recycle();
            return true;
        } catch (Exception e) {
            if (decoded != null && !decoded.isRecycled()) decoded.recycle();
            return false;
        }
    }

    public static boolean update(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        if (!TaskStore.wallpaperEnabled(context)) return false;
        try {
            int[] size = screenSize(context);
            Bitmap bitmap = render(context, size[0], size[1]);
            WallpaperManager.getInstance(context).setBitmap(bitmap, null, true, WallpaperManager.FLAG_LOCK);
            bitmap.recycle();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public static Bitmap preview(Context context, int width, int height) {
        return render(context, width, height);
    }

    private static Bitmap render(Context context, int width, int height) {
        Bitmap out = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Bitmap source = loadSource(context);
        if (source != null) {
            drawCenterCrop(c, source, width, height);
            source.recycle();
        } else {
            Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
            bg.setShader(new LinearGradient(0, 0, width, height,
                    Color.rgb(72, 82, 122), Color.rgb(34, 38, 58), Shader.TileMode.CLAMP));
            c.drawRect(0, 0, width, height, bg);
        }

        List<Task> allToday = TaskStore.today(context);
        List<Task> visiblePool = new ArrayList<>();
        for (Task t : allToday) {
            if (!t.done || TaskStore.showDone(context)) visiblePool.add(t);
        }
        int maxItems = Math.max(3, Math.min(9, TaskStore.maxItems(context)));
        int shown = Math.min(maxItems, visiblePool.size());
        float unit = width / 1080f;
        float margin = 72f * unit;
        float headerH = 150f * unit;
        float lineH = 92f * unit;
        float footerH = 72f * unit;
        float pad = 48f * unit;
        float cardHeight = headerH + Math.max(1, shown) * lineH + footerH + pad;
        cardHeight = Math.min(cardHeight, height * 0.50f);

        int pos = TaskStore.cardPosition(context);
        float top;
        if (pos == 0) top = height * 0.30f;
        else if (pos == 1) top = height * 0.43f;
        else top = height - cardHeight - height * 0.105f;
        top = Math.max(height * 0.27f, Math.min(top, height - cardHeight - 32f * unit));
        RectF card = new RectF(margin, top, width - margin, top + cardHeight);

        String style = TaskStore.cardStyle(context);
        int opacity = Math.max(10, Math.min(80, TaskStore.cardOpacity(context)));
        if (!"minimal".equals(style)) {
            Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
            shadow.setColor(Color.argb(45, 0, 0, 0));
            c.drawRoundRect(new RectF(card.left, card.top + 10f * unit, card.right, card.bottom + 10f * unit),
                    38f * unit, 38f * unit, shadow);

            Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
            glass.setColor(Color.argb((int)(255 * opacity / 100f), 10, 12, 18));
            c.drawRoundRect(card, 38f * unit, 38f * unit, glass);

            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Math.max(1f, 1.3f * unit));
            border.setColor(Color.argb(42, 255, 255, 255));
            c.drawRoundRect(card, 38f * unit, 38f * unit, border);
        } else {
            Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
            shade.setShader(new LinearGradient(0, card.top - 80f*unit, 0, card.bottom + 40f*unit,
                    Color.TRANSPARENT, Color.argb(105, 0, 0, 0), Shader.TileMode.CLAMP));
            c.drawRect(0, card.top - 80f*unit, width, card.bottom + 40f*unit, shade);
        }

        float x = card.left + pad;
        float y = card.top + 72f * unit;
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(android.graphics.Typeface.create("sans", android.graphics.Typeface.NORMAL));
        p.setColor(Color.WHITE);
        p.setTextSize(44f * unit);
        p.setFakeBoldText(true);
        c.drawText("今日计划", x, y, p);

        int done = 0;
        for (Task t : allToday) if (t.done) done++;
        String progress = done + " / " + allToday.size();
        p.setTextSize(30f * unit);
        p.setFakeBoldText(false);
        p.setColor(Color.argb(215, 255, 255, 255));
        c.drawText(progress, card.right - pad - p.measureText(progress), y, p);

        p.setTextSize(26f * unit);
        p.setColor(Color.argb(185,255,255,255));
        String dateText = new SimpleDateFormat("M月d日  EEEE", Locale.CHINA).format(new Date());
        c.drawText(dateText, x, y + 42f * unit, p);

        float lineY = card.top + headerH + 35f * unit;
        if (visiblePool.isEmpty()) {
            p.setTextSize(33f * unit);
            p.setColor(Color.argb(230,255,255,255));
            c.drawText(allToday.isEmpty() ? "今天没有待办，享受一下空闲。" : "今天的计划已经全部完成 ✓", x, lineY, p);
        } else {
            for (int i = 0; i < shown; i++) {
                Task t = visiblePool.get(i);
                float cy = lineY - 10f * unit;
                Paint circle = new Paint(Paint.ANTI_ALIAS_FLAG);
                circle.setStyle(Paint.Style.STROKE);
                circle.setStrokeWidth(3f * unit);
                circle.setColor(t.done ? Color.argb(130,255,255,255) : Color.argb(235,255,255,255));
                c.drawCircle(x + 14f * unit, cy - 3f * unit, 13f * unit, circle);
                if (t.done) {
                    circle.setStyle(Paint.Style.FILL);
                    circle.setTextSize(23f * unit);
                    circle.setColor(Color.WHITE);
                    c.drawText("✓", x + 5f * unit, cy + 5f * unit, circle);
                }

                float textX = x + 52f * unit;
                p.setFakeBoldText(false);
                p.setTextSize(29f * unit);
                p.setColor(t.done ? Color.argb(135,255,255,255) : Color.argb(245,255,255,255));
                String time = t.hasTime ? String.format(Locale.CHINA, "%02d:%02d", t.hour, t.minute) : "--:--";
                c.drawText(time, textX, lineY, p);
                float titleX = textX + 105f * unit;
                if (t.important) {
                    p.setFakeBoldText(true);
                    p.setColor(t.done ? Color.argb(145,255,255,255) : Color.WHITE);
                }
                String title = ellipsizeToWidth(p, t.title, card.right - pad - titleX);
                c.drawText(title, titleX, lineY, p);
                lineY += lineH;
            }
        }

        int hidden = Math.max(0, visiblePool.size() - shown);
        p.setTextSize(25f * unit);
        p.setFakeBoldText(false);
        p.setColor(Color.argb(170,255,255,255));
        String foot = hidden > 0 ? "还有 " + hidden + " 项 · 解锁后查看全部" : "修改待办后会自动更新锁屏";
        c.drawText(foot, x, card.bottom - 36f * unit, p);
        return out;
    }

    private static Bitmap loadSource(Context context) {
        try { return BitmapFactory.decodeFile(new File(context.getFilesDir(), SOURCE_FILE).getAbsolutePath()); }
        catch (Exception e) { return null; }
    }

    private static void drawCenterCrop(Canvas c, Bitmap src, int w, int h) {
        float scale = Math.max(w / (float)src.getWidth(), h / (float)src.getHeight());
        float sw = w / scale;
        float sh = h / scale;
        float left = (src.getWidth() - sw) / 2f;
        float top = (src.getHeight() - sh) / 2f;
        Rect srcRect = new Rect(Math.round(left), Math.round(top), Math.round(left + sw), Math.round(top + sh));
        Rect dstRect = new Rect(0,0,w,h);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        c.drawBitmap(src, srcRect, dstRect, p);
    }

    private static Bitmap scaleDown(Bitmap b, int maxDim) {
        int max = Math.max(b.getWidth(), b.getHeight());
        if (max <= maxDim) return b;
        float s = maxDim / (float)max;
        return Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth()*s)), Math.max(1, Math.round(b.getHeight()*s)), true);
    }

    private static String ellipsizeToWidth(Paint p, String s, float maxWidth) {
        if (s == null) return "";
        if (p.measureText(s) <= maxWidth) return s;
        String ell = "…";
        int end = s.length();
        while (end > 1 && p.measureText(s.substring(0,end) + ell) > maxWidth) end--;
        return s.substring(0, Math.max(1,end)) + ell;
    }

    private static int[] screenSize(Context context) {
        DisplayMetrics dm = new DisplayMetrics();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Rect b = wm.getCurrentWindowMetrics().getBounds();
            return new int[]{b.width(), b.height()};
        }
        wm.getDefaultDisplay().getRealMetrics(dm);
        return new int[]{dm.widthPixels, dm.heightPixels};
    }
}
