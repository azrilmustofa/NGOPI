begin;

-- Riwayat dan status harus berhasil dalam transaksi yang sama, termasuk pembayaran dari webhook.
create or replace function public.guard_order_transition()
returns trigger language plpgsql security definer set search_path = public
as $$
declare staff_role text;
begin
  if new.status is not distinct from old.status then return new; end if;
  if auth.uid() is not null then
    select role::text into staff_role from public.staff_user where uid = auth.uid() and is_active;
    if staff_role is null then
      raise exception 'Hanya staf aktif yang dapat mengubah status pesanan' using errcode = '42501';
    end if;
  elsif current_setting('role', true) not in ('service_role', 'postgres', 'none') then
    raise exception 'Akses perubahan status ditolak' using errcode = '42501';
  end if;

  if not (
    (old.status::text = 'AWAITING_PAYMENT' and new.status::text = 'PAID'
      and (staff_role is null or staff_role in ('CASHIER','ADMIN','OWNER')))
    or (old.status::text = 'PAID' and new.status::text = 'ACCEPTED'
      and (staff_role is null or staff_role in ('BARISTA','ADMIN','OWNER')))
    or (old.status::text = 'ACCEPTED' and new.status::text = 'PREPARING'
      and (staff_role is null or staff_role in ('BARISTA','ADMIN','OWNER')))
    or (old.status::text = 'PREPARING' and new.status::text = 'READY'
      and (staff_role is null or staff_role in ('BARISTA','ADMIN','OWNER')))
    or (old.status::text = 'READY' and new.status::text = 'COMPLETED'
      and (staff_role is null or staff_role in ('BARISTA','ADMIN','OWNER')))
    or (old.status::text in ('AWAITING_PAYMENT','PAID','ACCEPTED','PREPARING','READY')
      and new.status::text = 'CANCELLED'
      and (staff_role is null or staff_role in ('CASHIER','ADMIN','OWNER')))
  ) then
    raise exception 'Perubahan status % ke % tidak diizinkan', old.status, new.status;
  end if;
  if new.status::text = 'CANCELLED' and nullif(trim(new.cancellation_reason), '') is null then
    raise exception 'Alasan pembatalan wajib diisi';
  end if;
  new.updated_at := now();
  if new.status::text = 'PAID' then new.paid_at := now(); end if;
  if new.status::text = 'COMPLETED' then new.completed_at := now(); end if;
  return new;
end;
$$;

create or replace function public.capture_order_transition()
returns trigger language plpgsql security definer set search_path = public
as $$
declare actor public.order_status_history.actor_type%type;
begin
  if new.status is distinct from old.status then
    actor := case when auth.uid() is null then 'SYSTEM' else 'STAFF' end;
    insert into public.order_status_history (order_id, actor_uid, actor_type, from_status, to_status, note)
    values (new.id, auth.uid(), actor,
      old.status, new.status,
      coalesce(nullif(current_setting('ngopi.order_note', true), ''),
        case when new.status::text = 'CANCELLED' then new.cancellation_reason
          when new.status::text = 'PAID' then 'Pembayaran terverifikasi'
          else 'Status diperbarui oleh staf' end));
  end if;
  return new;
end;
$$;
revoke all on function public.guard_order_transition(), public.capture_order_transition() from public;
drop trigger if exists guard_order_transition on public.customer_order;
create trigger guard_order_transition before update of status on public.customer_order
  for each row execute function public.guard_order_transition();
drop trigger if exists capture_order_transition on public.customer_order;
create trigger capture_order_transition after update of status on public.customer_order
  for each row execute function public.capture_order_transition();

create or replace function public.transition_order(
  p_order_id uuid, p_expected_status text, p_status text, p_note text default null
) returns jsonb language plpgsql security definer set search_path = public
as $$
declare current_order public.customer_order%rowtype; next_status public.customer_order.status%type;
begin
  if not exists (select 1 from public.staff_user where uid = auth.uid() and is_active
    and role::text in ('BARISTA','CASHIER','ADMIN','OWNER')) then
    raise exception 'Hanya staf aktif yang diizinkan' using errcode = '42501';
  end if;
  if p_status is null or p_expected_status is null or length(coalesce(p_note, '')) > 500 then
    raise exception 'Status atau catatan tidak valid';
  end if;
  select * into current_order from public.customer_order where id = p_order_id for update;
  if not found then raise exception 'Pesanan tidak ditemukan'; end if;
  if current_order.status::text <> p_expected_status or p_status = p_expected_status then
    raise exception 'Status pesanan sudah berubah. Muat ulang sebelum melanjutkan' using errcode = '40001';
  end if;
  select status into next_status from jsonb_populate_record(null::public.customer_order, jsonb_build_object('status', p_status));
  perform set_config('ngopi.order_note', coalesce(nullif(trim(p_note), ''), ''), true);
  update public.customer_order set status = next_status,
    cancellation_reason = case when p_status = 'CANCELLED' then nullif(trim(p_note), '') else cancellation_reason end
    where id = p_order_id returning * into current_order;
  return jsonb_build_object('status', current_order.status, 'updated_at', current_order.updated_at);
