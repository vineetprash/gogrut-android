package com.grogu.yt.ui;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.grogu.yt.audio.Format;
import com.grogu.yt.core.Cancel;
import com.grogu.yt.core.Engine;

/**
 * Foreground service: keeps the download/convert alive when the app is backgrounded, shows a
 * progress notification with a Cancel action. The activity just observes {@link #state}.
 */
public class ConvertService extends Service {
    public static final String ACTION_START = "com.grogu.yt.START", ACTION_CANCEL = "com.grogu.yt.CANCEL";
    static final String CHANNEL = "convert";
    static final int NOTIF_ID = 1;

    public static final class State {
        public final boolean running;
        public final String text;
        public final int percent;
        public final Engine.Result result;
        public final String error;
        State(boolean running, String text, int percent, Engine.Result result, String error) {
            this.running = running; this.text = text; this.percent = percent; this.result = result; this.error = error;
        }
    }

    public interface Listener { void onState(State s); }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    public static volatile State state = new State(false, "", 0, null, null);
    private static volatile Listener listener;
    private static volatile boolean cancelled;
    private static volatile boolean busy;

    public static void setListener(Listener l) { listener = l; if (l != null) l.onState(state); }

    private static void publish(final State s) {
        state = s;
        MAIN.post(new Runnable() { public void run() { Listener l = listener; if (l != null) l.onState(s); } });
    }

    public static void start(Context c, String url, Format f, int bitrate) {
        Intent i = new Intent(c, ConvertService.class).setAction(ACTION_START)
                .putExtra("url", url).putExtra("fmt", f.name()).putExtra("br", bitrate);
        if (Build.VERSION.SDK_INT >= 26) c.startForegroundService(i); else c.startService(i);
    }

    public static void cancel(Context c) { cancelled = true; }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) { cancelled = true; return START_NOT_STICKY; }
        if (intent == null || !ACTION_START.equals(intent.getAction())) { stopSelf(); return START_NOT_STICKY; }
        if (busy) return START_NOT_STICKY;
        busy = true;
        cancelled = false;
        final String url = intent.getStringExtra("url");
        final Format fmt = Format.valueOf(intent.getStringExtra("fmt"));
        final int br = intent.getIntExtra("br", fmt.defaultBitrate());

        startFg(notification("Starting…", 0));
        publish(new State(true, "Starting…", 0, null, null));

        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            public void run() {
                Engine.Result r = null;
                String err = null;
                try {
                    r = Engine.convert(app, url, fmt, br, new Engine.Listener() {
                        long last;
                        public void status(String t, int p) {
                            long now = System.currentTimeMillis();
                            if (now - last < 250 && p < 99) return;
                            last = now;
                            publish(new State(true, t, p, null, null));
                            postNotification(notification(t, p));
                        }
                    }, new Cancel() { public boolean isCancelled() { return cancelled; } });
                } catch (Engine.Failure f) {
                    err = f.getMessage();
                } catch (Throwable t) {
                    err = "Conversion failed.";
                }
                busy = false;
                publish(new State(false, "", 0, r, err));
                stopForeground(true);
                if (r != null) postNotification(done("Ready: " + r.name));
                stopSelf();
            }
        }).start();
        return START_NOT_STICKY;
    }

    private void startFg(Notification n) {
        if (Build.VERSION.SDK_INT >= 29) startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        else startForeground(NOTIF_ID, n);
    }

    private void postNotification(Notification n) {
        ((NotificationManager) getSystemService(NOTIFICATION_SERVICE)).notify(NOTIF_ID, n);
    }

    private Notification.Builder builder() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Conversions", NotificationManager.IMPORTANCE_LOW));
            return new Notification.Builder(this, CHANNEL);
        }
        return new Notification.Builder(this);
    }

    private Notification notification(String text, int pct) {
        Intent cancel = new Intent(this, ConvertService.class).setAction(ACTION_CANCEL);
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getService(this, 0, cancel, fl);
        Notification.Builder b = builder().setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("Grogut").setContentText(text).setOngoing(true)
                .setProgress(100, pct, pct <= 0)
                .setContentIntent(PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), fl));
        if (Build.VERSION.SDK_INT >= 23) b.addAction(new Notification.Action.Builder(null, "Cancel", pi).build());
        return b.build();
    }

    private Notification done(String text) {
        int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
        return builder().setSmallIcon(android.R.drawable.stat_sys_download_done).setContentTitle("Grogut")
                .setContentText(text).setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(this, 1, new Intent(this, MainActivity.class), fl)).build();
    }
}
