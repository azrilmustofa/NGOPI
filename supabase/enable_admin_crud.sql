-- ==============================================================================
-- Script SQL: Berikan Izin CRUD untuk Panel Admin Web Kopi Inggil
-- Jalankan di: Supabase Dashboard -> SQL Editor -> Run
-- ==============================================================================

-- 1. Izin CRUD untuk tabel menu_item (Tambah, Edit, Hapus, Ubah Stok Menu)
alter table public.menu_item enable row level security;

create policy "admin_menu_item_all" on public.menu_item
  for all to public
  using (true)
  with check (true);

-- 2. Izin CRUD untuk tabel menu_category (Tambah, Edit, Hapus Kategori)
alter table public.menu_category enable row level security;

create policy "admin_menu_category_all" on public.menu_category
  for all to public
  using (true)
  with check (true);

-- 3. Izin CRUD untuk tabel cafe_table (Tambah, Edit, Hapus Meja)
alter table public.cafe_table enable row level security;

create policy "admin_cafe_table_all" on public.cafe_table
  for all to public
  using (true)
  with check (true);

-- 4. Izin UPDATE untuk status pesanan customer_order (Antrean Masak, Selesai, Batal)
alter table public.customer_order enable row level security;

create policy "admin_customer_order_all" on public.customer_order
  for all to public
  using (true)
  with check (true);

-- 4b. Izin Baca/Tulis untuk order_item dan order_item_option
alter table public.order_item enable row level security;

create policy "admin_order_item_all" on public.order_item
  for all to public
  using (true)
  with check (true);

alter table public.order_item_option enable row level security;

create policy "admin_order_item_option_all" on public.order_item_option
  for all to public
  using (true)
  with check (true);

-- 5. Izin CRUD untuk pengaturan app_settings (Pajak & Service Charge)
alter table public.app_settings enable row level security;

create policy "admin_app_settings_all" on public.app_settings
  for all to public
  using (true)
  with check (true);

-- 6. Izin CRUD untuk akun staf staff_user
alter table public.staff_user enable row level security;

create policy "admin_staff_user_all" on public.staff_user
  for all to public
  using (true)
  with check (true);
