# SIKAWAN – Aplikasi Android

Sistem Informasi Keuangan DWP BPMP Provinsi NTT, dibungkus sebagai aplikasi Android (WebView)
yang membuka Aplikasi Web Apps Script SIKAWAN.

## 1. Isi alamat aplikasi (wajib)
Buka `app/src/main/res/values/config.xml`, ganti `GANTI_DENGAN_URL_WEB_APP` dengan URL
Aplikasi Web dari Apps Script (Terapkan → Kelola deployment → salin URL yang berakhiran `/exec`).

## 2a. Build APK otomatis di GitHub (tanpa install apa pun)
1. Buat repository baru di GitHub (sebaiknya **Private**).
2. Unggah seluruh isi folder ini, termasuk folder `.github`.
3. Buka tab **Actions** → workflow **Build APK SIKAWAN** berjalan otomatis (± 5 menit).
4. Setelah selesai (centang hijau), buka run tersebut → bagian **Artifacts** → unduh **SIKAWAN-APK**
   (berisi file `SIKAWAN-v1.0.apk`).

## 2b. Build di Android Studio
Open folder ini → tunggu Gradle sync → **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.

## Memasang di HP
Kirim file APK ke HP → buka → izinkan "Instal aplikasi tidak dikenal" → Instal.

## Memperbarui aplikasi
- Perubahan tampilan/fitur di Apps Script **langsung ikut** tanpa perlu APK baru
  (cukup Kelola deployment → Edit → Versi baru, URL tetap sama).
- APK baru hanya perlu jika URL, nama, atau ikon berubah. Naikkan `versionCode` dan
  `versionName` di `app/build.gradle`.

## PENTING
`app/sikawan.keystore` adalah kunci tanda tangan aplikasi. Simpan baik-baik dan jangan
dibagikan ke publik. Tanpa kunci yang sama, pembaruan APK tidak bisa dipasang di atas versi lama.
