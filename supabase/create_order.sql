-- create_order: menyimpan pesanan dari app ke Supabase (pembayaran disimulasikan: langsung PAID).
-- Jalankan sekali di Supabase Dashboard -> SQL Editor. Aman dijalankan ulang (create or replace).
--
-- Kenapa function: RLS memblokir tulis langsung dari app ke tabel order. Function ini berjalan dengan
-- hak pemilik (security definer) tapi tetap memakai auth.uid() sebagai pemilik pesanan, dan semua harga
-- dihitung ulang dari menu_item / menu_option, jadi harga dari app tidak dipercaya.
-- Semuanya satu transaksi: kalau ada yang gagal, tidak ada data setengah jadi.
--
-- Parameter p_items berbentuk:
--   [{"menu_item_id": "...", "quantity": 2, "note": "less ice", "option_ids": ["opt1","opt2"]}, ...]

create or replace function public.create_order(
  p_table_id text,
  p_checkout_key text,
  p_payment_method text,
  p_items jsonb
) returns jsonb
language plpgsql
security definer
set search_path = public
as $$
declare
  v_uid uuid := auth.uid();
  v_order customer_order%rowtype;
  v_order_id uuid;
  v_public_id text;
  v_tax_pct numeric;
  v_service_pct numeric;
  v_item jsonb;
  v_menu menu_item%rowtype;
  v_opts jsonb;
  v_grp record;
  v_item_id uuid;
  v_qty int;
  v_unit int;
  v_delta int;
  v_found int;
  v_wanted int;
  v_subtotal int := 0;
  v_tax int;
  v_service int;
  v_total int;
