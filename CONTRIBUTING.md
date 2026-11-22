# Kontribusi ke Nofollow

Makasih mau kontribusi! Sebelum bikin PR, baca ini dulu — sebagian besar PR yang ditolak itu bukan soal logika salah, tapi soal melanggar aturan layer di bawah.

## Aturan wajib: batas antar-layer

Proyek ini Clean Architecture per-fitur (`features/<nama>/domain|data|ui`). Aturannya cuma satu arah:

```
ui  ──depend──>  domain  <──implements──  data
```

- **`domain/`** cuma boleh import: Kotlin standar, coroutines, dan package `domain/` lain. **Tidak boleh** import apapun dari `data/`, dan **tidak boleh** import `android.*`/`androidx.*` (kecuali `kotlin.*`/coroutines). Kalau use case kamu butuh akses network/database/WorkManager, taruh behind interface di `domain/repository` atau `domain/<nama lain>`, implementasinya baru ditaruh di `data/`.
- **`ui/`** (Compose screen + ViewModel) cuma boleh kenal `domain/` (use case, model). **Tidak boleh** import class dari `data/` langsung, walau cuma buat baca config.
- **`data/`** boleh depend ke `domain/` (implement interface-nya) dan ke framework Android/library lain sepuasnya.

Sebelum push, jalanin cek cepat ini (ganti nama fitur sesuai yang kamu sentuh):
```bash
# domain -> data (harus kosong)
grep -rl "import com\.nofollow\.app\..*\.data\." app/src/main/java --include="*.kt" | grep "/domain/"

# ui -> data (harus kosong)
grep -rl "import com\.nofollow\.app\..*\.data\." app/src/main/java --include="*.kt" | grep "/ui/"
```
Kalau ada hasil, berarti ada yang nembus layer — pindahin dulu ke `domain/model` (kalau dia pure Kotlin tanpa dependency framework) atau bungkus dengan use case/interface (kalau dia infrastruktur beneran, misal network/DB).

## Pola repository

- Interface di `domain/repository/XxxRepository.kt`
- Implementasi di `data/repository/XxxRepositoryImpl.kt`
- Didaftarkan di module Koin terkait lewat `singleOf(::XxxRepositoryImpl) bind XxxRepository::class`

Ini juga berlaku buat hal-hal non-"repository" yang sifatnya I/O (network test, file probe, dll) — bikin interface tipis di `domain/`, implementasinya di `data/`, dan use case di `domain/usecase` yang depend ke interface itu, bukan ke implementasinya langsung.

## Dependency Injection

Pakai **Koin**, bukan Hilt/Dagger. Module di `di/*Module.kt`:
- `authModule`, `coreModule`, `trackerModule`, `homeModule`, `kepoModule`, `onboardingModule`, `updateModule` — daftarin binding baru di module yang paling relevan dengan fitur kamu.
- Use case baru: `factoryOf(::NamaUseCase)`
- Repository baru: `singleOf(::XxxRepositoryImpl) bind XxxRepository::class`

## Pacing / rate-limit — HATI-HATI

Angka-angka di `SyncConfig` (jeda antar-request, plafon harian, jam tenang) itu bukan angka sembarangan — itu yang bikin app ini relatif aman dipakai tanpa memicu pembatasan dari Instagram. **Jangan** PR yang mempercepat pacing atau menaikkan plafon harian tanpa alasan kuat dan penjelasan risiko di deskripsi PR. Kalau mau eksperimen soal ini, buka issue dulu buat didiskusikan.

## Log dan data pribadi

Build rilis membuang semua `Log.*` lewat R8 (`-assumenosideeffects` di `app/proguard-rules.pro`), tapi build debug tidak. Jangan pernah menaruh username, pk, cookie, atau potongan response Instagram di log atau di kode yang akan di-commit. Kalau melampirkan log di issue atau PR, **hapus dulu semua username, pk, dan cookie**. Sesi login dan isi response itu data pribadi.

## Gaya kode

- Kotlin official code style (`kotlin.code.style=official`, sudah di-set di `gradle.properties`) — biarin default IDE/ktlint kalau ada.
- Komentar KDoc boleh Bahasa Indonesia (mengikuti gaya yang sudah ada di codebase) atau Inggris — konsisten dalam satu file.
- Nama use case: kata kerja + "UseCase" (`SyncFollowersUseCase`, bukan `FollowerSyncer`).

## Sebelum buka PR

1. `./gradlew assembleDebug` jalan tanpa error.
2. Cek layering di atas sudah bersih.
3. Kalau nambah dependency baru (library), jelasin alasannya di deskripsi PR — termasuk apakah dia butuh R8 keep-rule tambahan di `app/proguard-rules.pro` (lihat komentar di file itu untuk pola yang sudah ada).
4. PR kecil & fokus satu perubahan lebih gampang direview daripada PR besar gabungan banyak hal.

## Lisensi kontribusi

Dengan nge-submit PR, kamu setuju kontribusimu dilisensikan di bawah [GPL-3.0](LICENSE) yang sama dengan proyek ini.