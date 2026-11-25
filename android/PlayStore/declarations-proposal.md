# Jawaban deklarasi Play Console

Status: jawaban tersimpan di Play Console pada 2026-11-19. Rilis Production
"1.0.1" (versionCode 10001) tayang pada 2026-11-20. Update 1.0.2
(versionCode 10002) membawa karakter Seira 3D dan memakai wire id lama.
Google menyetujui 1.0.2. Rilis "1.0.2" tayang di Play pada 2026-11-20: 178
negara, rollout 100%.
Update 1.0.3 (versionCode 10003) membawa PR #11 (wire id lama dan wire id
Seira) dan PR #27 (pairing mencoba semua alamat). Google menyetujui 1.0.3.
Rilis "1.0.3" tayang di Play pada 2026-11-21: 178 negara, rollout 100%.
Update 1.0.4 (versionCode 10004) dikirim untuk review pada 2026-11-21: Production,
semua negara target, rollout 100%. Bundle dibangun dari `main` `0e5daef4`.
Google menyetujui 1.0.4. Rilis "1.0.4" tayang di Play pada 2026-11-21: 178 negara,
rollout 100%.
Update 1.0.5 (versionCode 10005) dikirim untuk review pada 2026-11-21: Production,
semua negara target, rollout 100%. Bundle dibangun dari `main` `b5d8186e`.
Google menyetujui 1.0.5. Rilis "1.0.5" tayang di Play pada 2026-11-22.
Update 1.0.6 (versionCode 10006) membawa Glance (PR #70), Try again di bawah
setiap giliran gagal (PR #63), sheet tampilan bot dengan tiga tab (PR #64), dan
pilihan tempat kerja bot dari catatan cloud (PR #65).
Update 1.0.6 dikirim untuk review pada 2026-11-23: Production, semua negara
target, rollout 100%. Bundle dibangun dari tree yang sama dengan `main` `603020e4`.
Google menyetujui 1.0.6. Rilis "1.0.6" tayang di Play pada 2026-11-23: 178 negara.
Update 1.0.7 (versionCode 10007) membawa ikon app baru "Malam" (PR #73) dan
layar pembuka "Night visor" (PR #74).
Update 1.0.7 dikirim untuk review pada 2026-11-24: Production, semua negara
target, rollout 100%. Bundle dibangun dari `184b500a`. Kode rilisnya sama dengan
`main` `34e4bb49`. Review yang sama memuat 9 perubahan listing dari PR #75,
termasuk ikon app 512 px.
Google menyetujui 1.0.7 dan perubahan listing. Rilis "1.0.7" tayang di Play pada
2026-11-24: 178 negara.
Update 1.0.8 (versionCode 10008) membawa perbaikan kartu Connected Apps untuk app
tanpa login (seira-bot/seira#443, PR #89), perbaikan notifikasi (PR #69, #81),
chip kode yang menunggu kode pertama (PR #79), dan pemilih workspace (PR #88).
Update 1.0.8 dikirim untuk review pada 2026-11-25: Production, semua negara
target, rollout 100%. Bundle dibangun dari `5145e987`. Tree-nya sama dengan
`main` `ac77e71d`.

Pemilik rilis memberi izin di chat untuk mengisi form, menyimpan, dan mengirim
rilis untuk review.

Dasar jawaban:

- Rilis pertama: versionCode `10001`, versionName `1.0.1`, sha256 bundle
  `f334454dd3beeeab0261b67c786ebdd9b8874de5ab44a7ba6ba36f6457e50c47`.
  Bundle ini dibangun dari `main` dengan PR #16 pada 2026-11-20. Bundle ini
  tanpa mode always-on dan memakai wire id lama (`openmausbot://pair`). Bundle
  ini hanya bisa pairing dengan desktop 0.1.181 atau lebih lama. Pemilik rilis
  memilih mengirim 1.0.1 lebih dulu (2026-11-20).
- Update 1.0.2: versionCode `10002`, dari `main` sesudah PR #19, #22, dan
  #24. Update ini memakai wire id lama, jadi bisa pairing dengan desktop
  0.1.181 dan server uji 0.1.180. Pemilik rilis memilih ini pada 2026-11-20.
  Sha256 bundle
  `35966bc1eeff7876459ea052952612412a98bc1dc888a1e20c9ecce69415bf4f`, upload
  key `FD:4C:…:C1:FD`. Desktop 0.1.182 tetap menerima wire id lama (FR-216),
  jadi 1.0.2 juga bisa pairing dengan 0.1.182.
- Update 1.0.3: versionCode `10003`, dari `main` sesudah PR #11 dan PR #27
  merge. Update ini membaca wire id lama dan wire id Seira, dan mengirim wire
  id lama. Jadi update ini bisa pairing dengan desktop 0.1.181 dan 0.1.182.
  Bundle dibangun dari `ae435d9`. Sha256 bundle
  `c20c1c431fe21945fa81aaaa8c324a311342380fd07b4b0b9b050bb224c53fc1`, upload
  key `FD:4C:…:C1:FD`. Fitur wajib hanya `android.hardware.faketouch`.
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
| AI-generated content | Label AI ada pada setiap aset listing yang dibuat dengan AI. Sejak 2026-11-24, 2 feature graphic dan 12 screenshot memakai label AI karena latar malamnya dibuat dengan AI. Ikon 512 tidak memakai label. |

## App access

Jawaban: **All or some functionality is restricted**. Entri "Reviewer test
server" tersimpan di Console pada 2026-11-20:

- Username: alamat halaman pairing di server uji.
- Password: passphrase halaman pairing. Pemilik rilis menempelnya sendiri.
- Kotak "full access" dicentang.
- Instruksi: kurang dari 500 karakter (batas Console). Console memuat teks
  di bawah, dengan "Tap Allow.", sejak rilis 1.0.2 (2026-11-20).

```text
Seira connects to a Seira server. A review server is ready.
1. Open the page in the Username field in a browser. Enter the Password as the passphrase. It shows a QR code and a code like ABCD-EFGH-JKLM.
2. In the app tap Scan QR Code. Or tap Enter a code instead, enter [SERVER ADDRESS], tap Continue, enter the code, tap Connect.
3. Open Demo Assistant. Send: Create a file named note.txt with the text hello. Tap Allow.
Each code works once. Tap Get a new code for another.
```

Alamat server dan passphrase ada hanya di Console. Jangan tulis keduanya di
repo. Server uji harus tetap menyala selama review, punya bot "Demo
Assistant", dan tidak menyimpan data pribadi. Server uji tetap memakai wire
id lama selama review 1.0.2. Server uji naik ke 0.1.182 pada 2026-11-21,
sebelum review 1.0.3. Uji pairing dengan kode lulus sesudah upgrade. Link QR
server tetap memakai `openmausbot://pair` (FR-216). App 1.0.3 menerima link
ini.

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
The welcome screen and Settings now show the 3D Seira character. The app now also installs on devices without Wi-Fi. Clearer help when Seira looks for your computer on Wi-Fi.
</en-US>
<id>
Layar sambutan dan Settings kini menampilkan karakter Seira 3D. App kini juga bisa dipasang di perangkat tanpa Wi-Fi. Petunjuk lebih jelas saat Seira mencari komputer Anda di Wi-Fi.
</id>
```

### Catatan rilis update 1.0.3

Isi teks ini saat rilis 1.0.3 dibuat di Console:

```text
<en-US>
Pairing now tries every address you allowed at once: same Wi-Fi, Tailscale and the internet address. The app moves to an address that works when your network changes. If Tailscale is off on this phone, the app tells you and opens Tailscale.
This version also works with Seira 0.1.182 and later on your computer.
</en-US>
<id>
Pairing sekarang mencoba semua alamat yang kamu izinkan sekaligus: Wi-Fi yang sama, Tailscale, dan alamat internet. Aplikasi pindah ke alamat yang jalan saat jaringan berubah. Kalau Tailscale di HP mati, aplikasi memberi tahu dan membuka Tailscale.
Versi ini juga bekerja dengan Seira 0.1.182 dan yang lebih baru di komputer.
</id>
```

### Catatan rilis update 1.0.4

Isi teks ini saat rilis 1.0.4 dibuat di Console:

```text
<en-US>
New cards in chat: widgets, goal runs, task batches, call summaries, sign-in and app connection requests, and a safe way to send a password or key to your computer. Switch the model after a failed reply. See your AI plan usage. A home screen widget shows what waits for you. Updates from Google Play install inside the app. Signing out also ends the session on your computer. Now in 13 more languages.
Some features need the newest Seira on your computer.
</en-US>
<id>
Kartu baru di chat: widget, goal run, tugas berkelompok, ringkasan panggilan, permintaan sign-in dan koneksi app, serta cara aman mengirim password atau key ke komputer. Ganti model setelah balasan gagal. Lihat pemakaian paket AI. Widget layar utama menampilkan yang menunggu kamu. Update dari Google Play terpasang di dalam app. Sign out juga mengakhiri sesi di komputer. Kini tersedia dalam 13 bahasa lagi.
Beberapa fitur butuh Seira terbaru di komputer.
</id>
```

### Catatan rilis update 1.0.5

Isi teks ini saat rilis 1.0.5 dibuat di Console:

```text
<en-US>
Switch to another paired computer from the Chats screen: tap the computer name. A new pairing with a second server at the same address no longer replaces the first computer. Sending a password or key no longer stops when its card scrolls off the screen.
</en-US>
<id>
Pindah ke komputer lain yang sudah dipasangkan langsung dari layar Chats: ketuk nama komputer. Pairing server kedua di alamat yang sama tidak lagi menggantikan komputer pertama. Kirim password atau key tidak lagi berhenti saat kartunya tergulir keluar layar.
</id>
```

### Catatan rilis update 1.0.6

Isi teks ini saat rilis 1.0.6 dibuat di Console:

```text
<en-US>
See what your bots do without opening the app. One notification shows the bot that needs you most, with Deny, Allow once and Open chat. On Android 16 it shows as a Live Update. Try again now shows under every failed or stopped reply. Choose a bot's look from three tabs: Seira, Generate and Upload. An admin can choose where a bot works from the cloud notice.
</en-US>
<id>
Lihat kerja bot tanpa membuka app. Satu notifikasi menampilkan bot yang paling butuh kamu, dengan Deny, Allow once, dan Open chat. Di Android 16 notifikasi ini tampil sebagai Live Update. Try again kini muncul di bawah setiap balasan yang gagal atau berhenti. Pilih tampilan bot dari tiga tab: Seira, Generate, dan Upload. Admin bisa memilih tempat bot bekerja dari catatan cloud.
</id>
```

### Catatan rilis update 1.0.7

Isi teks ini saat rilis 1.0.7 dibuat di Console:

```text
<en-US>
Seira has a new look. The app icon is Seira with a wink on a night tile, and it works with themed icons too. The app now opens on a night screen: Seira's eyes glow, blink once, and a circle opens into the app. No more white flash before a dark screen.
</en-US>
<id>
Seira tampil baru. Ikon app kini Seira yang mengedip di tile malam, dan cocok juga dengan ikon bertema. App kini terbuka di layar malam: mata Seira menyala, berkedip sekali, lalu lingkaran terbuka ke dalam app. Tidak ada lagi kilatan putih sebelum layar gelap.
</id>
```

### Catatan rilis update 1.0.8

Isi teks ini saat rilis 1.0.8 dibuat di Console:

```text
<en-US>
Fixes: Connected Apps no longer shows a Connect button for an app that needs no sign-in, such as Composio. A tap on a notification opens the computer it came from. When a notification action has no computer, the app now says so. The Show diff and Run tests chips show only after the chat has run code.
</en-US>
<id>
Perbaikan: Connected Apps tidak lagi menampilkan tombol Connect untuk app yang tidak butuh login, seperti Composio. Ketukan pada notifikasi membuka komputer asal notifikasi itu. Saat aksi notifikasi tidak punya komputer, app kini memberi tahu. Chip Show diff dan Run tests baru muncul setelah chat menjalankan kode.
</id>
```

## Negara dan rollout

- Negara: 178 negara dan wilayah, termasuk "rest of world". Terkirim dengan
  rilis 1.0.1.
- Rollout: rilis Production pertama berjalan 100%. Play memberi staged
  rollout hanya untuk update.
- Managed publishing mati. Rilis tayang sendiri saat Google menyetujuinya.

## Langkah sesudah 1.0.1 tayang

1. Selesai 2026-11-20: Google menyetujui 1.0.1. App tayang di Play.
2. Selesai 2026-11-20: uji 1.0.1 terhadap server uji lulus. Kartu izin memakai
   tombol "Allow" dan "Deny".
3. Selesai 2026-11-20: `.aab` 1.0.2 (versionCode 10002) dibangun dari `main`.
   Tanda tangan, versi, dan hash cocok. Build ini membawa karakter Seira 3D
   (PR #22) dan fitur Wi-Fi opsional (PR #19).
4. Sebagian 2026-11-20: build rilis 10002 jalan di emulator. Karakter 3D
   tampil, tanpa crash. Pairing dan kartu izin tidak diuji ulang di 10002.
5. Selesai 2026-11-20: rilis Production "1.0.2" dibuat dengan 10002 dan
   "Catatan rilis update 1.0.2". App access langkah 3 sudah "Tap Allow.".
   Rilis dikirim untuk review. Google menyetujui 1.0.2 pada 2026-11-20.
   Console: "Available on Google Play", versionCode 10002, 178 negara.
6. Selesai 2026-11-21: PR #27 dan PR #11 merge ke `main` (`ae435d9`).
   Bundle 10003 dibangun dari `main`. Pairing kode, link, dan chat lulus
   terhadap fixture desktop 0.1.182. Pairing kode dan chat lulus terhadap
   fixture 0.1.181. Server uji naik ke 0.1.182. Rilis Production "1.0.3"
   dibuat dengan "Catatan rilis update 1.0.3", 178 negara, rollout 100%.
   Rilis dikirim untuk review pada 2026-11-21 00:53 WIB. Google menyetujui
   1.0.3. Console: "Available on Google Play", "Released on Nov 21 1:16 AM".
   Pemilik rilis memberi izin di chat pada
   2026-11-20: "lanjut sampai rilis 1.0.3".
7. Pemilik akun mendeklarasikan status trader sebelum Februari 2027.
8. Console (2026-11-20, dashboard rilis 1.0.2): "DEX code optimization is
   below our threshold". Kategori Obfuscation 0%. Batas perbaikan: Februari
   2027. Penyebab: build rilis memakai `isMinifyEnabled = false`. Perbaikan:
   nyalakan R8 dengan keep rule yang sudah diuji pada pairing dan sesi nyata.
   PR #33 merge ke `main` (`92d1a82`) pada 2026-11-21: R8 aktif. 1.0.3 tidak
   memuat R8, jadi peringatan ini hilang paling cepat di rilis berikutnya.
   Unggah file mapping bersama bundle rilis itu.

Peringatan opsional di halaman rilis: file deobfuscation (mapping) dan native
debug symbols belum diunggah.
