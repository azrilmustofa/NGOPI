# Kopi Inggil — Admin Web Dashboard

Panel administrasi berbasis web untuk manajemen operasional outlet Kopi Inggil (NGOPI PRD v2.0).

## Fitur
1. **Ringkasan & Metrik Kafe (Dashboard)**: Total omzet penjualan, jumlah transaksi, total antrean aktif, dan status ketersediaan menu.
2. **Antrean Pesanan Realtime**: Monitor pesanan langsung dari pelanggan via Supabase Realtime Channel, update status pesanan (`PAID` → `PREPARING` → `READY` → `COMPLETED` atau `CANCELLED`), serta rincian item & catatan.
3. **Katalog Menu & Kategori**: Tambah / edit menu, atur harga, deskripsi, foto produk, informasi alergen, dan toggle stok (Tersedia / Habis).
4. **Meja & Kode QR**: Registrasi nomor meja, toggle meja aktif/nonaktif, dan generate stiker QR siap cetak untuk dipasang di meja kafe.
5. **Kelola Staf**: Manajemen peran pengguna (Admin, Owner, Barista/Kitchen, Kasir).
6. **Pengaturan Pajak & Biaya**: Persentase PB1 restoran, service charge, serta konfigurasi database Supabase.
7. **Laporan Penjualan**: Filter periode WIB, omzet, transaksi, menu terlaris, metode pembayaran, dan unduh CSV.
8. **Template Struk WhatsApp**: Edit header, judul, dan penutup struk lunas/selesai dengan pratinjau.
9. **Audit Admin**: Filter pelaku, periode, bagian, dan tindakan; lihat nilai sebelum/sesudah perubahan.

## Cara Menjalankan

```bash
cd admin_web
npm install
npm run dev
```

Buka `http://localhost:5173` di browser.
Login menggunakan akun Supabase Auth dengan profil `staff_user` berperan `ADMIN` atau `OWNER` dan `is_active = true`. Panel menolak akses jika profil tidak tersedia atau gagal diverifikasi. Peran kasir di database adalah `CASHIER`.

Pembatasan panel harus dilengkapi kebijakan RLS Supabase untuk melindungi akses database langsung; pemeriksaan di browser tidak menggantikan otorisasi server.

Jalankan `npm test` untuk tes halaman, akses admin, laporan, CSV, fungsi staf, dan RLS PostgreSQL. Jalankan `npm run build` untuk build produksi.

Petunjuk backend dan status pemasangan ada di [supabase/README.md](supabase/README.md). Pekerjaan No. 27 (QR) ditunda dan disimpan terpisah di `supabase/deferred/`.
