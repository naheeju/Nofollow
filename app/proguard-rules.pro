# ------------------------------------------------------------------------------------------------
# Aturan R8 untuk Nofollow (rilis: minify + shrink resources).
# Room, WorkManager, Compose, OkHttp, Coroutines dan DataStore membawa aturan sendiri lewat consumer rules,
# jadi tidak diulang di sini. Yang di bawah hanya yang spesifik app ini.
# ------------------------------------------------------------------------------------------------

# Stack trace rilis tetap bisa dibaca dengan mapping.txt (app/build/outputs/mapping/release/).
# SIMPAN mapping.txt tiap versi yang dirilis; tanpa itu crash rilis tidak bisa diterjemahkan.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- WebView JS bridge (WebViewFetcher.Bridge) -------------------------------------------------
# Skrip JS memanggil Android.onResult / onInfo / onError berdasarkan NAMA method. Kalau R8 mengganti nama
# atau membuangnya, setiap request ke Instagram diam-diam timeout (tidak ada error, tidak ada crash).
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- Enum yang dipetakan lewat nama ----------------------------------------------------------------
# EventKind.valueOf(FollowEventType.name): nama konstanta kedua enum harus tetap sama.
-keep enum com.nofollow.app.features.tracker.data.local.FollowEventType { *; }
-keep enum com.nofollow.app.features.tracker.domain.model.EventKind { *; }

# --- WorkManager: worker diinstansiasi lewat refleksi oleh factory bawaan -------------------------
# HomeSyncJobsImpl mendaftarkan HomeSyncWorker lewat OneTimeWorkRequestBuilder<HomeSyncWorker>, dan App.kt
# TIDAK memasang Configuration.Provider/WorkerFactory kustom, jadi WorkManager pakai factory default yang
# memanggil constructor(Context, WorkerParameters) via Class.forName(...).newInstance(...).
# R8 melihat kelasnya tetap "terpakai" (lewat type literal generic), tapi constructor 2-argumen itu tidak
# pernah dipanggil langsung di kode yang bisa dilacak R8 -> di R8 full mode constructor itu bisa dibuang
# sebagai "tidak terpakai". Hasilnya NoSuchMethodException saat worker dijalankan, HANYA di build rilis,
# dan baru muncul belakangan (saat sinkron Beranda dijalankan, bukan saat app start) -- klasik "error hantu".
-keep public class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}

# --- OkHttp: provider TLS opsional yang tidak ada di Android ---------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# --- Ratakan semua class hasil obfuscate ke satu paket tanpa nama --------------------------------
# Tanpa ini, struktur paket asli (com.nofollow.app.features...) tetap kebaca di hasil decompile
# walau nama class-nya sudah diacak jadi huruf pendek. Dengan ini semua class pindah ke paket kosong
# di root, jadi hasilnya cuma daftar datar "a.class, b.class, ab.class, ..." tanpa info struktur modul
# sama sekali. R8 full mode (default di AGP 9.3.3 kamu) sudah otomatis mengizinkan penyesuaian
# modifier akses yang diperlukan supaya penggabungan paket ini tidak bentrok.
-repackageclasses ''

# --- Matikan semua log di build rilis ------------------------------------------------------------
# Semua pemanggilan android.util.Log (d, v, i, w, e) dibuang total oleh R8, termasuk dari library
# (Koin, OkHttp, WorkManager, Room), jadi logcat di build rilis kosong. Build debug tidak terpengaruh
# (R8 tidak jalan di debug), jadi tester dan log pengembangan tetap bisa dipakai.
# Ini juga membuang pembentukan string argumennya (username, pk, potongan body) bila tidak ada efek lain.
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
    public static int w(...);
    public static int e(...);
}