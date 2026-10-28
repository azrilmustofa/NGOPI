-- private_customer.sql
-- Data pribadi pemesan (nama, WA E.164, persetujuan) disimpan create-payment di tabel private_customer,
-- terpisah dari customer_order supaya tidak ikut terbaca di antrean staf.
-- Jalankan sekali di Supabase Dashboard -> SQL Editor.

-- Persetujuan menerima promo (opsional di Checkout). Persetujuan WA wajib, dicatat lewat consented_at + consent_version.
alter table public.private_customer add column if not exists promo_consent boolean not null default false;

-- Hanya Edge Function (service role) yang menulis/membaca tabel ini. Tanpa policy = tidak bisa diakses dari app.
alter table public.private_customer enable row level security;

-- Cek: hasilnya harus kosong. Kalau ada policy untuk public/anon/authenticated, tabel ini bisa dibaca dari app.
select policyname, roles, cmd, qual from pg_policies where tablename = 'private_customer';
