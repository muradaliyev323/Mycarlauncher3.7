package com.mycar.launcher;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.List;

/** Команды плееру из любого экрана. Если служба уже работает, они выполняются сразу. */
final class PlayerControl {

    private PlayerControl() {}

    static void play(Context context, List<Track> queue, int index) {
        play(context, queue, index, 0, false);
    }

    /** Запуск очереди; startMs и paused нужны для восстановления прошлого места (трек ждёт на паузе). */
    static void play(Context context, List<Track> queue, int index, int startMs, boolean paused) {
        PlaybackService.pendingQueue = new ArrayList<>(queue);
        PlaybackService.pendingIndex = index;
        PlaybackService.pendingStartMs = startMs;
        PlaybackService.pendingPaused = paused;

        PlaybackService running = PlaybackService.instance;
        if (running != null) {
            running.loadPending();
            return;
        }
        Intent intent = new Intent(context, PlaybackService.class);
        if (Build.VERSION.SDK_INT >= 26) {
            context.startForegroundService(intent);
        } else {
            context.startService(intent);
        }
    }

    static void toggle() {
        PlaybackService s = PlaybackService.instance;
        if (s != null) s.toggle();
    }

    static void next() {
        PlaybackService s = PlaybackService.instance;
        if (s != null) s.next();
    }

    static void previous() {
        PlaybackService s = PlaybackService.instance;
        if (s != null) s.previous();
    }

    static void previousTrack() {
        PlaybackService s = PlaybackService.instance;
        if (s != null) s.previousTrack();
    }

    static void seekTo(int ms) {
        PlaybackService s = PlaybackService.instance;
        if (s != null) s.seekTo(ms);
    }

    static int position() {
        PlaybackService s = PlaybackService.instance;
        return s == null ? 0 : s.position();
    }

    static void setDisplay(SurfaceHolder holder) {
        PlaybackService s = PlaybackService.instance;
        if (s != null) {
            s.setDisplay(holder);
        } else {
            PlaybackService.pendingDisplay = holder;
        }
    }

    static void toggleShuffle() {
        PlayerState.shuffle = !PlayerState.shuffle;
        PlaybackService s = PlaybackService.instance;
        if (s != null) s.refreshNeighbors();
    }

    static void cycleRepeat() {
        PlayerState.repeat = (PlayerState.repeat + 1) % 3;
        PlaybackService s = PlaybackService.instance;
        if (s != null) s.refreshNeighbors();
    }
}
