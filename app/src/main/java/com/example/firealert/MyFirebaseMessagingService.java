package com.example.firealert;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

public class MyFirebaseMessagingService extends FirebaseMessagingService {

    private static final String TAG = "FCMService";
    private static final String CHANNEL_ID = "FIRE_ALERT_CHANNEL_ID";
    private static final String CHANNEL_NAME = "Peringatan Kebakaran";

    /**
     * Dipanggil saat mendapatkan token registrasi FCM baru (atau token diperbarui).
     * Token ini harus dikirim ke backend Anda (Google Apps Script / Database)
     * agar server tahu ke mana harus mengirim notifikasi.
     */
    @Override
    public void onNewToken(String token) {

    }

    /**
     * Dipanggil saat menerima pesan FCM dari server.
     * Pesan dari Apps Script harus dikirim sebagai 'Data Message'.
     */
    @Override
    public void onMessageReceived(RemoteMessage remoteMessage) {
        Log.d(TAG, "Pesan Diterima Dari: " + remoteMessage.getFrom());

        // Pastikan pesan memiliki payload data (bukan hanya notifikasi)
        if (remoteMessage.getData().size() > 0) {
            Log.d(TAG, "Payload Data: " + remoteMessage.getData());

            // 1. Ambil data penting dari payload JSON
            String title = remoteMessage.getData().get("title");
            String body = remoteMessage.getData().get("body");
            String latitudeStr = remoteMessage.getData().get("latitude");
            String longitudeStr = remoteMessage.getData().get("longitude");

            if (title == null || title.isEmpty()) {
                title = "Peringatan Kebakaran Baru";
            }
            if (body == null || body.isEmpty()) {
                body = "Terdeteksi lokasi tidak aman. Tekan untuk melihat.";
            }

            // 2. Tampilkan Notifikasi di System Bar
            if (latitudeStr != null && longitudeStr != null) {
                try {
                    double latitude = Double.parseDouble(latitudeStr);
                    double longitude = Double.parseDouble(longitudeStr);

                    sendNotification(title, body, latitude, longitude);

                } catch (NumberFormatException e) {
                    Log.e(TAG, "Gagal mengurai koordinat: " + e.getMessage());
                    // Tetap tampilkan notifikasi tanpa data koordinat jika parsing gagal
                    sendNotification(title, body, 0.0, 0.0);
                }
            } else {
                // Tampilkan notifikasi dasar jika koordinat tidak ada
                sendNotification(title, body, 0.0, 0.0);
            }
        }
    }

    /**
     * Membuat dan menampilkan notifikasi di System Bar Android.
     * Intent notifikasi membawa koordinat untuk navigasi peta.
     */
    private void sendNotification(String title, String body, double lat, double lon) {

        // 1. Setup Intent untuk MainActivity
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Tambahkan data koordinat dan flag aksi khusus ke Intent
        if (lat != 0.0 && lon != 0.0) {
            intent.putExtra("ACTION_GOTO_POINT", true);
            intent.putExtra("LATITUDE", lat);
            intent.putExtra("LONGITUDE", lon);
        }

        // 2. Buat PendingIntent
        // FLAG_IMMUTABLE wajib untuk Android 12 (S) ke atas
        int flags = PendingIntent.FLAG_ONE_SHOT | PendingIntent.FLAG_IMMUTABLE;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
            // Untuk Android M ke bawah
            flags = PendingIntent.FLAG_ONE_SHOT;
        }

        PendingIntent pendingIntent = PendingIntent.getActivity(this,
                0 /* Request code unik */, intent, flags);

        // 3. Bangun Notifikasi
        NotificationCompat.Builder notificationBuilder =
                new NotificationCompat.Builder(this, CHANNEL_ID)
                        // TODO: Ganti R.drawable.ic_notification dengan ikon notifikasi Anda
                        .setSmallIcon(R.drawable.icon_logout)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true) // Notifikasi hilang setelah diklik
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH); // Prioritas tinggi untuk peringatan

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);

        // 4. Buat Channel Notifikasi (Wajib untuk Android O ke atas)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH); // Penting agar muncul sebagai Heads-up
            notificationManager.createNotificationChannel(channel);
        }

        // 5. Tampilkan Notifikasi
        notificationManager.notify(0 /* ID notifikasi unik */, notificationBuilder.build());
    }

}
