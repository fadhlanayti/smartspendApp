# PRD SmartSpend: Android Personal Finance Manager

**Versi:** 1.0 (MVP Scope) | **Platform:** Android 8.0+ (API 26+) | **Status:** Siap eksekusi engineering | **Bahasa produk:** Indonesia

---

## 1. Executive Summary & Value Proposition

### 1.1 Problem Statement

Pengguna Indonesia memakai banyak kanal pembayaran sekaligus: m-banking (BCA, Mandiri, BRI), e-wallet (DANA, GoPay, ShopeePay, OVO), plus uang tunai. Akibatnya:

1. **Pencatatan manual membosankan.** Mayoritas pengguna berhenti mencatat dalam 2-3 minggu karena harus membuka aplikasi dan mengetik setiap transaksi.
2. **Data tersebar.** Tidak ada satu tempat yang menjawab "bulan ini saya sudah habis berapa dan sisa jatah berapa?".
3. **Aplikasi finansial yang ada meminta akses berlebihan.** Banyak yang meminta login internet banking, membaca SMS penuh, atau mengunggah data ke server, sehingga menimbulkan risiko privasi.
4. **Visibilitas rendah.** Informasi budget harus dicari di dalam aplikasi, padahal keputusan belanja terjadi di momen singkat (di kasir, di checkout).

### 1.2 Target Pengguna

| Persona | Profil | Kebutuhan utama |
| --- | --- | --- |
| **Rani, 24, karyawan baru** | Gaji bulanan, 3-4 e-wallet, sering jajan online | Tahu sisa jatah bulan ini tanpa input manual |
| **Dimas, 21, mahasiswa** | Uang saku mingguan, banyak transaksi kecil (GoPay, DANA, tunai) | Burn rate mingguan, alarm saat hampir habis |
| **Bu Sari, 38, ibu rumah tangga** | Mengelola belanja keluarga, transfer BCA, banyak tunai | Catat tunai cepat, laporan kategori sederhana |
| **Fajar, 29, freelancer** | Pemasukan tidak tetap, beberapa rekening | Cashflow bersih, pemisahan pemasukan vs pengeluaran |

**Segmen utama:** pengguna Android 18-40 tahun di kota besar dan menengah Indonesia yang sudah memakai minimal satu e-wallet dan satu rekening bank.

### 1.3 Competitive Advantage

| Pilar | Deskripsi | Pembeda terhadap kompetitor |
| --- | --- | --- |
| **Privacy-first** | Seluruh parsing dan penyimpanan terjadi di perangkat; database dienkripsi SQLCipher; tidak ada akun, tidak ada server | Banyak aplikasi lokal mengunggah data ke cloud |
| **Zero internet dependency** | Aplikasi tidak meminta izin `INTERNET`; fungsi penuh 100% offline | Dapat dibuktikan lewat manifest di Play Store |
| **Zero-friction** | Transaksi tercatat otomatis sebelum pengguna sempat membuka aplikasi | Kompetitor mengandalkan input manual atau SMS parsing |
| **Glanceable** | Widget bergaya iOS menampilkan sisa budget tanpa membuka aplikasi | Widget kompetitor umumnya sekadar shortcut |

### 1.4 Success Metrics (target 90 hari setelah rilis)

| Metrik | Target |
| --- | --- |
| Auto-capture rate (transaksi terdeteksi otomatis / total transaksi) | >= 80% |
| Parsing accuracy (nominal + tipe benar) | >= 97% pada rule yang didukung |
| Duplikasi lolos ke UI | < 0,5% |
| Retensi D30 | >= 35% |
| Pengguna dengan minimal 1 widget terpasang | >= 50% |
| Crash-free sessions | >= 99,5% |
| Rata-rata waktu input manual | < 5 detik |

### 1.5 Non-Goals (MVP)

- Tidak ada sinkronisasi cloud atau multi-device (ekspor/impor file lokal terenkripsi menjadi mitigasi).
- Tidak ada integrasi API bank atau open banking.
- Tidak ada dukungan iOS.
- Tidak membaca SMS (hanya notifikasi).
- Tidak ada fitur investasi atau tagihan berulang pada MVP. Fitur utang-piutang dan Rencana Beli masuk cakupan lewat bagian 9.

---

## 2. Arsitektur Sistem & Flow Data

### 2.1 Diagram Alur Data

```
[Status Bar OS: notifikasi bank/e-wallet masuk]
            |
            v
[NotificationListenerService.onNotificationPosted()]
   - filter packageName whitelist (dari notification_rules)
   - ekstrak title, text, bigText, subText, postTime
   - abaikan notifikasi grup/summary, ongoing, dan milik aplikasi sendiri
            |
            v
[Raw Event Queue (Channel Kotlin / WorkManager expedited)]
   - simpan raw event dulu agar tidak hilang bila proses mati
            |
            v
[Regex Parser Engine]
   - pilih rule berdasarkan package + prioritas
   - ekstrak: nominal, tipe (debit/kredit/transfer), merchant, akun, saldo (opsional)
   - hasil: ParsedTransaction atau ParseFailure
            |
      +-----+------------------+
      |                        |
   sukses                   gagal
      |                        v
      |              [Fallback Pipeline -> Inbox "Perlu Dicek"]
      v
[Deduplication Engine]
   - hitung hash (SHA-256) + window 30-60 detik
   - duplikat -> buang / gabungkan sumber
            |
            v
[Auto-Categorizer]
   - merchant -> kategori (aturan keyword + riwayat koreksi pengguna)
            |
            v
[Local Encrypted DB (Room + SQLCipher)]
   - insert transaksi + update saldo akun dalam satu DB transaction
            |
            v
[Reactive Layer: Flow<List<Transaction>> + WidgetStateStore]
            |
     +------+-------------------+
     |                          |
[UI Compose (Dashboard)]   [Glance AppWidget update + budget alert notification]
```

### 2.2 Komponen Utama

| Komponen | Tanggung jawab | Catatan teknis |
| --- | --- | --- |
| `SpendNotificationListener` | Menerima notifikasi OS | Ringan: hanya filter + enqueue, maksimal 50 ms per event |
| `RawEventStore` | Menyimpan event mentah sementara (tabel `raw_events`, retensi 7 hari) | Menjamin tidak ada kehilangan data bila parsing crash |
| `ParserEngine` | Menjalankan rule regex | Murni Kotlin, tanpa dependensi Android agar mudah diuji unit |
| `DedupEngine` | Mencegah duplikasi | Lihat bagian 4.4 |
| `CategoryEngine` | Menebak kategori dari merchant | Belajar dari koreksi pengguna (tabel `merchant_category_map`) |
| `BudgetEvaluator` | Menghitung pemakaian vs limit | Memicu notifikasi lokal pada ambang peringatan |
| `WidgetUpdater` | Menulis state ke widget dan memicu refresh | Debounce 2 detik agar tidak boros baterai |

### 2.3 Strategi Battery Optimization & Anti-Kill (MIUI, OneUI, ColorOS, dll.)

`NotificationListenerService` dikelola sistem; bila izin aktif, OS yang mengikat (bind) service tersebut. Masalahnya, beberapa OEM membunuh proses aplikasi sehingga binding terputus. Strategi berlapis:

**Lapis 1: Disiplin dasar**

- Service tidak menyimpan state berat; semua kerja dilempar ke WorkManager (expedited) agar selesai walau proses dibunuh di tengah jalan.
- Panggil `NotificationListenerService.requestRebind(ComponentName)` saat `onListenerDisconnected()` dan dari `BootReceiver` / `WorkManager` periodik.
- Tidak memakai foreground service permanen pada MVP (menghindari notifikasi tetap yang mengganggu); disediakan sebagai opsi "Mode Andal" di pengaturan.

**Lapis 2: Watchdog ringan**

- `PeriodicWorkRequest` tiap 15 menit memeriksa `NotificationManagerCompat.getEnabledListenerPackages()` dan status koneksi listener; bila terputus, panggil rebind dan catat ke log diagnostik lokal.
- Saat aplikasi dibuka, tampilkan banner jika listener tidak aktif.

**Lapis 3: Panduan OEM (onboarding terpandu)**

| OEM | Pengaturan yang diarahkan | Intent / deep link |
| --- | --- | --- |
| Xiaomi (MIUI/HyperOS) | Autostart ON; Battery saver -> "Tanpa batasan"; kunci aplikasi di Recent Apps | `com.miui.securitycenter` -> `AutoStartManagementActivity` |
| Samsung (OneUI) | Battery -> "Unrestricted"; keluarkan dari "Sleeping apps" dan "Deep sleeping apps" | `Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` |
| OPPO/Realme (ColorOS) | Allow auto-launch; Allow background activity; kunci di Recent Apps | `com.coloros.safecenter` -> `StartupAppListActivity` |
| Vivo (Funtouch) | Autostart; High background power consumption | `com.vivo.permissionmanager` |
| Lainnya | Batasan baterai standar | `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` |

Deteksi OEM dengan `Build.MANUFACTURER`. Setiap intent dibungkus `try/catch` dengan fallback ke halaman info aplikasi (`ACTION_APPLICATION_DETAILS_SETTINGS`). Layar panduan memakai ilustrasi langkah demi langkah per OEM dan tombol "Buka Pengaturan".

**Lapis 4: Pemulihan data hilang**

- Bila ada gap (listener mati beberapa jam), pengguna dapat memakai fitur **"Pulihkan dari Notifikasi Aktif"**: aplikasi memanggil `getActiveNotifications()` untuk membaca notifikasi yang masih tersisa di bayangan notifikasi dan memprosesnya melalui pipeline yang sama (dengan dedup).
- Indikator "Kesehatan Pencatatan" di Dashboard: hijau (listener aktif & event terakhir < 24 jam), kuning (listener aktif, tidak ada event > 48 jam), merah (listener terputus).

---

## 3. Spesifikasi Skema Database

Penyimpanan: **Room (SQLite) + SQLCipher**. Semua nilai uang disimpan sebagai **INTEGER dalam satuan rupiah penuh** (IDR tidak memakai sen) untuk menghindari galat floating point. Semua timestamp disimpan sebagai **INTEGER epoch milidetik UTC**; konversi zona waktu dilakukan di UI (default Asia/Jakarta).

### 3.1 Tabel `accounts`

