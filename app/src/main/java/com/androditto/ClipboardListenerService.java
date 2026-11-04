package com.androditto;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.IBinder;
import android.provider.MediaStore;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Keeps Ditto's inbound listener available while the user switches to other apps. */
public final class ClipboardListenerService extends Service {
    private static final String CHANNEL = "androditto_receive";
    private static final int SERVICE_NOTIFICATION = 20;
    private static volatile boolean listenerReady;
    private volatile Thread listenerThread;

    static boolean isReady() { return listenerReady; }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForeground(SERVICE_NOTIFICATION, notification("Ready to receive from your PC"));
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && "stop".equals(intent.getAction())) {
            getSharedPreferences("peer", MODE_PRIVATE).edit().putBoolean("backgroundReceive", false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        String secret = getSharedPreferences("peer", MODE_PRIVATE).getString("password", "");
        if (secret.isEmpty()) {
            getSharedPreferences("peer", MODE_PRIVATE).edit().putBoolean("backgroundReceive", false).apply();
            stopSelf();
            return START_NOT_STICKY;
        }
        Thread current = listenerThread;
        if (current == null || !current.isAlive()) {
            listenerThread = new Thread(() -> listenUntilStopped(secret), "AndroDitto-listener");
            listenerThread.start();
        }
        return START_STICKY;
    }

    private void listenUntilStopped(String secret) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                DittoProtocol.listen(secret, incoming -> {
                    ClipboardManager clipboard = (ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
                    clipboard.setPrimaryClip(ClipData.newPlainText("Ditto", incoming));
                    TransferHistory.add(this, "RECEIVED", "TEXT", "Text from PC");
                    getSystemService(NotificationManager.class).notify(SERVICE_NOTIFICATION + 1,
                            notification("Text copied from PC — paste it in any app"));
                }, this::filesRequested,
                        new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "Ditto"), files -> {
                            if (!files.isEmpty()) {
                                var prefs = getSharedPreferences("peer", MODE_PRIVATE);
                                int visibleCount = 0;
                                Uri latestPublicUri = null;
                                for (File file : files) {
                                    TransferHistory.add(this, "RECEIVED", "FILE", file.getName());
                                    try {
                                        Uri publicUri = saveToPublicDownloads(file);
                                        if (publicUri != null) {
                                            if (!file.delete()) {
                                                getContentResolver().delete(publicUri, null, null);
                                                throw new IOException("Could not clean up the temporary received file");
                                            }
                                            latestPublicUri = publicUri;
                                        }
                                        visibleCount++;
                                    } catch (Exception ignored) { }
                                }
                                var update = prefs.edit();
                                if (latestPublicUri != null) {
                                    update.putString("lastReceivedUri", latestPublicUri.toString())
                                            .remove("lastReceivedPath");
                                } else {
                                    update.putString("lastReceivedPath", files.get(files.size() - 1).getAbsolutePath())
                                            .remove("lastReceivedUri");
                                }
                                update.apply();
                                getSystemService(NotificationManager.class).notify(SERVICE_NOTIFICATION + 1,
                                        notification(visibleCount == files.size()
                                                ? "Received " + files.size() + " file(s) — saved to Download/Ditto"
                                                : "Received " + files.size() + " file(s) in AndroDitto"));
                            }
                        }, () -> listenerReady = true);
            } catch (Exception e) {
                listenerReady = false;
                if (!Thread.currentThread().isInterrupted()) {
                    getSystemService(NotificationManager.class).notify(SERVICE_NOTIFICATION,
                            notification("Reconnecting to Ditto…"));
                    try { Thread.sleep(2000); }
                    catch (InterruptedException stopped) { Thread.currentThread().interrupt(); }
                }
            }
        }
    }

    private List<DittoProtocol.FilePart> filesRequested(List<String> paths) {
        var prefs = getSharedPreferences("peer", MODE_PRIVATE);
        List<DittoProtocol.FilePart> result = new ArrayList<>();
        for (String path : paths) {
            String name = path.replace('\\', '/');
            name = name.substring(name.lastIndexOf('/') + 1);
            String stored = prefs.getString("sharedFile:" + name, null);
            if (stored != null) {
                File file = new File(stored);
                if (file.isFile()) result.add(new DittoProtocol.FilePart(path, file));
            }
        }
        return result;
    }

    private Uri saveToPublicDownloads(File source) throws IOException {
        if (Build.VERSION.SDK_INT < 29) {
            File folder = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Ditto");
            if (!folder.exists() && !folder.mkdirs()) throw new IOException("Could not create Download/Ditto");
            File target = new File(folder, source.getName());
            try (InputStream input = new FileInputStream(source); OutputStream output = new FileOutputStream(target)) {
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            return null;
        }

        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, source.getName());
        values.put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Ditto");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) throw new IOException("Could not create file in Download/Ditto");
        try {
            try (InputStream input = new FileInputStream(source);
                 OutputStream output = getContentResolver().openOutputStream(uri, "w")) {
                if (output == null) throw new IOException("Could not write file in Download/Ditto");
                byte[] buffer = new byte[65536]; int count;
                while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            }
            ContentValues ready = new ContentValues();
            ready.put(MediaStore.Downloads.IS_PENDING, 0);
            getContentResolver().update(uri, ready, null, null);
            return uri;
        } catch (Exception error) {
            getContentResolver().delete(uri, null, null);
            if (error instanceof IOException) throw (IOException)error;
            throw new IOException("Could not save file to Download/Ditto", error);
        }
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager manager = getSystemService(NotificationManager.class);
            manager.createNotificationChannel(new NotificationChannel(CHANNEL, "Ditto connection", NotificationManager.IMPORTANCE_LOW));
        }
    }

    private Notification notification(String message) {
        Intent stop = new Intent(this, ClipboardListenerService.class).setAction("stop");
        PendingIntent stopIntent = PendingIntent.getService(this, 0, stop,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL) : new Notification.Builder(this);
        return builder.setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
                .setContentTitle("AndroDitto")
                .setContentText(message)
                .setOngoing(message.startsWith("Ready") || message.startsWith("Reconnecting"))
                .addAction(android.R.drawable.ic_media_pause, "Stop", stopIntent)
                .build();
    }

    @Override public void onDestroy() {
        listenerReady = false;
        Thread thread = listenerThread;
        if (thread != null) thread.interrupt();
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
