package com.example.firealert;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NotificationHistoryActivity extends AppCompatActivity {

    private RecyclerView recyclerViewNotifications;
    private Button buttonPantauanKondisi;

    private DatabaseReference notifReference;
    private List<NotificationItem> notificationList;
    private NotificationAdapter notifAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_notification_history);

        // Firebase
        notifReference = FirebaseDatabase.getInstance().getReference("notifikasi");

        // Binding UI
        recyclerViewNotifications = findViewById(R.id.recyclerViewNotifications);
        buttonPantauanKondisi = findViewById(R.id.buttonPantauanKondisi);

        // List & Adapter
        notificationList = new ArrayList<>();
        notifAdapter = new NotificationAdapter(notificationList);

        recyclerViewNotifications.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewNotifications.setAdapter(notifAdapter);

        // Tombol kembali ke halaman sebelumnya
        buttonPantauanKondisi.setOnClickListener(v -> finish());

        // Load data dari Firebase
        loadNotificationsFromFirebase();
    }

    private void loadNotificationsFromFirebase() {
        notifReference.addValueEventListener(new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                notificationList.clear();

                for (DataSnapshot data : snapshot.getChildren()) {
                    NotificationItem item = data.getValue(NotificationItem.class);
                    if (item != null) {
                        notificationList.add(item);
                    }
                }

                Collections.reverse(notificationList);

                notifAdapter.notifyDataSetChanged();
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                Toast.makeText(NotificationHistoryActivity.this,
                        "Gagal memuat data notifikasi",
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
