package com.mycar.launcher;

import android.graphics.Bitmap;

import java.util.ArrayList;
import java.util.List;

/** Общее состояние плеера: его пишет служба, читают экран плеера и мини-плеер на главном экране. */
final class PlayerState {

    static final int REPEAT_OFF = 0;
    static final int REPEAT_ALL = 1;
    static final int REPEAT_ONE = 2;

    static volatile boolean active;      // служба запущена и в ней есть очередь
    static volatile boolean playing;
    static volatile boolean video;       // текущий файл — видео
    static volatile String title = "";
    static volatile String artist = "";
    static volatile String album = "";
    static volatile Bitmap art;          // обложка (небольшая) или null
    static volatile int durationMs;
    static volatile int videoWidth;
    static volatile int videoHeight;
    static volatile int index;
    static volatile int size;
    static volatile int prevIndex = -1;  // какой трек будет «предыдущим»
    static volatile int nextIndex = -1;  // какой трек будет «следующим» (с учётом перемешивания)
    static volatile String currentPath = "";
    static volatile List<Track> queue = new ArrayList<>();
    static volatile boolean shuffle;
    static volatile int repeat = REPEAT_OFF;
    static volatile String error;        // название файла, который не удалось открыть

    private PlayerState() {}
}