| Kolom | Tipe | Constraint | Default | Keterangan |
| --- | --- | --- | --- | --- |
| `id` | INTEGER | PK AUTOINCREMENT |  |  |
| `name` | TEXT | NOT NULL |  | Nama tampilan, mis. "BCA Utama" |
| `type` | TEXT | NOT NULL, CHECK IN ('BANK','EWALLET','CASH','CARD','OTHER') | 'OTHER' |  |
| `provider_code` | TEXT | NULL | NULL | 'BCA','DANA','GOPAY', dst. untuk pemetaan rule |
| `package_name` | TEXT | NULL | NULL | Package aplikasi sumber notifikasi |
| `account_hint` | TEXT | NULL | NULL | 4 digit terakhir / nomor terakhir untuk pencocokan |
| `initial_balance` | INTEGER | NOT NULL | 0 | Saldo awal saat dibuat |
| `current_balance` | INTEGER | NOT NULL | 0 | Hasil turunan; diperbarui dalam DB transaction |
| `color_hex` | TEXT | NOT NULL | '#0A84FF' | Format #RRGGBB |
| `icon` | TEXT | NOT NULL | 'wallet' | Kunci ikon internal |
| `include_in_total` | INTEGER | NOT NULL, CHECK IN (0,1) | 1 | Ikut dalam total saldo |
| `is_archived` | INTEGER | NOT NULL, CHECK IN (0,1) | 0 | Soft delete |
| `sort_order` | INTEGER | NOT NULL | 0 |  |
| `created_at` | INTEGER | NOT NULL |  |  |
| `updated_at` | INTEGER | NOT NULL |  |  |

Indeks: `UNIQUE(name) WHERE is_archived = 0` (partial index), `INDEX idx_accounts_pkg(package_name, account_hint)`.

### 3.2 Tabel `categories`

| Kolom | Tipe | Constraint | Default | Keterangan |
| --- | --- | --- | --- | --- |
| `id` | INTEGER | PK AUTOINCREMENT |  |  |
| `parent_id` | INTEGER | FK -> `categories.id` ON DELETE CASCADE, NULL | NULL | NULL berarti kategori induk; kedalaman maksimal 2 level |
| `name` | TEXT | NOT NULL |  |  |
| `kind` | TEXT | NOT NULL, CHECK IN ('EXPENSE','INCOME','TRANSFER') | 'EXPENSE' |  |
| `icon` | TEXT | NOT NULL | 'tag' |  |
| `color_hex` | TEXT | NOT NULL | '#8E8E93' |  |
| `is_system` | INTEGER | NOT NULL, CHECK IN (0,1) | 0 | Kategori bawaan tidak dapat dihapus |
| `is_archived` | INTEGER | NOT NULL, CHECK IN (0,1) | 0 |  |
| `sort_order` | INTEGER | NOT NULL | 0 |  |

Indeks: `UNIQUE(parent_id, name, kind)`, `INDEX idx_categories_parent(parent_id)`.

**Seed kategori bawaan (kategori induk -> sub-kategori):**

| Induk | Sub-kategori |
| --- | --- |
| Makanan & Minuman | Makan di luar, Kopi & jajan, Belanja bahan makanan, Pesan antar |
| Transportasi | Ojek online, BBM, Parkir & tol, Transportasi umum |
| Belanja | Marketplace, Pakaian, Elektronik, Kebutuhan rumah |
| Tagihan & Utilitas | Listrik, Air, Internet & pulsa, Langganan digital |
| Kesehatan | Obat & apotek, Dokter, Olahraga |
| Hiburan | Streaming, Game, Wisata |
| Pendidikan | Kursus, Buku |
| Lain-lain (Pengeluaran) | Tidak terkategori (default saat auto-capture belum cocok) |
| Gaji & Pendapatan | Gaji, Freelance, Bonus, Pemasukan lain |
| Transfer | Antar-rekening (khusus tipe TRANSFER) |

### 3.3 Tabel `transactions`

| Kolom | Tipe | Constraint | Default | Keterangan |
| --- | --- | --- | --- | --- |
| `id` | INTEGER | PK AUTOINCREMENT |  |  |
| `type` | TEXT | NOT NULL, CHECK IN ('EXPENSE','INCOME','TRANSFER') |  |  |
| `amount` | INTEGER | NOT NULL, CHECK (amount > 0) |  | Rupiah penuh, selalu positif |
| `fee` | INTEGER | NOT NULL, CHECK (fee >= 0) | 0 | Biaya admin (khusus transfer / top-up) |
| `source_account_id` | INTEGER | FK -> `accounts.id` ON DELETE RESTRICT, NULL | NULL | Wajib untuk EXPENSE dan TRANSFER |
| `destination_account_id` | INTEGER | FK -> `accounts.id` ON DELETE RESTRICT, NULL | NULL | Wajib untuk INCOME dan TRANSFER |
| `category_id` | INTEGER | FK -> `categories.id` ON DELETE SET NULL, NULL | NULL | NULL untuk TRANSFER |
| `merchant` | TEXT | NULL | NULL | Nama merchant / penerima / pengirim |
| `note` | TEXT | NULL | NULL | Catatan pengguna |
| `raw_notification` | TEXT | NULL | NULL | Teks asli notifikasi (hanya capture AUTO); dapat dimatikan di pengaturan |
| `source_package` | TEXT | NULL | NULL | Package sumber notifikasi |
| `hash_dedup` | TEXT | NULL | NULL | SHA-256 heksadesimal |
| `capture_source` | TEXT | NOT NULL, CHECK IN ('AUTO','MANUAL') | 'MANUAL' |  |
| `review_status` | TEXT | NOT NULL, CHECK IN ('CONFIRMED','NEEDS\_REVIEW') | 'CONFIRMED' | AUTO dengan keyakinan rendah = NEEDS\_REVIEW |
| `confidence` | REAL | NOT NULL | 1.0 | 0.0-1.0 hasil parser |
| `timestamp` | INTEGER | NOT NULL |  | Waktu transaksi (epoch ms UTC) |
| `created_at` | INTEGER | NOT NULL |  |  |
| `updated_at` | INTEGER | NOT NULL |  |  |
| `is_deleted` | INTEGER | NOT NULL, CHECK IN (0,1) | 0 | Soft delete (memungkinkan Undo) |

**Constraint tingkat tabel (CHECK):**

- `type='EXPENSE'` -> `source_account_id IS NOT NULL AND destination_account_id IS NULL`
- `type='INCOME'` -> `destination_account_id IS NOT NULL AND source_account_id IS NULL`
- `type='TRANSFER'` -> kedua akun NOT NULL dan `source_account_id <> destination_account_id`

**Aturan saldo:** EXPENSE mengurangi `source.current_balance` sebesar `amount + fee`; INCOME menambah `destination.current_balance` sebesar `amount`; TRANSFER mengurangi source sebesar `amount + fee` dan menambah destination sebesar `amount`. Setiap insert / update / delete dijalankan dalam satu `@Transaction` Room bersama pembaruan saldo.

### 3.4 Tabel `notification_rules`

| Kolom | Tipe | Constraint | Default | Keterangan |
| --- | --- | --- | --- | --- |
| `id` | INTEGER | PK AUTOINCREMENT |  |  |
| `provider_code` | TEXT | NOT NULL |  | 'BCA','DANA','GOPAY','SHOPEEPAY','MANDIRI', dst. |
| `package_name` | TEXT | NOT NULL |  | Mis. `com.bca.mybca.omni.android` |
| `rule_name` | TEXT | NOT NULL |  | Mis. "BCA - Transfer keluar" |
| `title_pattern` | TEXT | NULL | NULL | Regex opsional untuk judul notifikasi |
| `body_pattern` | TEXT | NOT NULL |  | Regex utama dengan named groups |
| `tx_type` | TEXT | NOT NULL, CHECK IN ('EXPENSE','INCOME','TRANSFER') |  | Tipe hasil bila cocok |
| `group_map_json` | TEXT | NOT NULL | '{}' | Pemetaan capture group, mis. `{"amount":"amt","merchant":"mrc","account_hint":"acc"}` |
| `amount_format` | TEXT | NOT NULL, CHECK IN ('ID\_DOT\_COMMA','US\_COMMA\_DOT','AUTO') | 'AUTO' | Format pemisah ribuan / desimal |
| `priority` | INTEGER | NOT NULL | 100 | Angka lebih kecil dievaluasi lebih dulu |
| `is_enabled` | INTEGER | NOT NULL, CHECK IN (0,1) | 1 |  |
| `is_builtin` | INTEGER | NOT NULL, CHECK IN (0,1) | 1 | Rule bawaan; hanya dapat dinonaktifkan, tidak dihapus |
| `version` | INTEGER | NOT NULL | 1 | Versi rule untuk pembaruan lewat rilis aplikasi |
| `match_count` | INTEGER | NOT NULL | 0 | Statistik lokal |
| `fail_count` | INTEGER | NOT NULL | 0 | Statistik lokal |
| `created_at` | INTEGER | NOT NULL |  |  |

Indeks: `INDEX idx_rules_pkg(package_name, is_enabled, priority)`.

### 3.5 Tabel `budgets`

| Kolom | Tipe | Constraint | Default | Keterangan |
| --- | --- | --- | --- | --- |
| `id` | INTEGER | PK AUTOINCREMENT |  |  |
| `period_month` | TEXT | NOT NULL |  | Format 'YYYY-MM' |
| `category_id` | INTEGER | FK -> `categories.id` ON DELETE CASCADE, NULL | NULL | NULL berarti budget total bulanan |
| `limit_amount` | INTEGER | NOT NULL, CHECK (limit\_amount > 0) |  |  |
| `alert_threshold_pct` | INTEGER | NOT NULL, CHECK (alert\_threshold\_pct BETWEEN 1 AND 100) | 80 | Persentase pemicu peringatan |
| `rollover` | INTEGER | NOT NULL, CHECK IN (0,1) | 0 | Sisa dibawa ke bulan depan |
| `last_alert_level` | INTEGER | NOT NULL | 0 | 0 = belum, 1 = ambang, 2 = terlampaui; mencegah notifikasi berulang |
| `created_at` | INTEGER | NOT NULL |  |  |
| `updated_at` | INTEGER | NOT NULL |  |  |

Indeks: `UNIQUE(period_month, category_id)` (gunakan nilai -1 pada kolom generated untuk NULL agar unik), `INDEX idx_budgets_period(period_month)`.

### 3.6 Tabel Pendukung

