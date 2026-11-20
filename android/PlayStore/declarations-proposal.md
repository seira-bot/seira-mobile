# Jawaban deklarasi Play Console

Status: jawaban tersimpan di Play Console pada 2026-11-19. Diperbarui
2026-11-20 untuk rilis 1.0.1. Belum ada yang dikirim untuk review.

Pemilik rilis memberi izin di chat untuk mengisi dan menyimpan form ini.
"Send app for review" tetap butuh izin pemilik rilis.

Dasar jawaban:

- Build rilis: versionCode `10001`, versionName `1.0.1`. Bundle `10000`
  (`19feb05`) sudah diunggah ke draft pada 2026-11-19, jadi Play tidak
  menerima versionCode itu lagi.
- Build rilis tidak membawa mode panggilan. `src/release/AndroidManifest.xml`
  membuang `LiveCallService`, `TalkTileService`, dan
  `FOREGROUND_SERVICE_MICROPHONE`.
- Build rilis tidak membawa mode "always on". `src/release/AndroidManifest.xml`
  membuang `AlwaysOnConnectionService`, `AlwaysOnBootReceiver`,
  `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_CONNECTED_DEVICE`,
  `RECEIVE_BOOT_COMPLETED`, dan `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`.
  Build rilis tidak punya foreground service.
- Aturan Play dibaca pada 2026-11-19: Data safety (`answer/10787469`), AI-Generated
  Content (`answer/14094294`), dan data disclosure ML Kit
  (`developers.google.com/ml-kit/android-data-disclosure`).
- Kebijakan privasi: `https://seira.bot/privacy`, bagian "Seira phone apps".

Periksa ulang semua jawaban kalau build rilis, dependensi, atau layanan berubah.

## Keputusan

