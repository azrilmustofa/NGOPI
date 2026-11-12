# Antrean, status, dan kirim ulang struk (No. 19, 20, 25)

`order_operations.sql` memasang RPC `transition_order` dan trigger untuk urutan PAID → ACCEPTED → PREPARING → READY → COMPLETED. Status, waktu server, dan riwayat disimpan dalam satu transaksi. Permintaan dengan status lama ditolak; aplikasi memuat ulang data sebelum melanjutkan.

Barista dapat menjalankan tahap produksi. Kasir dapat menerima pembayaran tunai dan membatalkan pesanan aktif dengan alasan. Admin/pemilik aktif dapat melakukan keduanya. Tulis langsung dari client ke `customer_order` dan `order_status_history`, termasuk RPC simulasi pembayaran lama, ditutup. Webhook tetap memakai service role; trigger turut mencatat riwayatnya.

`get_order_receipt` hanya memberikan struk pesanan lunas kepada kasir/admin/pemilik aktif. Data berasal dari `private_customer`, snapshot item/opsi, dan nominal pesanan. Tabel data pribadi tetap tidak dibuka untuk pembacaan umum. Pengiriman ulang dicatat melalui `record_receipt_resend` ke `audit_log`, terpisah dari riwayat status produksi.

## Pemasangan

1. Terapkan `order_operations.sql` melalui SQL Editor pada proyek Supabase PTT.
2. Deploy versi terbaru `functions/midtrans-webhook/index.ts`. Fungsi ini memakai trigger untuk riwayat dan dapat melanjutkan pembaruan pesanan bila attempt sudah lunas. Pengaturan JWT webhook tetap OFF dan secret `MIDTRANS_SERVER_KEY` tetap digunakan.
3. Pasang APK Android baru dan gunakan build admin web baru. Client lama yang masih menulis status langsung akan ditolak setelah SQL diterapkan.

Untuk kirim ulang struk: **Bantuan Kasir → Kirim Ulang Struk → pilih transaksi lunas → Kirim ulang struk WhatsApp**. Nama, nomor, item, total, dan waktu pembayaran diambil ulang dari server. Nomor dapat dikoreksi khusus untuk pengiriman ini. SENT berarti diterima WAHA; OPENED hanya berarti aplikasi WhatsApp dibuka, bukan bukti pesan terkirim.

Pengiriman masih memakai WAHA yang sudah ada di laptop. Migrasi ini tidak memasang bot percakapan, notifikasi Firebase, refund, atau antrean kirim otomatis di server.

## Cek teknis perubahan

- Dari `admin_web`: `node --test tests/order-operations.test.mjs tests/order-workflow.test.mjs` menguji urutan/peran/riwayat atomik, akses data struk, alur admin, dan pemulihan webhook.
- Dari root: `./gradlew :app:testDebugUnitTest --tests kelompok.satu.kopiinggil.OrderReceiptTest --tests kelompok.satu.kopiinggil.WhatsAppBotTest :app:assembleDebug`.
- `verify_order_operations.sql` memeriksa transaksi dan akses menggunakan data server yang sudah ada; seluruh perubahan dibatalkan dengan ROLLBACK. Memerlukan staf barista/kasir aktif dan setidaknya satu pesanan PAID beserta data pelanggan.

Pengujian pengguna, pengujian menyeluruh, dan backup/restore tetap pekerjaan No. 31–32 yang ditunda.

## Status penerapan

SQL `order_operations.sql` dan pembaruan `midtrans-webhook` sudah diterapkan ke proyek PTT (`uvaeklvwkxibwcrhofku`) pada 10 November 2026. Dashboard mengonfirmasi deployment webhook berhasil; Verify JWT tetap OFF.

`verify_order_operations.sql` berhasil dijalankan pada database aktif. Urutan status, penolakan permintaan lama, pencatatan riwayat, akses struk kasir, dan penolakan akses struk barista lolos. Seluruh perubahan pemeriksaan dibatalkan dengan ROLLBACK. Tidak ada WhatsApp yang dikirim.

APK baru tersedia di `app/build/outputs/apk/debug/app-debug.apk` dari root proyek. Pemasangan APK ke perangkat belum dilakukan pada penerapan server ini.

## Notifikasi Firebase (No. 20)

`push_notifications.sql` menyimpan token perangkat melalui RPC yang hanya dapat dipakai pemilik sesi, lalu membuat event saat status pesanan berubah. `functions/order-push` mengambil event sekali, memilih perangkat pelanggan atau barista aktif, dan mengirim data message FCM HTTP v1. Pesanan yang dibuat kasir untuk pelanggan tidak mengirim notifikasi pelanggan ke perangkat kasir.

Penerapan server:

1. Terapkan `push_notifications.sql` melalui SQL Editor.
2. Atur secret Edge Function `FIREBASE_PROJECT_ID`, `FIREBASE_CLIENT_EMAIL`, dan `FIREBASE_PRIVATE_KEY` dari service-account JSON. Private key tidak boleh disimpan di Git.
3. Deploy `functions/order-push/index.ts` dengan Verify JWT OFF.
4. Buat Database Webhook untuk tabel `public.push_event`, event `INSERT`, tujuan Edge Function `order-push`, metode `POST`, timeout 1000 ms.

Android meminta izin notifikasi pada Android 13+, mendaftarkan ulang token saat masuk, menghapus token saat keluar, dan membuka tab pesanan atau antrean staf saat notifikasi ditekan.
