# Usulan jawaban deklarasi Play Console

Status: usulan untuk ditinjau pemilik rilis. Belum ada yang diisi di Console.

Agen tidak boleh menjawab form ini. Pemilik rilis memutuskan setiap jawaban,
lalu mengisinya sendiri di Play Console.

Dasar usulan:

- Kandidat rilis: `main` setelah redesign PR #3 merge (`c7b84a4`), ditambah
  karakter Seira (`claude/android-character`) dan sheet Change look
  (`claude/android-character-sheet`), manifest dan dependensi pada 2026-11-19.
- Aturan Play dibaca pada 2026-11-19: Data safety (`answer/10787469`), AI-Generated
  Content (`answer/14094294`), dan data disclosure ML Kit
  (`developers.google.com/ml-kit/android-data-disclosure`).

Periksa ulang semua jawaban pada build rilis yang sudah ditandatangani.
Manifest gabungan (merged manifest) dan dependensi bisa berbeda dari sumber.

## Keputusan yang harus diambil dulu

| # | Keputusan | Mengapa penting |
|---|-----------|-----------------|
| D-1 | Apakah build rilis tetap membawa mode panggilan (`LiveCallService`, `TalkTileService`, `FOREGROUND_SERVICE_MICROPHONE`)? | Listing tidak menyebut panggilan. Kalau kode tetap ada, deklarasi foreground service mikrofon dan Data safety audio tetap wajib, dan reviewer bisa menemukannya. Pilihan yang lebih bersih: buang dari build rilis. |
| D-2 | Apakah Seira atau PT GROW menjalankan server hosted (Cloud VM, sign-in hosted) yang menerima data dari app? | Kalau ya, data ke server itu adalah "collection" oleh developer, dan kebijakan privasi wajib menjelaskannya. |
| D-3 | URL kebijakan privasi | Wajib untuk menyelesaikan Data safety, juga kalau app tidak mengumpulkan data. |
| D-4 | Cara pengguna meminta penghapusan data | Form menanyakan ini. Data utama ada di komputer pengguna. Data di server hosted (D-2) butuh jalur hapus sendiri. |
| D-5 | Lingkungan uji untuk reviewer (App access) | Semua fitur butuh komputer dengan Seira desktop. Reviewer butuh komputer uji yang bisa dijangkau lewat alamat https. |

## Data safety

### Pertanyaan umum

| Pertanyaan Console | Usulan | Alasan dan sumber |
|---|---|---|
| Does your app collect or share any of the required user data types? | Yes | App mengirim pesan, lampiran, dan suara keluar dari ponsel ke komputer pengguna. Definisi Play: "collect" berarti data dikirim keluar dari perangkat. Play tidak punya pengecualian umum untuk server milik pengguna. ML Kit juga mengirim data diagnostik ke Google. |
| Is all of the user data collected by your app encrypted in transit? | No | `res/xml/network_security_config.xml` mengizinkan HTTP polos di LAN (`cleartextTrafficPermitted="true"`). Rute https dan Tailscale ada, tetapi tidak wajib. |
| Do you provide a way for users to request that their data is deleted? | TBD (D-4) | Pengguna bisa menghapus percakapan di komputernya sendiri. Lepas pasangan menghapus kredensial di ponsel. Jalur untuk data hosted belum diketahui. |

### Tipe data

Semua baris di bawah memakai tujuan **App functionality**, kecuali ML Kit.
Usulan "Shared": **No** untuk semua baris. Data pergi ke komputer milik
pengguna sendiri atas tindakan pengguna, dan ML Kit tidak meneruskan data ke
pihak ketiga.

| Kategori → tipe | Collected | Opsional? | Ephemeral? | Sumber di app |
|---|---|---|---|---|
| Messages → Other in-app messages | Yes | No (fitur inti) | No | Composer dan transkrip chat ke komputer pengguna |
| Photos and videos → Photos | Yes | Yes | No | Lampiran dan berbagi dari app lain (`ShareReceiveActivity`, `SEND`/`SEND_MULTIPLE`) |
| Files and docs → Files and docs | Yes | Yes | No | Lampiran PDF dan berkas office lewat share |
| Audio files → Voice or sound recordings | Yes (lihat D-1) | Yes | Dikte: Yes. Panggilan: TBD | Dikte memakai pengenal suara platform yang bisa memakai jaringan (`dictation/SpeechDictation.kt`). Panggilan mengalirkan mikrofon ke komputer lewat WebRTC (`audio/LiveCallService`). |
| App activity → App interactions | Yes | No | No | Jawaban persetujuan (Allow/Deny), pilihan balasan cepat, tampilan bot yang pengguna pilih di Change look |
| App activity → Other user-generated content | Yes | No | No | Tugas dan instruksi yang pengguna kirim ke bot |
| Personal info → Name, Email address | TBD (D-2) | — | — | Profil di Settings menampilkan nama dan email dari komputer. Tandai Yes hanya kalau app mengirimnya keluar. |
| Device or other IDs → Device or other IDs | Yes | No | No | Nama perangkat saat pairing (`deviceName`). ML Kit: "Per-installation identifiers". |
| App info and performance → Diagnostics | Yes | No | No | ML Kit barcode scanning (`com.google.mlkit:barcode-scanning:17.3.0`): "Used for diagnostics and usage analytics", dienkripsi https, tidak dibagikan. Tujuan: **Analytics**. |

