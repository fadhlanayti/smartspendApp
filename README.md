# SmartSpend ⚡
> Modern Android Personal Finance Manager with System-Level Dynamic Island, Instant Notification Parser, and Glance Widgets.

SmartSpend adalah aplikasi manajemen keuangan pribadi offline-first berkinerja tinggi yang dirancang dengan estetika **Dark Obsidian & Electric Lime**. Aplikasi ini mengintegrasikan floating **Dynamic Island tingkat sistem** yang disesuaikan secara presisi dengan punch-hole kamera perangkat, auto-capture notifikasi transaksi bank/e-wallet, serta home screen widget interaktif berbasis Jetpack Glance.

---

## 📱 Tampilan Aplikasi

<p align="center">
  <img src="screenshots/01_home_dashboard.png" width="31%" alt="Home Dashboard" />
  <img src="screenshots/02_dynamic_island_compact.png" width="31%" alt="Dynamic Island Compact" />
  <img src="screenshots/03_dynamic_island_expanded.png" width="31%" alt="Dynamic Island Expanded" />
</p>

<p align="center">
  <img src="screenshots/04_transactions.png" width="48%" alt="Riwayat Transaksi" />
  <img src="screenshots/05_reports.png" width="48%" alt="Laporan & Analitik" />
</p>

---

## 🌟 Fitur Utama

### 1. 🏝️ System-Level Dynamic Island (Accessibility Overlay)
- **Punch-Hole Framing Presisi**: Dikalibrasi khusus dengan spacer tengah (`42dp`) dan safe margin atas (`8dp`) untuk membingkai punch-hole kamera fisik tanpa menutupi status bar sistem.
- **Launcher-Only Lifecycle**: Island hanya aktif dan muncul di Home Screen / Launcher; otomatis tersembunyi (*gone*) saat membuka aplikasi lain dan muncul kembali saat ditutup ke homescreen.
- **Auto-Collapse Cerdas (10 Detik)**: Mode expanded otomatis mengecil kembali menjadi compact pill jika tidak ada interaksi sentuh selama 10 detik (timer otomatis di-reset saat disentuh).
- **Gestur Geser Fluid**: Swipe up untuk menciutkan expanded card secara instan dengan kurva interpolasi Apple Spring.
- **Live Alert Banner**: Notifikasi transaksi masuk langsung memicu morphing banner pulau dengan detail merchant dan nominal.

### 2. ⚡ Real-Time Notification Parser & Dedup Engine
- Menangkap notifikasi transaksi SMS dan push app secara real-time dari bank konvensional (BCA, Mandiri, BRI, BNI) dan dompet digital (GoPay, OVO, ShopeePay, Dana).
- **Deduplication Engine**: Mencegah pencatatan ganda melalui hashing hash/payload jendela waktu 60 detik.
- **Amount Normalizer**: Deteksi otomatis format mata uang lokal (`Rp`, titik/koma ribuan, desimal) dengan akurasi tinggi.

### 3. 🎨 Obsidian & Electric Lime Design System
- Dibangun 100% menggunakan **Jetpack Compose** & **Material 3**.
- Palet warna gelap kontras tinggi: Obsidian Surface (`#101216`), Hairline Border (`#1F222A`), dan aksen Electric Lime (`#CCFF00`).
- **Animated Celestial Canvas**: Header dinamis dengan elemen matahari berputar mulus 360° (siang) atau bulan sabit bercahaya (malam) tanpa emoji statis atau gradien artifisial.
- **Monospace Live Clock**: Jam realtime presisi dengan indikator denyut nadi hijau (*pulsing dot*).

### 4. 📊 Budget Enveloping & Cashflow Analytics
- **Alokasi Budget Fleksibel**: Pembagian anggaran per kategori dengan indikator visual sisa dana bulanan dan harian.
- **Net Cashflow Tracking**: Monitoring rasio pengeluaran vs pemasukan realtime.
- **Review Review Queue**: Antrean konfirmasi transaksi dengan tingkat keyakinan rendah agar data keuangan tetap 100% akurat.

### 5. 📱 Glance AppWidget
- Widget beranda modern berbasis **Jetpack Glance**.
- Menyediakan snapshot saldo rekening, ringkasan pengeluaran bulan berjalan, dan tombol jalan pintas cepat (*Fast Add* & *Riwayat*).

---

## 🛠️ Tech Stack & Arsitektur

- **Bahasa**: Kotlin (100%)
- **UI Toolkit**: Jetpack Compose & Material 3
- **Local Database**: Room DB (SQLite) dengan Kotlin Coroutines & Flow
- **Widget**: AndroidX Glance AppWidget
- **Overlay System**: Android Accessibility Service (`TYPE_ACCESSIBILITY_OVERLAY` di layer `311000`) & Android WindowManager
- **Background Processing**: WorkManager (Daily Sweep Worker & Reminder)
- **Local DataStore**: Preferences DataStore untuk konfigurasi & snapshot widget
- **Arsitektur**: MVVM (Model-View-ViewModel) + Clean Architecture pattern (Repository, Dao, Engine)

---

## 🚀 Memulai (Build & Setup)

### Prasyarat
- Android Studio Ladybug / Meerkat atau yang lebih baru
- JDK 17 / JBR 17+
- Android SDK 35 (Android 15) dengan minimal SDK 26 (Android 8.0)

### Clone & Build
```bash
git clone https://github.com/fadhlanayti/smartspendApp.git
cd smartspendApp
./gradlew assembleDebug
```

### Izin yang Diperlukan pada Perangkat
Aplikasi ini memerlukan izin sistem berikut untuk menjalankan fitur otomatisasi:
1. **Akses Notifikasi** (`Notification Listener Service`): Untuk parsing transaksi otomatis.
2. **Layanan Aksesibilitas** (`Accessibility Service`): Untuk mendeteksi launcher dan menampilkan Dynamic Island di atas antarmuka sistem.
3. **Gambar di Atas Aplikasi Lain** (`SYSTEM_ALERT_WINDOW`): Untuk rendering floating overlay.

---

## 📄 Lisensi
Hak Cipta © 2026 SmartSpend Project. Didistribusikan di bawah lisensi open source.
