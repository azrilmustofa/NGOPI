-- payment_retry.sql  (No. 13: coba bayar ulang dengan aman)
-- Aturan: satu transaksi Midtrans aktif (PENDING) per pesanan. retry-payment membatalkan transaksi lama
-- sebelum membuat yang baru; index ini menjaga kalau dua permintaan datang bersamaan.
-- Jalankan sekali di Supabase Dashboard -> SQL Editor.

-- Bersihkan data lama: kalau satu pesanan punya lebih dari satu PENDING, sisakan yang terbaru.
update public.payment_attempt p
set status = 'CANCELLED', updated_at = now()
where p.status = 'PENDING'
  and exists (
    select 1 from public.payment_attempt q
    where q.order_id = p.order_id and q.status = 'PENDING' and q.created_at > p.created_at
  );

create unique index if not exists payment_attempt_one_pending_per_order
  on public.payment_attempt (order_id)
  where status = 'PENDING';
