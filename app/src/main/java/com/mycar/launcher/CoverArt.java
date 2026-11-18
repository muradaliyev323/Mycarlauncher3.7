package com.mycar.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.Typeface;

import java.io.File;
import java.util.Locale;

/**
 * Обложка для трека, у которого её нет в тегах: сначала ищем картинку в папке
 * (cover.jpg, folder.jpg и т. п. или файл с именем трека), если и её нет — рисуем свою
 * (цветной градиент и буква). Так у каждого трека на экране есть картинка.
 */
final class CoverArt {

    private static final String[] NAMES = {"cover", "folder", "front", "album", "albumart", "thumb"};
    private static final String[] EXTS = {"jpg", "jpeg", "png"};
    private static final int SIZE = 360;

    private CoverArt() {}

    static Bitmap fallback(String path, String title, String artist) {
        File track = new File(path);
        Bitmap b = folderImage(track);
        return b != null ? b : generated(track, title, artist);
    }

    private static boolean has(String[] list, String value) {
        for (String s : list) {
            if (s.equals(value)) return true;
        }
        return false;
    }

    private static Bitmap folderImage(File track) {
        File dir = track.getParentFile();
        if (dir == null) return null;
        String[] names = dir.list();
        if (names == null) return null;
        String stem = MediaScan.stripExt(track.getName()).toLowerCase(Locale.ROOT);
        String best = null;
        for (String n : names) {
            String low = n.toLowerCase(Locale.ROOT);
            int dot = low.lastIndexOf('.');
            if (dot < 0 || !has(EXTS, low.substring(dot + 1))) continue;
            String base = low.substring(0, dot);
            if (base.equals(stem)) {
                best = n;
                break;
            }
            if (best == null && has(NAMES, base)) best = n;
        }
        return best == null ? null : decodeFile(new File(dir, best).getAbsolutePath());
    }

    private static Bitmap decodeFile(String path) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(path, o);
            int side = Math.max(o.outWidth, o.outHeight);
            int sample = 1;
            while (side / sample > 500) sample *= 2;
            o = new BitmapFactory.Options();
            o.inSampleSize = sample;
            return BitmapFactory.decodeFile(path, o);
        } catch (Exception e) {
            return null;
        }
    }

    /** Цвет зависит от исполнителя (или названия папки), поэтому треки одного альбома похожи. */
    private static Bitmap generated(File track, String title, String artist) {
        String key = artist != null && !artist.trim().isEmpty() ? artist : "";
        if (key.isEmpty() && track.getParentFile() != null) key = track.getParentFile().getName();
        float hue = Math.abs(key.hashCode()) % 360;
        int c1 = Color.HSVToColor(new float[]{hue, 0.55f, 0.60f});
        int c2 = Color.HSVToColor(new float[]{(hue + 40f) % 360f, 0.70f, 0.28f});

        Bitmap bmp = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bmp);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, SIZE, SIZE, c1, c2, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, SIZE, SIZE, p);

        Paint t = new Paint();
        t.setAntiAlias(true);
        t.setColor(0x66FFFFFF);
        t.setTextAlign(Paint.Align.CENTER);
        t.setTextSize(SIZE * 0.55f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        Paint.FontMetrics fm = t.getFontMetrics();
        canvas.drawText(initial(title, track), SIZE / 2f, SIZE / 2f - (fm.ascent + fm.descent) / 2f, t);
        return bmp;
    }

    private static String initial(String title, File track) {
        String s = title != null && !title.trim().isEmpty() ? title : MediaScan.stripExt(track.getName());
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (Character.isLetterOrDigit(ch)) return String.valueOf(Character.toUpperCase(ch));
        }
        return "M";
    }
}
