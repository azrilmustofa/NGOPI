# Kopi Inggil — Admin Web Dashboard

Panel administrasi berbasis web untuk manajemen operasional outlet Kopi Inggil (NGOPI PRD v2.0).

## Fitur
1. **Ringkasan & Metrik Kafe (Dashboard)**: Total omzet penjualan, jumlah transaksi, total antrean aktif, dan status ketersediaan menu.
2. **Antrean Pesanan Realtime**: Monitor pesanan langsung dari pelanggan via Supabase Realtime Channel, update status pesanan (`PAID` → `PREPARING` → `READY` → `COMPLETED` atau `CANCELLED`), serta rincian item & catatan.
3. **Katalog Menu & Kategori**: Tambah / edit menu, atur harga, deskripsi, foto produk, informasi alergen, dan toggle stok (Tersedia / Habis).
4. **Meja & Kode QR**: Registrasi nomor meja, toggle meja aktif/nonaktif, dan generate stiker QR siap cetak untuk dipasang di meja kafe.
5. **Kelola Staf**: Manajemen peran pengguna (Admin, Owner, Barista/Kitchen, Kasir).
6. **Pengaturan Pajak & Biaya**: Persentase PB1 restoran, service charge, serta konfigurasi database Supabase.

## Cara Menjalankan

```bash
cd admin_web
npm install
npm run dev
```

Buka `http://localhost:5173` di browser.
Dapat login menggunakan akun staf Supabase Auth atau menggunakan tombol **Mode Pengembang / Demo** untuk akses langsung.
