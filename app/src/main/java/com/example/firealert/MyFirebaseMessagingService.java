package com.example.firealert;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.messaging.FirebaseMessagingService;
import com.google.firebase.messaging.RemoteMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

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
            String name = remoteMessage.getData().get("name");
            String title = name;
            String body = remoteMessage.getData().get("body");
            String label = remoteMessage.getData().get("label");
            double lpg = Double.parseDouble(remoteMessage.getData().get("lpg"));
            double suhu = Double.parseDouble(remoteMessage.getData().get("suhu"));
            long api = Long.parseLong(remoteMessage.getData().get("api"));

            if (title == null || title.isEmpty()) {
                title = "Peringatan Kebakaran Baru";
            }
            if (body == null || body.isEmpty()) {
                body = "Terdeteksi tidak aman. Tekan untuk melihat.";
            }

            writeNotif(name, label, api, lpg, suhu);

            sendNotification(title, body, name, label);


        }
    }

    /**
     * Membuat dan menampilkan notifikasi di System Bar Android.
     * Intent notifikasi membawa koordinat untuk navigasi peta.
     */
    private void sendNotification(String title, String body, String name, String label) {

        // 1. Setup Intent untuk MainActivity
        Intent intent = new Intent(this, HomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

        // Tambahkan data koordinat dan flag aksi khusus ke Intent
        if (!label.isEmpty()) {
            intent.putExtra("ACTION_GOTO_PLACE", true);
            intent.putExtra("LABEL", label);
            intent.putExtra("NAME", name);
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
                        .setSmallIcon(R.drawable.logo_apk)
                        .setContentTitle(title)
                        .setContentText(body)
                        .setAutoCancel(true) // Notifikasi hilang setelah diklik
                        .setContentIntent(pendingIntent)
                        .setPriority(NotificationCompat.PRIORITY_HIGH); // Prioritas tinggi untuk peringatan

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            Uri soundUri = Uri.parse(
                    "android.resource://" + getApplicationContext().getPackageName() + "/" + R.raw.fire_alarm
            );
            notificationBuilder.setSound(soundUri);
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);


        // 4. Buat Channel Notifikasi (Wajib untuk Android O ke atas)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {

            Uri soundUri = Uri.parse(
                    "android.resource://" + getApplicationContext().getPackageName() + "/" + R.raw.fire_alarm
            );

            // Definisikan Atribut Audio
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_ALARM) // Atau .USAGE_ALARM
                    .build();

            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH); // Penting agar muncul sebagai Heads-up
            channel.setSound(soundUri, audioAttributes);
            channel.enableVibration(true);

            notificationManager.createNotificationChannel(channel);
        }

        // 5. Tampilkan Notifikasi
        notificationManager.notify(new Random().nextInt() /* ID notifikasi unik */, notificationBuilder.build());
    }

    // Simpan riwayat ke Firebase
    private void writeNotif(String name, String label, long flame, double lpg, double suhu) {
        String currentDateTime = new SimpleDateFormat("EEEE, dd MMMM yyyy\nhh:mm:ss a",
                Locale.getDefault()).format(new Date());
        DatabaseReference notifRef = FirebaseDatabase.getInstance().getReference("notifikasi");
        String key = notifRef.push().getKey();

        notifRef.child(key).child("time").setValue(currentDateTime);
        notifRef.child(key).child("flame").setValue(String.valueOf(flame));
        notifRef.child(key).child("suhu").setValue(String.valueOf(suhu));
        notifRef.child(key).child("lpg").setValue(String.valueOf(lpg));
        notifRef.child(key).child("name").setValue(name);
        notifRef.child(key).child("label").setValue(label);

        DatabaseReference sensorRef = FirebaseDatabase.getInstance().getReference("sensors").child(label);

        sensorRef.child("flame").setValue(flame);
        sensorRef.child("suhu").setValue(suhu);
        sensorRef.child("lpg").setValue(lpg);


    }

}
