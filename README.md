# Nofollow

Pantau siapa yang unfollow kamu di Instagram, tanpa nunggu export data resmi yang lambat.
Buka app, sinkron sekali, dan datanya langsung muncul: siapa yang unfollow, siapa yang tidak follow balik, siapa fans-mu, dan histori perubahan dari waktu ke waktu.

> **Disclaimer:** Nofollow adalah proyek independen, dibuat untuk dipakai di akun sendiri. Tidak berafiliasi dengan, didukung, atau disponsori oleh Meta/Instagram. Memakai app ini berarti mengakses data lewat mekanisme yang tidak didokumentasikan secara resmi oleh Instagram, jadi **akun kamu bisa kena pembatasan**. Pakai dengan risiko sendiri (lihat [Risiko & cara kerja](#-risiko--cara-kerja)).

<!--
Screenshot: taruh gambar di docs/screenshots/ lalu hapus tanda komentar di bawah.
<p align="center">
  <img src="docs/screenshots/beranda.png" width="220" alt="Beranda">
  <img src="docs/screenshots/tracker.png" width="220" alt="Tracker">
  <img src="docs/screenshots/kepo.png" width="220" alt="Kepo">
  <img src="docs/screenshots/pengaturan.png" width="220" alt="Pengaturan">
</p>
-->

## 📲 Install

1. Buka halaman [Releases](https://github.com/naheeju/Nofollow/releases/latest) dan unduh APK yang cocok dengan HP-mu:
    - `nofollow-arm64-v8a.apk` untuk hampir semua HP modern (pilih ini kalau ragu).
    - `nofollow-armeabi-v7a.apk` untuk HP lama 32-bit.
2. Ketuk file yang diunduh. Kalau diminta, izinkan **"Instal dari sumber ini"** untuk browser atau file manager yang kamu pakai.
3. Buka Nofollow dan masuk lewat halaman login Instagram yang asli.

**Syarat:** Android 7.0 (API 24) atau lebih baru.

**Pembaruan:** app mengecek versi baru di GitHub (sekali sehari, bisa dimatikan di Pengaturan). Kalau ada, muncul dialog, kartu di Beranda, dan notifikasi. Ketuk Unduh, lalu pasang seperti biasa, data lama tetap aman selama kamu memasang rilis resmi dari repo ini. Kamu juga bisa memantau rilis lewat [Obtainium](https://github.com/ImranR98/Obtainium) kalau tidak mau fitur cek pembaruan bawaan.

## ✨ Fitur

- **Sinkron satu klik.** Tekan tombol, data follower dan following ter-update. Sinkron pertama butuh waktu: sekitar 1,5 sampai 2 menit untuk ±700 follower, lebih lama kalau follower-mu lebih banyak. Setelahnya jauh lebih ringan.
- **Beranda.** Ringkasan akun: pertumbuhan follower dan aktivitas terbaru.
- **Tracker.** Daftar *unfollow*, *tidak follow balik*, *fans* (follower yang tidak kamu follow), *mutual*, serta histori follow, follow ulang, ganti username, dan akun yang tidak lagi tersedia.
- **Kepo.** Lihat akun sering kepoin kamu diam-diam tag seperti *Secret Admirer* dan *Ghost Admirer*, dan tombol Intip Balik.
- **Mode kecepatan:** Hemat, Normal (disarankan), Cepat, dan Kilat. Makin cepat, makin banyak request ke Instagram dan makin besar risikonya. Kilat tidak disarankan.
- **Pantau latar belakang (opsional).** Sinkron berjalan sendiri di luar jam tenang (00:00 sampai 07:00) dan memberi notifikasi kalau ada yang unfollow.
- **Pacing acak & adaptif.** Jeda antar-request diacak, ada plafon request harian, dan app otomatis istirahat kalau Instagram membatasi request.
- **Data tersimpan lokal.** Database ada di HP-mu. Tidak ada server Nofollow.

## 🔐 Izin yang diminta

| Izin | Untuk apa |
|---|---|
| Internet | Membaca data dari Instagram dan mengecek versi baru di GitHub |
| Notifikasi | Memberi tahu kalau ada yang unfollow atau ada versi baru |
| Layanan latar depan (sinkronisasi data) dan wake lock | Menyelesaikan sinkron saat app ditutup |
| Alarm tepat waktu (exact alarm) | Menjadwalkan sinkron otomatis sesuai mode |
| Jalan saat HP menyala ulang | Memulihkan jadwal sinkron setelah restart atau update |
| Abaikan optimasi baterai (opsional) | Supaya sinkron otomatis tidak dimatikan sistem |

## 🔒 Privasi

- Kamu masuk lewat halaman login Instagram yang asli. Password diketik langsung di sana; Nofollow tidak membaca atau menyimpannya.
- Sesi login disimpan terenkripsi (Android Keystore) dan tidak ikut backup. Tombol **Keluar** menghapusnya.
- Daftar follower dan following hanya ada di HP-mu.
- App berkomunikasi dengan **Instagram** (untuk membaca data akunmu) dan dengan **github.com** (hanya untuk mengecek apakah ada versi baru, tanpa mengirim data apa pun darimu). Cek ini bisa dimatikan di Pengaturan.
- Tidak ada analitik, iklan, atau server pihak ketiga.

## 🔍 Risiko & cara kerja

Instagram tidak menyediakan API publik untuk membaca follower dan following. Nofollow membaca datanya lewat `WebView` tersembunyi yang membuka `instagram.com` dengan sesi login kamu sendiri, mirip membuka Instagram lewat browser biasa.

Konsekuensinya:
- Ini **bukan** API resmi. Bisa berhenti berfungsi kapan saja kalau Instagram mengubah struktur internalnya. Kalau itu terjadi, kami akan merilis perbaikan dan app akan memberi tahu.
- Pemakaian otomatis semacam ini berpotensi melanggar [Ketentuan Layanan Instagram](https://help.instagram.com/581066165581870).
- Nofollow membatasi kecepatan request (pacing acak, plafon harian, jam tenang) untuk mengurangi risiko, tapi ini **bukan jaminan**. Akun bisa tetap kena pembatasan atau *checkpoint*, terutama kalau akunmu punya follower dan following sangat banyak atau kamu memakai mode Cepat atau Kilat.
- Kalau Instagram meminta verifikasi atau membatasi akunmu, selesaikan dulu di aplikasi Instagram. Nofollow akan berhenti otomatis sampai kamu melanjutkannya.

Pakai akun yang kamu siap menanggung risikonya.

## 🏗️ Arsitektur

Clean Architecture, 3 layer per fitur:

```
features/<nama>/
├── domain/        # business rule murni: model, interface repository, use case
│                  #   TIDAK BOLEH import apa pun dari data/ atau Android framework
├── data/          # implementasi nyata: Room, WebView, DataStore, WorkManager
│                  #   boleh depend ke domain (implements interface-nya)
└── ui/            # Compose screen + ViewModel
                   #   hanya boleh kenal domain (use case), TIDAK boleh import data/ langsung
```

Dependency Injection pakai **Koin** (lihat `di/*Module.kt`), bukan Hilt/Dagger.

**Stack:** Kotlin, Jetpack Compose, Koin, Room, WorkManager, Coroutines, DataStore Preferences.

## 🛠️ Build dari source

```bash
git clone https://github.com/naheeju/Nofollow.git
cd Nofollow
```

Untuk build **debug**:
```bash
./gradlew assembleDebug
```

Untuk build **rilis**, kamu perlu keystore sendiri. Buat `keystore.properties` di root project:
```properties
storeFile=/path/ke/nofollow.jks
storePassword=<password keystore>
keyAlias=<alias>
keyPassword=<password key>
```
Lalu:
```bash
./gradlew assembleRelease
```
Tanpa `keystore.properties`, build rilis otomatis fallback ke kunci debug (hanya untuk uji lokal, APK hasilnya **jangan dibagikan**).

Versi app diatur di satu tempat: `appVersionName` di `app/build.gradle.kts` (format `MAJOR.MINOR.PATCH`). `versionCode` dihitung otomatis darinya.

## 🤝 Kontribusi

Lihat [CONTRIBUTING.md](CONTRIBUTING.md) sebelum bikin PR. Ada aturan layering (domain/data/ui) yang wajib diikuti.

## 📄 Lisensi

[GPL-3.0](LICENSE): bebas dipakai, diubah, dan disebarluaskan, selama turunannya tetap open source di bawah lisensi yang sama.

Copyright (C) 2026 naheeju