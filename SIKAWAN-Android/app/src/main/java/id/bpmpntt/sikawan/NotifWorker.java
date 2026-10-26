package id.bpmpntt.sikawan;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/**
 * Pemeriksa latar belakang: setiap +-15 menit menanyakan pemberitahuan & pesan baru ke SIKAWAN
 * (Apps Script ?notif=<kunci>) dan menampilkannya sebagai notifikasi HP walaupun aplikasi tertutup.
 */
public class NotifWorker extends Worker {
    static final String NAMA_TUGAS = "sikawan_cek_notif";
    static final String KEY_KUNCI = "perangkat_kunci";
    static final String KEY_PEMILIK = "perangkat_pemilik";
    static final String KEY_SEJAK = "perangkat_sejak";

    public NotifWorker(@NonNull Context c, @NonNull WorkerParameters p) {
        super(c, p);
    }

    static void jadwalkan(Context c) {
        Constraints syarat = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
        PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(NotifWorker.class, 15, TimeUnit.MINUTES)
                .setConstraints(syarat).build();
        WorkManager.getInstance(c).enqueueUniquePeriodicWork(NAMA_TUGAS, ExistingPeriodicWorkPolicy.KEEP, req);
    }

    static void hentikan(Context c) {
        WorkManager.getInstance(c).cancelUniqueWork(NAMA_TUGAS);
    }

    @NonNull
    @Override
    public Result doWork() {
        Context c = getApplicationContext();
        SharedPreferences p = NotifUtil.prefs(c);
        String kunci = p.getString(KEY_KUNCI, "");
        if (kunci.isEmpty()) { hentikan(c); return Result.success(); }
        long sejak = p.getLong(KEY_SEJAK, 0);
        HttpURLConnection con = null;
        try {
            String alamat = c.getString(R.string.web_app_url).trim() + "?notif=" + kunci + "&sejak=" + sejak;
            con = (HttpURLConnection) new URL(alamat).openConnection();
            con.setInstanceFollowRedirects(true);
            con.setConnectTimeout(20000);
            con.setReadTimeout(30000);
            int kode = con.getResponseCode();
            // Apps Script mengalihkan ke script.googleusercontent.com
            for (int i = 0; i < 3 && (kode == 301 || kode == 302 || kode == 303 || kode == 307); i++) {
                String lanjut = con.getHeaderField("Location");
                con.disconnect();
                con = (HttpURLConnection) new URL(lanjut).openConnection();
                con.setConnectTimeout(20000);
                con.setReadTimeout(30000);
                kode = con.getResponseCode();
            }
            if (kode != 200) return Result.retry();
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(con.getInputStream(), StandardCharsets.UTF_8))) {
                String baris;
                while ((baris = r.readLine()) != null) sb.append(baris);
            }
            JSONObject o = new JSONObject(sb.toString());
            if (!o.optBoolean("ok")) {
                if (o.optBoolean("hapus")) {   // kunci tidak berlaku lagi (keluar / akun nonaktif)
                    p.edit().remove(KEY_KUNCI).remove(KEY_PEMILIK).apply();
                    hentikan(c);
                }
                return Result.success();
            }
            JSONArray items = o.optJSONArray("items");
            if (items != null) {
                for (int i = 0; i < items.length(); i++) {
                    JSONObject it = items.getJSONObject(i);
                    NotifUtil.tampil(c, it.optString("judul", "SIKAWAN"), it.optString("isi", ""));
                }
            }
            p.edit().putLong(KEY_SEJAK, o.optLong("waktu", System.currentTimeMillis())).apply();
            return Result.success();
        } catch (Exception e) {
            return Result.retry();
        } finally {
            if (con != null) con.disconnect();
        }
    }
}
