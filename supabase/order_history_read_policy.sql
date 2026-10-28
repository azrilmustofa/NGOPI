-- order_history_read_policy.sql  (No. 16: timeline status pelanggan)
-- Pelanggan boleh MEMBACA riwayat status pesanannya sendiri, supaya timeline di "Pesanan diproses" bisa
-- menampilkan jam tiap tahap. Menulis riwayat tetap hanya oleh Edge Function / staf.
-- Jalankan sekali di Supabase Dashboard -> SQL Editor.

drop policy if exists "order_status_history_select_own" on public.order_status_history;
create policy "order_status_history_select_own" on public.order_status_history
  for select to authenticated
  using (exists (
    select 1 from public.customer_order o
    where o.id = order_status_history.order_id and o.owner_uid = auth.uid()
  ));
