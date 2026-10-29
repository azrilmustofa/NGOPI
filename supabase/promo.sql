-- promo.sql
-- Promo/diskon per menu yang tampil di banner "Promo hari ini" (Menu) dan benar-benar memotong harga saat bayar
-- (dihitung di quote-cart dan create-payment). Sampai layar admin dibuat, promo diatur lewat Table Editor.
-- Jalankan sekali di Supabase Dashboard -> SQL Editor.

create table if not exists public.promo (
  id uuid primary key default gen_random_uuid(),
  menu_item_id text not null references public.menu_item(id) on delete cascade,
  title text not null,                       -- judul di banner, mis. "Promo pagi"
  description text,                          -- teks kecil opsional, mis. "Khusus jam 07.00-10.00"
  discount_type text not null check (discount_type in ('PERCENT', 'AMOUNT')),
  discount_value integer not null check (discount_value > 0),  -- PERCENT: 1-100, AMOUNT: rupiah
  starts_at timestamptz not null default now(),
  ends_at timestamptz not null,
  priority integer not null default 0,      -- urutan di carousel, besar = lebih dulu
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  constraint promo_percent_max check (discount_type <> 'PERCENT' or discount_value <= 100),
  constraint promo_period check (ends_at > starts_at)
);

create index if not exists promo_active_idx on public.promo (is_active, starts_at, ends_at);

-- Pelanggan (dan yang belum login) hanya bisa membaca promo yang sedang berlaku.
alter table public.promo enable row level security;
drop policy if exists "promo_select_running" on public.promo;
create policy "promo_select_running" on public.promo
  for select to anon, authenticated
  using (is_active and now() >= starts_at and now() < ends_at);

-- Staf dan Admin dapat membaca seluruh data promo (termasuk yang non-aktif atau kedaluwarsa)
drop policy if exists "staff_promo_select" on public.promo;
create policy "staff_promo_select" on public.promo
  for select to authenticated
  using (public.is_active_staff());

-- Admin / Owner dapat mengelola (INSERT, UPDATE, DELETE) seluruh promo
drop policy if exists "admin_promo_manage" on public.promo;
create policy "admin_promo_manage" on public.promo
  for all to authenticated
  using (public.is_admin_or_owner())
  with check (public.is_admin_or_owner());

-- Catatan diskon di pesanan (customer_order.discount_rp sudah ada).
alter table public.order_item add column if not exists discount_rp integer not null default 0;  -- potongan per porsi
alter table public.order_item add column if not exists promo_id uuid references public.promo(id) on delete set null;

-- Contoh promo (hapus tanda -- untuk mencoba): diskon 20% Caramel Macchiato sampai akhir hari ini (WIB).
-- insert into public.promo (menu_item_id, title, description, discount_type, discount_value, ends_at)
-- values ('MENU-04', 'Promo hari ini', 'Hemat 20% khusus hari ini', 'PERCENT', 20,
--         date_trunc('day', now() at time zone 'Asia/Jakarta') at time zone 'Asia/Jakarta' + interval '1 day');
