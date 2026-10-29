-- ==============================================================================
-- FIX SECURITY POLICIES (MENGATASI KERENTANAN RLS DI KOPI INGGIL)
-- Jalankan di: Supabase Dashboard -> SQL Editor -> Run
-- ==============================================================================
--
-- File ini mencabut policy publik terbuka (using true) yang rentan disalahgunakan
-- pihak anonim/luar untuk memanipulasi pesanan (misal set PAID secara gratis)
-- atau merusak data pengguna lain.
--
-- Arsitektur Keamanan:
-- 1. Publik / Anonim: HANYA boleh membaca data publik (katalog menu, kategori, meja aktif, promo).
-- 2. Pelanggan (authenticated): HANYA membaca pesanan miliknya sendiri (owner_uid = auth.uid()).
-- 3. Staf / Barista / Kasir / Admin (authenticated + terdaftar aktif di staff_user):
--    Diberikan hak membaca antrean dan memperbarui status pesanan.
-- 4. Edge Function / Webhook: Menggunakan service_role key yang otomatis bypass RLS.
-- ==============================================================================

-- ==============================================================================
-- 1. FUNGSI BANTU SECURITY DEFINER (Mencegah Rekursi RLS & Menangani Enum Role)
-- ==============================================================================
create or replace function public.is_active_staff()
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1 from public.staff_user
    where uid = auth.uid() and is_active = true
  );
$$;

create or replace function public.is_admin_or_owner()
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1 from public.staff_user
    where uid = auth.uid()
      and is_active = true
      and lower(role::text) in ('admin', 'owner')
  );
$$;

-- ------------------------------------------------------------------------------
-- 2. CABUT SELURUH POLICY TERBUKA (PUBLIC USING TRUE)
-- ------------------------------------------------------------------------------
drop policy if exists "admin_customer_order_all" on public.customer_order;
drop policy if exists "admin_order_item_all" on public.order_item;
drop policy if exists "admin_order_item_option_all" on public.order_item_option;
drop policy if exists "admin_staff_user_all" on public.staff_user;

-- Pastikan RLS aktif di seluruh tabel operasional
alter table public.customer_order enable row level security;
alter table public.order_item enable row level security;
alter table public.order_item_option enable row level security;
alter table public.order_status_history enable row level security;
alter table public.staff_user enable row level security;
alter table public.menu_item enable row level security;
alter table public.menu_category enable row level security;
alter table public.cafe_table enable row level security;
alter table public.app_settings enable row level security;

-- ------------------------------------------------------------------------------
-- 3. TABEL: staff_user
-- ------------------------------------------------------------------------------
-- Pengguna login boleh membaca profil staf miliknya sendiri (untuk validasi role saat login)
drop policy if exists "staff_user_select_self" on public.staff_user;
create policy "staff_user_select_self" on public.staff_user
  for select to authenticated
  using (uid = auth.uid());

-- Staf dengan role admin / owner dapat mengelola (CRUD) seluruh data staff_user
drop policy if exists "staff_user_admin_manage" on public.staff_user;
create policy "staff_user_admin_manage" on public.staff_user
  for all to authenticated
  using (public.is_admin_or_owner())
  with check (public.is_admin_or_owner());

-- ------------------------------------------------------------------------------
-- 4. TABEL: customer_order
-- ------------------------------------------------------------------------------
-- Pelanggan: Hanya dapat membaca (SELECT) pesanannya sendiri
drop policy if exists "customer_order_select_own" on public.customer_order;
create policy "customer_order_select_own" on public.customer_order
  for select to authenticated
  using (owner_uid = auth.uid());

-- Staf Aktif: Membaca (SELECT) seluruh pesanan untuk antrean barista, kasir, dan admin
drop policy if exists "staff_order_select_all" on public.customer_order;
create policy "staff_order_select_all" on public.customer_order
  for select to authenticated
  using (public.is_active_staff());

-- Staf Aktif: Memperbarui (UPDATE) status pesanan
drop policy if exists "staff_order_update_status" on public.customer_order;
create policy "staff_order_update_status" on public.customer_order
  for update to authenticated
  using (public.is_active_staff())
  with check (public.is_active_staff());

-- Catatan: INSERT dan DELETE pada customer_order TIDAK dibuka ke client secara bebas.
-- Pembuatan pesanan baru dilakukan via Edge Function 'create-payment' / 'create_order' (service role).

-- ------------------------------------------------------------------------------
-- 5. TABEL: order_item & order_item_option
-- ------------------------------------------------------------------------------
-- Pelanggan: Membaca item miliknya sendiri
drop policy if exists "order_item_select_own" on public.order_item;
create policy "order_item_select_own" on public.order_item
  for select to authenticated
  using (
    exists (
      select 1 from public.customer_order o
      where o.id = order_item.order_id and o.owner_uid = auth.uid()
    )
  );