| Tabel | Kolom inti | Fungsi |
| --- | --- | --- |
| `raw_events` | `id`, `package_name`, `title`, `text`, `post_time`, `status` ('PENDING','PARSED','FAILED'), `created_at` | Antrian durable event notifikasi, retensi 7 hari |
| `merchant_category_map` | `merchant_key` (PK, lowercase ternormalisasi), `category_id` (FK), `hit_count` | Pembelajaran kategori dari koreksi pengguna |
| `app_settings` | `key` (PK), `value` | Preferensi (hide balance, tema, simpan raw notification, dll.) |

### 3.7 Strategi Indexing

Kueri kritis dan indeks yang melayaninya:

| Kueri | Pola | Indeks |
| --- | --- | --- |
| Total pengeluaran bulan ini (widget, dashboard) | `WHERE type='EXPENSE' AND is_deleted=0 AND timestamp BETWEEN ? AND ?` | `idx_tx_type_time(type, is_deleted, timestamp DESC)` |
| Transaksi terbaru | `ORDER BY timestamp DESC LIMIT N` | `idx_tx_time(timestamp DESC) WHERE is_deleted = 0` (partial) |
| Breakdown kategori | `GROUP BY category_id` pada rentang waktu | `idx_tx_cat_time(category_id, timestamp) WHERE is_deleted=0 AND type='EXPENSE'` |
| Riwayat per akun | `WHERE source_account_id=? OR destination_account_id=?` | `idx_tx_src(source_account_id, timestamp DESC)`, `idx_tx_dst(destination_account_id, timestamp DESC)` |
| Dedup lookup | `WHERE hash_dedup=? AND timestamp BETWEEN ? AND ?` | `idx_tx_hash(hash_dedup, timestamp)` |
| Perlu dicek | `WHERE review_status='NEEDS_REVIEW'` | `idx_tx_review(review_status) WHERE review_status='NEEDS_REVIEW'` (partial) |

Optimasi tambahan:

- **Tabel agregat `daily_summary`** (`day` 'YYYY-MM-DD' PK, `account_id`, `expense_total`, `income_total`, `tx_count`) diperbarui lewat trigger / DAO pada setiap perubahan transaksi, sehingga widget dan chart mingguan membaca maksimal ratusan baris, bukan memindai seluruh transaksi.
- PRAGMA: `journal_mode=WAL`, `synchronous=NORMAL`, `foreign_keys=ON`.
- Target performa: kueri widget < 20 ms dan dashboard < 100 ms pada 50.000 transaksi di perangkat kelas menengah.

---

## 4. Detail Parsing Engine & Deduplikasi

### 4.1 Contoh Format Notifikasi

> **Catatan:** format teks notifikasi dapat berubah sewaktu-waktu mengikuti pembaruan aplikasi bank. Contoh di bawah adalah pola representatif yang dipakai sebagai dasar rule dan test fixture; rule wajib divalidasi dengan sampel notifikasi nyata pada tahap QA dan ditempatkan di tabel `notification_rules` agar mudah diperbarui.

**BCA (m-BCA / MyBCA)** `com.bca.mybca.omni.android`

| Skenario | Judul | Isi |
| --- | --- | --- |
| Transfer keluar | `m-Transfer` | `Transfer Rp 150.000 ke BUDI SANTOSO berhasil. 02/11 14:32` |
| Debit QRIS | `Pembayaran` | `Pembayaran QRIS Rp 47.500 di KOPI KENANGAN berhasil` |
| Dana masuk | `Dana Masuk` | `Anda menerima Rp 2.500.000 dari PT MAJU JAYA. Saldo Rp 4.120.350` |

**DANA** `id.dana`

| Skenario | Judul | Isi |
| --- | --- | --- |
| Bayar merchant | `Pembayaran Berhasil` | `Kamu berhasil membayar Rp32.000 ke Indomaret` |
| Terima uang | `Uang Masuk` | `Kamu menerima Rp100.000 dari Rani Putri` |
| Top up | `Top Up Berhasil` | `Top up DANA sebesar Rp200.000 berhasil` |

**GoPay (Gojek)** `com.gojek.app`

| Skenario | Judul | Isi |
| --- | --- | --- |
| Bayar GoFood | `Pembayaran berhasil` | `Pembayaran GoPay Rp 56.000 ke Warteg Bahari berhasil` |
| Bayar GoRide | `Pembayaran GoRide` | `Kamu bayar Rp 18.500 pakai GoPay untuk perjalanan GoRide` |
| Terima dana | `GoPay masuk` | `Kamu menerima Rp 75.000 dari Dimas A.` |

### 4.2 Logika Regex

**Normalisasi nominal.** Format Indonesia memakai titik sebagai pemisah ribuan dan koma sebagai desimal (`1.250.000,00`); sebagian aplikasi memakai spasi atau tanpa spasi setelah `Rp`.

Pola nominal generik (named group `amt`):

```
(?i)rp\.?\s?(?<amt>\d{1,3}(?:[.,]\d{3})*(?:[.,]\d{1,2})?|\d+)
```

Algoritma `normalizeAmount(raw: String): Long`:

1. Buang spasi.
2. Jika ada **dua pemisah berbeda** (mis. `1.250.000,50`): pemisah terakhir adalah desimal; pemisah lainnya ribuan.
3. Jika hanya satu jenis pemisah dan **muncul lebih dari sekali** (`1.250.000`): semuanya ribuan.
4. Jika hanya satu pemisah dan **tepat 3 digit setelahnya** (`150.000`, `1,500`): ribuan.
5. Jika hanya satu pemisah dan **1-2 digit setelahnya** (`50,5`, `12.50`): desimal; dibulatkan ke rupiah terdekat.
6. Hapus semua pemisah ribuan, parse sebagai `Long`.
7. Tolak hasil <= 0 atau > 10.000.000.000 (batas kewajaran, masuk ke review).

**Rule contoh (disimpan sebagai data di `notification_rules`):**

| Rule | Package | `body_pattern` | `tx_type` | Group map |
| --- | --- | --- | --- | --- |
| BCA transfer keluar | `com.bca.mybca.omni.android` | `(?i)transfer\s+rp\.?\s?(?<amt>[\d.,]+)\s+ke\s+(?<mrc>.+?)\s+berhasil` | TRANSFER/EXPENSE | amount=amt, merchant=mrc |
| BCA QRIS | idem | `(?i)pembayaran\s+qris\s+rp\.?\s?(?<amt>[\d.,]+)\s+di\s+(?<mrc>.+?)\s+berhasil` | EXPENSE | amount=amt, merchant=mrc |
| BCA dana masuk | idem | `(?i)menerima\s+rp\.?\s?(?<amt>[\d.,]+)\s+dari\s+(?<mrc>.+?)(?:\.\|$)` | INCOME | amount=amt, merchant=mrc |
| DANA bayar | `id.dana` | `(?i)membayar\s+rp\.?\s?(?<amt>[\d.,]+)\s+ke\s+(?<mrc>.+)` | EXPENSE | amount=amt, merchant=mrc |
| DANA terima | `id.dana` | `(?i)menerima\s+rp\.?\s?(?<amt>[\d.,]+)\s+dari\s+(?<mrc>.+)` | INCOME | amount=amt, merchant=mrc |
| GoPay bayar | `com.gojek.app` | `(?i)(?:gopay\s+)?rp\.?\s?(?<amt>[\d.,]+)\s+ke\s+(?<mrc>.+?)\s+berhasil` | EXPENSE | amount=amt, merchant=mrc |
| GoPay masuk | `com.gojek.app` | `(?i)menerima\s+rp\.?\s?(?<amt>[\d.,]+)\s+dari\s+(?<mrc>.+)` | INCOME | amount=amt, merchant=mrc |

**Penentuan jenis transaksi (Debit vs Kredit).** Berurutan: (1) `tx_type` dari rule yang cocok; (2) jika rule bertipe ambigu, kata kunci kredit (`menerima`, `dana masuk`, `uang masuk`, `kredit`, `refund`, `cashback`) -> INCOME; kata kunci debit (`membayar`, `pembayaran`, `transfer ke`, `debit`, `belanja`, `top up`) -> EXPENSE; (3) bila keduanya atau tidak ada, tandai `NEEDS_REVIEW` dengan tebakan terbaik.

**Top-up dan transfer antar-akun.** Notifikasi "Top up DANA" dari bank + "Top up berhasil" dari DANA dalam jendela 2 menit dengan nominal sama digabung menjadi satu TRANSFER (bank -> DANA), bukan EXPENSE + INCOME. Merchant dibersihkan: trim, hapus awalan "ke/di", kapitalisasi judul, pangkas sufiks umum ("berhasil", titik di akhir).

**Pencocokan akun.** `package_name` + `account_hint` (4 digit terakhir bila ada di notifikasi) -> akun; jika tidak ada petunjuk, pakai akun default untuk package tersebut; jika belum ada akun, buat otomatis akun draf bernama sesuai provider dan minta konfirmasi saat pertama kali.

### 4.3 Skor Keyakinan (Confidence)

| Kondisi | Skor |
| --- | --- |
| Rule dengan nominal + merchant + tipe cocok | 1.0 |
| Nominal + tipe cocok, merchant kosong | 0.8 |
| Hasil dari fallback generik | 0.5 |
| Tipe ambigu | 0.4 |

Transaksi dengan skor < 0.7 masuk status `NEEDS_REVIEW` dan muncul di inbox "Perlu Dicek" (badge pada tab Home).

### 4.4 Deduplikasi

Sumber duplikasi: aplikasi bank yang mengirim notifikasi ganda (push + in-app), notifikasi di-*update* (`onNotificationPosted` dipanggil ulang dengan teks sama), serta pengguna yang menginput manual padahal auto-capture sudah mencatat.

**Hash:**

```
hash_dedup = SHA-256( package_name | type | amount | normalize(merchant) | account_hint )
```

`normalize` = huruf kecil, hapus spasi ganda dan tanda baca. **Waktu tidak masuk hash** agar dapat dibandingkan dengan window.

**Alur:**

1. Hitung hash untuk `ParsedTransaction`.
2. Cari di DB: `hash_dedup = ? AND timestamp BETWEEN (t - 45s) AND (t + 45s)`. Window default **45 detik** (dapat dikonfigurasi 30-60 detik di pengaturan lanjutan).
3. Ditemukan -> **duplikat**: buang event, tambahkan `match_count` ke rule, tidak membuat baris baru.
4. Tidak ditemukan -> lanjut insert.