| # | Keputusan | Jawaban |
|---|-----------|---------|
| D-1 | Mode panggilan di build rilis | Dibuang dari build rilis (PR #9). |
| D-2 | Server hosted yang menerima data dari app | Tidak ada. App hanya mengirim data ke komputer milik pengguna. |
| D-3 | URL kebijakan privasi | `https://seira.bot/privacy` |
| D-4 | Cara meminta penghapusan data | Kebijakan privasi, bagian "Delete your data": lepas pasangan di app, hapus app, dan hapus data di komputer sendiri. |
| D-5 | Lingkungan uji untuk reviewer | Belum siap. Instruksi App access memakai placeholder. Ganti placeholder sebelum Send for review. |

## Data safety

### Pertanyaan umum

| Pertanyaan Console | Jawaban | Alasan |
|---|---|---|
| Does your app collect or share any of the required user data types? | Yes | App mengirim pesan, lampiran, dan jawaban keluar dari ponsel. ML Kit mengirim data diagnostik ke Google. |
| Is all of the user data collected by your app encrypted in transit? | No | `res/xml/network_security_config.xml` mengizinkan HTTP polos di LAN. |
| Account creation methods | My app does not allow users to create an account | App tidak punya akun sendiri. |
| Can users login with accounts created outside of the app? | No | Pairing memakai kode, bukan akun. |
| Do you provide a way for users to request that their data is deleted? | Yes, `https://seira.bot/privacy` | Lihat D-4. |

### Tipe data

Semua tipe: **Collected**, tidak **Shared**. Tidak ada tipe Personal info,
Location, Financial info, Health and fitness, Calendar, Contacts, atau Web
browsing.

| Kategori → tipe | Tujuan | Sumber di app |
|---|---|---|
| Messages → Other in-app messages | App functionality | Composer dan transkrip chat ke komputer pengguna |
| Photos and videos → Photos | App functionality | Lampiran dan berbagi dari app lain (`ShareReceiveActivity`) |
| Audio files → Voice or sound recordings | App functionality | Dikte lewat pengenal suara platform (`dictation/SpeechDictation.kt`) |
| Files and docs → Files and docs | App functionality | Lampiran berkas lewat share |
| App activity → App interactions | App functionality | Jawaban persetujuan, balasan cepat, tampilan bot yang pengguna pilih |
| App activity → Other user-generated content | App functionality | Tugas dan instruksi yang pengguna kirim ke bot |
| App info and performance → Diagnostics | Analytics | ML Kit barcode scanning (`com.google.mlkit:barcode-scanning`) |
| Device or other IDs → Device or other IDs | App functionality | Nama perangkat saat pairing. ML Kit: per-installation identifier. |

Jumlah tipe per kategori dicek di Console pada 2026-11-19 (langkah 3, "Data types").

## Form lain

| Form | Jawaban |
|---|---|
| Privacy policy | `https://seira.bot/privacy` |
| Ads | No ads |
| Advertising ID | No. Build rilis tidak memakai `com.google.android.gms.permission.AD_ID`. |
| Government apps | No |
| Financial features | App tidak punya fitur keuangan |
| Health apps | App tidak punya fitur kesehatan |
| Content rating (IARC) | Kategori "All Other App Types". Tidak ada kekerasan, seks, bahasa kasar, narkoba, judi, interaksi antar pengguna, berbagi lokasi, atau pembelian digital. Hasil: Everyone, PEGI 3, USK 0, ClassInd All ages. |
| Target audience | 18 and over |
| App category | Productivity |
| Contact details | Email dukungan dan situs `https://seira.bot` tersimpan di Store settings |
| AI-generated content | Label AI ada pada setiap aset listing yang dibuat dengan AI |

## App access

Jawaban: **All or some functionality is restricted**.
Instruksi untuk reviewer yang tersimpan di Console:

```text
Seira pairs with a computer or server that runs Seira. We keep a test server online for review.
1. Open [PAIRING PAGE URL] in a browser. It shows a new QR code and 6-digit code each time it loads.
2. In the app, scan the QR code. Or tap "Enter a code instead", type the address [SERVER ADDRESS], then the 6-digit code.
3. Confirm the pairing.
4. Open a bot chat and send a message. Answer a bot question on the Home screen.
```

Ganti `[PAIRING PAGE URL]` dan `[SERVER ADDRESS]` setelah server uji siap.
Server uji harus tetap menyala selama review, punya minimal satu bot, dan
tidak menyimpan data pribadi.

## Izin dan deklarasi terkait

| Izin atau komponen | Form di Console | Status |
|---|---|---|
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` (`AlwaysOnConnectionService`) | Foreground service permissions: tugas, deskripsi, dan link video | **Terbuka.** Console meminta link video untuk setiap pilihan tugas. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Tidak ada form. Reviewer menilai terhadap kebijakan izin terbatas. | Risiko penolakan. Izin ini hanya untuk mode always-on yang pengguna nyalakan sendiri. |
| `CAMERA` | Tidak ada form | Memindai kode QR pairing dan memotret lampiran |
| `RECORD_AUDIO` | Tidak ada form | Dikte |
| `POST_NOTIFICATIONS` | Tidak ada form | Memberi tahu saat bot butuh jawaban |
| `NEARBY_WIFI_DEVICES` (`neverForLocation`), `ACCESS_LOCAL_NETWORK` | Tidak ada form | Menemukan komputer di Wi-Fi yang sama |
| `RECEIVE_BOOT_COMPLETED` | Tidak ada form | Menyalakan lagi koneksi always-on setelah ponsel restart, hanya kalau setting itu aktif |

Usulan untuk form foreground service `connectedDevice`:

- Tugas: "Continuous data transfer to an external device". Kalau tidak cocok,
  pilih "Other" dan pakai deskripsi di bawah.
- Deskripsi: "Keeps the connection to the user's paired computer open while the
  user turns on the always-on setting, so the phone shows a bot's question when
  it arrives."
- Video: nyalakan setting always-on, tunjukkan notifikasi yang tetap tampil,
  lalu tunjukkan pertanyaan bot yang masuk saat app di latar belakang.

## Langkah yang tersisa sebelum Send for review

1. Rekam video foreground service, unggah, lalu simpan form `connectedDevice`.
2. Buka halaman review rilis Production "1.0.0", lalu pilih Save. Rilis baru
   muncul di Publishing overview setelah langkah ini.
3. Siapkan server uji dan halaman pairing. Ganti placeholder di App access.
4. Pemilik akun mendeklarasikan status trader sebelum Februari 2027.
5. Pemilik rilis meninjau Publishing overview dan memilih "Send app for review".

Peringatan opsional di halaman rilis: file deobfuscation (mapping) dan native
debug symbols belum diunggah.
