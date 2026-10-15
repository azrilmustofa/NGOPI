-- Pelanggan yang login hanya boleh MEMBACA order miliknya sendiri (owner_uid = auth.uid()).
-- Semua tulis ke tabel order dilakukan Edge Function dengan service role, jadi tidak ada policy INSERT/UPDATE.
-- Jalankan sekali di Supabase Dashboard -> SQL Editor.

create policy "customer_order_select_own" on public.customer_order
  for select to authenticated
  using (owner_uid = auth.uid());

create policy "order_item_select_own" on public.order_item
  for select to authenticated
  using (exists (
    select 1 from public.customer_order o
    where o.id = order_item.order_id and o.owner_uid = auth.uid()
  ));

create policy "order_item_option_select_own" on public.order_item_option
  for select to authenticated
  using (exists (
    select 1
    from public.order_item i
    join public.customer_order o on o.id = i.order_id
    where i.id = order_item_option.order_item_id and o.owner_uid = auth.uid()
  ));

create policy "payment_attempt_select_own" on public.payment_attempt
  for select to authenticated
  using (exists (
    select 1 from public.customer_order o
    where o.id = payment_attempt.order_id and o.owner_uid = auth.uid()
  ));
