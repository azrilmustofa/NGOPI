# Integrasi admin No. 28–30

## Fitur

- **Laporan penjualan:** filter tanggal WIB (maksimal 367 hari), omzet, transaksi lunas/batal/belum dibayar, diskon, pajak, biaya layanan, penjualan harian, menu terlaris, metode pembayaran, dan unduh CSV. Agregasi dilakukan di database tanpa batas baris pembacaan tabel dari browser. Satu pesanan dihitung sekali meskipun punya beberapa percobaan pembayaran.
- **Staf dan biaya:** buat akun melalui Edge Function, ubah peran/aktif/nonaktif staf, dan simpan pajak/biaya layanan. Peran database mengikuti skema produksi: `ADMIN`, `OWNER`, `BARISTA`, dan `CASHIER`. Panel tidak membuat peran OWNER. Akun pengelola yang sedang dipakai tidak dapat diturunkan atau dinonaktifkan; akses pemilik dilindungi di database.
- **Template struk:** edit header, judul, serta penutup struk `PAID` dan `COMPLETED` dengan pratinjau. Nomor pesanan, item, total, dan status tetap dibuat aplikasi. Build Android baru memuat template sebelum mengirim struk dan memakai format bawaan jika template gagal dimuat.
- **Audit:** perubahan menu, kategori, meja, staf, biaya, serta template masuk ke tabel `audit_log` yang sudah ada. View `admin_audit_log` menyediakan nama pelaku dan nilai sebelum/sesudah untuk panel. Filter periode, pelaku, bagian, tindakan, dan pagination dilakukan di server. Browser tidak dapat menulis atau menghapus log.

Laporan memakai waktu pesanan dibuat dan status saat laporan dibuka. Omzet termasuk pajak/biaya layanan, serta mengecualikan pesanan batal dan belum dibayar. Angka tersebut bukan perhitungan refund atau rekonstruksi status historis.

## Pemasangan

1. Terapkan `migrations/202611090001_admin_operations.sql` pada proyek Supabase aplikasi.
2. Deploy `functions/admin-create-staff/index.ts` dengan konfigurasi `config.toml`. Fungsi memerlukan environment bawaan Supabase `SUPABASE_URL` dan `SUPABASE_SERVICE_ROLE_KEY`.
3. Gunakan build web terbaru. Jalankan `npm run dev` untuk pengembangan atau `npm run build` untuk menyiapkan `dist/`.
4. Pasang build Android terbaru agar pengiriman struk menggunakan template dari server.

Gateway JWT legacy dimatikan khusus untuk fungsi staf karena handler sudah memverifikasi token melalui `auth.getUser(token)`, lalu memeriksa profil ADMIN/OWNER aktif. Secret/service role key tidak masuk ke browser. Pembuatan profil memakai token admin pemanggil agar audit mencatat pelaku; akun Auth baru dibersihkan bila profil gagal disimpan.

## Verifikasi

- `npm test`: halaman dan formulir React, akses admin/pemilik, CSV, pembuatan staf, serta migrasi/RLS dengan PostgreSQL PGlite di memori.
- `npm run build` dan `npm run lint`: build dan pemeriksaan kode web.
- Dari root Android: `./gradlew :app:testDebugUnitTest :app:assembleDebug`.
- `verify_admin_operations.sql`: uji langsung laporan, pembatasan kasir, dan audit perubahan template. Seluruh perubahan uji dibatalkan melalui rollback. Script memerlukan satu profil admin/pemilik aktif dan satu kasir aktif.

Migrasi No. 28–30 dan fungsi `admin-create-staff` sudah diterapkan pada proyek PTT (`uvaeklvwkxibwcrhofku`) pada 9 November 2026. Uji SQL langsung lulus, endpoint tanpa login ditolak dengan HTTP 401, dan CORS mengembalikan HTTP 204. Akun staf riil baru dan pengiriman WhatsApp riil tidak dibuat/dikirim saat pengujian.

## No. 27 ditunda

Kode QR tidak ikut dipasang. Draft migrasi dan perubahan scanner tersimpan di `deferred/`. Scanner Android tetap memakai alur QR sebelumnya sampai pekerjaan No. 27 dilanjutkan.
