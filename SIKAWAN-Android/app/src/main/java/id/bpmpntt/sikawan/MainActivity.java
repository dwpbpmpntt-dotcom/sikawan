package id.bpmpntt.sikawan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Base64;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.core.content.FileProvider;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.io.IOException;

/**
 * SIKAWAN – pembungkus Android (WebView) untuk Aplikasi Web Apps Script.
 * Alamat aplikasi diatur di res/values/config.xml (web_app_url).
 */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;
    private static final int REQ_CAMERA_PERMISSION = 1002;
    private static final int REQ_NOTIF_PERMISSION = 1003;
    private static final String CHANNEL_ID = "sikawan_pemberitahuan";
    private int notifId = 1;

    private WebView web;
    private ProgressBar progress;
    private LinearLayout errorView;
    private TextView errorText;

    private String homeUrl;
    private ValueCallback<Uri[]> fileCallback;
    private Uri cameraUri;
    private boolean cameraOnly;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        homeUrl = getString(R.string.web_app_url).trim();
        buildLayout();

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setBuiltInZoomControls(false);

        // Jembatan notifikasi: halaman SIKAWAN memanggil window.SikawanApp.notify(judul, isi)
        web.addJavascriptInterface(new NotifBridge(), "SikawanApp");
        siapkanNotifikasi();

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        cookies.setAcceptThirdPartyCookies(web, true);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (!request.isForMainFrame()) return false;
                return handleUrl(request.getUrl());
            }

            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                progress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showError("Tidak dapat terhubung ke SIKAWAN.\nPeriksa koneksi internet Anda, lalu coba lagi.");
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progress.setProgress(newProgress);
            }

            @Override
            public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback,
                                             FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                // Tombol "Ambil Foto" di SIKAWAN memakai capture="environment" → langsung kamera.
                cameraOnly = params.isCaptureEnabled();
                if (hasCameraPermission()) {
                    openPicker(true);
                } else {
                    requestPermissions(new String[]{Manifest.permission.CAMERA}, REQ_CAMERA_PERMISSION);
                }
                return true;
            }
        });

        if (homeUrl.isEmpty() || homeUrl.startsWith("GANTI")) {
            showError("Alamat aplikasi belum diatur.\nIsi web_app_url di res/values/config.xml.");
            return;
        }
        if (savedInstanceState != null) {
            web.restoreState(savedInstanceState);
        } else {
            web.loadUrl(homeUrl);
        }
    }

    /* ---------------- Notifikasi ---------------- */

    private void siapkanNotifikasi() {
        NotifUtil.siapkanKanal(this);
        // Pemeriksa latar belakang tetap berjalan bila HP ini sudah terdaftar untuk sebuah akun
        if (!NotifUtil.prefs(this).getString(NotifWorker.KEY_KUNCI, "").isEmpty()) NotifWorker.jadwalkan(this);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQ_NOTIF_PERMISSION);
        }
    }

    private void tampilkanNotifikasi(String judul, String isi) {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent buka = new Intent(this, MainActivity.class);
        buka.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(this, 0, buka,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
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
            NotificationManagerCompat.from(this).notify(notifId++, b.build());
        } catch (SecurityException e) {
            // izin notifikasi dicabut pengguna
        }
    }

    /** Dipanggil dari JavaScript halaman SIKAWAN. */
    private class NotifBridge {
        @JavascriptInterface
        public void notify(final String judul, final String isi) {
            NotifUtil.tampil(getApplicationContext(), judul, isi);
        }

        /** Daftarkan HP ini untuk notifikasi saat aplikasi tertutup (kunci dari server SIKAWAN). */
        @JavascriptInterface
        public void daftarPerangkat(String kunci, String pemilik, String sejak) {
            long t;
            try { t = Long.parseLong(sejak); } catch (Exception e) { t = System.currentTimeMillis(); }
            NotifUtil.prefs(MainActivity.this).edit()
                    .putString(NotifWorker.KEY_KUNCI, kunci == null ? "" : kunci)
                    .putString(NotifWorker.KEY_PEMILIK, pemilik == null ? "" : pemilik)
                    .putLong(NotifWorker.KEY_SEJAK, t).apply();
            NotifWorker.jadwalkan(MainActivity.this);
        }

        @JavascriptInterface
        public String kunciPerangkat() {
            return NotifUtil.prefs(MainActivity.this).getString(NotifWorker.KEY_KUNCI, "");
        }

        @JavascriptInterface
        public String pemilikPerangkat() {
            return NotifUtil.prefs(MainActivity.this).getString(NotifWorker.KEY_PEMILIK, "");
        }

        /** Keluar akun: hentikan notifikasi latar belakang di HP ini. */
        @JavascriptInterface
        public void hapusPerangkat() {
            NotifUtil.prefs(MainActivity.this).edit()
                    .remove(NotifWorker.KEY_KUNCI).remove(NotifWorker.KEY_PEMILIK).apply();
            NotifWorker.hentikan(MainActivity.this);
        }

        /**
         * Simpan file unduhan (base64) ke folder Download/SIKAWAN.
         * Hasil: "ok|<uri>|<lokasi>" atau "err|<pesan>".
         */
        @JavascriptInterface
        public String simpanFile(String base64, String mime, String nama) {
            try {
                return "ok|" + simpanUnduhan(base64, mime, nama);
            } catch (Exception e) {
                return "err|" + (e.getMessage() == null ? e.toString() : e.getMessage());
            }
        }

        /** Buka file yang sudah diunduh dengan aplikasi yang sesuai (PDF/Word). */
        @JavascriptInterface
        public void bukaFile(final String uri, final String mime) {
            runOnUiThread(() -> bukaUnduhan(Uri.parse(uri), mime));
        }

        @JavascriptInterface
        public int versi() {
            return 3;
        }
    }

    /* ---------------- Unduhan dokumen ---------------- */

    private String simpanUnduhan(String base64, String mime, String nama) throws IOException {
        byte[] data = Base64.decode(base64, Base64.DEFAULT);
        if (mime == null || mime.isEmpty()) mime = "application/octet-stream";
        if (nama == null || nama.trim().isEmpty()) nama = "SIKAWAN-" + System.currentTimeMillis();
        nama = nama.replaceAll("[\\\\/:*?\"<>|]", "_");
        Uri uri;
        String lokasi;
        if (Build.VERSION.SDK_INT >= 29) {
            ContentResolver cr = getContentResolver();
            ContentValues v = new ContentValues();
            v.put(MediaStore.MediaColumns.DISPLAY_NAME, nama);
            v.put(MediaStore.MediaColumns.MIME_TYPE, mime);
            v.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SIKAWAN");
            v.put(MediaStore.MediaColumns.IS_PENDING, 1);
            uri = cr.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
            if (uri == null) throw new IOException("Folder Download tidak dapat diakses.");
            try (OutputStream out = cr.openOutputStream(uri)) {
                if (out == null) throw new IOException("File tidak dapat ditulis.");
                out.write(data);
            }
            v.clear();
            v.put(MediaStore.MediaColumns.IS_PENDING, 0);
            cr.update(uri, v, null, null);
            lokasi = "Download/SIKAWAN/" + nama;
        } else {
            // Android 9 ke bawah: simpan di folder unduhan aplikasi (tanpa izin penyimpanan)
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "SIKAWAN");
            if (!dir.exists() && !dir.mkdirs()) throw new IOException("Folder unduhan tidak dapat dibuat.");
            File f = new File(dir, nama);
            try (FileOutputStream out = new FileOutputStream(f)) {
                out.write(data);
            }
            uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", f);
            lokasi = "Android/data/" + getPackageName() + "/files/Download/SIKAWAN/" + nama;
        }
        final Uri u = uri;
        final String m = mime, n = nama, l = lokasi;
        runOnUiThread(() -> notifUnduhan(u, m, n, l));
        return uri.toString() + "|" + lokasi;
    }

    private void notifUnduhan(Uri uri, String mime, String nama, String lokasi) {
        Toast.makeText(this, "Tersimpan: " + lokasi, Toast.LENGTH_LONG).show();
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return;
        Intent buka = new Intent(Intent.ACTION_VIEW);
        buka.setDataAndType(uri, mime);
        buka.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pi = PendingIntent.getActivity(this, notifId, buka,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder b = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notif)
                .setColor(0xFF0B5D3B)
                .setContentTitle("Unduhan selesai")
                .setContentText(nama)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(nama + "\nTersimpan di " + lokasi + ". Ketuk untuk membuka."))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setContentIntent(pi);
        try {
            NotificationManagerCompat.from(this).notify(notifId++, b.build());
        } catch (SecurityException e) {
            // izin notifikasi dicabut pengguna
        }
    }

    private void bukaUnduhan(Uri uri, String mime) {
        Intent i = new Intent(Intent.ACTION_VIEW);
        i.setDataAndType(uri, mime == null || mime.isEmpty() ? "*/*" : mime);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            startActivity(Intent.createChooser(i, "Buka dengan"));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka file ini. File tersimpan di folder Download/SIKAWAN.", Toast.LENGTH_LONG).show();
        }
    }

    /* ---------------- Tampilan ---------------- */

    private void buildLayout() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.parseColor("#F3F5F8"));

        web = new WebView(this);
        root.addView(web, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        progress = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setMax(100);
        root.addView(progress, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4), Gravity.TOP));

        errorView = new LinearLayout(this);
        errorView.setOrientation(LinearLayout.VERTICAL);
        errorView.setGravity(Gravity.CENTER);
        errorView.setPadding(dp(32), dp(32), dp(32), dp(32));
        errorView.setBackgroundColor(Color.parseColor("#F3F5F8"));
        errorView.setVisibility(View.GONE);

        TextView title = new TextView(this);
        title.setText("SIKAWAN");
        title.setTextColor(Color.parseColor("#1F4E79"));
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setGravity(Gravity.CENTER);
        errorView.addView(title);

        errorText = new TextView(this);
        errorText.setTextColor(Color.parseColor("#4A5565"));
        errorText.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        errorText.setGravity(Gravity.CENTER);
        errorText.setPadding(0, dp(12), 0, dp(20));
        errorView.addView(errorText);

        Button retry = new Button(this);
        retry.setText("Coba lagi");
        retry.setOnClickListener(v -> {
            errorView.setVisibility(View.GONE);
            web.setVisibility(View.VISIBLE);
            String current = web.getUrl();
            if (current == null || current.startsWith("data:") || current.equals("about:blank")) {
                web.loadUrl(homeUrl);
            } else {
                web.reload();
            }
        });
        errorView.addView(retry);

        root.addView(errorView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(root);
    }

    private void showError(String message) {
        progress.setVisibility(View.GONE);
        errorText.setText(message);
        web.setVisibility(View.INVISIBLE);
        errorView.setVisibility(View.VISIBLE);
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    /* ---------------- Navigasi ---------------- */

    /** true = buka di luar aplikasi; false = tetap di dalam WebView. */
    private boolean handleUrl(Uri uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme();
        String host = uri.getHost() == null ? "" : uri.getHost();
        if (!scheme.equals("http") && !scheme.equals("https")) {
            openExternal(uri);
            return true;
        }
        if (host.equals("script.google.com")
                || host.endsWith(".googleusercontent.com")
                || host.equals("accounts.google.com")) {
            return false;
        }
        // Tautan lain (mis. foto bukti di Google Drive) dibuka di browser / aplikasi Drive.
        openExternal(uri);
        return true;
    }

    private void openExternal(Uri uri) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Tidak ada aplikasi untuk membuka tautan ini.", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onBackPressed() {
        if (errorView.getVisibility() == View.VISIBLE) {
            super.onBackPressed();
        } else if (web.canGoBack()) {
            web.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /* ---------------- Foto bukti: kamera & galeri ---------------- */

    private boolean hasCameraPermission() {
        return checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void openPicker(boolean cameraAllowed) {
        Intent cameraIntent = cameraAllowed ? createCameraIntent() : null;

        if (cameraOnly) {
            if (cameraIntent == null) {
                Toast.makeText(this, "Kamera tidak tersedia. Gunakan tombol Pilih dari Galeri.", Toast.LENGTH_LONG).show();
                finishFileChooser(null);
                return;
            }
            launch(cameraIntent);
            return;
        }

        Intent gallery = new Intent(Intent.ACTION_GET_CONTENT);
        gallery.addCategory(Intent.CATEGORY_OPENABLE);
        gallery.setType("image/*");
        Intent chooser = Intent.createChooser(gallery, "Pilih foto bukti");
        if (cameraIntent != null) {
            chooser.putExtra(Intent.EXTRA_INITIAL_INTENTS, new Intent[]{cameraIntent});
        }
        launch(chooser);
    }

    private Intent createCameraIntent() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) == null) return null;
        try {
            File dir = new File(getCacheDir(), "foto");
            if (!dir.exists() && !dir.mkdirs()) return null;
            File photo = File.createTempFile("struk_", ".jpg", dir);
            cameraUri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", photo);
            intent.putExtra(MediaStore.EXTRA_OUTPUT, cameraUri);
            intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION | Intent.FLAG_GRANT_READ_URI_PERMISSION);
            return intent;
        } catch (IOException | IllegalArgumentException e) {
            cameraUri = null;
            return null;
        }
    }

    private void launch(Intent intent) {
        try {
            startActivityForResult(intent, REQ_FILE);
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, "Tidak dapat membuka kamera atau galeri.", Toast.LENGTH_SHORT).show();
            finishFileChooser(null);
        }
    }

    private void finishFileChooser(Uri[] result) {
        if (fileCallback != null) fileCallback.onReceiveValue(result);
        fileCallback = null;
        cameraUri = null;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_FILE) return;
        Uri[] result = null;
        if (resultCode == RESULT_OK) {
            if (data != null && data.getData() != null) {
                result = new Uri[]{data.getData()};          // dari galeri
            } else if (cameraUri != null) {
                result = new Uri[]{cameraUri};               // dari kamera
            }
        }
        finishFileChooser(result);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode != REQ_CAMERA_PERMISSION) return;   // izin notifikasi tidak perlu tindak lanjut
        boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (!granted && cameraOnly) {
            Toast.makeText(this, "Izin kamera ditolak. Aktifkan di Pengaturan > Aplikasi > SIKAWAN.", Toast.LENGTH_LONG).show();
            finishFileChooser(null);
            return;
        }
        openPicker(granted);
    }

    /* ---------------- Siklus hidup ---------------- */

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        web.saveState(outState);
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
    }

    @Override
    protected void onPause() {
        // WebView tidak dijeda agar pengecekan pemberitahuan tetap berjalan saat aplikasi di latar belakang
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
