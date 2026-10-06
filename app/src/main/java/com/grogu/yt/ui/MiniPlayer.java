package com.grogu.yt.ui;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.io.File;
import java.util.Locale;

final class MiniPlayer extends LinearLayout {
    private final TextView play, time;
    private final SeekBar seek;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private MediaPlayer mp;
    private boolean prepared;

    private final Runnable tick = new Runnable() {
        public void run() {
            if (mp != null && prepared) {
                try {
                    seek.setProgress(mp.getCurrentPosition());
                    time.setText(fmt(mp.getCurrentPosition()) + " / " + fmt(mp.getDuration()));
                } catch (IllegalStateException ignored) { }
                if (mp.isPlaying()) handler.postDelayed(this, 250);
            }
        }
    };

    MiniPlayer(Context c) {
        super(c);
        float d = c.getResources().getDisplayMetrics().density;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xFFF1F3F4);
        bg.setCornerRadius(21 * d);
        setBackground(bg);
        setPadding(Math.round(4 * d), 0, Math.round(14 * d), 0);

        play = new TextView(c);
        play.setText("\u25B6");
        play.setTextSize(16);
        play.setGravity(Gravity.CENTER);
        play.setTextColor(0xFF171916);
        play.setLayoutParams(new LayoutParams(Math.round(40 * d), Math.round(40 * d)));
        play.setOnClickListener(new OnClickListener() {
            public void onClick(android.view.View v) { toggle(); }
        });
        addView(play);

        seek = new SeekBar(c);
        LayoutParams sp = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        seek.setLayoutParams(sp);
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar s, int p, boolean fromUser) {
                if (fromUser && mp != null && prepared) mp.seekTo(p);
            }
            public void onStartTrackingTouch(SeekBar s) { }
            public void onStopTrackingTouch(SeekBar s) { }
        });
        addView(seek);

        time = new TextView(c);
        time.setTextSize(11);
        time.setTextColor(0xFF5F6368);
        time.setText("0:00 / 0:00");
        time.setPadding(Math.round(8 * d), 0, 0, 0);
        addView(time);
    }

    void load(File f) {
        release();
        try {
            mp = new MediaPlayer();
            mp.setDataSource(f.getAbsolutePath());
            mp.prepare(); // preload="metadata": the file is local, so this is instant
            prepared = true;
            seek.setMax(mp.getDuration());
            seek.setProgress(0);
            time.setText("0:00 / " + fmt(mp.getDuration()));
            mp.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                public void onCompletion(MediaPlayer m) {
                    play.setText("\u25B6");
                    seek.setProgress(0);
                    time.setText("0:00 / " + fmt(m.getDuration()));
                }
            });
        } catch (Exception e) {
            release();
        }
    }

    private void toggle() {
        if (mp == null || !prepared) return;
        if (mp.isPlaying()) {
            mp.pause();
            play.setText("\u25B6");
        } else {
            mp.start();
            play.setText("\u275A\u275A");
            handler.post(tick);
        }
    }

    void release() {
        handler.removeCallbacks(tick);
        if (mp != null) {
            try { mp.release(); } catch (Exception ignored) { }
            mp = null;
        }
        prepared = false;
        play.setText("\u25B6");
        seek.setProgress(0);
        time.setText("0:00 / 0:00");
    }

    void pause() {
        if (mp != null && prepared && mp.isPlaying()) { mp.pause(); play.setText("\u25B6"); }
    }

    private static String fmt(int ms) {
        int s = ms / 1000;
        return String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }
}
