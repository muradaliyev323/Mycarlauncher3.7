package com.mycar.launcher;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.List;

/** Автозапуск после включения магнитолы: продолжаем то, что играло перед выключением. */
final class AutoResume {

    /** Пробуем один раз за запуск приложения. */
    static boolean started;

    private AutoResume() {}

    /**
     * Возвращает 0 — делать нечего, 1 — музыка запущена, 2 — запущено видео,
     * -1 — файл пока недоступен (флешка ещё не подключилась), нужно повторить позже.
     */
    static int attempt(Context context, SharedPreferences prefs) {
        if (!prefs.getBoolean("auto_resume", true) || !prefs.getBoolean("last_playing", false)) return 0;
        if (PlayerState.active) return 0;

        String kind = prefs.getString("last_kind", "a");
        boolean video = "v".equals(kind);
        String path = prefs.getString("last_" + kind + "_path", null);
        if (path == null) return 0;

        File f = new File(path);
        File dir = f.getParentFile();
        if (dir == null || !f.isFile()) return -1;

        List<Track> queue = MediaScan.tracksIn(dir, video);
        int idx = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (queue.get(i).path.equals(path)) idx = i;
        }
        if (idx < 0) return -1;

        PlayerControl.play(context, queue, idx, prefs.getInt("last_" + kind + "_pos", 0), false);
        return video ? 2 : 1;
    }
}