**Dedup lintas-sumber (AUTO vs MANUAL).** Jika transaksi MANUAL dengan `amount` sama dan akun sama ada dalam window **5 menit** sebelum auto-capture, tampilkan prompt: "Sepertinya ini transaksi yang sama. Gabungkan?" Bila setuju, transaksi MANUAL ditandai `capture_source='MANUAL'` dengan `raw_notification` dari AUTO (data tidak hilang). Dedup tingkat key notifikasi: simpan juga `sbn.key + postTime` di memori (LRU 200 entri) untuk menyaring re-post identik sebelum menyentuh DB.

### 4.5 Fallback Mechanism Saat Regex Gagal

Fallback berjenjang:

1. **Parser generik.** Jika package ada di whitelist namun tidak ada rule yang cocok: cari pola `Rp` + nominal; kata kunci debit/kredit menentukan tipe; merchant dikosongkan. Skor 0.5 -> `NEEDS_REVIEW`.
2. **Inbox "Perlu Dicek".** Transaksi draf ditampilkan dengan teks asli notifikasi; pengguna cukup menekan Konfirmasi atau mengedit.
3. **"Ajari Aplikasi" (rule builder lokal).** Pengguna menyorot nominal dan merchant pada teks notifikasi; aplikasi menghasilkan regex kandidat, menguji terhadap teks tersebut, dan menyimpannya sebagai rule `is_builtin=0`.
4. **Notifikasi gagal total.** Event masuk `raw_events` berstatus `FAILED`. Bila satu rule memiliki `fail_count` tinggi (>= 5 kegagalan berturut-turut), Dashboard menampilkan peringatan "Format notifikasi {provider} mungkin berubah".
5. **Pembaruan rule.** Rule bawaan diperbarui melalui rilis aplikasi (kolom `version`); pengguna dapat mengekspor sampel notifikasi yang gagal (teks dapat disamarkan manual) untuk dikirim ke tim lewat email atas inisiatif sendiri, tanpa pengiriman otomatis.

---

## 5. Design System & User Interface (UI/UX)

### 5.1 Arsitektur Navigasi

Navigasi utama: **Bottom Navigation Bar** dengan 4 tujuan dan satu tombol aksi tengah.

| Posisi | Tujuan | Isi |
| --- | --- | --- |
| 1 | **Beranda** | Dashboard ringkasan |
| 2 | **Transaksi** | Daftar lengkap, filter, pencarian, inbox "Perlu Dicek" |
| Tengah | **+ (FAB)** | Membuka Fast Add Modal |
| 3 | **Laporan** | Analytics |
| 4 | **Pengaturan** | Akun, kategori, budget, rules, keamanan, tampilan |

Semua layar dibangun dengan satu `Activity` (single-activity) dan Navigation Compose. Deep link internal: `smartspend://home`, `smartspend://add?type=expense`, `smartspend://transactions/{id}`, `smartspend://reports`, `smartspend://review`.

### 5.2 Dashboard / Beranda

Urutan komponen dari atas ke bawah:

1. **Header:** salam singkat, tombol mata (hide/show saldo), indikator "Kesehatan Pencatatan" (titik hijau / kuning / merah).
2. **Net Cashflow Card (kartu utama, squircle besar):**
   - Angka besar: **Sisa budget bulan ini** (budget total - pengeluaran).
   - Baris sekunder: Pemasukan bulan ini (hijau), Pengeluaran bulan ini (merah), Arus bersih.
   - Progress bar tipis: persentase budget terpakai; warna berubah di ambang peringatan (hijau -> kuning pada 80% -> merah pada 100%).
3. **Quick Spend Graph:** area-chart ringkas 7 hari terakhir (tinggi 96 dp); sentuh batang / titik untuk melihat nominal harian (tooltip).
4. **Banner "Perlu Dicek"** (hanya tampil bila ada transaksi `NEEDS_REVIEW`): "3 transaksi perlu dikonfirmasi".
5. **Recent Transactions:** 5 transaksi terakhir; setiap baris berisi ikon kategori berwarna, merchant, akun + waktu relatif, dan nominal (merah untuk pengeluaran, hijau untuk pemasukan, abu-abu untuk transfer). Geser kiri = hapus (dengan Undo snackbar 5 detik), geser kanan = ubah kategori. Tombol "Lihat semua".
6. **Ringkasan Akun:** carousel horizontal kartu saldo per akun.

**State khusus:** kosong (ilustrasi + ajakan mengaktifkan izin notifikasi atau menambah transaksi pertama), loading (skeleton shimmer), error (pesan inline + tombol coba lagi).

### 5.3 Fast Add Modal

Bottom sheet setinggi 85% layar; target: **transaksi tercatat dalam <= 5 detik / 3 ketukan**.

| Area | Spesifikasi |
| --- | --- |
| Segmented control atas | Pengeluaran / Pemasukan / Transfer (default Pengeluaran) |
| Tampilan nominal | Font angka besar (48 sp), awalan "Rp", pemisah ribuan otomatis, bergetar halus + merah bila melebihi batas wajar |
| **Custom numpad** | Grid 4 x 4: `1-9`, `000`, `0`, `<` (hapus), `.` tidak ditampilkan karena rupiah bulat; haptic feedback ringan tiap tekan |
| **Quick denomination chips** | `+10rb`, `+20rb`, `+50rb`, `+100rb` di atas numpad; menekan chip menambah nominal; tekan lama = kurangi |
| Selector akun | Chip horizontal akun (default: akun yang terakhir dipakai); untuk Transfer muncul dua selector "Dari" dan "Ke" serta input biaya admin |
| Selector kategori | Grid ikon 2 baris yang bisa di-scroll, menampilkan 8 kategori paling sering dipakai; tombol "Semua" membuka daftar hierarki |
| Field opsional | Merchant, catatan, tanggal / waktu (default sekarang) |
| Tombol simpan | Lebar penuh "Simpan" (aktif bila nominal > 0 dan akun terpilih); tombol sekunder "Simpan & Tambah Lagi" |

Perilaku: nominal otomatis fokus saat modal terbuka; kategori disarankan dari riwayat merchant; modal bisa dibuka langsung dari widget lewat deep link `smartspend://add`.

### 5.4 Analytics / Laporan

| Komponen | Spesifikasi |
| --- | --- |
| Pemilih periode | Minggu ini / Bulan ini / 3 Bulan / Kustom; panah navigasi periode sebelum dan sesudah |
| **Breakdown kategori** | Donut chart (maks 6 segmen + "Lainnya") + daftar kategori dengan persentase dan nominal; ketuk kategori induk -> drill-down ke sub-kategori dan transaksi |
| **Weekly burn rate** | Bar chart per hari dalam minggu berjalan, garis horizontal putus-putus = rata-rata harian ideal (sisa budget / sisa hari); kartu insight: "Kamu menghabiskan Rp X per hari; di laju ini budget habis pada tanggal Y" |
| Perbandingan | Selisih persentase terhadap periode sebelumnya (panah naik / turun berwarna) |
| Top merchant | 5 merchant dengan pengeluaran terbesar |
| Ekspor | CSV dan PDF ringkasan, disimpan lokal lewat Storage Access Framework |

### 5.5 Account & Rules Settings

- **Akun:** daftar, tambah, ubah, arsipkan, atur urutan, set saldo awal, koreksi saldo (membuat transaksi penyesuaian).
- **Kategori:** kelola hierarki, ikon, warna.
- **Budget:** budget total dan per kategori, ambang peringatan, rollover.
- **Aturan Notifikasi:** daftar rule per aplikasi dengan sakelar aktif / nonaktif, statistik cocok / gagal, tombol "Ajari Aplikasi", pengujian rule (tempel teks, lihat hasil parse).
- **Izin & Kesehatan:** status Notification Listener, Battery Optimization, panduan OEM, tombol "Pulihkan dari Notifikasi Aktif".
- **Privasi & Keamanan:** kunci aplikasi (biometrik / PIN), sembunyikan nominal default, simpan teks notifikasi asli (on / off), ekspor / impor cadangan terenkripsi, hapus semua data.
- **Tampilan:** tema (Sistem / Terang / Gelap), format tanggal.

### 5.6 Design Language

**Prinsip:** bersih, lapang, tipografi angka yang tegas, satu warna aksen, sudut besar bergaya squircle ala iOS.

**Warna (token semantik):**

| Token | Light | Dark |
| --- | --- | --- |
| `bg.primary` | #F2F2F7 | #000000 |
| `bg.surface` (kartu) | #FFFFFF | #1C1C1E |
| `bg.surface.elevated` | #FFFFFF | #2C2C2E |
| `text.primary` | #000000 | #FFFFFF |
| `text.secondary` | #6C6C70 | #98989F |
| `separator` | #E5E5EA | #38383A |
| `accent` | #0A84FF | #0A84FF |
| `income` | #30B350 | #32D74B |
| `expense` | #E5352B | #FF453A |
| `warning` | #FF9F0A | #FF9F0A |
| `transfer` | #8E8E93 | #8E8E93 |

Rasio kontras teks minimal 4.5:1 (WCAG AA) pada kedua tema.

**Tipografi:** font keluarga `Inter` (fallback `Roboto`) dengan fitur angka tabular (`tnum`) agar kolom nominal sejajar.

| Gaya | Ukuran / Berat | Penggunaan |
| --- | --- | --- |
| Display | 40 sp / Bold | Nominal utama |
| Title 1 | 28 sp / Bold | Judul layar |
| Title 2 | 20 sp / SemiBold | Judul kartu |
| Body | 16 sp / Regular | Teks umum |
| Callout | 14 sp / Medium | Baris transaksi |
| Caption | 12 sp / Regular | Meta (waktu, akun) |

**Spacing (grid 4 dp):** 4, 8, 12, 16, 20, 24, 32, 48. Margin layar 16 dp; jarak antar-kartu 12 dp; padding dalam kartu 16 dp.

**Sudut membulat (squircle):** kartu 24 dp, modal 28 dp (atas), chip 16 dp, tombol 14 dp, ikon kategori kontainer 12 dp. Implementasi Compose: `RoundedCornerShape` untuk sebagian besar elemen; kartu utama memakai custom `Shape` superelips (eksponen n = 4-5) agar mendekati continuous corner iOS.

