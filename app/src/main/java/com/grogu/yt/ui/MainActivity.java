package com.grogu.yt.ui;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.grogu.yt.BuildConfig;
import com.grogu.yt.R;
import com.grogu.yt.audio.Codecs;
import com.grogu.yt.audio.Format;
import com.grogu.yt.core.Engine;
import com.grogu.yt.core.LibraryStore;
import com.grogu.yt.core.LibraryTrack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity implements ConvertService.Listener {
    static final int PAPER = 0xFFF3EFDF, CARD = 0xFFFFFDF4, INK = 0xFF171916, GREEN = 0xFFB8E85A,
            GREEN_DARK = 0xFF40582B, PINK = 0xFFED3F91, ORANGE = 0xFFF27B32, BLUE = 0xFF65A5C5,
            DANGER = 0xFFC43B2E, PLACEHOLDER = 0xFF888B82, WHITE = 0xFFFFFFFF;
    private static final int REQ_WRITE = 77, REQ_NOTIF = 78;

    private float d;
    private boolean small;
    private EditText url;
    private Brutal inputBox, goBtn, dlBtn, cancelBtn;
    private TextView goLabel, statusEl, filename, hint;
    private LinearLayout result, formatRow, bitrateRow;
    private MiniPlayer preview;
    private Format format = Format.MP3;
    private int bitrate = Format.BEST_VBR;
    private final List<Brutal> formatChips = new ArrayList<Brutal>(), bitrateChips = new ArrayList<Brutal>();
    private File audioFile; private String audioName, audioMime;
    private boolean opusEncode;
    private FrameLayout screen;
    private MediaPlayer player;
    private LibraryTrack playing;
    private TextView playerTitle, playerArtist, playerPlay, playerTime;
    private SeekBar playerSeek;
    private boolean shuffle, repeat;
    private final List<LibraryTrack> queue = new ArrayList<LibraryTrack>();
    private final Handler playerHandler = new Handler(Looper.getMainLooper());
    private final Runnable playerTick = new Runnable() {
        public void run() {
            if (player != null) {
                try {
                    if (playerSeek != null) playerSeek.setProgress(player.getCurrentPosition());
                    if (playerTime != null) playerTime.setText(formatTime(player.getCurrentPosition()));
                    if (player.isPlaying()) playerHandler.postDelayed(this, 500);
                } catch (IllegalStateException ignored) { }
            }
        }
    };

    private int dp(float v) { return Math.round(v * d); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        Log.i("Gogrut", "NewPipeExtractor " + BuildConfig.EXTRACTOR_VERSION);
        d = getResources().getDisplayMetrics().density;
        small = getResources().getConfiguration().screenWidthDp <= 520;
        opusEncode = Codecs.canEncodeOpus();
        getWindow().setStatusBarColor(0xFFD7EF9B);
        if (Build.VERSION.SDK_INT >= 23) getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);

        screen = new FrameLayout(this);
        screen.setBackground(new DotBackground(PAPER, INK, d));
        setContentView(screen);
        showLibraryOrDownloader();
        handleShare(getIntent());
    }

    @Override protected void onStart() { super.onStart(); ConvertService.setListener(this); }
    @Override protected void onStop() { super.onStop(); ConvertService.setListener(null); if (preview != null) preview.pause(); }
    @Override protected void onDestroy() { super.onDestroy(); if (preview != null) preview.release(); releasePlayer(); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); handleShare(i); }

    private void handleShare(Intent i) {
        if (i == null || !Intent.ACTION_SEND.equals(i.getAction())) return;
        String t = i.getStringExtra(Intent.EXTRA_TEXT);
        if (t == null) return;
        Matcher m = Pattern.compile("https?://\\S+").matcher(t);
        url.setText(m.find() ? m.group() : t.trim());
    }

    // ------------------------------------------------------------------ layout

    private void showLibraryOrDownloader() {
        if (LibraryStore.list(this).isEmpty()) showDownloader(); else showLibrary();
    }

    private void showDownloader() {
        screen.removeAllViews();
        LinearLayout shell = shell("GET AUDIO", false);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        int pad = small ? 18 : 24;
        LinearLayout holder = new LinearLayout(this);
        holder.setGravity(Gravity.CENTER);
        holder.setPadding(dp(pad), dp(pad), dp(Math.max(0, pad - 14)), dp(pad));
        int faceW = Math.min(dp(480), getResources().getDisplayMetrics().widthPixels - dp(pad) * 2);
        holder.addView(buildCard(), new LinearLayout.LayoutParams(faceW + dp(14), -2));
        scroll.addView(holder, new FrameLayout.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        screen.addView(shell);
        selectFormat(Format.MP3);
    }

    private void showLibrary() {
        screen.removeAllViews();
        LinearLayout shell = shell("LIBRARY", true);
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(18), dp(18), dp(18), dp(24));
        List<LibraryTrack> tracks = LibraryStore.list(this);
        queue.clear(); queue.addAll(tracks);
        TextView heading = text("YOUR MUSIC", 27, INK, "sans-serif-black", Typeface.NORMAL);
        heading.setLetterSpacing(-0.04f);
        list.addView(heading, lp(-1, -2, 0, 0, 0, 2));
        TextView sub = text(tracks.size() + (tracks.size() == 1 ? " SONG" : " SONGS") + "  /  READY OFFLINE", 11, GREEN_DARK, "sans-serif", Typeface.BOLD);
        sub.setLetterSpacing(0.07f); list.addView(sub, lp(-1, -2, 0, 0, 0, 16));
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL);
        Brutal playAll = action("PLAY ALL", GREEN, INK);
        playAll.setOnClickListener(v -> { if (!tracks.isEmpty()) playTrack(tracks.get(0)); });
        controls.addView(playAll, new LinearLayout.LayoutParams(0, dp(46), 1));
        Brutal shuffleBtn = action(shuffle ? "SHUFFLE ON" : "SHUFFLE", ORANGE, INK);
        shuffleBtn.setOnClickListener(v -> { shuffle = !shuffle; showLibrary(); });
        LinearLayout.LayoutParams sh = new LinearLayout.LayoutParams(0, dp(46), 1); sh.leftMargin = dp(10); controls.addView(shuffleBtn, sh);
        list.addView(controls, lp(-1, 54, 0, 0, 0, 14));
        for (final LibraryTrack track : tracks) list.addView(trackRow(track));
        if (tracks.isEmpty()) {
            TextView empty = text("Your downloaded songs will show up here.\nPaste a YouTube link to get started.", 15, INK, "sans-serif", Typeface.BOLD);
            empty.setGravity(Gravity.CENTER); empty.setPadding(dp(10), dp(50), dp(10), dp(50));
            list.addView(empty, lp(-1, -2, 0, 20, 0, 0));
        }
        if (playing != null) list.addView(buildPlayerPanel(), lp(-1, -2, 0, 22, 0, 0));
        scroll.addView(list, new FrameLayout.LayoutParams(-1, -2));
        shell.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        screen.addView(shell);
    }

    private LinearLayout shell(String selected, boolean library) {
        LinearLayout shell = new LinearLayout(this); shell.setOrientation(LinearLayout.VERTICAL);
        LinearLayout nav = new LinearLayout(this); nav.setPadding(dp(16), dp(14), dp(16), dp(6));
        Brutal lib = action("LIBRARY", library ? GREEN : CARD, INK);
        lib.setOnClickListener(v -> showLibrary()); nav.addView(lib, new LinearLayout.LayoutParams(0, dp(44), 1));
        Brutal get = action("GET AUDIO", library ? CARD : GREEN, INK);
        get.setOnClickListener(v -> showDownloader()); LinearLayout.LayoutParams gp = new LinearLayout.LayoutParams(0, dp(44), 1); gp.leftMargin = dp(10); nav.addView(get, gp);
        shell.addView(nav, new LinearLayout.LayoutParams(-1, dp(64)));
        return shell;
    }

    private Brutal action(String title, int face, int ink) {
        Brutal b = new Brutal(this, face, ink, 3).shadows(new float[]{4}, new int[]{INK}).pressEffect(true);
        TextView t = text(title, 11.5f, ink, "sans-serif", Typeface.BOLD); t.setGravity(Gravity.CENTER); t.setLetterSpacing(0.04f);
        b.addView(t, new FrameLayout.LayoutParams(-1, -1)); return b;
    }

    private View trackRow(final LibraryTrack track) {
        Brutal row = new Brutal(this, CARD, INK, 2).shadows(new float[]{3}, new int[]{PINK}).pressEffect(true);
        LinearLayout inside = new LinearLayout(this); inside.setGravity(Gravity.CENTER_VERTICAL); inside.setPadding(dp(12), dp(8), dp(8), dp(8));
        ImageView art = new ImageView(this);
        art.setImageResource(Math.abs(track.file.getName().hashCode()) % 2 == 0 ? R.drawable.p1 : R.drawable.p2);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        inside.addView(art, new LinearLayout.LayoutParams(dp(40), dp(48)));
        LinearLayout words = new LinearLayout(this); words.setOrientation(LinearLayout.VERTICAL); words.setPadding(dp(10), 0, dp(6), 0);
        TextView title = text(track.title, 14, INK, "sans-serif", Typeface.BOLD); title.setSingleLine(true); title.setEllipsize(TextUtils.TruncateAt.END);
        TextView artist = text(track.subtitle(), 11, GREEN_DARK, "sans-serif", Typeface.BOLD); artist.setSingleLine(true); artist.setEllipsize(TextUtils.TruncateAt.END);
        words.addView(title, new LinearLayout.LayoutParams(-1, 0, 1)); words.addView(artist, new LinearLayout.LayoutParams(-1, 0, 1));
        inside.addView(words, new LinearLayout.LayoutParams(0, dp(52), 1));
        TextView more = text("⋮", 27, INK, "sans-serif", Typeface.BOLD); more.setGravity(Gravity.CENTER); inside.addView(more, new LinearLayout.LayoutParams(dp(32), dp(48)));
        row.addView(inside, new FrameLayout.LayoutParams(-1, -1));
        row.setOnClickListener(v -> playTrack(track));
        row.setOnLongClickListener(v -> { LibraryStore.remove(track); if (playing == track) { releasePlayer(); playing = null; } showLibrary(); Toast.makeText(this, "Removed from library", Toast.LENGTH_SHORT).show(); return true; });
        return row;
    }

    private View buildPlayerPanel() {
        Brutal panel = new Brutal(this, GREEN, INK, 3).shadows(new float[]{5}, new int[]{INK});
        LinearLayout box = new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(dp(14), dp(12), dp(14), dp(10));
        playerTitle = text(playing.title, 16, INK, "sans-serif-black", Typeface.NORMAL); playerTitle.setSingleLine(true); playerTitle.setEllipsize(TextUtils.TruncateAt.END);
        playerArtist = text(playing.subtitle(), 11, GREEN_DARK, "sans-serif", Typeface.BOLD); box.addView(playerTitle); box.addView(playerArtist, lp(-1, -2, 0, 2, 0, 5));
        LinearLayout controls = new LinearLayout(this); controls.setGravity(Gravity.CENTER_VERTICAL);
        TextView prev = text("|◀", 18, INK, "sans-serif", Typeface.BOLD); prev.setGravity(Gravity.CENTER); prev.setOnClickListener(v -> step(-1)); controls.addView(prev, new LinearLayout.LayoutParams(dp(42), dp(38)));
        playerPlay = text(player != null && player.isPlaying() ? "❚❚" : "▶", 22, INK, "sans-serif", Typeface.BOLD); playerPlay.setGravity(Gravity.CENTER); playerPlay.setOnClickListener(v -> togglePlayer()); controls.addView(playerPlay, new LinearLayout.LayoutParams(dp(50), dp(38)));
        TextView next = text("▶|", 18, INK, "sans-serif", Typeface.BOLD); next.setGravity(Gravity.CENTER); next.setOnClickListener(v -> step(1)); controls.addView(next, new LinearLayout.LayoutParams(dp(42), dp(38)));
        playerSeek = new SeekBar(this); if (player != null) { try { playerSeek.setMax(player.getDuration()); } catch (IllegalStateException ignored) {} } controls.addView(playerSeek, new LinearLayout.LayoutParams(0, -2, 1));
        playerTime = text("0:00", 10, INK, "sans-serif", Typeface.BOLD); controls.addView(playerTime, new LinearLayout.LayoutParams(dp(42), -2)); box.addView(controls);
        TextView modes = text((shuffle ? "SHUFFLE ON" : "SHUFFLE OFF") + "   /   " + (repeat ? "REPEAT ON" : "REPEAT OFF"), 10, INK, "sans-serif", Typeface.BOLD);
        modes.setGravity(Gravity.RIGHT); modes.setOnClickListener(v -> { repeat = !repeat; showLibrary(); }); box.addView(modes, lp(-1, -2, 0, 3, 0, 0));
        panel.addView(box, new FrameLayout.LayoutParams(-1, -2));
        playerSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() { public void onProgressChanged(SeekBar s, int p, boolean u) { if (u && player != null) player.seekTo(p); } public void onStartTrackingTouch(SeekBar s) {} public void onStopTrackingTouch(SeekBar s) {} });
        return panel;
    }

    private void playTrack(LibraryTrack track) {
        releasePlayer(); playing = track;
        try { player = new MediaPlayer(); player.setDataSource(track.file.getAbsolutePath()); player.setOnPreparedListener(mp -> { mp.start(); playerHandler.post(playerTick); showLibrary(); }); player.setOnCompletionListener(mp -> step(1)); player.prepareAsync(); }
        catch (Exception e) { Toast.makeText(this, "Couldn't play this file", Toast.LENGTH_SHORT).show(); releasePlayer(); }
    }

    private void togglePlayer() { if (player == null) return; if (player.isPlaying()) player.pause(); else { player.start(); playerHandler.post(playerTick); } showLibrary(); }
    private void step(int direction) {
        if (queue.isEmpty()) return;
        int i = playing == null ? -1 : queue.indexOf(playing); if (shuffle) i = (int) (Math.random() * queue.size()); else i = (i + direction + queue.size()) % queue.size();
        if (repeat && direction > 0) i = queue.indexOf(playing);
        playTrack(queue.get(Math.max(0, i)));
    }
    private void releasePlayer() { playerHandler.removeCallbacks(playerTick); if (player != null) { try { player.release(); } catch (Exception ignored) {} player = null; } }
    private static String formatTime(int ms) { int seconds = Math.max(0, ms / 1000); return String.format(java.util.Locale.US, "%d:%02d", seconds / 60, seconds % 60); }

    private Brutal buildCard() {
        Brutal card = new Brutal(this, CARD, INK, 3).shadows(new float[]{14, 8}, new int[]{INK, PINK}).clipToFace(true);
        int art = small ? 85 : 105;
        Brutal a1 = art(R.drawable.p2);
        FrameLayout.LayoutParams p1 = new FrameLayout.LayoutParams(dp(art + 5), dp(art + 5));
        p1.gravity = Gravity.TOP | Gravity.RIGHT; p1.topMargin = dp(-24); p1.rightMargin = dp(-25);
        a1.setRotation(7); card.addView(a1, p1);
        Brutal a2 = art(R.drawable.p1);
        FrameLayout.LayoutParams p2 = new FrameLayout.LayoutParams(dp(art + 5), dp(art + 5));
        p2.gravity = Gravity.TOP | Gravity.LEFT; p2.topMargin = dp(small ? 105 : 92); p2.leftMargin = dp(small ? -32 : -38);
        a2.setRotation(-8); card.addView(a2, p2);

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        int cp = small ? 25 : 30;
        c.setPadding(dp(cp), dp(cp), dp(cp), dp(cp));
        card.addView(c, new FrameLayout.LayoutParams(-1, -2));
        c.addView(buildHeader(), lp(-1, -2, 0, 0, 0, 20));

        c.addView(label("YOUTUBE LINK"), lp(-2, -2, 0, 0, 0, 7));
        inputBox = new Brutal(this, WHITE, INK, 3).shadows(new float[]{5}, new int[]{BLUE});
        url = new EditText(this);
        url.setBackground(null); url.setHint("Paste a link"); url.setHintTextColor(PLACEHOLDER); url.setTextColor(INK);
        url.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 15.2f);
        url.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        url.setImeOptions(EditorInfo.IME_ACTION_GO);
        url.setPadding(dp(14), 0, dp(14), 0);
        url.setEllipsize(TextUtils.TruncateAt.END);
        url.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            public boolean onEditorAction(TextView v, int a, KeyEvent e) {
                if (a == EditorInfo.IME_ACTION_GO || (e != null && e.getKeyCode() == KeyEvent.KEYCODE_ENTER)) { convert(); return true; }
                return false;
            }
        });
        url.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            public void onFocusChange(View v, boolean f) {
                inputBox.setBorderColor(f ? PINK : INK); inputBox.setShadowColors(new int[]{f ? PINK : BLUE});
            }
        });
        inputBox.addView(url, new FrameLayout.LayoutParams(-1, dp(50)));
        c.addView(inputBox, new LinearLayout.LayoutParams(-1, dp(55)));

        c.addView(label("FORMAT"), lp(-2, -2, 0, 18, 0, 7));
        formatRow = chipRow();
        for (final Format f : Format.values()) {
            Brutal chip = chip(f.label);
            chip.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { selectFormat(f); } });
            formatChips.add(chip);
            formatRow.addView(chip, chipLp());
        }
        c.addView(formatRow, new LinearLayout.LayoutParams(-1, dp(42)));

        c.addView(label("BITRATE (KBPS)"), lp(-2, -2, 0, 16, 0, 7));
        bitrateRow = chipRow();
        c.addView(bitrateRow, new LinearLayout.LayoutParams(-1, dp(42)));
        hint = text("", 11.5f, GREEN_DARK, "sans-serif", Typeface.BOLD);
        c.addView(hint, lp(-1, -2, 0, 10, 0, 0));

        goBtn = new Brutal(this, GREEN, INK, 3).shadows(new float[]{5}, new int[]{INK}).pressEffect(true);
        goLabel = text("CONVERT", 13.6f, INK, "sans-serif", Typeface.BOLD);
        goLabel.setLetterSpacing(0.04f); goLabel.setGravity(Gravity.CENTER);
        goBtn.addView(goLabel, new FrameLayout.LayoutParams(-1, -1));
        goBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { convert(); } });
        c.addView(goBtn, lp(-1, 55, 0, 15, 0, 0));

        cancelBtn = new Brutal(this, CARD, INK, 3).shadows(new float[]{4}, new int[]{BLUE}).pressEffect(true);
        TextView cl = text("CANCEL", 12, INK, "sans-serif", Typeface.BOLD);
        cl.setGravity(Gravity.CENTER);
        cancelBtn.addView(cl, new FrameLayout.LayoutParams(-1, -1));
        cancelBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { ConvertService.cancel(MainActivity.this); } });
        cancelBtn.setVisibility(View.GONE);
        c.addView(cancelBtn, lp(-1, 44, 0, 12, 0, 0));

        statusEl = text("", 12.8f, GREEN_DARK, "sans-serif", Typeface.BOLD);
        statusEl.setMinHeight(dp(20));
        c.addView(statusEl, lp(-1, -2, 0, 13, 0, 0));

        result = new LinearLayout(this);
        result.setOrientation(LinearLayout.VERTICAL);
        result.setVisibility(View.GONE);
        result.addView(new DashedLine(this, INK, dp(3)), new LinearLayout.LayoutParams(-1, dp(3)));
        filename = text("", 12.5f, INK, "sans-serif", Typeface.BOLD);
        filename.setSingleLine(true); filename.setEllipsize(TextUtils.TruncateAt.END);
        filename.setPadding(dp(10), dp(8), dp(10), dp(8));
        GradientDrawable chipBg = new GradientDrawable(); chipBg.setColor(GREEN); chipBg.setStroke(dp(2), INK);
        filename.setBackground(chipBg);
        result.addView(filename, lp(-1, -2, 0, 20, 0, 0));
        preview = new MiniPlayer(this);
        result.addView(preview, lp(-1, 42, 0, 11, 0, 0));
        dlBtn = new Brutal(this, PINK, INK, 3).shadows(new float[]{5}, new int[]{INK}).pressEffect(true);
        TextView dl = text("DOWNLOAD", 13.6f, WHITE, "sans-serif", Typeface.BOLD);
        dl.setLetterSpacing(0.04f); dl.setGravity(Gravity.CENTER);
        dlBtn.addView(dl, new FrameLayout.LayoutParams(-1, -1));
        dlBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { download(); } });
        result.addView(dlBtn, lp(-1, 55, 0, 15, 0, 0));
        c.addView(result, lp(-1, -2, 0, 24, 0, 0));
        return card;
    }

    private View buildHeader() {
        FrameLayout h = new FrameLayout(this);
        h.setMinimumHeight(dp(150));
        h.setPadding(dp(20), 0, 0, 0);
        TextView k = text("\u97F3", 48, PINK, "serif", Typeface.BOLD);
        k.setRotation(-8);
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(-2, -2);
        kp.gravity = Gravity.TOP | Gravity.RIGHT; kp.topMargin = dp(3); kp.rightMargin = dp(small ? 85 : 105);
        h.addView(k, kp);
        TextView s = text("\u2605", 32, ORANGE, "sans-serif", Typeface.NORMAL);
        s.setRotation(12);
        FrameLayout.LayoutParams sp = new FrameLayout.LayoutParams(-2, -2);
        sp.gravity = Gravity.BOTTOM | Gravity.RIGHT; sp.bottomMargin = dp(5); sp.rightMargin = dp(25);
        h.addView(s, sp);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(0, dp(40), 0, 0);
        Brutal h1 = new Brutal(this, GREEN, INK, 3).shadows(new float[]{5}, new int[]{INK});
        float size = Math.min(48f, Math.max(32f, getResources().getConfiguration().screenWidthDp * 0.09f));
        TextView tv = text("GOGRUT\n", size, INK, "sans-serif-black", Typeface.NORMAL);
        tv.setLetterSpacing(-0.07f); tv.setLineSpacing(0, 0.86f); tv.setIncludeFontPadding(false);
        tv.setPadding(dp(9), dp(8), dp(9), dp(8));
        h1.addView(tv, new FrameLayout.LayoutParams(-2, -2));
        h1.setRotation(-2);
        t.addView(h1, new LinearLayout.LayoutParams(-2, -2));      // ("LOCAL AUDIO DECK" subtitle removed)
        h.addView(t, new FrameLayout.LayoutParams(-2, -2));
        return h;
    }

    // ------------------------------------------------------------------ chips

    private LinearLayout chipRow() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        return r;
    }

    private LinearLayout.LayoutParams chipLp() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -1, 1f);
        p.rightMargin = dp(4);
        return p;
    }

    private Brutal chip(String text) {
        Brutal b = new Brutal(this, WHITE, INK, 2).shadows(new float[]{3}, new int[]{INK}).pressEffect(true);
        TextView t = text(text, 10.5f, INK, "sans-serif", Typeface.BOLD);
        t.setGravity(Gravity.CENTER); t.setSingleLine(true);
        b.addView(t, new FrameLayout.LayoutParams(-1, -1));
        b.setTag(t);
        return b;
    }

    private void selectFormat(Format f) {
        format = f;
        Format[] all = Format.values();
        for (int i = 0; i < formatChips.size(); i++) formatChips.get(i).setFace(all[i] == f ? GREEN : WHITE);
        bitrateRow.removeAllViews();
        bitrateChips.clear();
        final List<Integer> opts = new ArrayList<Integer>();
        for (int b : f.bitrates) {
            if (f == Format.OPUS && b != Format.ORIGINAL && !opusEncode) continue;   // no Opus encoder on this phone
            opts.add(b);
        }
        if (!f.supports(bitrate) || !opts.contains(bitrate)) bitrate = opts.get(0);
        for (final int b : opts) {
            Brutal c = chip(Format.bitrateLabel(b));
            c.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { selectBitrate(b); } });
            bitrateChips.add(c);
            bitrateRow.addView(c, chipLp());
        }
        selectBitrate(bitrate);
    }

    private void selectBitrate(int b) {
        bitrate = b;
        List<Integer> opts = new ArrayList<Integer>();
        for (int x : format.bitrates) if (!(format == Format.OPUS && x != Format.ORIGINAL && !opusEncode)) opts.add(x);
        for (int i = 0; i < bitrateChips.size(); i++) bitrateChips.get(i).setFace(opts.get(i) == b ? GREEN : WHITE);
        if (b == Format.ORIGINAL) hint.setText("ORIG = YouTube's own " + (format == Format.OPUS ? "Opus" : "AAC") + " audio, no re-encode. Fastest, best quality.");
        else if (b == Format.BEST_VBR) hint.setText("VBR = best-quality variable bitrate (LAME V0).");
        else hint.setText("Re-encodes at " + b + " kbps. Going above the source (~128-160) adds size, not quality.");
    }

    // ------------------------------------------------------------------ actions

    private void convert() {
        if (ConvertService.state.running) return;
        String link = url.getText().toString().trim();
        if (link.length() == 0) { setStatus("Paste a link first.", true); return; }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF);   // optional; conversion still runs
        }
        setStatus("", false);
        result.setVisibility(View.GONE);
        preview.release();
        ConvertService.start(this, link, format, bitrate);
    }

    @Override public void onState(ConvertService.State s) {
        if (statusEl == null && s.result != null) showDownloader();
        if (statusEl == null) return;
        goBtn.setEnabled(!s.running);
        goLabel.setText(s.running ? "Working…" : "CONVERT");
        cancelBtn.setVisibility(s.running ? View.VISIBLE : View.GONE);
        if (s.running) {
            setStatus(s.text + (s.percent > 0 ? "  " + s.percent + "%" : ""), false);
        } else if (s.result != null && s.result.file != audioFile) {
            audioFile = s.result.file; audioName = s.result.name; audioMime = s.result.mime;
            if (audioFile.getName().endsWith(".opus") || audioFile.getName().endsWith(".aac")) preview.load(audioFile); else preview.load(audioFile);
            filename.setText(audioName);
            result.setVisibility(View.VISIBLE);
            setStatus("", false);
            url.setText("");
        } else if (s.error != null) {
            setStatus(s.error, true);
        }
    }

    private void setStatus(String m, boolean err) { statusEl.setTextColor(err ? DANGER : GREEN_DARK); statusEl.setText(m); }

    private void download() {
        if (audioFile == null || !audioFile.exists()) { setStatus("Nothing to download yet.", true); return; }
        if (Saver.needsLegacyPermission() && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_WRITE);
            return;
        }
        try {
            String where = Saver.save(this, audioFile, audioName, audioMime);
            setStatus("Saved to " + where, false);
            Toast.makeText(this, "Saved to " + where, Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            setStatus("Couldn't save: " + e.getMessage(), true);
        }
    }

    @Override public void onRequestPermissionsResult(int code, String[] p, int[] r) {
        if (code == REQ_WRITE && r.length > 0 && r[0] == PackageManager.PERMISSION_GRANTED) download();
        else if (code == REQ_WRITE) setStatus("Storage permission is needed to save the file.", true);
    }

    // ------------------------------------------------------------------ helpers

    private Brutal art(int res) {
        Brutal b = new Brutal(this, WHITE, INK, 3).shadows(new float[]{5}, new int[]{INK});
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(-1, -1);
        p.setMargins(dp(3), dp(3), dp(3), dp(3));
        b.addView(iv, p);
        return b;
    }

    private TextView label(String s) {
        TextView t = text(s, 11.5f, INK, "sans-serif", Typeface.BOLD);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private TextView text(String s, float sp, int color, String family, int style) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp); t.setTextColor(color);
        t.setTypeface(Typeface.create(family, style));
        return t;
    }

    private LinearLayout.LayoutParams lp(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w < 0 ? w : dp(w), h < 0 ? h : dp(h));
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }
}
