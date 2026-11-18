package com.mycar.launcher;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Какие файлы считаем музыкой и видео, и как собрать очередь из папки. */
final class MediaScan {

    static final String[] AUDIO_EXT = {"mp3", "m4a", "aac", "flac", "wav", "ogg", "opus"};
    static final String[] VIDEO_EXT = {"mp4", "m4v", "mkv", "avi", "3gp", "webm", "mov", "ts"};

    private MediaScan() {}

    static boolean hasExt(String name, boolean video) {
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        for (String e : video ? VIDEO_EXT : AUDIO_EXT) {
            if (e.equals(ext)) return true;
        }
        return false;
    }

    static String stripExt(String name) {
        int dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    /** Все файлы нужного типа из папки, по алфавиту (так же, как в списке справа). */
    static List<Track> tracksIn(File dir, boolean video) {
        List<File> files = new ArrayList<>();
        File[] all = dir.listFiles();
        if (all != null) {
            for (File f : all) {
                String n = f.getName();
                if (f.isFile() && !n.startsWith(".") && hasExt(n, video)) files.add(f);
            }
        }
        Collections.sort(files, new Comparator<File>() {
            @Override
            public int compare(File a, File b) {
                return a.getName().compareToIgnoreCase(b.getName());
            }
        });
        List<Track> result = new ArrayList<>();
        for (File f : files) {
            result.add(new Track(f.getAbsolutePath(), stripExt(f.getName()), video));
        }
        return result;
    }
}