**Elevasi dan bayangan:** Light mode memakai bayangan lembut (blur 16 dp, opasitas 6%); Dark mode memakai perbedaan warna permukaan tanpa bayangan.

**Motion:** durasi 200-300 ms, easing `FastOutSlowIn`; angka nominal beranimasi hitung (count-up) saat berubah; hormati pengaturan "Remove animations" sistem.

**Aksesibilitas:** ukuran sentuh minimal 48 dp, `contentDescription` pada semua ikon, dukungan TalkBack dan font scale hingga 200%, warna tidak menjadi satu-satunya pembeda (ikon panah naik / turun menyertai warna).

---

## 6. Spesifikasi Widget Home Screen (Apple-Inspired Glanceable Design)

Implementasi memakai **Jetpack Glance (`GlanceAppWidget`)** di atas `AppWidgetProvider`, dengan `SizeMode.Responsive` untuk tiga ukuran. Latar widget memakai sudut membulat 24 dp (`android:background` + `@android:dimen/system_app_widget_background_radius` untuk Android 12+), warna mengikuti tema sistem dan pilihan tema aplikasi.

### 6.1 Small Widget (2x2)

| Aspek | Spesifikasi |
| --- | --- |
| Ukuran | 2x2 sel (sekitar 146 x 146 dp), minimal 110 x 110 dp |
| Metrik utama | **Pengeluaran hari ini** (font 28 sp, tebal) |
| Metrik sekunder | "Sisa hari ini Rp X" (sisa jatah harian = sisa budget / sisa hari) |
| Progress bar | Bar horizontal tipis (6 dp) pemakaian budget bulanan; hijau < 80%, kuning 80-99%, merah >= 100% |
| Transaksi terakhir | 1 baris: ikon kategori + merchant (dipotong 1 baris) + nominal |
| Sumber data | `daily_summary` + `budgets` + 1 baris `transactions` terbaru |
| Interaksi | Ketuk area mana pun -> `smartspend://home`; ketuk baris transaksi -> `smartspend://transactions/{id}` |

### 6.2 Medium Widget (4x2)

Split view dua kolom (rasio 55 : 45).

| Kolom | Isi |
| --- | --- |
| **Kiri** | Judul "Bulan Ini"; pengeluaran bulan berjalan (besar); "Sisa jatah Rp X dari Rp Y"; progress bar; baris kecil: "Rata-rata Rp Z / hari" |
| **Kanan** | **Top 3 kategori pengeluaran** (ikon + nama + nominal + mini-bar proporsional); di pojok kanan bawah tombol bulat **"+"** (48 dp) |

- Ketuk kolom kiri -> `smartspend://home`; ketuk baris kategori -> `smartspend://reports?category={id}`; ketuk **+** -> `smartspend://add?type=expense` (membuka Fast Add Modal langsung tanpa melewati Beranda).
- Sumber data: `budgets`, agregasi `daily_summary` bulan berjalan, query top-3 kategori.

### 6.3 Large Widget (4x4)

| Blok | Isi |
| --- | --- |
| Header | Judul "SmartSpend", periode ("Minggu ini"), tombol mata (toggle sembunyikan) |
| **Weekly bar chart** | 7 batang (Sen-Min), batang hari ini disorot warna aksen, garis rata-rata; digambar dengan `Canvas`-ke-bitmap di dalam Glance |
| **Tren** | Chip "Hemat 12% vs minggu lalu" (hijau) atau "Boros 8% vs minggu lalu" (merah) |
| **3 transaksi terkini** | Daftar 3 baris (ikon, merchant, waktu, nominal) |
| **Multi-account balance** | Baris horizontal hingga 3 akun teratas (nama pendek + saldo) dengan penanda "+N" bila lebih |
| Aksi cepat | Tombol "+ Pengeluaran" dan "+ Pemasukan" di footer |

Interaksi: header -> Beranda; chart -> `smartspend://reports`; baris transaksi -> detail; akun -> `smartspend://transactions?account={id}`; tombol aksi -> Fast Add Modal dengan tipe sesuai.

### 6.4 Sinkronisasi Data Instan Listener -> Widget

1. Setelah transaksi tersimpan, `TransactionRepository` memancarkan perubahan melalui Room `Flow`.
2. `WidgetStateStore` (DataStore Proto, tidak terenkripsi tetapi hanya berisi **nilai ter-agregasi** dan sudah di-obfuscate bila perlu) menerima snapshot baru: total harian, total bulanan, top kategori, 3 transaksi terkini, saldo akun.
3. `WidgetUpdater` memanggil `GlanceAppWidget.updateAll(context)` dengan **debounce 2 detik** (menggabungkan lonjakan notifikasi).
4. Fallback: `WorkManager` periodik 30 menit memperbarui widget bila update instan terlewat; update juga dipicu `ACTION_DATE_CHANGED`, `ACTION_TIMEZONE_CHANGED`, dan pergantian bulan (tengah malam).
5. Target latensi: dari notifikasi bank muncul sampai widget berubah **<= 3 detik** pada kondisi normal.

**Keamanan snapshot:** widget tidak pernah membuka database SQLCipher langsung; ia hanya membaca snapshot agregat. Kunci database tidak diekspos ke proses widget.

### 6.5 Fitur Privasi Widget

- **Mode Sembunyikan Saldo:** semua nominal diganti `Rp ••••••`; progress bar tetap tampil tanpa angka; nama merchant diganti "Transaksi" bila opsi "Sembunyikan merchant" aktif.
- Sakelar global di Pengaturan, plus **tombol mata** di widget Medium / Large untuk toggle instan (menyimpan preferensi di DataStore lalu memperbarui widget).
- **Auto-hide saat terkunci:** bila opsi aktif, widget menampilkan versi tersamar ketika perangkat terkunci (`ACTION_USER_PRESENT` / `KeyguardManager`) dan menampilkan nominal setelah dibuka.
- Widget lock-screen (Android 14+ tablet / perangkat yang mendukung) selalu memakai mode tersamar.
- Ketuk widget dalam mode tersamar yang mengarah ke detail akan meminta autentikasi biometrik terlebih dahulu bila "Kunci aplikasi" aktif.

---

## 7. Security, Privacy & Compliance

### 7.1 Enkripsi Database Lokal (SQLCipher)

| Aspek | Spesifikasi |
| --- | --- |
| Mesin | SQLCipher untuk Android (AES-256-CBC, HMAC-SHA512, PBKDF2 256.000 iterasi), terintegrasi dengan Room lewat `SupportOpenHelperFactory` |
| Pembuatan kunci | 32 byte acak (`SecureRandom`) dibuat saat instalasi pertama |
| Penyimpanan kunci | Dibungkus (wrap) dengan kunci AES-GCM dalam **Android Keystore** (hardware-backed / StrongBox bila tersedia); kunci terbungkus disimpan di `EncryptedFile` / DataStore |
| Kunci aplikasi (opsional) | Kunci Keystore dapat dikonfigurasi `setUserAuthenticationRequired(true)` sehingga basis data hanya dapat dibuka setelah biometrik / PIN |
| Cadangan | Ekspor file `.ssbackup` dienkripsi AES-256-GCM dengan kunci turunan dari frasa sandi pengguna (Argon2id / PBKDF2); `android:allowBackup="false"` agar data tidak ikut Auto Backup Google |
| Data teks asli notifikasi | Opsional; bila dimatikan, `raw_notification` tidak pernah disimpan, hanya hasil ter-parse |
| Retensi `raw_events` | Dihapus otomatis setelah 7 hari |
| Anti-penyadapan UI | `FLAG_SECURE` pada layar sensitif (dapat dimatikan pengguna); layar Recent Apps menyamarkan konten |
| Log | Tidak ada nominal, merchant, atau teks notifikasi dalam log rilis |

### 7.2 Kebijakan Izin Android

| Izin / akses | Tujuan | Cara meminta |
| --- | --- | --- |
| **Notification Listener** (`BIND_NOTIFICATION_LISTENER_SERVICE`) | Inti auto-tracking | Layar penjelasan (prominent disclosure) sebelum membuka `ACTION_NOTIFICATION_LISTENER_SETTINGS`; tanpa izin ini, aplikasi tetap berfungsi sebagai pencatat manual |
| **Abaikan optimasi baterai** (`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`) | Mengurangi risiko listener terputus | Diminta secara kontekstual dengan penjelasan manfaat; selalu opsional dan dapat ditolak |
| `POST_NOTIFICATIONS` (Android 13+) | Peringatan budget | Diminta saat pengguna mengaktifkan peringatan |
| `RECEIVE_BOOT_COMPLETED` | Menjadwalkan ulang watchdog dan memicu rebind | Tidak ada prompt (normal permission) |
| `USE_BIOMETRIC` | Kunci aplikasi | Prompt sistem |
| **Tanpa** `INTERNET`, `READ_SMS`, `READ_CONTACTS`, `READ_EXTERNAL_STORAGE` | Sengaja tidak diminta | Bukti klaim privasi; ekspor / impor memakai Storage Access Framework |

Prinsip: **minimum izin, penjelasan sebelum permintaan, degradasi anggun saat ditolak**. Aplikasi hanya memproses notifikasi dari package yang ada di whitelist rule; notifikasi aplikasi lain (chat, sosial media) langsung dibuang pada `onNotificationPosted` tanpa dibaca isinya, dan tidak disimpan.

### 7.3 Kepatuhan Google Play

| Area | Tindakan |
| --- | --- |
| **Data safety form** | Deklarasikan: data finansial (info pembayaran / transaksi) **dikumpulkan dan diproses di perangkat saja**, **tidak dibagikan**, **tidak dikirim ke luar perangkat**; data dienkripsi saat disimpan; pengguna dapat meminta penghapusan (hapus semua data di aplikasi). Pastikan klaim konsisten dengan manifest (tanpa izin `INTERNET`) |
| **Prominent disclosure & izin sensitif** | Notification Listener adalah akses sensitif; sertakan layar pengungkapan yang jelas (apa yang dibaca, mengapa, bahwa tidak dikirim ke mana pun) sebelum mengarahkan ke pengaturan sistem; deskripsi toko menjelaskan fitur ini secara eksplisit |
| **Kebijakan layanan keuangan** | Posisikan aplikasi sebagai alat pencatatan pribadi; bukan layanan pinjaman, bukan penyedia layanan pembayaran, tidak memberi nasihat investasi; cantumkan penafian |
| **Kebijakan privasi** | Halaman kebijakan privasi publik yang menjelaskan pemrosesan on-device, retensi, dan hak pengguna |
| **UU PDP (Indonesia, UU No. 27 Tahun 2022)** | Persetujuan eksplisit untuk pemrosesan data pribadi, pengguna dapat menarik persetujuan (cabut izin dan hapus data), data minim, tidak ada transfer lintas batas karena tidak ada transfer data. Konsultasikan dengan penasihat hukum sebelum rilis |
| **Target API** | Memenuhi persyaratan target API level terbaru yang berlaku saat rilis |
| **Brand & merek dagang** | Gunakan nama bank / e-wallet hanya sebagai teks deskriptif; jangan memakai logo resmi tanpa izin; tambahkan penafian "tidak berafiliasi dengan bank atau penyedia dompet digital mana pun" |