end;
$$;
revoke all on function public.transition_order(uuid,text,text,text) from public, anon;
grant execute on function public.transition_order(uuid,text,text,text) to authenticated;

-- RPC hanya mengubah kolom operasional; client tidak dapat memalsukan harga, pemilik, atau riwayat.
revoke insert, update, delete on public.customer_order, public.order_status_history from public, anon, authenticated;
do $$
declare target text; col record;
begin
  if to_regprocedure('public.create_order(text,text,text,jsonb)') is not null then
    execute 'revoke all on function public.create_order(text,text,text,jsonb) from public, anon, authenticated';
  end if;
  foreach target in array array['customer_order','order_status_history'] loop
    for col in select column_name from information_schema.columns where table_schema = 'public' and table_name = target loop
      execute format('revoke insert (%I), update (%I) on public.%I from public, anon, authenticated', col.column_name, col.column_name, target);
    end loop;
  end loop;
end;
$$;

create or replace function public.get_order_receipt(p_order_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public
as $$
declare result jsonb;
begin
  if not exists (select 1 from public.staff_user where uid = auth.uid() and is_active
    and role::text in ('CASHIER','ADMIN','OWNER')) then
    raise exception 'Hanya kasir atau admin aktif yang dapat membuka struk' using errcode = '42501';
  end if;
  select jsonb_build_object(
    'order_id', o.id, 'public_id', o.public_id, 'table_label', coalesce(t.label, o.table_id),
    'status', o.status, 'total_rp', o.total_rp, 'subtotal_rp', o.subtotal_rp,
    'tax_rp', o.tax_rp, 'service_charge_rp', o.service_charge_rp,
    'paid_at', o.paid_at, 'customer_name', c.name, 'phone_e164', c.phone_e164,
    'items', coalesce((select jsonb_agg(jsonb_build_object(
      'name', i.name_snapshot, 'quantity', i.quantity, 'line_total_rp', i.line_total_rp,
      'note', coalesce(i.note, ''), 'options', coalesce((
        select jsonb_agg(opt.option_name_snapshot order by opt.id) from public.order_item_option opt where opt.order_item_id = i.id
      ), '[]'::jsonb)) order by i.id) from public.order_item i where i.order_id = o.id), '[]'::jsonb),
    'last_attempt_at', (select max(occurred_at) from public.audit_log
      where target_type = 'customer_order' and target_id = o.id::text
        and action in ('RECEIPT_SENT','RECEIPT_FAILED','RECEIPT_OPENED')),
    'last_attempt_status', (select changes_summary->>'result' from public.audit_log
      where target_type = 'customer_order' and target_id = o.id::text
        and action in ('RECEIPT_SENT','RECEIPT_FAILED','RECEIPT_OPENED')
      order by occurred_at desc, id desc limit 1)
  ) into result from public.customer_order o
    join public.private_customer c on c.order_id = o.id
    left join public.cafe_table t on t.id = o.table_id
    where o.id = p_order_id and o.status::text in ('PAID','ACCEPTED','PREPARING','READY','COMPLETED');
  if result is null then raise exception 'Pesanan belum lunas, dibatalkan, atau data pelanggan tidak tersedia'; end if;
  return result;
end;
$$;
revoke all on function public.get_order_receipt(uuid) from public, anon;
grant execute on function public.get_order_receipt(uuid) to authenticated;

create or replace function public.record_receipt_resend(
  p_order_id uuid, p_result text, p_phone text, p_reason text
) returns void language plpgsql security definer set search_path = public
as $$
begin
  perform public.get_order_receipt(p_order_id);
  if p_result is null or p_result not in ('SENT','FAILED','OPENED')
    or p_phone is null or p_phone !~ '^62[0-9]{8,13}$'
    or nullif(trim(p_reason), '') is null or length(p_reason) > 500 then
    raise exception 'Data pengiriman struk tidak valid';
  end if;
  insert into public.audit_log (actor_uid, action, target_type, target_id, reason, changes_summary)
  values (auth.uid(), 'RECEIPT_' || p_result, 'customer_order', p_order_id::text, trim(p_reason),
    jsonb_build_object('recipient', p_phone, 'result', p_result,
      'actor_name', (select name from public.staff_user where uid = auth.uid())));
end;
$$;
revoke all on function public.record_receipt_resend(uuid,text,text,text) from public, anon;
grant execute on function public.record_receipt_resend(uuid,text,text,text) to authenticated;

notify pgrst, 'reload schema';
commit;
