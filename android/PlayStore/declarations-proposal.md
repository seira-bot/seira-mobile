# Jawaban deklarasi Play Console

Status: jawaban tersimpan di Play Console pada 2026-11-19. Rilis Production
"1.0.1" (versionCode 10001) dikirim untuk review Google pada 2026-11-20.
Update 1.0.2 (versionCode 10002) menyusul sesudah desktop 0.1.182 rilis.

Pemilik rilis memberi izin di chat untuk mengisi form, menyimpan, dan mengirim
rilis untuk review.

Dasar jawaban:

- Rilis pertama: versionCode `10001`, versionName `1.0.1`, sha256 bundle
  `f334454dd3beeeab0261b67c786ebdd9b8874de5ab44a7ba6ba36f6457e50c47`.
  Bundle ini dibangun dari `main` dengan PR #16 pada 2026-11-20. Bundle ini
  tanpa mode always-on dan memakai wire id lama (`openmausbot://pair`). Bundle
  ini hanya bisa pairing dengan desktop 0.1.181 atau lebih lama. Pemilik rilis
  memilih mengirim 1.0.1 lebih dulu (2026-11-20).
- Update: versionCode `10002`, versionName `1.0.2`, dari `main` sesudah PR #11
  merge. Update ini memakai wire id desktop 0.1.182 dan membawa PR #19.
  Bundle `10000` (`19feb05`) juga ada di library Play. Play tidak menerima
  versionCode `10000` dan `10001` lagi.
- Bundle `10001` mewajibkan fitur `android.hardware.wifi`. Publishing overview
  menandai ini: Play tidak menampilkan app untuk perangkat tanpa Wi-Fi.
  Pemilik rilis memilih tetap mengirim. PR #19 membuat fitur ini opsional
  mulai 1.0.2.
- Penandatanganan: Play App Signing aktif dengan kunci dari Google. Upload key
  yang terdaftar memakai sertifikat SHA-256 `FD:4C:55:…:5F:33:C1:FD`. Play
  menerima bundle 10000 dan 10001 dengan kunci itu (cek 2026-11-20).
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

Jawaban: **All or some functionality is restricted**. Entri "Reviewer test
server" tersimpan di Console pada 2026-11-20:

- Username: alamat halaman pairing di server uji.
- Password: passphrase halaman pairing. Pemilik rilis menempelnya sendiri.
- Kotak "full access" dicentang.
- Instruksi: 487 karakter (batas Console 500).

```text
Seira connects to a Seira server. A review server is ready.
1. Open the page in the Username field in a browser. Enter the Password as the passphrase. It shows a QR code and a code like ABCD-EFGH-JKLM.
2. In the app tap Scan QR Code. Or tap Enter a code instead, enter [SERVER ADDRESS], tap Continue, enter the code, tap Connect.
3. Open Demo Assistant. Send: Create a file named note.txt with the text hello. Tap Allow once.
Each code works once. Tap Get a new code for another.
```

Alamat server dan passphrase ada hanya di Console. Jangan tulis keduanya di
repo. Server uji harus tetap menyala selama review, punya bot "Demo
Assistant", dan tidak menyimpan data pribadi. Server uji memakai wire id lama
sampai Google menyetujui 1.0.1. Upgrade server ke 0.1.182 sesudah itu, dan
sebelum review 1.0.2.

## Izin dan deklarasi terkait

| Izin atau komponen | Form di Console | Status |
|---|---|---|
| `CAMERA` | Tidak ada form | Memindai kode QR pairing dan memotret lampiran |
| `RECORD_AUDIO` | Tidak ada form | Dikte |
| `POST_NOTIFICATIONS` | Tidak ada form | Memberi tahu saat bot butuh jawaban |
| `NEARBY_WIFI_DEVICES` (`neverForLocation`), `ACCESS_LOCAL_NETWORK` | Tidak ada form | Menemukan komputer di Wi-Fi yang sama |

Build rilis tidak punya foreground service. Form "Foreground service
permissions" dan videonya tidak perlu. `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`
dan `RECEIVE_BOOT_COMPLETED` juga tidak ada di build rilis. Build debug tetap
punya mode always-on.

## Catatan rilis

Teks ada di `listing/en-US.md` dan `listing/id-ID.md`, bagian "Release notes".
Teks di bawah tersimpan di Console pada 2026-11-20 dan terkirim dengan rilis
1.0.1. Format Console:

```text
<en-US>
First release of Seira for Android. Pair your phone with Seira on your computer, then read and answer your bots from your phone.
</en-US>
<id>
Rilis pertama Seira untuk Android. Pasangkan ponsel dengan Seira di komputer, lalu baca dan jawab bot Anda dari ponsel.
</id>
```

### Catatan rilis update 1.0.2

Isi teks ini saat rilis 1.0.2 dibuat di Console:

```text
<en-US>
This update works with Seira 0.1.182 or later on your computer. Update Seira on your computer too. If your phone does not connect, pair it again. The app now also installs on devices without Wi-Fi.
</en-US>
<id>
Update ini bekerja dengan Seira 0.1.182 atau lebih baru di komputer. Perbarui juga Seira di komputer Anda. Kalau ponsel tidak tersambung, pasangkan lagi. App kini juga bisa dipasang di perangkat tanpa Wi-Fi.
</id>
```

## Negara dan rollout

- Negara: 178 negara dan wilayah, termasuk "rest of world". Terkirim dengan
  rilis 1.0.1.
- Rollout: rilis Production pertama berjalan 100%. Play memberi staged
  rollout hanya untuk update.
- Managed publishing mati. Rilis tayang sendiri saat Google menyetujuinya.

## Langkah sesudah pengiriman 1.0.1

1. Pantau keputusan Google untuk 1.0.1. Google menyebut kebanyakan review
   selesai dalam 7 hari.
2. Sesudah Google menyetujui 1.0.1, upgrade server uji ke desktop 0.1.182.
3. Sesudah desktop 0.1.182 rilis, merge PR #11. Bangun `.aab` 1.0.2
   (versionCode 10002) dari `main` dan cek tanda tangan, versi, dan hash.
4. Uji 1.0.2 di emulator dengan fixture terpisah, termasuk kartu izin
   "Allow once".
5. Di Console, pilih "Create new release" di Production. Unggah 10002, beri
   nama "1.0.2", isi "Catatan rilis update 1.0.2", lalu kirim untuk review.
6. Pemilik akun mendeklarasikan status trader sebelum Februari 2027.

Peringatan opsional di halaman rilis: file deobfuscation (mapping) dan native
debug symbols belum diunggah.
