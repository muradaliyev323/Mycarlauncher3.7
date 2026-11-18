package com.mycar.launcher;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/** Обложки соседних треков: читаются из тегов файла в фоне и держатся в небольшом кэше. */
final class ArtCache {

    private final LruCache<String, Bitmap> cache = new LruCache<>(10);
    private final Set<String> loading = new HashSet<>();
    private final Set<String> none = new HashSet<>();
    private final Handler handler = new Handler(Looper.getMainLooper());

    /** Возвращает обложку, если она уже загружена; иначе запускает загрузку и возвращает null. */
    Bitmap get(final String path) {
        if (path == null) return null;
        Bitmap b = cache.get(path);
        if (b != null || loading.contains(path) || none.contains(path)) return b;
        loading.add(path);
        new Thread(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                try {
                    r.setDataSource(path);
                    byte[] pic = r.getEmbeddedPicture();
                    if (pic != null) bmp = decode(pic);
                } catch (Exception ignored) {
                    // нет обложки
                } finally {
                    try {
                        r.release();
                    } catch (Exception ignored) {
                        // ничего
                    }
                }
                if (bmp == null) {
                    bmp = CoverArt.fallback(path, MediaScan.stripExt(new File(path).getName()), null);
                }
                final Bitmap result = bmp;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        loading.remove(path);
                        if (result != null) {
                            cache.put(path, result);
                        } else {
                            none.add(path);
                        }
                    }
                });
            }
        }, "art").start();
        return null;
    }

    private static Bitmap decode(byte[] data) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int side = Math.max(o.outWidth, o.outHeight);
        int sample = 1;
        while (side / sample > 400) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }
}
