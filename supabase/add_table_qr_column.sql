-- Tambahkan kolom qr_image_url ke tabel cafe_table jika belum ada
do $$
begin
  if not exists (
    select 1 from information_schema.columns 
    where table_schema = 'public' and table_name = 'cafe_table' and column_name = 'qr_image_url'
  ) then
    alter table public.cafe_table add column qr_image_url text;
  end if;
end $$;

-- Pastikan policy allow all update & insert
alter table public.cafe_table enable row level security;
drop policy if exists "admin_cafe_table_all" on public.cafe_table;
create policy "admin_cafe_table_all" on public.cafe_table
  for all to public using (true) with check (true);
