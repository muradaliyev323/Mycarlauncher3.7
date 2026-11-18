package com.mycar.launcher;

import android.app.Activity;
import android.graphics.Bitmap;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.TextView;

/** Мини-плеер на главном экране: круглая обложка с кольцом, название, исполнитель и три кнопки. */
final class MiniPlayer {

    private static final int LIGHT = 0xFFC9D0DC;
    private static final int MUTED = 0xFF8F98A8;

    private final float u;
    private final PlayerHelper helper;
    private final View ringBox;
    private final RingView ring;
    private final ImageView cover;
    private final ImageView play;
    private final TextView title;
    private final TextView artist;

    private Bitmap shownArt;
    private boolean coverInit;
    private int boxSize = -1;
    private int shownPlaying = -1;

    MiniPlayer(final Activity a, float u, final PlayerHelper helper, View.OnClickListener cardClick) {
        this.u = u;
        this.helper = helper;
        ringBox = a.findViewById(R.id.ring_box);
        ring = a.findViewById(R.id.ring);
        cover = a.findViewById(R.id.pl_cover);
        play = a.findViewById(R.id.pl_play);
        title = a.findViewById(R.id.pl_title);
        artist = a.findViewById(R.id.pl_artist);

        title.setTextSize(TypedValue.COMPLEX_UNIT_PX, 20 * u);
        artist.setTextSize(TypedValue.COMPLEX_UNIT_PX, 15 * u);
        ring.setStroke(Math.max(2f, 4f * u));

        cover.setBackgroundResource(R.drawable.cover_oval);
        cover.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        cover.setClipToOutline(true);

        style((ImageView) a.findViewById(R.id.pl_prev), 48, 9, LIGHT);
        style((ImageView) a.findViewById(R.id.pl_next), 48, 9, LIGHT);
        style(play, 64, 15, 0xFFFFFFFF);

        a.findViewById(R.id.pl_prev).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                helper.previous();
            }
        });
        play.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                helper.playPause();
            }
        });
        a.findViewById(R.id.pl_next).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                helper.next();
            }
        });
        a.findViewById(R.id.card_player).setOnClickListener(cardClick);
    }

    private void style(ImageView v, float size, float pad, int color) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        lp.width = Math.round(size * u);
        lp.height = Math.round(size * u);
        v.setLayoutParams(lp);
        int p = Math.round(pad * u);
        v.setPadding(p, p, p, p);
        v.setColorFilter(color);
    }

    void update() {
        String t = helper.title;
        if (t != null && !t.isEmpty()) {
            title.setText(t);
            artist.setText(helper.artist == null ? "" : helper.artist);
        } else {
            title.setText(R.string.pl_idle);
            artist.setText(R.string.pl_hint);
        }

        boolean own = PlayerState.active;
        int d = Math.min(ringBox.getWidth(), ringBox.getHeight());
        if (d > 0 && d != boxSize) {
            boxSize = d;
            int c = Math.round(d * 0.72f);
            ViewGroup.LayoutParams lp = cover.getLayoutParams();
            lp.width = c;
            lp.height = c;
            cover.setLayoutParams(lp);
            coverInit = false;
        }

        Bitmap art = own ? PlayerState.art : null;
        if (boxSize > 0 && (!coverInit || art != shownArt)) {
            coverInit = true;
            shownArt = art;
            if (art != null) {
                cover.setScaleType(ImageView.ScaleType.CENTER_CROP);
                cover.setPadding(0, 0, 0, 0);
                cover.clearColorFilter();
                cover.setImageBitmap(art);
            } else {
                int p = Math.round(boxSize * 0.72f * 0.25f);
                cover.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cover.setPadding(p, p, p, p);
                cover.setColorFilter(MUTED);
                cover.setImageResource(R.drawable.ic_music);
            }
        }

        float progress = 0f;
        if (own && PlayerState.durationMs > 0) {
            progress = (float) PlayerControl.position() / PlayerState.durationMs;
        }
        ring.setProgress(progress);

        int playing = helper.playing ? 1 : 0;
        if (playing != shownPlaying) {
            shownPlaying = playing;
            play.setImageResource(playing == 1 ? R.drawable.ic_pause : R.drawable.ic_play);
        }
    }
}
