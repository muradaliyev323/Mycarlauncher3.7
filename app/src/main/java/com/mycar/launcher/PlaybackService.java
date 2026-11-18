package com.mycar.launcher;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaMetadata;
import android.media.MediaMetadataRetriever;
import android.media.MediaPlayer;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.view.SurfaceHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Служба плеера: играет музыку и видео, держит очередь, управляется кнопками руля
 * (через MediaSession) и продолжает играть, когда вы вернулись на главный экран.
 */
public class PlaybackService extends Service implements
        MediaPlayer.OnPreparedListener,
        MediaPlayer.OnCompletionListener,
        MediaPlayer.OnErrorListener,
        MediaPlayer.OnVideoSizeChangedListener,
        AudioManager.OnAudioFocusChangeListener {

    static volatile PlaybackService instance;
    static volatile List<Track> pendingQueue;
    static volatile int pendingIndex;
    static volatile SurfaceHolder pendingDisplay;
    static volatile int pendingStartMs;
    static volatile boolean pendingPaused;

    private static final String CHANNEL = "player";
    private static final int NOTIFICATION_ID = 7;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final List<Integer> history = new ArrayList<>();
    private List<Track> queue = new ArrayList<>();

    private MediaPlayer mp;
    private MediaSession session;
    private AudioManager audio;
    private SurfaceHolder display;

    private int index = -1;
    private int shuffleNext = -1;
    private int resumeMs;
    private boolean startPaused;
    private boolean intentPlaying;   // пользователь хочет, чтобы играло (не меняется от пауз навигатора)
    private boolean prepared;
    private boolean wantPlay;
    private boolean pausedByFocus;
    private int errorsInRow;
    private int metaToken;

    /** Магнитола проснулась (включили зажигание): если играло, продолжаем. */
    private final Runnable wakeResume = new Runnable() {
        @Override
        public void run() {
            if (PlayerState.active && prepared && intentPlaying && !PlayerState.playing) resume();
        }
    };

    private final BroadcastReceiver screenReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_ON.equals(intent.getAction())) {
                handler.postDelayed(wakeResume, 1500);
            }
        }
    };

    private boolean screenOn() {
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        return pm == null || pm.isInteractive();
    }

    /** Раз в несколько секунд запоминаем, где остановились. */
    private final Runnable saver = new Runnable() {
        @Override
        public void run() {
            if (PlayerState.playing) saveState();
            handler.postDelayed(this, 5000);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        display = pendingDisplay;
        audio = (AudioManager) getSystemService(AUDIO_SERVICE);

        mp = new MediaPlayer();
        mp.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
        mp.setOnPreparedListener(this);
        mp.setOnCompletionListener(this);
        mp.setOnErrorListener(this);
        mp.setOnVideoSizeChangedListener(this);

        session = new MediaSession(this, "MLPlayer");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS
                | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setCallback(new MediaSession.Callback() {
            @Override
            public void onPlay() {
                resume();
            }

            @Override
            public void onPause() {
                pause();
                if (screenOn()) intentPlaying = false;   // пауза кнопкой руля — это желание пользователя
            }

            @Override
            public void onSkipToNext() {
                next();
            }

            @Override
            public void onSkipToPrevious() {
                previous();
            }

            @Override
            public void onSeekTo(long pos) {
                seekTo((int) pos);
            }

            @Override
            public void onStop() {
                finishQueue();
            }
        });
        session.setActive(true);

        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL, getString(R.string.pl_channel), NotificationManager.IMPORTANCE_LOW));
        }
        startForeground(NOTIFICATION_ID, buildNotification());
        handler.postDelayed(saver, 5000);
        registerReceiver(screenReceiver, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        loadPending();
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        saveState();
        instance = null;
        PlayerState.active = false;
        PlayerState.playing = false;
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(screenReceiver);
        } catch (IllegalArgumentException ignored) {
            // уже снят
        }
        try {
            mp.release();
        } catch (Exception ignored) {
            // уже освобождён
        }
        if (session != null) {
            session.setActive(false);
            session.release();
        }
        if (audio != null) audio.abandonAudioFocus(this);
        super.onDestroy();
    }

    // ---------- очередь и переключение ----------

    void loadPending() {
        List<Track> q = pendingQueue;
        pendingQueue = null;
        if (q == null || q.isEmpty()) return;
        if (PlayerState.active) saveState();   // запоминаем место в том, что играло до переключения
        queue = q;
        PlayerState.queue = q;
        history.clear();
        index = -1;
        errorsInRow = 0;
        PlayerState.size = q.size();
        PlayerState.active = true;
        resumeMs = pendingStartMs;
        startPaused = pendingPaused;
        pendingStartMs = 0;
        pendingPaused = false;
        startTrack(Math.max(0, Math.min(pendingIndex, q.size() - 1)), false);
    }

    private void startTrack(int i, boolean record) {
        if (queue.isEmpty() || i < 0 || i >= queue.size()) return;
        if (record && index >= 0 && index != i) {
            history.add(index);
            if (history.size() > 100) history.remove(0);
        }
        index = i;
        Track t = queue.get(i);
        prepared = false;
        wantPlay = !startPaused;
        intentPlaying = wantPlay;
        startPaused = false;
        pausedByFocus = false;
        metaToken++;

        PlayerState.index = i;
        PlayerState.currentPath = t.path;
        PlayerState.title = t.name;
        PlayerState.artist = "";
        PlayerState.album = "";
        PlayerState.art = null;
        PlayerState.video = t.video;
        PlayerState.playing = false;
        PlayerState.durationMs = 0;
        PlayerState.videoWidth = 0;
        PlayerState.videoHeight = 0;
        refreshNeighbors();
        saveState();

        try {
            mp.reset();
            mp.setDataSource(t.path);
            mp.setAudioStreamType(AudioManager.STREAM_MUSIC);
            mp.setDisplay(t.video ? display : null);
            mp.prepareAsync();
        } catch (Exception e) {
            onError(mp, 0, 0);
            return;
        }
        loadMeta(t, metaToken);
        updateSession();
        refreshNotification();
    }

    private int nextIndex(boolean auto) {
        int n = queue.size();
        if (n == 0) return -1;
        if (PlayerState.shuffle && n > 1) {
            if (shuffleNext < 0 || shuffleNext >= n || shuffleNext == index) {
                int r;
                do {
                    r = random.nextInt(n);
                } while (r == index);
                shuffleNext = r;
            }
            return shuffleNext;
        }
        int next = index + 1;
        if (next >= n) {
            return (PlayerState.repeat == PlayerState.REPEAT_ALL || !auto) ? 0 : -1;
        }
        return next;
    }

    /** Заранее определяем, какие треки будут «предыдущим» и «следующим»: их обложки видны на экране. */
    void refreshNeighbors() {
        int n = queue.size();
        shuffleNext = -1;
        if (n == 0) {
            PlayerState.prevIndex = -1;
            PlayerState.nextIndex = -1;
            return;
        }
        PlayerState.nextIndex = nextIndex(false);
        if (PlayerState.shuffle && !history.isEmpty()) {
            PlayerState.prevIndex = history.get(history.size() - 1);
        } else {
            PlayerState.prevIndex = (index - 1 + n) % n;
        }
    }

    void next() {
        int n = nextIndex(false);
        if (n >= 0) startTrack(n, true);
    }

    void previous() {
        if (prepared && position() > 3000) {
            seekTo(0);
            return;
        }
        previousTrack();
    }

    /** Настоящий переход к предыдущему треку (для свайпа), без «сначала, если играет больше трёх секунд». */
    void previousTrack() {
        if (PlayerState.shuffle && !history.isEmpty()) {
            startTrack(history.remove(history.size() - 1), false);
        } else if (!queue.isEmpty()) {
            startTrack((index - 1 + queue.size()) % queue.size(), false);
        }
    }

    private void finishQueue() {
        intentPlaying = false;
        getSharedPreferences("launcher", MODE_PRIVATE).edit().putBoolean("last_playing", false).apply();
        wantPlay = false;
        PlayerState.playing = false;
        PlayerState.active = false;
        PlayerState.queue = new ArrayList<>();
        PlayerState.currentPath = "";
        PlayerState.prevIndex = -1;
        PlayerState.nextIndex = -1;
        stopForeground(true);
        stopSelf();
    }

    /** Запоминаем файл и позицию отдельно для музыки и для видео. */
    private void saveState() {
        if (!PlayerState.active || index < 0 || index >= queue.size()) return;
        Track t = queue.get(index);
        int pos = prepared ? position() : resumeMs;
        String k = t.video ? "v" : "a";
        SharedPreferences.Editor e = getSharedPreferences("launcher", MODE_PRIVATE).edit();
        e.putString("last_" + k + "_path", t.path);
        e.putInt("last_" + k + "_pos", pos);
        e.putString("last_kind", k);
        e.putBoolean("last_playing", intentPlaying);
        e.apply();
    }

    // ---------- пауза, перемотка ----------

    void toggle() {
        if (!prepared) {
            wantPlay = !wantPlay;
            intentPlaying = wantPlay;
            return;
        }
        if (PlayerState.playing) {
            intentPlaying = false;
            pause();
        } else {
            resume();
        }
    }

    void pause() {
        wantPlay = false;
        pausedByFocus = false;
        try {
            if (prepared && mp.isPlaying()) mp.pause();
        } catch (IllegalStateException ignored) {
            // плеер в другом состоянии
        }
        PlayerState.playing = false;
        saveState();
        updateSession();
    }

    void resume() {
        wantPlay = true;
        intentPlaying = true;
        if (!prepared) return;
        if (requestFocus()) {
            try {
                mp.start();
                PlayerState.playing = true;
            } catch (IllegalStateException ignored) {
                // плеер в другом состоянии
            }
        }
        updateSession();
    }

    void seekTo(int ms) {
        if (!prepared) return;
        try {
            mp.seekTo(ms);
        } catch (IllegalStateException ignored) {
            // плеер в другом состоянии
        }
        updateSession();
    }

    int position() {
        if (!prepared) return 0;
        try {
            return mp.getCurrentPosition();
        } catch (IllegalStateException e) {
            return 0;
        }
    }

    void setDisplay(SurfaceHolder holder) {
        display = holder;
        try {
            if (mp != null && PlayerState.video) mp.setDisplay(holder);
        } catch (Exception ignored) {
            // плеер в другом состоянии
        }
    }

    // ---------- события MediaPlayer ----------

    @Override
    public void onPrepared(MediaPlayer p) {
        prepared = true;
        errorsInRow = 0;
        PlayerState.durationMs = p.getDuration();
        if (resumeMs > 0 && resumeMs < p.getDuration() - 3000) p.seekTo(resumeMs);
        resumeMs = 0;
        if (wantPlay && requestFocus()) {
            p.start();
            PlayerState.playing = true;
        } else {
            PlayerState.playing = false;
        }
        updateSession();
        refreshNotification();
    }

    @Override
    public void onCompletion(MediaPlayer p) {
        if (PlayerState.repeat == PlayerState.REPEAT_ONE) {
            startTrack(index, false);
            return;
        }
        int n = nextIndex(true);
        if (n < 0) {
            finishQueue();
        } else {
            startTrack(n, true);
        }
    }

    @Override
    public boolean onError(MediaPlayer p, int what, int extra) {
        prepared = false;
        PlayerState.playing = false;
        if (index >= 0 && index < queue.size()) PlayerState.error = queue.get(index).name;
        errorsInRow++;
        if (errorsInRow >= queue.size() || errorsInRow >= 5) {
            finishQueue();
            return true;
        }
        handler.post(new Runnable() {
            @Override
            public void run() {
                int n = nextIndex(true);
                if (n < 0) {
                    finishQueue();
                } else {
                    startTrack(n, true);
                }
            }
        });
        return true;
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer p, int width, int height) {
        PlayerState.videoWidth = width;
        PlayerState.videoHeight = height;
    }

    // ---------- звуковой фокус (навигатор, звонки) ----------

    private boolean requestFocus() {
        return audio.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
    }

    @Override
    public void onAudioFocusChange(int change) {
        try {
            if (change == AudioManager.AUDIOFOCUS_LOSS) {
                pause();
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
                if (PlayerState.playing) {
                    pause();
                    pausedByFocus = true;
                }
            } else if (change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) {
                mp.setVolume(0.3f, 0.3f);
            } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
                mp.setVolume(1f, 1f);
                if (pausedByFocus) {
                    pausedByFocus = false;
                    resume();
                }
            }
        } catch (IllegalStateException ignored) {
            // плеер в другом состоянии
        }
    }

    // ---------- название, исполнитель, обложка ----------

    private void loadMeta(final Track t, final int token) {
        new Thread(new Runnable() {
            @Override
            public void run() {
                MediaMetadataRetriever r = new MediaMetadataRetriever();
                String title = null;
                String artist = null;
                String album = null;
                Bitmap art = null;
                try {
                    r.setDataSource(t.path);
                    title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
                    artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
                    album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
                    byte[] pic = r.getEmbeddedPicture();
                    if (pic != null) art = decodeSmall(pic);
                } catch (Exception ignored) {
                    // нет тегов — останется имя файла
                } finally {
                    try {
                        r.release();
                    } catch (Exception ignored) {
                        // ничего
                    }
                }
                if (art == null && !t.video) {
                    art = CoverArt.fallback(t.path,
                            title != null && !title.trim().isEmpty() ? title : t.name, artist);
                }
                final String ft = title;
                final String fa = artist;
                final String fl = album;
                final Bitmap fb = art;
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (token != metaToken) return;
                        if (ft != null && !ft.trim().isEmpty()) PlayerState.title = ft.trim();
                        PlayerState.artist = fa == null ? "" : fa.trim();
                        PlayerState.album = fl == null ? "" : fl.trim();
                        PlayerState.art = fb;
                        updateSession();
                        refreshNotification();
                    }
                });
            }
        }, "meta").start();
    }

    private static Bitmap decodeSmall(byte[] data) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        int side = Math.max(o.outWidth, o.outHeight);
        int sample = 1;
        while (side / sample > 600) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeByteArray(data, 0, data.length, o);
    }

    // ---------- сессия и уведомление ----------

    private void updateSession() {
        if (session == null) return;
        int st;
        if (!PlayerState.active) {
            st = PlaybackState.STATE_NONE;
        } else if (!prepared) {
            st = PlaybackState.STATE_BUFFERING;
        } else {
            st = PlayerState.playing ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED;
        }
        PlaybackState ps = new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE
                        | PlaybackState.ACTION_PLAY_PAUSE | PlaybackState.ACTION_SKIP_TO_NEXT
                        | PlaybackState.ACTION_SKIP_TO_PREVIOUS | PlaybackState.ACTION_SEEK_TO
                        | PlaybackState.ACTION_STOP)
                .setState(st, position(), 1f)
                .build();
        session.setPlaybackState(ps);

        MediaMetadata md = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, PlayerState.title)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, PlayerState.artist)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, PlayerState.album)
                .putLong(MediaMetadata.METADATA_KEY_DURATION, PlayerState.durationMs)
                .build();
        session.setMetadata(md);
    }

    private Notification buildNotification() {
        Notification.Builder b = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL)
                : new Notification.Builder(this);
        String t = PlayerState.title;
        if (t == null || t.isEmpty()) t = getString(R.string.app_name);
        PendingIntent open = PendingIntent.getActivity(
                this, 0, new Intent(this, PlayerActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        b.setSmallIcon(R.drawable.ic_stat_music)
                .setContentTitle(t)
                .setContentText(PlayerState.artist == null ? "" : PlayerState.artist)
                .setOngoing(true)
                .setContentIntent(open);
        return b.build();
    }

    private void refreshNotification() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(NOTIFICATION_ID, buildNotification());
    }
}