Tipe yang diusulkan **tidak** ditandai: Location, Financial info, Health and
fitness, Calendar, Contacts, Web browsing, Installed apps, SMS, Crash logs.
Tidak ada SDK iklan, analitik pihak ketiga, atau crash reporter di dependensi.

Periksa juga `allowBackup="true"` dengan `data_extraction_rules`. Pastikan
kredensial pairing tidak ikut backup cloud.

## Content rating (kuesioner IARC)

| Pertanyaan | Usulan | Alasan |
|---|---|---|
| Kategori | All Other App Types (utilitas atau produktivitas) | App ini pendamping kerja, bukan game atau media sosial |
| Kekerasan, seks, bahasa kasar, narkoba, judi | No | App tidak membawa konten itu. Bot milik pengguna yang membuat isi chat. |
| Interaksi antar pengguna atau berbagi konten antar pengguna | No | Pengguna hanya bicara dengan bot di komputernya sendiri. Tidak ada pengguna lain. |
| Berbagi lokasi | No | App tidak memakai izin lokasi (`NEARBY_WIFI_DEVICES` memakai `neverForLocation`). |
| Pembelian digital | No | App gratis, tanpa in-app purchase |

## AI-Generated Content policy

Usulan: anggap app ini **masuk ruang lingkup** sebagai "Text-to-text AI chatbot
apps". Interaksi dengan bot AI adalah fitur pusat app.

Konsekuensi:

- Developer bertanggung jawab agar app tidak menampilkan konten terlarang.
  Bot berjalan dengan model dan kunci milik pengguna, tetapi reviewer tetap
  menilai app.
- Siapkan jawaban untuk reviewer: model dipilih pengguna di komputernya, dan app
  ponsel hanya menampilkan hasil kerja bot milik pengguna.
- Pertimbangkan tombol lapor atau umpan balik pada balasan bot. Halaman kebijakan
  yang dibaca 2026-11-19 tidak menyebut kewajiban itu secara tertulis, tetapi
  reviewer sering memintanya.

## Target audience and content

| Pertanyaan | Usulan | Alasan |
|---|---|---|
| Target age groups | 18 and over | Alat kerja untuk orang dewasa yang menjalankan bot di komputer |
| Appeals to children? | No | Tidak ada konten atau desain untuk anak |

## Ads

| Pertanyaan | Usulan | Alasan |
|---|---|---|
| Does your app contain ads? | No | Tidak ada SDK iklan |
| Advertising ID | No | Manifest sumber tidak punya `com.google.android.gms.permission.AD_ID`. Periksa manifest gabungan build rilis. |

## App access

Usulan: **All or some functionality is restricted**.

Draf instruksi untuk reviewer (isi setelah D-5 selesai):

1. Buka app, lalu pilih "Enter a code instead".
2. Masukkan alamat https komputer uji dan kode pairing dari daftar di bawah.
3. Konfirmasi nama komputer.

Komputer uji harus tetap menyala selama review, punya minimal satu bot, dan
punya data contoh tanpa data pribadi. Jangan janjikan akses tanpa akun atau
video demo sebelum keduanya benar-benar ada.

## Izin dan deklarasi terkait

| Izin atau komponen | Form di Console | Usulan penjelasan | Catatan |
|---|---|---|---|
| `FOREGROUND_SERVICE_CONNECTED_DEVICE` (`AlwaysOnConnectionService`) | Foreground service permissions: wajib deskripsi dan link video | "Keeps the connection to the user's paired computer open while the user turns on the always-on setting, so the phone shows a bot's question when it arrives." | Tipe `connectedDevice` mencakup koneksi jaringan ke perangkat eksternal. Rekam video setting dinyalakan dan notifikasi tetap tampil. |
| `FOREGROUND_SERVICE_MICROPHONE` (`LiveCallService`) | Foreground service permissions | Hanya kalau D-1 = tetap dibawa. "Keeps a live voice call with the user's bot running while the screen is off." | Kalau mode panggilan dibuang dari rilis, buang juga izin ini. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Tidak ada form. Reviewer menilai terhadap kebijakan izin terbatas. | Hanya untuk mode always-on yang pengguna nyalakan sendiri. | Risiko penolakan. Pertimbangkan membuang izin ini dan memakai halaman pengaturan baterai sistem. |
| `CAMERA` | Tidak ada form | Memindai kode QR pairing | Runtime permission |
| `RECORD_AUDIO` | Tidak ada form | Dikte (dan panggilan, D-1) | Runtime permission |
| `POST_NOTIFICATIONS` | Tidak ada form | Memberi tahu saat bot butuh jawaban | Runtime permission |
| `NEARBY_WIFI_DEVICES` (`neverForLocation`), `ACCESS_LOCAL_NETWORK` | Tidak ada form | Menemukan komputer di Wi-Fi yang sama | Bukan izin lokasi |
| `RECEIVE_BOOT_COMPLETED` | Tidak ada form | Menyalakan lagi koneksi always-on setelah ponsel restart, hanya kalau setting itu aktif | |
| Quick Settings tile (`TalkTileService`) | Tidak ada form | Bagian mode panggilan | Ikut keputusan D-1 |

Form lain yang diusulkan dijawab **No** atau **Not applicable**: Government
apps, Financial features, Health, News, COVID-19, dan Data safety untuk anak.
App tidak memakai izin SMS, Call log, lokasi, akses semua berkas,
`QUERY_ALL_PACKAGES`, exact alarm, full-screen intent, atau accessibility.