**Risiko kebijakan utama:** peninjau Play dapat mempertanyakan akses Notification Listener pada aplikasi finansial. Mitigasi: video demonstrasi fitur, pengungkapan dalam aplikasi, dan bukti ketiadaan izin internet.

---

## 8. Teknologi Stack Rekomendasi & Roadmap MVP

### 8.1 Perbandingan Native Kotlin (Jetpack Compose) vs Flutter

| Kriteria | Kotlin + Jetpack Compose | Flutter |
| --- | --- | --- |
| `NotificationListenerService` | API native langsung, tanpa jembatan | Perlu kode native Kotlin + platform channel, dan isolate Dart tersendiri saat proses diawali dari service |
| Widget home screen | **Glance / AppWidget native**, akses penuh | Widget harus ditulis native; Flutter hanya dapat berbagi data lewat paket pihak ketiga |
| Background reliability | Kontrol penuh WorkManager, rebind, dan manifest | Lapisan tambahan menambah titik kegagalan |
| SQLCipher | Terintegrasi dengan Room | Perlu plugin (mis. `sqflite_sqlcipher` / Drift + SQLCipher), dukungan beragam |
| Keystore dan biometrik | API langsung | Via plugin |
| Ukuran APK dan memori | Lebih kecil | Lebih besar (runtime engine) |
| Reuse iOS | Tidak ada (bukan target) | Ada, tetapi iOS bukan target karena tidak mengizinkan membaca notifikasi aplikasi lain |
| Kecepatan UI | Setara | Setara |

**Keputusan: Native Kotlin + Jetpack Compose.** Seluruh nilai produk bergantung pada tiga kapabilitas yang bersifat Android-native (listener, widget, background reliability), sedangkan keunggulan utama Flutter (lintas-platform) tidak relevan karena iOS tidak dapat membaca notifikasi aplikasi lain.

### 8.2 Stack Terpilih

| Lapisan | Teknologi |
| --- | --- |
| Bahasa | Kotlin 2.x, Coroutines + Flow |
| UI | Jetpack Compose, Material 3 (dengan token kustom), Navigation Compose, Jetpack Glance |
| Arsitektur | MVVM + Clean Architecture ringan (UI -> ViewModel -> UseCase -> Repository -> DataSource), single-activity |
| DI | Hilt |
| Database | Room + SQLCipher (net.zetetic) |
| Pengaturan | DataStore (Preferences / Proto) |
| Background | WorkManager, NotificationListenerService |
| Chart | Vico atau komponen Canvas kustom (agar dapat dipakai ulang di Glance) |
| Keamanan | Android Keystore, BiometricPrompt |
| Testing | JUnit5, MockK, Turbine, Compose UI Test, Robolectric; test regex berbasis fixture notifikasi |
| CI/CD | GitHub Actions, Gradle versi katalog, ktlint + detekt, Firebase Test Lab **opsional** untuk uji OEM (tidak masuk ke dalam aplikasi) |
| Analitik | Tidak ada analitik pihak ketiga; metrik kualitas diukur lewat Play Console (vitals) dan survei opsional |

Modul Gradle: `:app`, `:core:database`, `:core:parser`, `:core:designsystem`, `:feature:home`, `:feature:add`, `:feature:reports`, `:feature:settings`, `:feature:widget`, `:service:listener`.

### 8.3 Roadmap MVP

Sprint 2 minggu; total MVP sekitar 6 minggu untuk tim 3 engineer + 1 desainer + 1 QA.

**Sprint 1 (Minggu 1-2): Fondasi & Pipeline Auto-Tracking**

| Epik | Deliverable | Kriteria selesai |
| --- | --- | --- |
| Setup proyek | Modul, DI, CI, design tokens | Build hijau, lint lolos |
| Database | Skema Room + SQLCipher, migrasi, seed kategori dan akun | Semua tabel dan indeks dibuat; uji migrasi lolos |
| Listener | `SpendNotificationListener`, `raw_events`, whitelist | Notifikasi uji tersimpan < 100 ms |
| Parser | Rule BCA, DANA, GoPay + `normalizeAmount` | >= 95% akurasi pada 60 fixture |
| Dedup | Hash + window 45 detik | Uji duplikasi 0 lolos pada 30 kasus |
| Onboarding izin | Prominent disclosure, aktivasi listener | Alur jalan di 3 OEM |

**Sprint 2 (Minggu 3-4): UI Inti & Pencatatan Manual**

| Epik | Deliverable | Kriteria selesai |
| --- | --- | --- |
| Dashboard | Cashflow card, grafik 7 hari, transaksi terbaru | Render < 100 ms pada 10.000 transaksi |
| Fast Add Modal | Numpad kustom, chip denominasi, selector akun / kategori | Input <= 5 detik |
| Transaksi | Daftar, filter, edit, hapus + Undo, transfer antar-akun | Saldo akun konsisten pada uji properti |
| Inbox Perlu Dicek | Konfirmasi / ubah transaksi draf | Alur penuh berfungsi |
| Budget | Budget total dan per kategori, notifikasi ambang | Peringatan tepat sekali per level |
| Panduan OEM | Layar panduan MIUI, OneUI, ColorOS, Vivo | Intent tiap OEM teruji di perangkat nyata |

**Sprint 3 (Minggu 5-6): Widget, Laporan, Hardening, Rilis**

| Epik | Deliverable | Kriteria selesai |
| --- | --- | --- |
| Widget | Small, Medium, Large + mode sembunyikan | Latensi update <= 3 detik |
| Laporan | Donut kategori, burn rate mingguan, ekspor CSV | Angka cocok dengan total transaksi |
| Keamanan | Kunci aplikasi, cadangan terenkripsi, `FLAG_SECURE` | Uji penetrasi dasar lolos |
| Rule builder | "Ajari Aplikasi" + uji rule | Pengguna uji berhasil membuat rule |
| Kualitas | Uji ketahanan listener 72 jam di 4 OEM, uji baterai | Konsumsi baterai < 2% / hari; tidak ada ANR |
| Rilis | Data safety form, kebijakan privasi, listing Play, closed beta | Persetujuan Play Console |

### 8.4 Risiko & Mitigasi

| Risiko | Dampak | Mitigasi |
| --- | --- | --- |
| Format notifikasi bank berubah | Auto-capture gagal | Rule sebagai data, fallback generik, "Ajari Aplikasi", peringatan di Dashboard, rilis rule cepat |
| OEM membunuh listener | Transaksi hilang | Watchdog, panduan OEM, pemulihan dari notifikasi aktif, indikator kesehatan |
| Penolakan kebijakan Play | Tidak dapat rilis | Pengungkapan jelas, video demo, tanpa izin internet, konsultasi dini |
| Akurasi kategori rendah | Frustrasi pengguna | Pembelajaran dari koreksi, kategori default aman, inbox review |
| Kehilangan data (ganti ponsel) | Churn | Ekspor / impor terenkripsi, pengingat cadangan berkala |
| Notifikasi berisi data sensitif lain | Risiko privasi | Whitelist ketat per package, buang notifikasi di luar whitelist tanpa dibaca |

### 8.5 Di Luar MVP (Backlog Berikutnya)

Tagihan berulang, target tabungan, kategori otomatis berbasis model on-device, dukungan lebih banyak penyedia (OVO, BRI, BNI, Jenius, Seabank), mode multi-mata uang, tema widget kustom, dan cadangan ke penyimpanan pilihan pengguna.

---

## 9. Fitur Tambahan: Utang-Piutang & Rencana Beli

Bagian ini menambah tiga kemampuan: pengingat bayar utang, pengingat tagih utang, dan Rencana Beli untuk menahan pembelian impulsif (FOMO). Semuanya tetap 100% on-device, tanpa izin `INTERNET` dan tanpa akses kontak.

### 9.1 Ringkasan Fitur

| Fitur | Masalah yang dipecahkan | Perilaku inti | Indikator keberhasilan |
| --- | --- | --- | --- |
| **Pengingat Bayar Utang** (Saya Berutang) | Lupa jatuh tempo, cicilan terlewat | Catat utang dan cicilan; pengingat H-3, H-1, hari-H, lalu berulang setelah lewat tempo | >= 70% utang jatuh tempo dibayar atau dilunasi dalam 7 hari |
| **Pengingat Tagih Utang** (Berutang ke Saya) | Sungkan atau lupa menagih | Pengingat jatuh tempo + pesan tagihan siap kirim lewat share sheet | >= 60% tagihan berstatus lunas dalam 14 hari setelah jatuh tempo |
| **Rencana Beli** | Beli karena FOMO atau promo sesaat | Masa tunggu, simulasi dampak ke budget, 3 pertanyaan refleksi, lalu keputusan Beli / Tidak jadi | >= 25% pengguna aktif membuat minimal 1 rencana dalam 30 hari |

### 9.2 Perubahan Skema Database (Migrasi v2)

Aturan tipe data sama dengan bagian 3: uang = INTEGER rupiah, waktu = INTEGER epoch ms UTC, boolean = INTEGER 0/1.

**Tabel `debts`**

