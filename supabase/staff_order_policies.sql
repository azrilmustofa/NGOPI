-- staff_order_policies.sql
-- Kebijakan RLS agar akun staf (barista/kasir yang aktif di tabel staff_user)
-- dapat membaca seluruh antrean pesanan dan memperbarui status pesanan.
-- Jalankan sekali di Supabase Dashboard -> SQL Editor jika RLS diaktifkan.

-- 1. Izin bagi staf untuk MEMBACA (SELECT) seluruh customer_order
create policy "staff_order_select_all" on public.customer_order
  for select to authenticated
  using (
    exists (
      select 1 from public.staff_user
      where staff_user.uid = auth.uid() and staff_user.is_active = true
    )
  );

-- 2. Izin bagi staf untuk MEMPERBARUI (UPDATE) status pada customer_order
create policy "staff_order_update_status" on public.customer_order
  for update to authenticated
  using (
    exists (
      select 1 from public.staff_user
      where staff_user.uid = auth.uid() and staff_user.is_active = true
    )
  )
  with check (
    exists (
      select 1 from public.staff_user
      where staff_user.uid = auth.uid() and staff_user.is_active = true
    )
  );

-- 3. Izin bagi staf untuk MEMBACA (SELECT) seluruh order_item
drop policy if exists "staff_order_item_select_all" on public.order_item;
create policy "staff_order_item_select_all" on public.order_item
  for select to authenticated
  using (
    exists (
      select 1 from public.staff_user
      where staff_user.uid = auth.uid() and staff_user.is_active = true
    )
  );

-- Pastikan policy publik terbuka dicabut
drop policy if exists "admin_order_item_all" on public.order_item;
drop policy if exists "admin_order_item_option_all" on public.order_item_option;

-- 4. Izin bagi staf untuk MEMBACA (SELECT) seluruh order_item_option
create policy "staff_order_item_option_select_all" on public.order_item_option
  for select to authenticated
  using (
    exists (
      select 1 from public.staff_user
      where staff_user.uid = auth.uid() and staff_user.is_active = true
    )
  );

-- 5. Izin bagi staf untuk MENULIS (INSERT) ke order_status_history
create policy "staff_order_status_history_insert" on public.order_status_history
  for insert to authenticated
  with check (
    exists (
      select 1 from public.staff_user
      where staff_user.uid = auth.uid() and staff_user.is_active = true
    )
  );

-- 6. Izin bagi staf untuk MEMBACA (SELECT) order_status_history
create policy "staff_order_status_history_select" on public.order_status_history
  for select to authenticated
  using (
    exists (
      select 1 from public.staff_user
      where staff_user.uid = auth.uid() and staff_user.is_active = true
    )
  );