begin
  if v_uid is null then
    raise exception 'Belum login';
  end if;
  if p_checkout_key is null or length(p_checkout_key) < 8 or length(p_checkout_key) > 64 then
    raise exception 'Kunci checkout tidak valid';
  end if;
  if p_payment_method not in ('QRIS', 'VIRTUAL_ACCOUNT', 'EWALLET') then
    raise exception 'Metode pembayaran tidak valid';
  end if;
  if p_items is null or jsonb_typeof(p_items) <> 'array' or jsonb_array_length(p_items) = 0
     or jsonb_array_length(p_items) > 50 then
    raise exception 'Keranjang kosong atau tidak valid';
  end if;

  -- Retry / tap ganda dengan checkout_key yang sama mengembalikan pesanan yang sama.
  select * into v_order from customer_order
   where checkout_key = p_checkout_key and owner_uid = v_uid;
  if found then
    return jsonb_build_object(
      'order_id', v_order.id, 'public_id', v_order.public_id, 'status', v_order.status,
      'subtotal_rp', v_order.subtotal_rp, 'tax_rp', v_order.tax_rp,
      'service_charge_rp', v_order.service_charge_rp, 'total_rp', v_order.total_rp);
  end if;

  if not exists (select 1 from cafe_table where id::text = p_table_id and is_active) then
    raise exception 'Meja tidak valid atau tidak aktif';
  end if;

  select tax_percentage, service_charge_percentage into v_tax_pct, v_service_pct
    from app_settings where id = 'default';
  if not found then
    raise exception 'Pengaturan pajak belum ada';
  end if;

  -- Nomor pesanan pendek yang mudah dibaca; ulangi kalau kebetulan sudah dipakai.
  loop
    v_public_id := 'NG-' || (
      select string_agg(substr('ABCDEFGHJKLMNPQRSTUVWXYZ23456789', 1 + floor(random() * 32)::int, 1), '')
      from generate_series(1, 6));
    exit when not exists (select 1 from customer_order where public_id = v_public_id);
  end loop;

  -- Total diisi setelah semua baris dihitung (masih satu transaksi).
  insert into customer_order (public_id, table_id, owner_uid, checkout_key, subtotal_rp, total_rp)
  values (v_public_id, p_table_id, v_uid, p_checkout_key, 0, 0)
  returning id into v_order_id;

  for v_item in select * from jsonb_array_elements(p_items) loop
    v_qty := (v_item ->> 'quantity')::int;
    if v_qty is null or v_qty < 1 or v_qty > 99 then
      raise exception 'Jumlah menu tidak valid';
    end if;

    select * into v_menu from menu_item
     where id = v_item ->> 'menu_item_id' and is_active and is_available;
    if not found then
      raise exception 'Menu tidak tersedia';
    end if;

    v_opts := coalesce(v_item -> 'option_ids', '[]'::jsonb);

    -- Semua opsi harus ada, tersedia, dan memang milik grup yang menempel ke menu ini.
    select count(distinct x) into v_wanted from jsonb_array_elements_text(v_opts) as x;
    select count(*), coalesce(sum(o.price_delta_rp), 0)::int into v_found, v_delta
      from menu_option o
      join menu_item_option_group l on l.option_group_id = o.option_group_id and l.menu_item_id = v_menu.id
     where o.is_available and o.id in (select jsonb_array_elements_text(v_opts));
    if v_found <> v_wanted then
      raise exception 'Opsi tidak valid untuk %', v_menu.name;
    end if;

    -- Aturan jumlah pilihan per grup (max_selection < 1 dianggap tanpa batas atas).
    for v_grp in
      select g.name, g.min_selection, g.max_selection,
             (select count(*) from menu_option o2
               where o2.option_group_id = g.id
                 and o2.id in (select jsonb_array_elements_text(v_opts))) as n
        from menu_item_option_group l
        join option_group g on g.id = l.option_group_id
       where l.menu_item_id = v_menu.id
    loop
      if v_grp.n < v_grp.min_selection or (v_grp.max_selection >= 1 and v_grp.n > v_grp.max_selection) then
        raise exception 'Pilihan "%" untuk % belum sesuai aturan', v_grp.name, v_menu.name;
      end if;
    end loop;

    v_unit := v_menu.base_price_rp + v_delta;
    v_item_id := gen_random_uuid();

    insert into order_item (id, order_id, menu_item_id, name_snapshot, unit_price_rp, quantity, note, line_total_rp)
    values (v_item_id, v_order_id, v_menu.id, v_menu.name, v_unit, v_qty,
            nullif(left(trim(coalesce(v_item ->> 'note', '')), 300), ''), v_unit * v_qty);

    insert into order_item_option (order_item_id, menu_option_id, option_group_name_snapshot,
                                   option_name_snapshot, price_delta_rp)
    select v_item_id, o.id, g.name, o.name, o.price_delta_rp
      from menu_option o
      join option_group g on g.id = o.option_group_id
     where o.id in (select jsonb_array_elements_text(v_opts));

    v_subtotal := v_subtotal + v_unit * v_qty;
  end loop;

  v_tax := round(v_subtotal * v_tax_pct / 100)::int;
  v_service := round(v_subtotal * v_service_pct / 100)::int;
  v_total := v_subtotal + v_tax + v_service;

  -- Pembayaran disimulasikan: langsung lunas. Nanti diganti alur Midtrans (create-payment + webhook).
  update customer_order
     set subtotal_rp = v_subtotal, tax_rp = v_tax, service_charge_rp = v_service, total_rp = v_total,
         status = 'PAID', paid_at = now(), updated_at = now()
   where id = v_order_id;

  insert into payment_attempt (order_id, provider_order_id, amount_rp, payment_method, status, paid_at, paid_source)
  values (v_order_id, v_public_id || '-SIM', v_total, p_payment_method, 'PAID', now(), 'SIMULATED');

  -- Riwayat status pelengkap: kegagalannya (mis. aturan actor_type) tidak boleh membatalkan pesanan.
  begin
    insert into order_status_history (order_id, actor_uid, actor_type, to_status, note)
    values (v_order_id, v_uid, 'CUSTOMER', 'AWAITING_PAYMENT', 'Pesanan dibuat');
    insert into order_status_history (order_id, actor_uid, actor_type, from_status, to_status, note)
    values (v_order_id, v_uid, 'CUSTOMER', 'AWAITING_PAYMENT', 'PAID', 'Pembayaran disimulasikan');
  exception when others then
    raise warning 'order_status_history dilewati: %', sqlerrm;
  end;

  return jsonb_build_object(
    'order_id', v_order_id, 'public_id', v_public_id, 'status', 'PAID',
    'subtotal_rp', v_subtotal, 'tax_rp', v_tax, 'service_charge_rp', v_service, 'total_rp', v_total);
end;
$$;

revoke all on function public.create_order(text, text, text, jsonb) from public, anon;
grant execute on function public.create_order(text, text, text, jsonb) to authenticated;
