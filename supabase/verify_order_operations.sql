begin;
do $$
declare barista uuid; cashier uuid; paid uuid; unpaid uuid; receipt jsonb; before_count bigint; blocked boolean;
begin
  select uid into barista from public.staff_user where role::text = 'BARISTA' and is_active limit 1;
  select uid into cashier from public.staff_user where role::text = 'CASHIER' and is_active limit 1;
  select id into paid from public.customer_order o where status::text = 'PAID'
    and exists (select 1 from public.private_customer c where c.order_id = o.id) limit 1;
  select id into unpaid from public.customer_order where status::text = 'AWAITING_PAYMENT' limit 1;
  assert barista is not null and cashier is not null, 'Barista dan kasir aktif diperlukan';
  assert paid is not null, 'Pesanan PAID dengan data pelanggan diperlukan untuk pemeriksaan';
  assert not has_table_privilege('authenticated', 'public.customer_order', 'UPDATE'), 'Client masih dapat menulis status langsung';
  assert not has_any_column_privilege('authenticated', 'public.customer_order', 'UPDATE'), 'Client masih dapat mengubah kolom pesanan';
  assert not has_table_privilege('authenticated', 'public.order_status_history', 'INSERT'), 'Client masih dapat memalsukan riwayat';

  perform set_config('request.jwt.claim.sub', barista::text, true);
  select count(*) into before_count from public.order_status_history where order_id = paid;
  blocked := false;
  begin perform public.transition_order(paid, 'PAID', 'PREPARING', 'Pemeriksaan sementara');
  exception when others then blocked := true; end;
  assert blocked, 'Lompatan tahap produksi diizinkan';
  if unpaid is not null then
    blocked := false;
    begin perform public.transition_order(unpaid, 'AWAITING_PAYMENT', 'ACCEPTED', 'Pemeriksaan sementara');
    exception when others then blocked := true; end;
    assert blocked, 'Pesanan belum lunas dapat diproduksi';
  end if;
  perform public.transition_order(paid, 'PAID', 'ACCEPTED', 'Pemeriksaan sementara');
  blocked := false;
  begin perform public.transition_order(paid, 'PAID', 'ACCEPTED', 'Ketukan berulang');
  exception when serialization_failure then blocked := true; end;
  assert blocked, 'Permintaan status lama tidak ditolak';
  perform public.transition_order(paid, 'ACCEPTED', 'PREPARING', 'Pemeriksaan sementara');
  perform public.transition_order(paid, 'PREPARING', 'READY', 'Pemeriksaan sementara');
  perform public.transition_order(paid, 'READY', 'COMPLETED', 'Pemeriksaan sementara');
  assert (select count(*) from public.order_status_history where order_id = paid) = before_count + 4, 'Riwayat tidak sesuai';
  assert (select completed_at is not null from public.customer_order where id = paid), 'Waktu selesai belum tercatat';

  perform set_config('request.jwt.claim.sub', cashier::text, true);
  receipt := public.get_order_receipt(paid);
  assert receipt->>'order_id' = paid::text and receipt->>'status' = 'COMPLETED', 'Struk bukan pesanan yang dipilih';
  assert receipt->>'phone_e164' is not null, 'Nomor pelanggan tidak tersedia';
  select count(*) into before_count from public.audit_log where target_type = 'customer_order' and target_id = paid::text;
  perform public.record_receipt_resend(paid, 'FAILED', '6281234567890', 'Pemeriksaan sementara tanpa pengiriman WA');
  assert (select count(*) from public.audit_log where target_type = 'customer_order' and target_id = paid::text) = before_count + 1, 'Hasil kirim ulang tidak dicatat';
  perform set_config('request.jwt.claim.sub', barista::text, true);
  blocked := false;
  begin perform public.get_order_receipt(paid);
  exception when insufficient_privilege then blocked := true; end;
  assert blocked, 'Data pelanggan terbuka kepada barista';
end;
$$;
rollback;
select 'Pemeriksaan lolos; seluruh perubahan dibatalkan, tidak ada WhatsApp dikirim' as verification;
