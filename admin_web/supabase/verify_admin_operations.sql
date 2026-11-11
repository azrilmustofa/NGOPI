reset role;
begin;
select set_config('ngopi.test.expected_revenue', (select coalesce(sum(total_rp),0)::text from public.customer_order
  where created_at >= (((now() at time zone 'Asia/Jakarta')::date - 30)::timestamp at time zone 'Asia/Jakarta')
    and created_at < (((now() at time zone 'Asia/Jakarta')::date + 1)::timestamp at time zone 'Asia/Jakarta')
    and status::text in ('PAID','ACCEPTED','PREPARING','READY','COMPLETED')), true);
select set_config('ngopi.test.cashier_uid', coalesce((select uid::text from public.staff_user where role::text = 'CASHIER' and is_active limit 1), ''), true);
select set_config('request.jwt.claim.sub', coalesce((select uid::text from public.staff_user where role::text in ('ADMIN','OWNER') and is_active order by uid limit 1), ''), true);
set local role authenticated;
do $$
declare report jsonb; expected bigint; before_footer text; before_count bigint;
  date_from date := (now() at time zone 'Asia/Jakarta')::date - 30;
  date_until date := (now() at time zone 'Asia/Jakarta')::date;
begin
  assert public.is_active_admin(), 'Akun admin aktif diperlukan untuk verifikasi';
  report := public.admin_sales_report(date_from, date_until);
  expected := current_setting('ngopi.test.expected_revenue')::bigint;
  assert (report->'summary'->>'revenue_rp')::bigint = expected, 'Omzet tidak cocok';
  assert (report->'summary'->>'transactions')::bigint =
    (select coalesce(sum((entry->>'transactions')::bigint),0) from jsonb_array_elements(report->'methods') entry), 'Transaksi metode bayar ganda';
  select footer into before_footer from public.receipt_template where id = 'PAID';
  assert before_footer is not null, 'Template lunas belum tersedia';
  select count(*) into before_count from public.admin_audit_log;
  update public.receipt_template set footer = 'Verifikasi sementara, dibatalkan dengan rollback' where id = 'PAID';
  assert (select count(*) from public.admin_audit_log) = before_count + 1, 'Audit tidak tercatat';
  assert exists (select 1 from public.admin_audit_log where actor_uid = auth.uid()
    and table_name = 'receipt_template' and record_id = 'PAID'
    and old_data->>'footer' = before_footer
    and new_data->>'footer' = 'Verifikasi sementara, dibatalkan dengan rollback'), 'Pelaku atau perubahan audit tidak cocok';
end;
$$;
reset role;
select set_config('request.jwt.claim.sub', current_setting('ngopi.test.cashier_uid'), true);
set local role authenticated;
do $$
declare affected integer;
begin
  assert auth.uid() is not null, 'Akun kasir aktif diperlukan untuk verifikasi';
  assert not public.is_active_admin(), 'Kasir mendapat akses admin';
  begin
    perform public.admin_sales_report(current_date,current_date);
    raise exception 'Kasir dapat membaca laporan';
  exception when insufficient_privilege then null;
  end;
  update public.app_settings set tax_percentage = tax_percentage where id = 'default';
  get diagnostics affected = row_count;
  assert affected = 0, 'Kasir dapat mengubah biaya';
  update public.receipt_template set footer = 'Tidak boleh tersimpan' where id = 'PAID';
  get diagnostics affected = row_count;
  assert affected = 0, 'Kasir dapat mengubah template';
  assert (select count(*) from public.admin_audit_log) = 0, 'Kasir dapat membaca audit';
end;
$$;
rollback;
select 'Laporan, RLS, template, dan audit lolos; perubahan uji sudah di-rollback' as verification;
