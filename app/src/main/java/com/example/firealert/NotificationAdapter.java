package com.example.firealert;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class NotificationAdapter extends RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder> {

    private List<NotificationItem> notificationList;

    public NotificationAdapter(List<NotificationItem> notificationList) {
        this.notificationList = notificationList;
    }

    @NonNull
    @Override
    public NotificationViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.activity_notification_item, parent, false);
        return new NotificationViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull NotificationViewHolder holder, int position) {
        NotificationItem item = notificationList.get(position);
        holder.textViewTime.setText(item.getTime());
        holder.textViewLokasi.setText(item.getName());
        if(item.getFlame().equals("1")){
            holder.textViewApi.setText("Api Terdeteksi" );
        }else{
            holder.textViewApi.setText("Tidak Ada Api" );
        }
        holder.textViewSuhu.setText("Suhu: " + item.getSuhu()+ " °C");
        holder.textViewAsap.setText("Gas: " + item.getLpg()+ " %");
    }

    @Override
    public int getItemCount() {
        return notificationList.size();
    }

    public static class NotificationViewHolder extends RecyclerView.ViewHolder {
        TextView textViewTime, textViewLokasi, textViewApi, textViewSuhu, textViewAsap;

        public NotificationViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewTime = itemView.findViewById(R.id.textViewTime);
            textViewLokasi = itemView.findViewById(R.id.textViewLokasi);
            textViewApi = itemView.findViewById(R.id.textViewApi);
            textViewSuhu = itemView.findViewById(R.id.textViewSuhu);
            textViewAsap = itemView.findViewById(R.id.textViewAsap);
        }
    }
}
