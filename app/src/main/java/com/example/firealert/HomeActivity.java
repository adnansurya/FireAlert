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
import androidx.core.content.ContextCompat;

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

    // Kode untuk identifikasi permintaan izin notifikasi
    private static final int POST_NOTIFICATIONS_REQUEST_CODE = 1001;

    boolean intentBlocked = false;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 1. Inisialisasi Konfigurasi OSMdroid
        // Penting: Konfigurasi harus dilakukan sebelum set content view
        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));
        setContentView(R.layout.activity_home);

        requestNotificationPermission();

        allSensorsData = new HashMap<>();


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
        handleNotificationIntent(getIntent());

    }

    // MainActivity.java (lanjutan)

    /**
     * Memeriksa dan meminta izin POST_NOTIFICATIONS (Wajib untuk Android 13/API 33 ke atas).
     */
    private void requestNotificationPermission() {
        // Izin POST_NOTIFICATIONS hanya ada di Android 13 (API 33) ke atas
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {

            // 1. Cek apakah izin sudah diberikan
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {

                // Izin belum diberikan, tampilkan dialog permintaan
                Log.d("PERMISSION_CHECK", "Izin notifikasi belum diberikan, meminta izin...");

                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        POST_NOTIFICATIONS_REQUEST_CODE
                );

            } else {
                // Izin sudah diberikan
                Log.d("PERMISSION_CHECK", "Izin notifikasi sudah diberikan.");
                // Lanjutkan dengan operasi yang memerlukan notifikasi
            }
        } else {
            // Versi Android di bawah 13, izin notifikasi dianggap sudah ada secara default
            Log.d("PERMISSION_CHECK", "Android versi lama, izin notifikasi otomatis diberikan.");
        }
        executorService = Executors.newSingleThreadExecutor();
        getFCMTokenDirectly();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        if (requestCode == POST_NOTIFICATIONS_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Izin diberikan oleh user
                Log.d("PERMISSION_RESULT", "Izin Notifikasi Diberikan!");
                Toast.makeText(this, "Izin Notifikasi Diberikan.", Toast.LENGTH_SHORT).show();
                // Lanjutkan operasi yang memerlukan notifikasi (misalnya inisialisasi FCM atau tampilkan UI yang relevan)

            } else {
                // Izin ditolak oleh user
                Log.w("PERMISSION_RESULT", "Izin Notifikasi Ditolak!");
                Toast.makeText(this, "Peringatan: Notifikasi mungkin tidak berfungsi.", Toast.LENGTH_LONG).show();

                // Opsi: Anda bisa menunjukkan dialog penjelasan mengapa izin penting,
                // terutama jika user menolak kedua kalinya (shouldShowRequestPermissionRationale)
            }
        }
    }

    // ==============================
    // AMBIL DATA SENSOR DARI FIREBASE
    // ==============================
    private void fetchSensorData() {
        DatabaseReference sensorsRef = FirebaseDatabase.getInstance()
                .getReference("sensors");


            sensorsRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
//                    allSensorsData.clear();
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

//
//
                                addSingleMarker(markerPoint, title, label);

                            if(!intentBlocked){
                                map.getController().setCenter(markerPoint);
                                setSensorDisplay(title, sensorKey);
                            }


                            }
                        }
//                        Toast.makeText(HomeActivity.this, "ALLSENSOR", Toast.LENGTH_SHORT).show();
                        Log.d("AllSensor",String.valueOf(allSensorsData));
                        if(intentBlocked){
                            handleNotificationIntent(getIntent());
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
            fireAlarm = true;
        } else fireAlarm = false;

        // GAS
        if (lpg >= 50.0) {
            lpgAlarm = true;
        } else lpgAlarm = false;

        // SUHU
        if (suhu >= 42.0) {
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

    private void setSensorDisplay(String stringTitle, String stringId){
        tvLokasi.setText(stringTitle);
        if(allSensorsData.containsKey(stringId)){
            Map<String, Object> sensorData = allSensorsData.get(stringId);

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
//                idHighlight = snippet;

                setSensorDisplay(title,snippet);
                intentBlocked = false;
//
//                // Tampilkan Toast dengan keterangan
//                Toast.makeText(HomeActivity.this,
//                        marker.getTitle(),
//                        Toast.LENGTH_LONG).show();
                return true;
            }
        });


        map.getOverlays().add(newMarker);
        map.invalidate(); // Refresh peta untuk menampilkan marker baru
    }

    @Override
    public void onResume() {
        super.onResume();

        if (getIntent() != null && getIntent().hasExtra("ACTION_GOTO_PLACE")) {
            handleNotificationIntent(getIntent());
        }
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
//                        Toast.makeText(HomeActivity.this, token, Toast.LENGTH_SHORT).show();

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
//                        Toast.makeText(this, "Token berhasil didaftarkan!", Toast.LENGTH_SHORT).show();
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

    private void handleNotificationIntent(Intent intent) {

        // Periksa apakah intent memiliki flag ACTION_GOTO_PLACE
        if (intent != null && intent.hasExtra("ACTION_GOTO_PLACE")) {
            intentBlocked = true;
            boolean gotoPlace = intent.getBooleanExtra("ACTION_GOTO_PLACE", false);
            String label = intent.getStringExtra("LABEL");
            String name = intent.getStringExtra("NAME");
//            Toast.makeText(this, label + " " + name, Toast.LENGTH_SHORT).show();

            if (gotoPlace && label != null && !label.isEmpty()) {
                // Lakukan aksi navigasi ke tempat/koordinat

                navigateToPlace(intent, name, label);
            }
        }
    }

    private void navigateToPlace(Intent intent, String name, String label) {

        // Implementasi navigasi ke tempat berdasarkan label
        // Misalnya: tampilkan di map, buka detail, dll.
//        Toast.makeText(HomeActivity.this, "ALLSENSOR", Toast.LENGTH_SHORT).show();
        Log.d("AllSensorNav",String.valueOf(allSensorsData));

        // Contoh: Tampilkan toast atau dialog
//        Toast.makeText(this, "Navigasi ke: "+name , Toast.LENGTH_SHORT).show();
//        idHighlight = label;

//        intentBlocked = false;
//        Toast.makeText(this, "HIGHLIGHT : " + idHighlight, Toast.LENGTH_SHORT).show();

        if(allSensorsData.containsKey(label)) {
            Toast.makeText(this, "loaded", Toast.LENGTH_SHORT).show();
            Map<String, Object> sensorData = allSensorsData.get(label);
            double lat = (double) sensorData.get("lat");
            double lng = (double) sensorData.get("long");

            if (lat != 0 && lng != 0) {
                GeoPoint sensorPoint = new GeoPoint(lat, lng);
                map.getController().setCenter(sensorPoint);
                map.getController().setZoom(17);

                // Opsional: Hapus flag setelah diproses
                intent.removeExtra("ACTION_GOTO_PLACE");
                intent.removeExtra("LABEL");
                intent.removeExtra("NAME");

                setSensorDisplay(name, label);

//                intentBlocked = false;
            }
        }

    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        // Tangkap intent saat activity sudah berjalan
        setIntent(intent);
        handleNotificationIntent(intent);
    }

}
