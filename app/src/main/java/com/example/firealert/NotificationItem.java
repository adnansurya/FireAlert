package com.example.firealert;

public class NotificationItem {
    public String time, name, label, flame, suhu, lpg;

    public NotificationItem(){}

    public NotificationItem(String time, String name, String label, String flame, String suhu, String lpg) {
        this.time = time;
        this.name = name;
        this.label = label;
        this.flame = flame;
        this.suhu = suhu;
        this.lpg = lpg;
    }

    public String getTime() {
        return time;
    }

    public String getName() {
        return name;
    }

    public String getLabel() {
        return label;
    }

    public String getFlame() {
        return flame;
    }

    public String getSuhu() {
        return suhu;
    }

    public String getLpg() {
        return lpg;
    }
}