| Kolom | Tipe | Constraint | Default | Keterangan |
| --- | --- | --- | --- | --- |
| `id` | INTEGER | PK AUTOINCREMENT |  |  |
| `direction` | TEXT | NOT NULL, CHECK IN ('OWE','OWED') |  | OWE = saya berutang (bayar); OWED = orang berutang ke saya (tagih) |
| `counterparty_name` | TEXT | NOT NULL |  | Nama pihak lawan, diketik manual |
| `principal_amount` | INTEGER | NOT NULL, CHECK (principal\_amount > 0) |  | Pokok utang |
| `paid_amount` | INTEGER | NOT NULL, CHECK (paid\_amount >= 0) | 0 | Total terbayar; diturunkan dari `debt_payments` |
| `status` | TEXT | NOT NULL, CHECK IN ('ACTIVE','PAID','CANCELLED') | 'ACTIVE' | PAID otomatis saat `paid_amount >= principal_amount` |
| `start_date` | INTEGER | NOT NULL |  | Tanggal utang terjadi |
| `due_date` | INTEGER | NULL | NULL | Jatuh tempo (cicilan: jatuh tempo cicilan berikutnya) |
| `installment_amount` | INTEGER | NULL, CHECK (installment\_amount > 0) | NULL | Nominal cicilan bila bercicil |
| `installment_period` | TEXT | NULL, CHECK IN ('WEEKLY','MONTHLY') | NULL | Periode cicilan |
| `account_id` | INTEGER | FK -> `accounts.id` ON DELETE SET NULL, NULL | NULL | Akun tempat uang keluar / masuk |
| `record_to_balance` | INTEGER | NOT NULL, CHECK IN (0,1) | 1 | Bila 1, pembayaran ikut mengubah saldo akun |
| `note` | TEXT | NULL | NULL |  |
| `created_at` / `updated_at` | INTEGER | NOT NULL |  |  |
| `is_deleted` | INTEGER | NOT NULL, CHECK IN (0,1) | 0 | Soft delete |

**Tabel `debt_payments`**

| Kolom | Tipe | Constraint | Default | Keterangan |
| --- | --- | --- | --- | --- |
| `id` | INTEGER | PK AUTOINCREMENT |  |  |
| `debt_id` | INTEGER | NOT NULL, FK -> `debts.id` ON DELETE CASCADE |  |  |
| `amount` | INTEGER | NOT NULL, CHECK (amount > 0) |  | Tidak boleh melebihi sisa utang |
| `paid_at` | INTEGER | NOT NULL |  |  |
| `transaction_id` | INTEGER | FK -> `transactions.id` ON DELETE SET NULL, NULL | NULL | Transaksi yang dibuat (bila `record_to_balance` = 1) |
| `note` | TEXT | NULL | NULL |  |

**Tabel `reminders`**

| Kolom | Tipe | Constraint | Default | Keterangan |
| --- | --- | --- | --- | --- |
| `id` | INTEGER | PK AUTOINCREMENT |  |  |
| `debt_id` | INTEGER | FK -> `debts.id` ON DELETE CASCADE, NULL | NULL | Diisi untuk pengingat utang |
| `plan_id` | INTEGER | FK -> `purchase_plans.id` ON DELETE CASCADE, NULL | NULL | Diisi untuk pengingat Rencana Beli |
| `offset_days` | INTEGER | NOT NULL | 0 | Hari relatif ke jatuh tempo: -3 = H-3, 0 = hari-H, 1 = H+1 |
| `time_of_day_min` | INTEGER | NOT NULL, CHECK (time\_of\_day\_min BETWEEN 0 AND 1439) | 540 | Menit sejak tengah malam (540 = 09:00 waktu setempat) |
| `repeat_every_days` | INTEGER | NULL | NULL | Interval pengulangan setelah lewat tempo |
| `max_repeats` | INTEGER | NOT NULL | 0 | Batas pengulangan |
| `fired_count` | INTEGER | NOT NULL | 0 |  |
| `next_fire_at` | INTEGER | NULL | NULL | Waktu pemicu berikutnya (dihitung ulang tiap perubahan) |
| `snooze_until` | INTEGER | NULL | NULL |  |
| `is_enabled` | INTEGER | NOT NULL, CHECK IN (0,1) | 1 |  |

Constraint tabel: `CHECK ((debt_id IS NULL) <> (plan_id IS NULL))` (tepat satu pemilik).

**Tabel `purchase_plans`**

| Kolom | Tipe | Constraint | Default | Keterangan |
| --- | --- | --- | --- | --- |
| `id` | INTEGER | PK AUTOINCREMENT |  |  |
| `name` | TEXT | NOT NULL |  | Nama barang / layanan |
| `estimated_price` | INTEGER | NOT NULL, CHECK (estimated\_price > 0) |  |  |
| `category_id` | INTEGER | FK -> `categories.id` ON DELETE SET NULL, NULL | NULL | Dipakai untuk simulasi budget kategori |
| `need_level` | TEXT | NOT NULL, CHECK IN ('NEED','WANT') | 'WANT' | Klasifikasi diri sendiri |
| `reason` | TEXT | NULL | NULL | Alasan ingin membeli |
| `link_or_note` | TEXT | NULL | NULL | Tautan produk atau catatan (disimpan sebagai teks, tidak dibuka otomatis) |
| `cooling_off_days` | INTEGER | NOT NULL, CHECK (cooling\_off\_days BETWEEN 0 AND 30) | 3 | Diisi dari aturan harga (9.4) |
| `decide_at` | INTEGER | NOT NULL |  | `created_at` + masa tunggu |
| `postpone_count` | INTEGER | NOT NULL, CHECK (postpone\_count BETWEEN 0 AND 3) | 0 |  |
| `status` | TEXT | NOT NULL, CHECK IN ('WAITING','READY','BOUGHT','SKIPPED','EXPIRED') | 'WAITING' |  |
| `reflection_json` | TEXT | NULL | NULL | Jawaban 3 pertanyaan refleksi |
| `bought_before_ready` | INTEGER | NOT NULL, CHECK IN (0,1) | 0 | 1 bila terbeli sebelum masa tunggu selesai |
| `bought_transaction_id` | INTEGER | FK -> `transactions.id` ON DELETE SET NULL, NULL | NULL |  |
| `decided_at` | INTEGER | NULL | NULL |  |
| `created_at` / `updated_at` | INTEGER | NOT NULL |  |  |

**Kolom tambahan pada `transactions`**

| Kolom | Tipe | Constraint | Default | Keterangan |
| --- | --- | --- | --- | --- |
| `debt_id` | INTEGER | FK -> `debts.id` ON DELETE SET NULL, NULL | NULL | Menandai transaksi arus utang |
| `plan_id` | INTEGER | FK -> `purchase_plans.id` ON DELETE SET NULL, NULL | NULL | Menandai transaksi hasil Rencana Beli |
| `exclude_from_reports` | INTEGER | NOT NULL, CHECK IN (0,1) | 0 | 1 untuk arus utang: tetap mengubah saldo akun, tetapi tidak dihitung sebagai pengeluaran / pemasukan di budget dan laporan kategori |

**Indeks baru:** `idx_debts_dir_status(direction, status, due_date)`, `idx_payments_debt(debt_id, paid_at DESC)`, `idx_reminders_next(is_enabled, next_fire_at)`, `idx_plans_status_decide(status, decide_at)`, `idx_tx_debt(debt_id) WHERE debt_id IS NOT NULL`.

### 9.3 Aturan Bisnis Utang

1. **Dua arah, satu model.** `OWE` (kamu harus bayar) dan `OWED` (kamu harus menagih) memakai tabel yang sama; perbedaannya hanya arah saldo dan teks notifikasi.
2. **Uang pinjaman bukan pendapatan.** Saat utang dibuat dengan `record_to_balance` = 1: OWE membuat transaksi INCOME ke akun terpilih; OWED membuat transaksi EXPENSE dari akun terpilih. Keduanya `exclude_from_reports` = 1 sehingga budget dan laporan kategori tidak terdistorsi.
3. **Pembayaran.** Setiap pembayaran menambah baris `debt_payments` dan (bila `record_to_balance` = 1) transaksi berlawanan arah dengan `exclude_from_reports` = 1. Pembayaran sebagian diperbolehkan; pembayaran melebihi sisa ditolak dengan pesan jelas.
4. **Lunas otomatis.** Saat `paid_amount >= principal_amount`, status menjadi PAID, semua pengingat dinonaktifkan, dan notifikasi aktif dibatalkan.
5. **Cicilan.** Setelah satu cicilan tercatat, `due_date` maju satu periode; jatuh tempo bulanan pada tanggal 29-31 dijepit ke hari terakhir bulan tersebut.
6. **Tanpa jatuh tempo.** `due_date` boleh kosong; pengguna dapat memilih "Ingatkan setiap N hari" (7, 14, atau 30) sebagai gantinya.
7. **Edit dan hapus.** Mengubah `due_date` menghitung ulang `next_fire_at`; menghapus utang (soft delete) membatalkan pengingatnya, dan transaksi terkait tetap ada dengan `debt_id` dikosongkan setelah penghapusan permanen.
8. **Saran dari auto-capture.** Bila transaksi otomatis cocok dengan utang aktif (nominal sama dengan cicilan atau sisa, nama merchant mirip `counterparty_name`, dalam jendela +/- 3 hari dari jatuh tempo), tampilkan saran "Ini pembayaran utang ke {nama}?" di Inbox Perlu Dicek. Penautan hanya terjadi setelah pengguna mengonfirmasi.

### 9.4 Alur & Aturan Rencana Beli

**Masa tunggu default** (dapat diubah per rencana 0-30 hari, ambang dapat diubah di Pengaturan):

| Harga estimasi | Masa tunggu |
| --- | --- |
| < Rp100.000 | 1 hari |
| Rp100.000 - Rp999.999 | 3 hari |
| Rp1.000.000 - Rp4.999.999 | 7 hari |
| >= Rp5.000.000 | 14 hari |

Rencana berklasifikasi `NEED` boleh memakai masa tunggu 0-1 hari agar kebutuhan mendesak tidak tertahan.

**Diagram status:**

```
[Buat Rencana] -> WAITING (timer berjalan, pengingat dijadwalkan di decide_at)
      |
      | decide_at tercapai (notifikasi "Waktunya memutuskan")
      v
    READY -> layar Keputusan: dampak ke budget + 3 pertanyaan refleksi
      |
      +--> [Beli sekarang]   -> Fast Add terisi otomatis -> BOUGHT
      +--> [Tidak jadi]      -> SKIPPED (masuk hitungan "Uang terselamatkan")
      +--> [Tunda 3 hari]    -> WAITING (decide_at diperpanjang, maks 3 kali)
      +--> tanpa respons 30 hari setelah READY -> EXPIRED

WAITING -> transaksi otomatis cocok terdeteksi sebelum waktunya
        -> BOUGHT dengan bought_before_ready = 1 (+ catatan lembut, tanpa menyalahkan)
```

