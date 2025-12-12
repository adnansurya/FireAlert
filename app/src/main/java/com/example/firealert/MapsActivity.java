package com.example.firealert;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import android.graphics.Color;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import org.osmdroid.config.Configuration;
import org.osmdroid.tileprovider.tilesource.TileSourceFactory;
import org.osmdroid.util.GeoPoint;
import org.osmdroid.views.MapView;
import org.osmdroid.views.overlay.Marker;
import org.osmdroid.views.overlay.infowindow.InfoWindow;
import org.osmdroid.events.MapEventsReceiver;
import org.osmdroid.views.overlay.MapEventsOverlay;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.widget.EditText;
import android.widget.LinearLayout;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.HashMap;
import java.util.Map;

public class MapsActivity extends AppCompatActivity implements MapEventsReceiver {

    private MapView map;

    String idHighlight = "";

    Map<String, Object> sensorData;
    Map<String, Map<String, Object>> allSensorsData;
    DatabaseReference sensorsRef;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Configuration.getInstance().load(getApplicationContext(),
                PreferenceManager.getDefaultSharedPreferences(getApplicationContext()));

        setContentView(R.layout.activity_maps);

        // 2. Setup MapView
        map = findViewById(R.id.map);
        map.setTileSource(TileSourceFactory.MAPNIK); // Tile source utama (tampilan peta)
        map.setBuiltInZoomControls(true); // Tampilkan kontrol zoom
        map.setMultiTouchControls(true); // Aktifkan pinch-to-zoom

        // 3. Atur Posisi Awal dan Zoom
        GeoPoint startPoint = new GeoPoint(-4.01244, 119.62974); // Pare-Pare
        map.getController().setZoom(16.0);
        map.getController().setCenter(startPoint);

        // 1. Tambahkan MapEventsOverlay ke peta
        MapEventsOverlay mapEventsOverlay = new MapEventsOverlay(this);
        map.getOverlays().add(0, mapEventsOverlay); // Penting: tambahkan di index 0 agar menangkap klik duluan

