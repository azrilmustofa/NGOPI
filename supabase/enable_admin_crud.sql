-- ==============================================================================
-- Script SQL: Izin Akses Aman untuk Panel Admin Web & Staf Kopi Inggil
-- Jalankan di: Supabase Dashboard -> SQL Editor -> Run
-- ==============================================================================
-- Catatan Keamanan:
-- Akses dibatasi untuk pengguna yang telah login (authenticated) dan tercatat
-- sebagai staf aktif di tabel staff_user. Kebijakan publik (to public using true)
-- dicabut agar tidak ada celah manipulasi pembayaran dari pihak luar.

-- 1. Cabut policy publik terbuka lama jika pernah dibuat
drop policy if exists "admin_customer_order_all" on public.customer_order;
drop policy if exists "admin_order_item_all" on public.order_item;
drop policy if exists "admin_order_item_option_all" on public.order_item_option;
drop policy if exists "admin_staff_user_all" on public.staff_user;
drop policy if exists "admin_menu_item_all" on public.menu_item;
drop policy if exists "admin_menu_category_all" on public.menu_category;
drop policy if exists "admin_cafe_table_all" on public.cafe_table;
drop policy if exists "admin_app_settings_all" on public.app_settings;

-- 2. Pastikan RLS Aktif
alter table public.menu_item enable row level security;
alter table public.menu_category enable row level security;
alter table public.cafe_table enable row level security;
alter table public.customer_order enable row level security;
alter table public.order_item enable row level security;
alter table public.order_item_option enable row level security;
alter table public.app_settings enable row level security;
alter table public.staff_user enable row level security;

-- 3. Izin Baca Publik untuk Katalog & Meja
drop policy if exists "public_menu_item_select" on public.menu_item;
create policy "public_menu_item_select" on public.menu_item
  for select to public using (true);

drop policy if exists "public_menu_category_select" on public.menu_category;
create policy "public_menu_category_select" on public.menu_category
  for select to public using (true);

drop policy if exists "public_cafe_table_select" on public.cafe_table;
create policy "public_cafe_table_select" on public.cafe_table
  for select to public using (true);

drop policy if exists "public_app_settings_select" on public.app_settings;
create policy "public_app_settings_select" on public.app_settings
  for select to public using (true);

-- 4. Izin CRUD Master Data untuk Admin / Owner
create policy "admin_menu_item_all" on public.menu_item
  for all to authenticated
  using (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true and lower(s.role::text) in ('admin', 'owner')))
  with check (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true and lower(s.role::text) in ('admin', 'owner')));

create policy "admin_menu_category_all" on public.menu_category
  for all to authenticated
  using (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true and lower(s.role::text) in ('admin', 'owner')))
  with check (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true and lower(s.role::text) in ('admin', 'owner')));

create policy "admin_cafe_table_all" on public.cafe_table
  for all to authenticated
  using (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true and lower(s.role::text) in ('admin', 'owner')))
  with check (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true and lower(s.role::text) in ('admin', 'owner')));

create policy "admin_app_settings_all" on public.app_settings
  for all to authenticated
  using (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true and lower(s.role::text) in ('admin', 'owner')))
  with check (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true and lower(s.role::text) in ('admin', 'owner')));

-- 5. Izin Operasional Order untuk Seluruh Staf Aktif
create policy "staff_order_select_all" on public.customer_order
  for select to authenticated
  using (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true));

create policy "staff_order_update_status" on public.customer_order
  for update to authenticated
  using (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true))
  with check (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true));

create policy "staff_order_item_select_all" on public.order_item
  for select to authenticated
  using (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true));

create policy "staff_order_item_option_select_all" on public.order_item_option
  for select to authenticated
  using (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true));

-- 6. Izin Kelola Staf untuk Admin / Owner
create policy "staff_user_select_self" on public.staff_user
  for select to authenticated using (uid = auth.uid());

create policy "admin_staff_user_all" on public.staff_user
  for all to authenticated
  using (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true and lower(s.role::text) in ('admin', 'owner')))
  with check (exists (select 1 from public.staff_user s where s.uid = auth.uid() and s.is_active = true and lower(s.role::text) in ('admin', 'owner')));
