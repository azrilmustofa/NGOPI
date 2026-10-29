-- ==============================================================================
-- 1. Pastikan Policy Publik Terbuka Dicabut
-- ==============================================================================
alter table public.order_item enable row level security;
drop policy if exists "admin_order_item_all" on public.order_item;

alter table public.order_item_option enable row level security;
drop policy if exists "admin_order_item_option_all" on public.order_item_option;

-- ==============================================================================
-- 2. Isi Data Rincian Item Dummy untuk Order yang Sudah Ada
--    (Mengisi order yang tabel order_item-nya masih kosong)
-- ==============================================================================
do $$
declare
  r record;
  v_menu1 record;
  v_menu2 record;
  v_item_id uuid;
begin
  select * into v_menu1 from public.menu_item where id = 'MENU-01' limit 1;
  select * into v_menu2 from public.menu_item where id = 'MENU-02' limit 1;

  for r in select id from public.customer_order loop
    -- Jika order belum memiliki item
    if not exists (select 1 from public.order_item where order_id = r.id) then
      -- Item 1
      if v_menu1.id is not null then
        insert into public.order_item (id, order_id, menu_item_id, name_snapshot, unit_price_rp, quantity, note, line_total_rp)
        values (
          gen_random_uuid(),
          r.id,
          v_menu1.id,
          v_menu1.name,
          coalesce(v_menu1.base_price_rp, 24000),
          1,
          'Less ice, normal sugar',
          coalesce(v_menu1.base_price_rp, 24000)
        );
      end if;

      -- Item 2
      if v_menu2.id is not null then
        insert into public.order_item (id, order_id, menu_item_id, name_snapshot, unit_price_rp, quantity, note, line_total_rp)
        values (
          gen_random_uuid(),
          r.id,
          v_menu2.id,
          v_menu2.name,
          coalesce(v_menu2.base_price_rp, 28000),
          1,
          null,
          coalesce(v_menu2.base_price_rp, 28000)
        );
      end if;
    end if;
  end loop;
end $$;