**Simulasi dampak pada layar Keputusan** (dihitung lokal dari data terbaru):

| Indikator | Rumus |
| --- | --- |
| Sisa budget setelah beli | sisa budget bulan ini - `estimated_price` (merah bila negatif, dengan label "Melebihi budget") |
| Setara hari pengeluaran | `estimated_price` / rata-rata pengeluaran harian 30 hari terakhir |
| Porsi budget kategori | `estimated_price` / limit budget kategori terkait (bila ada) |
| Beban utang bulan ini | Total utang OWE yang jatuh tempo dalam 30 hari ke depan |

**Tiga pertanyaan refleksi** (jawaban Ya / Tidak, disimpan di `reflection_json`; tidak memblokir keputusan):

1. Apakah saya masih menginginkannya setelah masa tunggu ini?
2. Apakah saya belum punya barang dengan fungsi yang sama?
3. Apakah pembelian ini tidak mengganggu tagihan dan utang yang jatuh tempo bulan ini?

**Pintu masuk Rencana Beli:** (a) layar Rencana, (b) tombol "Tunda dulu" di Fast Add Modal yang memindahkan nominal, kategori, dan merchant yang sudah diketik menjadi rencana baru, (c) tekan lama tombol + di bar navigasi.

**Metrik motivasi:** kartu "Uang terselamatkan bulan ini" = jumlah `estimated_price` rencana berstatus SKIPPED, ditampilkan di layar Rencana dan (opsional) Beranda. Status EXPIRED tidak dihitung.

### 9.5 Pengingat & Notifikasi

| Jenis | Preset default | Aksi pada notifikasi |
| --- | --- | --- |
| Bayar utang (OWE) | H-3, H-1, hari-H pukul 09:00; setelah lewat tempo ulang tiap 3 hari, maksimal 3 kali | Catat Pembayaran, Tunda 1 hari |
| Tagih utang (OWED) | H-1 dan hari-H pukul 09:00; setelah lewat tempo ulang tiap 3 hari, maksimal 3 kali | Kirim Pesan Tagihan, Sudah Dibayar, Tunda 1 hari |
| Rencana Beli | Saat `decide_at` tercapai; pengingat kedua 24 jam kemudian bila belum diputuskan | Lihat Keputusan, Tunda 1 hari |

**Penjadwalan.** Tidak memakai exact alarm (`SCHEDULE_EXACT_ALARM` dibatasi pada Android terbaru dan sensitif terhadap kebijakan Play). Mekanisme: satu `OneTimeWorkRequest` (WorkManager) per `next_fire_at` terdekat, ditambah `ReminderSweepWorker` harian pukul 08:30 sebagai jaring pengaman. Toleransi keterlambatan sampai sekitar 15 menit akibat Doze dan App Standby. Jadwal dihitung ulang pada `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, `ACTION_TIMEZONE_CHANGED`, dan setiap perubahan data utang atau rencana.

**Kanal notifikasi:** "Pengingat Utang" (importance DEFAULT) dan "Rencana Beli" (importance LOW), terpisah dari "Peringatan Budget" sehingga dapat dimatikan sendiri-sendiri.

**Privasi isi notifikasi.** Nama pihak lawan bersifat pribadi. Di lock screen, notifikasi memakai `VISIBILITY_PRIVATE` dengan versi publik "Ada pengingat utang". Bila Mode Sembunyikan Saldo aktif, nominal tampil sebagai `Rp ••••••`.

**Pesan tagihan.** Tombol "Kirim Pesan Tagihan" membuka share sheet sistem (`ACTION_SEND`, teks biasa); pengguna memilih sendiri aplikasi dan penerimanya, sehingga aplikasi tidak perlu izin kontak atau akses internet. Template dapat diedit dan memiliki dua nada:

| Nada | Isi template |
| --- | --- |
| Santai | Hai {nama}, mengingatkan soal pinjaman Rp {sisa} yang jatuh tempo {tanggal} ya. Kalau sudah ada, boleh transfer ke {akun}. Makasih! |
| Sopan | Selamat siang {nama}, saya mengingatkan pinjaman sebesar Rp {sisa} yang jatuh tempo pada {tanggal}. Mohon konfirmasinya bila ingin dibayar melalui {akun}. Terima kasih. |

Variabel dalam kurung kurawal diisi aplikasi saat pesan dibuat; akun hanya disertakan bila pengguna mengaktifkannya.

### 9.6 Desain UI

- **Pintu masuk:** dua kartu baru di Beranda ("Utang" dan "Rencana Beli") di bawah Net Cashflow Card. Kartu Utang menampilkan total yang harus dibayar, total yang akan ditagih, dan jumlah jatuh tempo 7 hari ke depan. Kartu Rencana menampilkan jumlah rencana menunggu dan yang paling dekat siap diputuskan. Deep link: `smartspend://debts`, `smartspend://debts/{id}`, `smartspend://plans`, `smartspend://plans/{id}`.
- **Tombol +:** tekan singkat membuka Fast Add Modal; tekan lama membuka speed dial berisi Transaksi, Catat Utang, dan Rencana Beli.
- **Layar Utang:** segmented control "Saya Berutang" / "Berutang ke Saya"; kartu per utang berisi nama, sisa, progress bar pelunasan, dan chip jatuh tempo (abu-abu bila lebih dari 7 hari, kuning dalam 7 hari, merah bila lewat). Detail utang memuat riwayat pembayaran, jadwal pengingat, dan tombol Catat Pembayaran / Tandai Lunas.
- **Form Catat Utang:** arah, nama, nominal (numpad kustom yang sama dengan Fast Add), tanggal, jatuh tempo, opsi cicilan, akun, sakelar "Catat ke saldo akun", dan pilihan preset pengingat.
- **Layar Rencana Beli:** tab Menunggu / Siap / Riwayat; kartu berisi nama, harga, cincin progres masa tunggu dengan hitung mundur (contoh: "Siap diputuskan dalam 2 hari 4 jam"), dan indikator dampak singkat. Tab Riwayat memuat Dibeli, Tidak jadi, dan Kedaluwarsa.
- **Layar Keputusan:** simulasi dampak, tiga pertanyaan refleksi, lalu tiga tombol (Beli sekarang, Tidak jadi, Tunda 3 hari). Tombol "Tidak jadi" memakai gaya utama karena itulah hasil yang ingin difasilitasi fitur ini, tanpa menghalangi tombol Beli.
- **Empty state:** ilustrasi dan satu kalimat manfaat, misalnya "Tahan dulu 3 hari, lalu putuskan dengan kepala dingin."

### 9.7 Dampak ke Bagian Lain

| Area | Perubahan |
| --- | --- |
| Dashboard dan laporan | Pemasukan, pengeluaran, dan breakdown kategori mengecualikan transaksi `exclude_from_reports` = 1; arus utang tampil terpisah di kartu Utang |
| Budget | Tidak terpengaruh oleh arus utang; Rencana Beli yang dibeli masuk sebagai pengeluaran biasa pada kategorinya |
| Widget Medium / Large | Medium: lencana kecil "N jatuh tempo" bila ada utang jatuh tempo dalam 7 hari. Large: satu baris "Bayar Rp X, tagih Rp Y dalam 7 hari". Mode Sembunyikan Saldo berlaku; Small tidak berubah |
| Snapshot widget | `WidgetStateStore` menambah agregat utang jatuh tempo (jumlah dan total), bukan data per orang |
| Parser dan dedup | Tidak berubah; hanya ditambah saran penautan utang (9.3 butir 8) dan deteksi pembelian dini untuk Rencana Beli (kecocokan nominal +/- 10% dan nama merchant mirip) |
| Cadangan terenkripsi | Empat tabel baru ikut diekspor dan diimpor |
| Privasi dan Play | Nama pihak lawan adalah data pribadi pihak ketiga yang disimpan hanya di perangkat; cantumkan di kebijakan privasi dan Data safety form, tetap tanpa pengiriman data dan tanpa izin kontak. Izin tambahan: tidak ada (memakai `POST_NOTIFICATIONS` yang sudah ada) |
| Hapus semua data | Mencakup `debts`, `debt_payments`, `reminders`, `purchase_plans` |

### 9.8 Kriteria Penerimaan & Roadmap

| Kriteria | Ambang lulus |
| --- | --- |
| Pengingat jatuh tempo muncul pada jam yang dijadwalkan | Deviasi <= 15 menit pada 4 OEM uji, termasuk setelah restart perangkat |
| Pembayaran sebagian dan pelunasan | `paid_amount`, status, dan saldo akun konsisten pada uji properti |
| Transaksi arus utang | Tidak muncul di budget dan breakdown kategori; saldo akun tetap benar |
| Rencana Beli | Status berpindah WAITING -> READY tepat pada `decide_at`; tunda maksimal 3 kali |
| Mode Sembunyikan Saldo | Nominal tersamar di notifikasi, layar utang, dan widget |
| Perubahan zona waktu | Pengingat dijadwalkan ulang ke jam lokal yang benar |
| Migrasi v1 ke v2 | Data lama utuh; uji migrasi otomatis lulus |

**Penambahan roadmap: Sprint 4 (Minggu 7-8).** Fitur ini ditempatkan setelah Sprint 3 agar pipeline auto-tracking dan widget tidak terganggu, sehingga total MVP menjadi sekitar 8 minggu.

| Epik | Deliverable | Kriteria selesai |
| --- | --- | --- |
| Skema dan migrasi | Empat tabel baru, kolom tambahan `transactions`, indeks | Uji migrasi lulus |
| Modul utang | Form, daftar, pembayaran, cicilan, pelunasan otomatis | Aturan 9.3 teruji |
| Mesin pengingat | WorkManager, sweep harian, aksi notifikasi, pesan tagihan | Kriteria penerimaan pengingat lulus |
| Rencana Beli | Pembuatan, masa tunggu, layar Keputusan, simulasi dampak, Uang terselamatkan | Diagram status 9.4 teruji |
| Integrasi | Kartu Beranda, speed dial, lencana widget, cadangan | Regresi Sprint 1-3 hijau |

Bila waktu terbatas, **Rencana Beli dapat digeser ke rilis 1.1** tanpa memengaruhi modul utang, karena keduanya hanya berbagi tabel `reminders`.
