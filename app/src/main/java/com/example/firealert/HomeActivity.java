package com.example.firealert;

import static android.content.ContentValues.TAG;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.widget.Toast;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.messaging.FirebaseMessaging;

import org.json.JSONException;
import org.json.JSONObject;
import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HomeActivity extends AppCompatActivity {

    private MapView map;

    private TextView tvDateTime, tvFlame, tvLpg, tvSuhu, tvStatus, tvLokasi;
    private ImageView imgStatusIcon;

    private Handler handler = new Handler();
    private Runnable updateTimeRunnable;

    private static final String CHANNEL_ID = "my_channel_id";
    private static final String CHANNEL_NAME = "my_channel_name";

    boolean fireAlarm = false;
    boolean lpgAlarm = false;
    boolean suhuAlarm = false;

    String currentDateTime;

    String idHighlight = "";

    Map<String, Object> sensorData;
    Map<String, Map<String, Object>> allSensorsData;
    private static final String SERVER_TOKEN_URL = "https://script.google.com/macros/s/AKfycbwi0ct9Lu3vBCBLTbnTqfbBKftLg_lThyE5ypPakmjQOdKKDg01oSY4-B8jnEAD9nLN/exec";
    private ExecutorService executorService; // Executor untuk background thread

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Inisialisasi Konfigurasi OSMdroid
        // Penting: Konfigurasi harus dilakukan sebelum set content view
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        setContentView(R.layout.activity_home);



        tvDateTime = findViewById(R.id.tvDateTime);
        tvFlame = findViewById(R.id.tvApiStatus);
        tvLpg = findViewById(R.id.tvGasStatus);
        tvSuhu = findViewById(R.id.tvTemperature);
        tvStatus = findViewById(R.id.tvStatus);
        imgStatusIcon = findViewById(R.id.imgStatusIcon);
        tvLokasi = findViewById(R.id.textLokasi);

        // 2. Setup MapView
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK); // Tile source utama (tampilan peta)
        map.setBuiltInZoomControls(true); // Tampilkan kontrol zoom
        map.setMultiTouchControls(true); // Aktifkan pinch-to-zoom

        updateTimeRunnable = () -> {
            currentDateTime = new SimpleDateFormat("EEEE, dd MMMM yyyy\nhh:mm:ss a",
                    Locale.getDefault()).format(new Date());
            tvDateTime.setText(currentDateTime);
            handler.postDelayed(updateTimeRunnable, 1000);
        };
        handler.post(updateTimeRunnable);

        GeoPoint startPoint = new GeoPoint(-4.01244, 119.62974);
        map.getController().setZoom(14.5);
        map.getController().setCenter(startPoint);
        tvLokasi.setText("Pare-Pare");

        fetchSensorData();

        Button btnNotificationHistory = findViewById(R.id.btnNotificationHistory);
        btnNotificationHistory.setOnClickListener(v -> {
            Intent intent = new Intent(HomeActivity.this, NotificationHistoryActivity.class);
            startActivity(intent);
        });

        FloatingActionButton btnMaps = findViewById(R.id.fabMaps);
        btnMaps.setOnClickListener(v ->{
            Intent intent = new Intent(HomeActivity.this, MapsActivity.class);
            startActivity(intent);
        });

        executorService = Executors.newSingleThreadExecutor();
        getFCMTokenDirectly();
    }

    // ==============================
    // AMBIL DATA SENSOR DARI FIREBASE
    // ==============================
    private void fetchSensorData() {
        DatabaseReference sensorsRef = FirebaseDatabase.getInstance()
                .getReference("sensors");

        allSensorsData = new HashMap<>();

        sensorsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    allSensorsData.clear();
                    for (DataSnapshot sensorSnapshot : dataSnapshot.getChildren()) {
                        String sensorKey = sensorSnapshot.getKey();
                        idHighlight = sensorKey;

                        sensorData =
                                (Map<String, Object>) sensorSnapshot.getValue();


                        if (sensorKey != null && sensorData != null) {

                            allSensorsData.put(sensorKey, sensorData);

                            double lng = sensorSnapshot.child("long").getValue(Double.class);
                            double lat = sensorSnapshot.child("lat").getValue(Double.class);
                            String title = sensorSnapshot.child("title").getValue(String.class);
                            String label = sensorSnapshot.child("label").getValue(String.class);

                            // 3. Atur Posisi Awal dan Zoom
                            GeoPoint markerPoint = new GeoPoint(lat, lng);
                            map.getController().setCenter(markerPoint);


                            addSingleMarker(markerPoint, title, label);
                            setSensorDisplay(title, idHighlight);

                        }
                    }


                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    private void setViewDetails(long flame, double lpg, double suhu){
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
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) return;

        notificationManager.notify(getNotificationId(kondisi), builder.build());
    }


    private void setSensorDisplay(String stringTitle, String stringId){
        tvLokasi.setText(stringTitle);
        if(allSensorsData.containsKey(stringId)){
            Map<String, Object> sensorData = allSensorsData.get(idHighlight);

            long flame = (long) sensorData.get("flame");
            double lpg, suhu;

            Object raw_lpg = sensorData.get("lpg");
            if(raw_lpg instanceof Double){
                lpg = (double) raw_lpg;
            }else{
                long long_lpg = (long) raw_lpg;
                lpg = (double) long_lpg;
            }

            Object raw_suhu = sensorData.get("suhu");
            if(raw_suhu instanceof Double){
                suhu = (double) raw_suhu;
            }else{
                long long_suhu = (long) raw_suhu;
                suhu = (double) long_suhu;
            }




            setViewDetails(flame, lpg, suhu);
        }
    }

    private void addSingleMarker(GeoPoint point, String title, String snippet) {
        Marker newMarker = new Marker(map);
        newMarker.setPosition(point);
        newMarker.setTitle(title);
        newMarker.setSnippet(snippet);

        // Atur agar keterangan muncul saat diklik
        newMarker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker, MapView mapView) {
                // Tampilkan info window bawaan
//                marker.showInfoWindow();

                map.getController().setCenter(point);
                idHighlight = snippet;

                setSensorDisplay(title,idHighlight);

                // Tampilkan Toast dengan keterangan
                Toast.makeText(HomeActivity.this,
                        marker.getTitle(),
                        Toast.LENGTH_LONG).show();
                return true;
            }
        });


        map.getOverlays().add(newMarker);
        map.invalidate(); // Refresh peta untuk menampilkan marker baru
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
    public void onResume() {
        super.onResume();
        // Konfigurasi diperlukan lagi di onResume untuk memuat tile sources
        Configuration.getInstance().load(this, PreferenceManager.getDefaultSharedPreferences(this));
        if (map != null) {
            map.onResume(); // Memuat peta
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Konfigurasi diperlukan lagi di onPause untuk menyimpan status
        Configuration.getInstance().save(this, PreferenceManager.getDefaultSharedPreferences(this));
        if (map != null) {
            map.onPause();  // Menghentikan perenderan peta
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

    private void getFCMTokenDirectly() {
        FirebaseMessaging.getInstance().getToken()
                .addOnCompleteListener(new OnCompleteListener<String>() {
                    @Override
                    public void onComplete(@NonNull Task<String> task) {
                        if (!task.isSuccessful()) {
                            Log.w(TAG, "Fetching FCM registration token failed", task.getException());
                            return;
                        }

                        // Token didapatkan langsung dari FCM SDK
                        String token = task.getResult();
                        Log.d("TOKEN_DIRECT", "Token didapatkan langsung: " + token);
                        Toast.makeText(HomeActivity.this, token, Toast.LENGTH_SHORT).show();

                        // Lakukan pengiriman POST
                        if (token != null) {
                            performTokenPostRequest(token);
                        }
                    }
                });
    }
    private void performTokenPostRequest(String token) {

        // Kirim tugas ke background thread
        executorService.execute(() -> {
            HttpURLConnection urlConnection = null;
            try {
                // 1. Persiapan Data
                JSONObject jsonBody = new JSONObject();
                jsonBody.put("token", token);
                jsonBody.put("comms", "cek");
                String jsonInputString = jsonBody.toString();

                // 2. Setup Koneksi
                URL url = new URL(SERVER_TOKEN_URL);
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("POST");
                urlConnection.setRequestProperty("Content-Type", "application/json; utf-8");
                urlConnection.setRequestProperty("Accept", "application/json");
                urlConnection.setDoOutput(true); // Mengizinkan output (body POST)

                // 3. Menulis Body Data
                try(OutputStream os = urlConnection.getOutputStream()) {
                    byte[] input = jsonInputString.getBytes("utf-8");
                    os.write(input, 0, input.length);
                }

                // 4. Menerima Respon
                int responseCode = urlConnection.getResponseCode();

                // Tambahkan logging respon server (Input Stream dari server)
                String responseMessage = urlConnection.getResponseMessage();
                Log.d("TOKEN_SEND_RESPONSE", "Response Code: " + responseCode + ", Message: " + responseMessage);

                // Jika Anda ingin melihat BODY dari response server:

                try (BufferedReader br = new BufferedReader(
                        new InputStreamReader(urlConnection.getInputStream(), "utf-8"))) {
                    StringBuilder responseBody = new StringBuilder();
                    String responseLine = null;
                    while ((responseLine = br.readLine()) != null) {
                        responseBody.append(responseLine.trim());
                    }
                    Log.d("TOKEN_SEND_RESPONSE_BODY", "Body: " + responseBody.toString());
                }

                // Masuk kembali ke Main Thread untuk update UI (Toast)
                runOnUiThread(() -> {
                    if (responseCode == HttpURLConnection.HTTP_OK || responseCode == HttpURLConnection.HTTP_CREATED) {
                        Log.i("TOKEN_SEND", "Token berhasil dikirim, Code: " + responseCode);
                        Toast.makeText(this, "Token berhasil didaftarkan!", Toast.LENGTH_SHORT).show();
                    } else {
                        Log.e("TOKEN_SEND", "Gagal mengirim token, Code: " + responseCode);
                        Toast.makeText(this, "Gagal mendaftarkan token (Code: " + responseCode + ").", Toast.LENGTH_SHORT).show();
                    }
                });

            } catch (Exception e) {
                Log.e("TOKEN_SEND", "Error koneksi: " + e.getMessage());
                // Masuk kembali ke Main Thread untuk notifikasi error
                runOnUiThread(() -> {
                    Toast.makeText(this, "Error Jaringan: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
            }
        });
    }

}
