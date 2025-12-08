package com.example.firealert;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.content.pm.PackageManager;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class HomeActivity extends AppCompatActivity {

    private TextView tvDateTime, tvFlame, tvLpg, tvSuhu, tvStatus;
    private ImageView imgStatusIcon;

    private Handler handler = new Handler();
    private Runnable updateTimeRunnable;

    private static final String CHANNEL_ID = "my_channel_id";
    private static final String CHANNEL_NAME = "my_channel_name";

    boolean fireAlarm = false;
    boolean lpgAlarm = false;
    boolean suhuAlarm = false;

    String currentDateTime;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        tvDateTime = findViewById(R.id.tvDateTime);
        tvFlame = findViewById(R.id.tvApiStatus);
        tvLpg = findViewById(R.id.tvGasStatus);
        tvSuhu = findViewById(R.id.tvTemperature);
        tvStatus = findViewById(R.id.tvStatus);
        imgStatusIcon = findViewById(R.id.imgStatusIcon);

        updateTimeRunnable = () -> {
            currentDateTime = new SimpleDateFormat("EEEE, dd MMMM yyyy\nhh:mm:ss a",
                    Locale.getDefault()).format(new Date());
            tvDateTime.setText(currentDateTime);
            handler.postDelayed(updateTimeRunnable, 1000);
        };
        handler.post(updateTimeRunnable);

        fetchSensorData();

        Button btnNotificationHistory = findViewById(R.id.btnNotificationHistory);
        btnNotificationHistory.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, NotificationHistoryActivity.class);
            startActivity(intent);
        });
    }

    // ==============================
    // AMBIL DATA SENSOR DARI FIREBASE
    // ==============================
    private void fetchSensorData() {
        DatabaseReference sensorsRef = FirebaseDatabase.getInstance()
                .getReference("sensors")
                .child("-OEnVfTt23-cM71d8RJd");

        sensorsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {

                    int flame = dataSnapshot.child("flame").getValue(Integer.class);
                    double lpg = dataSnapshot.child("lpg").getValue(Double.class);
                    double suhu = dataSnapshot.child("suhu").getValue(Double.class);

                    // FLAME
                    if (flame == 1) {
                        if (!fireAlarm) {
                            Notifikasi(HomeActivity.this, "Terjadi Kebakaran!", "Api Terdeteksi", "flame");
                            writeNotif("Api Terdeteksi", flame, lpg, suhu);
                        }
                        fireAlarm = true;
                    } else fireAlarm = false;

                    // GAS
                    if (lpg >= 50.0) {
                        if (!lpgAlarm) {
                            Notifikasi(HomeActivity.this, "Terjadi Kebakaran!", "Kadar Gas Melebihi Batas", "lpg");
                            writeNotif("Kadar Gas Melebihi Batas", flame, lpg, suhu);
                        }
                        lpgAlarm = true;
                    } else lpgAlarm = false;

                    // SUHU
                    if (suhu >= 42.0) {
                        if (!suhuAlarm) {
                            Notifikasi(HomeActivity.this, "Terjadi Kebakaran!", "Suhu Melebihi Batas", "suhu");
                            writeNotif("Suhu Melebihi Batas", flame, lpg, suhu);
                        }
                        suhuAlarm = true;
                    } else suhuAlarm = false;

                    // ==============================
                    // UBAH STATUS + GANTI IKON
                    // ==============================
                    if (!fireAlarm && !suhuAlarm && !lpgAlarm) {
                        tvStatus.setText("Kondisi Aman");
                        tvStatus.setTextColor(Color.parseColor("#008F39"));
                        imgStatusIcon.setImageResource(R.drawable.check_green);
                    } else {
                        tvStatus.setText("Terjadi Kebakaran!");
                        tvStatus.setTextColor(Color.parseColor("#BD1111"));
                        imgStatusIcon.setImageResource(R.drawable.warning); // ⛔ WARNING ICON
                    }

                    // Update sekunder
                    tvFlame.setText(flame == 1 ? "Api Terdeteksi" : "Tidak Ada Api");
                    tvLpg.setText("LPG: " + lpg);
                    tvSuhu.setText("Suhu: " + suhu + " °C");
                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    // ==============================
    // NOTIFIKASI
    // ==============================
    public void Notifikasi(Context context, String title, String content, String kondisi) {

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel =
                    new NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(channel);
            builder.setChannelId(CHANNEL_ID);
        }

        if (ActivityCompat.checkSelfPermission(this,
                android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        notificationManager.notify(getNotificationId(kondisi), builder.build());
    }

    private int getNotificationId(String kondisi) {
        switch (kondisi) {
            case "suhu": return 1;
            case "flame": return 2;
            case "lpg": return 3;
            default: return 0;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(updateTimeRunnable);
    }

    // Simpan riwayat ke Firebase
    private void writeNotif(String message, long flame, double lpg, double suhu) {
        DatabaseReference notifRef = FirebaseDatabase.getInstance().getReference("notifikasi");
        String key = notifRef.push().getKey();

        notifRef.child(key).child("time").setValue(currentDateTime);
        notifRef.child(key).child("api").setValue(String.valueOf(flame));
        notifRef.child(key).child("suhu").setValue(String.valueOf(suhu));
        notifRef.child(key).child("asap").setValue(String.valueOf(lpg));
        notifRef.child(key).child("status").setValue(message);
    }
}
