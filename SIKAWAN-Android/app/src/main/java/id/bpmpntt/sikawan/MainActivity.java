package id.bpmpntt.sikawan;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
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

import androidx.core.content.FileProvider;

import java.io.File;
import java.io.IOException;

/**
 * SIKAWAN – pembungkus Android (WebView) untuk Aplikasi Web Apps Script.
 * Alamat aplikasi diatur di res/values/config.xml (web_app_url).
 */
public class MainActivity extends Activity {

    private static final int REQ_FILE = 1001;
    private static final int REQ_CAMERA_PERMISSION = 1002;

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
        if (requestCode != REQ_CAMERA_PERMISSION) return;
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
        web.onPause();
        CookieManager.getInstance().flush();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (web != null) web.destroy();
        super.onDestroy();
    }
}
