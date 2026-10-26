package id.bpmpntt.sikawan;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.HashSet;
import java.util.Set;

/**
 * Notifikasi SIKAWAN yang dipakai bersama oleh halaman (saat aplikasi terbuka) dan
 * pemeriksa latar belakang (saat aplikasi tertutup). Notifikasi yang sama tidak ditampilkan dua kali.
 */
final class NotifUtil {
    static final String CHANNEL_ID = "sikawan_pemberitahuan";
    static final String PREFS = "sikawan";
    private static final String KEY_TAMPIL = "notif_tampil";

    private NotifUtil() {}

    static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void siapkanKanal(Context c) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL_ID, "Pemberitahuan SIKAWAN",
                    NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Tugas baru dan pesan dari pengguna SIKAWAN");
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    /** Tampilkan notifikasi; false bila sudah pernah ditampilkan atau izin tidak ada. */
    static synchronized boolean tampil(Context c, String judul, String isi) {
        if (judul == null) judul = "SIKAWAN";
        if (isi == null) isi = "";
        if (Build.VERSION.SDK_INT >= 33
                && c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false;
        String sidik = Integer.toHexString((judul + "|" + isi).hashCode());
        SharedPreferences p = prefs(c);
        Set<String> sudah = new HashSet<>(p.getStringSet(KEY_TAMPIL, new HashSet<>()));
        if (sudah.contains(sidik)) return false;
        if (sudah.size() > 200) sudah.clear();
        sudah.add(sidik);
        p.edit().putStringSet(KEY_TAMPIL, sudah).apply();

        siapkanKanal(c);
        Intent buka = new Intent(c, MainActivity.class);
        buka.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(c, 0, buka,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(c, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFF0B5D3B)
                .setContentTitle(judul)
                .setContentText(isi)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(isi))
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            int id = (int) (System.currentTimeMillis() % 100000000L);
            NotificationManagerCompat.from(c).notify(id, b.build());
            return true;
        } catch (SecurityException e) {
            return false;
        }
    }
}