-- Staf Aktif: Membaca seluruh item pesanan
drop policy if exists "staff_order_item_select_all" on public.order_item;
create policy "staff_order_item_select_all" on public.order_item
  for select to authenticated
  using (public.is_active_staff());

-- Staf Admin / Owner: Mengelola item jika diperlukan
drop policy if exists "staff_order_item_manage" on public.order_item;
create policy "staff_order_item_manage" on public.order_item
  for all to authenticated
  using (public.is_admin_or_owner())
  with check (public.is_admin_or_owner());

-- Pelanggan: Membaca opsi kustomisasi item miliknya
drop policy if exists "order_item_option_select_own" on public.order_item_option;
create policy "order_item_option_select_own" on public.order_item_option
  for select to authenticated
  using (
    exists (
      select 1 from public.order_item i
      join public.customer_order o on o.id = i.order_id
      where i.id = order_item_option.order_item_id and o.owner_uid = auth.uid()
    )
  );

-- Staf Aktif: Membaca seluruh opsi kustomisasi item
drop policy if exists "staff_order_item_option_select_all" on public.order_item_option;
create policy "staff_order_item_option_select_all" on public.order_item_option
  for select to authenticated
  using (public.is_active_staff());

-- Staf Admin / Owner: Mengelola opsi item
drop policy if exists "staff_order_item_option_manage" on public.order_item_option;
create policy "staff_order_item_option_manage" on public.order_item_option
  for all to authenticated
  using (public.is_admin_or_owner())
  with check (public.is_admin_or_owner());

-- ------------------------------------------------------------------------------
-- 6. TABEL: order_status_history
-- ------------------------------------------------------------------------------
-- Pelanggan: Membaca riwayat status pesanannya sendiri
drop policy if exists "order_status_history_select_own" on public.order_status_history;
create policy "order_status_history_select_own" on public.order_status_history
  for select to authenticated
  using (
    exists (
      select 1 from public.customer_order o
      where o.id = order_status_history.order_id and o.owner_uid = auth.uid()
    )
  );

-- Staf Aktif: Membaca seluruh riwayat status pesanan
drop policy if exists "staff_order_status_history_select" on public.order_status_history;
create policy "staff_order_status_history_select" on public.order_status_history
  for select to authenticated
  using (public.is_active_staff());

-- Staf Aktif: Menulis catatan perubahan status produksi
drop policy if exists "staff_order_status_history_insert" on public.order_status_history;
create policy "staff_order_status_history_insert" on public.order_status_history
  for insert to authenticated
  with check (public.is_active_staff());

-- ------------------------------------------------------------------------------
-- 7. TABEL MASTER: menu_item, menu_category, cafe_table, app_settings
-- ------------------------------------------------------------------------------
-- Publik/Pelanggan: Membaca menu, kategori, meja, dan pengaturan umum
drop policy if exists "public_menu_item_select" on public.menu_item;
create policy "public_menu_item_select" on public.menu_item
  for select to public
  using (true);

drop policy if exists "public_menu_category_select" on public.menu_category;
create policy "public_menu_category_select" on public.menu_category
  for select to public
  using (true);

drop policy if exists "public_cafe_table_select" on public.cafe_table;
create policy "public_cafe_table_select" on public.cafe_table
  for select to public
  using (true);

drop policy if exists "public_app_settings_select" on public.app_settings;
create policy "public_app_settings_select" on public.app_settings
  for select to public
  using (true);

-- Staf Admin / Owner: Mengelola (INSERT, UPDATE, DELETE) data master
drop policy if exists "admin_menu_item_manage" on public.menu_item;
create policy "admin_menu_item_manage" on public.menu_item
  for all to authenticated
  using (public.is_admin_or_owner())
  with check (public.is_admin_or_owner());

drop policy if exists "admin_menu_category_manage" on public.menu_category;
create policy "admin_menu_category_manage" on public.menu_category
  for all to authenticated
  using (public.is_admin_or_owner())
  with check (public.is_admin_or_owner());

drop policy if exists "admin_cafe_table_manage" on public.cafe_table;
create policy "admin_cafe_table_manage" on public.cafe_table
  for all to authenticated
  using (public.is_admin_or_owner())
  with check (public.is_admin_or_owner());

drop policy if exists "admin_app_settings_manage" on public.app_settings;
create policy "admin_app_settings_manage" on public.app_settings
  for all to authenticated
  using (public.is_admin_or_owner())
  with check (public.is_admin_or_owner());