        fetchSensorData();

    }

    // MainActivity.java

    /**
     * Menampilkan AlertDialog kustom saat marker diklik,
     * termasuk opsi untuk menghapus marker.
     */
    private void showMarkerDetailsDialog(final Marker marker) {
        // 1. Inflate Layout Kustom
        LayoutInflater inflater = getLayoutInflater();
        View dialogView = inflater.inflate(R.layout.dialog_marker_details, null);

        // 2. Inisialisasi Views dari Layout Kustom
        TextView textJudul = dialogView.findViewById(R.id.dialog_title);
        TextView textLabel = dialogView.findViewById(R.id.data_id_lokasi);
        TextView textStatus = dialogView.findViewById(R.id.data_status_area);
        TextView textApi = dialogView.findViewById(R.id.data_indikator_api);
        TextView textSuhu = dialogView.findViewById(R.id.data_suhu);
        TextView textLpg = dialogView.findViewById(R.id.data_gas_lpg);
        Button btnHapus = dialogView.findViewById(R.id.btn_hapus_marker);
        Button btnTutup = dialogView.findViewById(R.id.btn_tutup_dialog);

        // 3. Ambil Data Marker
        final String title = marker.getTitle() != null ? marker.getTitle() : "Tidak Ada Judul";
        final String label = marker.getSnippet() != null ? marker.getSnippet() : "Tidak Ada Keterangan";


        // 4. Isi Data ke Views
        textJudul.setText(title);
        textLabel.setText(label);

        if(allSensorsData.containsKey(label)){
            Map<String, Object> sensorData = allSensorsData.get(label);

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

            boolean fireAlarm = false;
            boolean lpgAlarm = false;
            boolean suhuAlarm = false;

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
                textStatus.setText("Kondisi Aman");
                textStatus.setTextColor(Color.parseColor("#008F39"));
//                imgStatusIcon.setImageResource(R.drawable.check_green);
            } else {
                textStatus.setText("Terjadi Kebakaran!");
                textStatus.setTextColor(Color.parseColor("#BD1111"));
//                imgStatusIcon.setImageResource(R.drawable.warning); // ⛔ WARNING ICON
            }

            // Update sekunder
            textApi.setText(flame == 1 ? "Api Terdeteksi" : "Tidak Ada Api");
            textLpg.setText(String.valueOf(lpg));
            textSuhu.setText(suhu + " °C");

        }


        // 5. Buat AlertDialog
        final AlertDialog customDialog = new AlertDialog.Builder(this)
                .setView(dialogView)
                .create(); // Penting: Gunakan .create() bukan .show()

        // 6. Atur Listener untuk Tombol Hapus (Neutral Button)
        btnHapus.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                customDialog.dismiss(); // Tutup dialog details
                confirmMarkerDeletion(marker); // Panggil dialog konfirmasi penghapusan
            }
        });

        // 7. Atur Listener untuk Tombol Tutup (Positive Button)
        btnTutup.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                customDialog.dismiss(); // Tutup dialog
            }
        });

        // Tampilkan Dialog
        customDialog.show();
    }

    /**
     * Dialog konfirmasi sebelum menghapus marker secara permanen.
     */
    private void confirmMarkerDeletion(final Marker marker) {
        new AlertDialog.Builder(this)
                .setTitle("Konfirmasi Hapus")
                .setMessage("Apakah Anda yakin ingin menghapus marker '" + marker.getTitle() + "'?")
                .setPositiveButton("Ya, Hapus", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        removeMarker(marker); // Panggil fungsi penghapusan
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    // MainActivity.java

    /**
     * Menghapus marker dari peta dan merefresh tampilan.
     */
    private void removeMarker(Marker marker) {
        if (map != null && marker != null) {
            String sensorId = marker.getSnippet();
            DatabaseReference deleteRef = sensorsRef.child(sensorId);
            deleteRef.removeValue();
            map.getOverlays().remove(marker);
            map.invalidate();
            Toast.makeText(this, "Marker '" + marker.getTitle() + "' telah dihapus.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean singleTapConfirmedHelper(GeoPoint p) {

        return false;
    }


    @Override
    public boolean longPressHelper(GeoPoint p) {

        showAddMarkerDialog(p);
        return true;
    }

    // -------------------------------------------------------------------
    // Metode untuk Menampilkan Dialog Input Marker
    // -------------------------------------------------------------------
    private void showAddMarkerDialog(final GeoPoint p) {
        // Buat Layout untuk input dialog
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(50, 20, 50, 20);

        final EditText titleInput = new EditText(this);
        titleInput.setHint("Judul Marker");
        layout.addView(titleInput);

        final EditText labelInput = new EditText(this);
        labelInput.setHint("Label Marker (tanpa spasi)");
        layout.addView(labelInput);

        // Buat AlertDialog
        new AlertDialog.Builder(this)
                .setTitle("Tambah Marker Baru")
                .setMessage("Masukkan judul dan keterangan untuk titik ini (Lat: "
                        + String.format("%.4f", p.getLatitude()) + ", Lon: "
                        + String.format("%.4f", p.getLongitude()) + ")")
                .setView(layout)
                .setPositiveButton("Tambah", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String title = titleInput.getText().toString();
                        String label = labelInput.getText().toString().trim();

                        if (!title.isEmpty()) {
                            // Panggil fungsi penambahan marker dengan data dari dialog
                            addNewSensor(title, label, p.getLongitude(), p.getLatitude());
                            addSingleMarker(p, title, label);
                            map.getController().setCenter(p);
                            Toast.makeText(MapsActivity.this,
                                    "Marker '" + title + "' berhasil ditambahkan.",
                                    Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MapsActivity.this, "Judul tidak boleh kosong.", Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton("Batal", null)
                .show();
    }

    private void addNewSensor(String title, String label, double lng, double lat){
        DatabaseReference sensorsRef = FirebaseDatabase.getInstance()
                .getReference("sensors").child(label);

        Map<String, Object> dataSensor = new HashMap<>();
        dataSensor.put("title", title);
        dataSensor.put("label", label);
        dataSensor.put("flame", 0);
        dataSensor.put("lpg", 0.0);
        dataSensor.put("suhu", 0.0);
        dataSensor.put("long", lng);
        dataSensor.put("lat", lat);

        sensorsRef.updateChildren(dataSensor)
                .addOnSuccessListener(aVoid -> {
                    Log.d("Firebase", "Data sensor " + label + " berhasil di-update.");
                    Toast.makeText(this, "Pembaruan Berhasil!", Toast.LENGTH_SHORT).show();
                })
                .addOnFailureListener(e -> {
                    Log.e("Firebase", "Error saat update data: " + e.getMessage());
                    Toast.makeText(this, "Pembaruan Gagal: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });

    }

    private void fetchSensorData() {
        sensorsRef = FirebaseDatabase.getInstance()
                .getReference("sensors");

        allSensorsData = new HashMap<>();

        sensorsRef.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (dataSnapshot.exists()) {
                    for (DataSnapshot sensorSnapshot : dataSnapshot.getChildren()) {
                        String sensorKey = sensorSnapshot.getKey();

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

                        }
                    }


                }
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {

            }
        });
    }

    // -------------------------------------------------------------------
    // Metode Baru untuk Menambahkan Marker Tunggal
    // -------------------------------------------------------------------
    private void addSingleMarker(GeoPoint point, String title, String description) {
        Marker newMarker = new Marker(map);
        newMarker.setPosition(point);
        newMarker.setTitle(title);
        newMarker.setSnippet(description);

        // Atur agar keterangan muncul saat diklik
        newMarker.setOnMarkerClickListener(new Marker.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker, MapView mapView) {
                // Tampilkan info window bawaan
                showMarkerDetailsDialog(marker);

                return true;
            }
        });


        map.getOverlays().add(newMarker);
        map.invalidate(); // Refresh peta untuk menampilkan marker baru
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


}