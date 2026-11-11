alter type public.payment_status add value if not exists 'REFUNDED' after 'REFUND_PENDING';

begin;

create table if not exists public.support_request (
  id uuid primary key default gen_random_uuid(),
  customer_uid uuid not null,
  table_id text not null references public.cafe_table(id),
  order_id uuid references public.customer_order(id),
  topic text not null check (topic in ('PAYMENT','ORDER','RECEIPT','OTHER')),
  status text not null default 'OPEN' check (status in ('OPEN','ACKNOWLEDGED','RESOLVED')),
  repeat_count integer not null default 1 check (repeat_count > 0),
  last_requested_at timestamptz not null default now(),
  acknowledged_by uuid,
  acknowledged_at timestamptz,
  resolved_by uuid,
  resolved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);
create unique index if not exists support_request_one_active
  on public.support_request (customer_uid, table_id)
  where status in ('OPEN','ACKNOWLEDGED');
create index if not exists support_request_queue
  on public.support_request (status, last_requested_at desc);

alter table public.support_request enable row level security;
revoke all on public.support_request from public, anon, authenticated;

create or replace function public.require_active_cashier()
returns void language plpgsql stable security definer set search_path = public
as $$
begin
  if not exists (select 1 from public.staff_user where uid = auth.uid() and is_active
    and role::text in ('CASHIER','ADMIN','OWNER')) then
    raise exception 'Hanya kasir atau admin aktif yang diizinkan' using errcode = '42501';
  end if;
end;
$$;
revoke all on function public.require_active_cashier() from public;

create or replace function public.support_request_payload(p_request_id uuid)
returns jsonb language sql stable security definer set search_path = public
as $$
  select jsonb_build_object(
    'id', r.id, 'order_id', r.order_id, 'public_id', o.public_id,
    'table_id', r.table_id, 'table_label', coalesce(t.label, r.table_id),
    'topic', r.topic, 'status', r.status, 'repeat_count', r.repeat_count,
    'created_at', r.created_at, 'last_requested_at', r.last_requested_at,
    'acknowledged_at', r.acknowledged_at, 'resolved_at', r.resolved_at,
    'payment_status', o.status, 'order_created_at', o.created_at, 'total_rp', o.total_rp
  )
  from public.support_request r
  left join public.customer_order o on o.id = r.order_id
  left join public.cafe_table t on t.id = r.table_id
  where r.id = p_request_id;
$$;
revoke all on function public.support_request_payload(uuid) from public, anon, authenticated;

create or replace function public.request_support(
  p_topic text, p_table_id text default null, p_order_public_id text default null
) returns jsonb language plpgsql security definer set search_path = public
as $$
declare
  selected_order public.customer_order%rowtype;
  selected_table text;
  active_request public.support_request%rowtype;
  cooldown integer;
begin
  if auth.uid() is null then raise exception 'Silakan login ulang' using errcode = '42501'; end if;
  if p_topic not in ('PAYMENT','ORDER','RECEIPT','OTHER') then
    raise exception 'Jenis bantuan tidak valid';
  end if;

  if nullif(trim(coalesce(p_order_public_id, '')), '') is not null then
    select * into selected_order from public.customer_order
      where owner_uid = auth.uid() and public_id = trim(leading '#' from trim(p_order_public_id))
      order by created_at desc limit 1;
  end if;
  selected_table := coalesce(selected_order.table_id, nullif(trim(coalesce(p_table_id, '')), ''));
  if selected_table is null or not exists (
    select 1 from public.cafe_table where id = selected_table and is_active = true
  ) then
    raise exception 'Meja tidak tersedia. Scan ulang QR meja sebelum memanggil kasir';
  end if;

  select * into active_request from public.support_request
    where customer_uid = auth.uid() and table_id = selected_table
      and status in ('OPEN','ACKNOWLEDGED')
    for update;

  if found then
    cooldown := greatest(0, 60 - floor(extract(epoch from (now() - active_request.last_requested_at)))::integer);
    if cooldown = 0 then
      update public.support_request set
        topic = p_topic,
        order_id = coalesce(selected_order.id, order_id),
        repeat_count = repeat_count + 1,
        last_requested_at = now(),
        updated_at = now()
      where id = active_request.id;
      cooldown := 60;
    end if;
    return public.support_request_payload(active_request.id)
      || jsonb_build_object('merged', true, 'cooldown_seconds', cooldown);
  end if;

  insert into public.support_request (customer_uid, table_id, order_id, topic)
  values (auth.uid(), selected_table, selected_order.id, p_topic)
  returning * into active_request;
  return public.support_request_payload(active_request.id)
    || jsonb_build_object('merged', false, 'cooldown_seconds', 60);
exception when unique_violation then
  select * into active_request from public.support_request
    where customer_uid = auth.uid() and table_id = selected_table
      and status in ('OPEN','ACKNOWLEDGED') limit 1;
  return public.support_request_payload(active_request.id)
    || jsonb_build_object('merged', true, 'cooldown_seconds', 60);
end;
$$;
revoke all on function public.request_support(text,text,text) from public, anon;
grant execute on function public.request_support(text,text,text) to authenticated;

create or replace function public.get_my_support_request(p_table_id text)
returns jsonb language plpgsql stable security definer set search_path = public
as $$
declare request_id uuid;
begin
  if auth.uid() is null then raise exception 'Silakan login ulang' using errcode = '42501'; end if;
  select id into request_id from public.support_request
    where customer_uid = auth.uid() and table_id = p_table_id
      and status in ('OPEN','ACKNOWLEDGED')
    order by last_requested_at desc limit 1;
  return case when request_id is null then null else public.support_request_payload(request_id) end;
end;
$$;
revoke all on function public.get_my_support_request(text) from public, anon;
grant execute on function public.get_my_support_request(text) to authenticated;

create or replace function public.list_active_support_requests()
returns jsonb language plpgsql stable security definer set search_path = public
as $$
declare result jsonb;
begin
  perform public.require_active_cashier();
  select coalesce(jsonb_agg(public.support_request_payload(id)
    order by case status when 'OPEN' then 0 else 1 end, last_requested_at), '[]'::jsonb)
  into result from public.support_request where status in ('OPEN','ACKNOWLEDGED');
  return result;
end;
$$;
revoke all on function public.list_active_support_requests() from public, anon;
grant execute on function public.list_active_support_requests() to authenticated;

create or replace function public.get_support_request(p_request_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public
as $$
begin
  perform public.require_active_cashier();
  if not exists (select 1 from public.support_request where id = p_request_id) then
    raise exception 'Permintaan bantuan tidak ditemukan';
  end if;
  return public.support_request_payload(p_request_id);
end;
$$;
revoke all on function public.get_support_request(uuid) from public, anon;
grant execute on function public.get_support_request(uuid) to authenticated;

create or replace function public.update_support_request(
  p_request_id uuid, p_expected_status text, p_status text
) returns jsonb language plpgsql security definer set search_path = public
as $$
declare current_request public.support_request%rowtype;
begin
  perform public.require_active_cashier();
  select * into current_request from public.support_request where id = p_request_id for update;
  if not found then raise exception 'Permintaan bantuan tidak ditemukan'; end if;
  if current_request.status <> p_expected_status then
    raise exception 'Status bantuan sudah berubah. Muat ulang sebelum melanjutkan' using errcode = '40001';
  end if;
  if not ((p_expected_status = 'OPEN' and p_status = 'ACKNOWLEDGED')
    or (p_expected_status = 'ACKNOWLEDGED' and p_status = 'RESOLVED')) then
    raise exception 'Perubahan status bantuan tidak diizinkan';
  end if;

  update public.support_request set
    status = p_status,
    acknowledged_by = case when p_status = 'ACKNOWLEDGED' then auth.uid() else acknowledged_by end,
    acknowledged_at = case when p_status = 'ACKNOWLEDGED' then now() else acknowledged_at end,
    resolved_by = case when p_status = 'RESOLVED' then auth.uid() else resolved_by end,
    resolved_at = case when p_status = 'RESOLVED' then now() else resolved_at end,
    updated_at = now()
  where id = p_request_id;
  insert into public.audit_log (actor_uid, action, target_type, target_id, reason, changes_summary)
  values (auth.uid(), 'SUPPORT_' || p_status, 'support_request', p_request_id::text,
    'Perubahan status bantuan kasir', jsonb_build_object(
      'actor_name', (select name from public.staff_user where uid = auth.uid()),
      'before', p_expected_status, 'after', p_status));
  return public.support_request_payload(p_request_id);
end;
$$;
revoke all on function public.update_support_request(uuid,text,text) from public, anon;
grant execute on function public.update_support_request(uuid,text,text) to authenticated;

create table if not exists public.refund_request (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null unique references public.customer_order(id),
  payment_attempt_id uuid references public.payment_attempt(id),
  provider_order_id text,
  refund_key text not null unique,
  amount_rp integer not null check (amount_rp >= 0),
  status text not null check (status in ('REQUESTED','PROCESSING','REFUNDED','FAILED','MANUAL_REQUIRED','NOT_REQUIRED')),
  reason text not null check (length(trim(reason)) between 3 and 255),
  provider_reference text,
  provider_response jsonb,
  requested_by uuid not null,
  attempt_count integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  refunded_at timestamptz
);
create index if not exists refund_request_status on public.refund_request (status, updated_at desc);
alter table public.refund_request enable row level security;
revoke all on public.refund_request from public, anon, authenticated;

create or replace function public.refund_request_payload(p_refund_id uuid)
returns jsonb language sql stable security definer set search_path = public
as $$
  select jsonb_build_object(
    'id', r.id, 'order_id', r.order_id, 'order_status', o.status,
    'refund_status', r.status, 'amount_rp', r.amount_rp,
    'provider_order_id', r.provider_order_id, 'refund_key', r.refund_key,
    'provider_reference', r.provider_reference, 'reason', r.reason,
    'attempt_count', r.attempt_count, 'updated_at', r.updated_at,
    'refunded_at', r.refunded_at
  ) from public.refund_request r join public.customer_order o on o.id = r.order_id
  where r.id = p_refund_id;
$$;
revoke all on function public.refund_request_payload(uuid) from public, anon, authenticated;

create or replace function public.request_order_refund(
  p_order_id uuid, p_expected_status text, p_reason text
) returns jsonb language plpgsql security definer set search_path = public
as $$
declare
  selected_order public.customer_order%rowtype;
  selected_attempt public.payment_attempt%rowtype;
  existing_refund public.refund_request%rowtype;
  initial_status text;
begin
  perform public.require_active_cashier();
  if nullif(trim(coalesce(p_reason, '')), '') is null or length(trim(p_reason)) not between 3 and 255 then
    raise exception 'Alasan pembatalan wajib berisi 3–255 karakter';
  end if;
  select * into selected_order from public.customer_order where id = p_order_id for update;
  if not found then raise exception 'Pesanan tidak ditemukan'; end if;
  select * into existing_refund from public.refund_request where order_id = p_order_id;
  if found then return public.refund_request_payload(existing_refund.id); end if;
  if selected_order.status::text <> p_expected_status then
    raise exception 'Status pesanan sudah berubah. Muat ulang sebelum melanjutkan' using errcode = '40001';
  end if;
  if selected_order.status::text not in ('AWAITING_PAYMENT','PAID','ACCEPTED','PREPARING','READY') then
    raise exception 'Pesanan ini tidak dapat dibatalkan';
  end if;

  perform set_config('ngopi.order_note', trim(p_reason), true);
  update public.customer_order set status = 'CANCELLED', cancellation_reason = trim(p_reason)
    where id = p_order_id;

  select * into selected_attempt from public.payment_attempt
    where order_id = p_order_id and status::text in ('PAID','REFUND_PENDING','REFUNDED')
    order by paid_at desc nulls last, created_at desc, id desc limit 1;
  initial_status := case
    when selected_attempt.id is not null and selected_attempt.status::text = 'REFUNDED' then 'REFUNDED'
    when selected_attempt.id is not null then 'REQUESTED'
    when selected_order.paid_at is not null then 'MANUAL_REQUIRED'
    else 'NOT_REQUIRED'
  end;
  insert into public.refund_request (
    order_id, payment_attempt_id, provider_order_id, refund_key, amount_rp,
    status, reason, requested_by, refunded_at
  ) values (
    selected_order.id, selected_attempt.id, selected_attempt.provider_order_id,
    'REF-' || replace(gen_random_uuid()::text, '-', ''), selected_order.total_rp,
    initial_status, trim(p_reason), auth.uid(),
    case when initial_status = 'REFUNDED' then now() end
  ) returning * into existing_refund;
  insert into public.audit_log (actor_uid, action, target_type, target_id, reason, changes_summary)
  values (auth.uid(), 'REFUND_' || initial_status, 'customer_order', p_order_id::text, trim(p_reason),
    jsonb_build_object('actor_name', (select name from public.staff_user where uid = auth.uid()),
      'amount_rp', selected_order.total_rp, 'refund_id', existing_refund.id));
  return public.refund_request_payload(existing_refund.id);
end;
$$;
revoke all on function public.request_order_refund(uuid,text,text) from public, anon;
grant execute on function public.request_order_refund(uuid,text,text) to authenticated;

create or replace function public.get_order_refund(p_order_id uuid)
returns jsonb language plpgsql stable security definer set search_path = public
as $$
declare refund_id uuid;
begin
  perform public.require_active_cashier();
  select id into refund_id from public.refund_request where order_id = p_order_id;
  return case when refund_id is null then null else public.refund_request_payload(refund_id) end;
end;
$$;
revoke all on function public.get_order_refund(uuid) from public, anon;
grant execute on function public.get_order_refund(uuid) to authenticated;

create or replace function public.update_refund_result(
  p_refund_id uuid, p_status text, p_provider_reference text default null,
  p_provider_response jsonb default null
) returns jsonb language plpgsql security definer set search_path = public
as $$
declare updated_refund public.refund_request%rowtype;
declare attempt_status public.payment_attempt.status%type;
begin
  if auth.uid() is not null or current_setting('role', true) not in ('service_role','postgres','none') then
    raise exception 'Hanya layanan server yang diizinkan' using errcode = '42501';
  end if;
  if p_status not in ('PROCESSING','REFUNDED','FAILED')
    or length(coalesce(p_provider_reference, '')) > 255 then
    raise exception 'Hasil refund tidak valid';
  end if;
  update public.refund_request set
    status = p_status,
    provider_reference = coalesce(nullif(trim(p_provider_reference), ''), provider_reference),
    provider_response = coalesce(p_provider_response, provider_response),
    attempt_count = attempt_count + case when p_status = 'PROCESSING' then 1 else 0 end,
    updated_at = now(),
    refunded_at = case when p_status = 'REFUNDED' then now() else refunded_at end
  where id = p_refund_id returning * into updated_refund;
  if not found then raise exception 'Refund tidak ditemukan'; end if;
  if p_status = 'REFUNDED' and updated_refund.payment_attempt_id is not null then
    select status into attempt_status from jsonb_populate_record(
      null::public.payment_attempt, jsonb_build_object('status', 'REFUNDED'));
    update public.payment_attempt set status = attempt_status, updated_at = now()
      where id = updated_refund.payment_attempt_id;
  end if;
  insert into public.audit_log (actor_uid, action, target_type, target_id, reason, changes_summary)
  values (null, 'REFUND_' || p_status, 'customer_order', updated_refund.order_id::text,
    updated_refund.reason, jsonb_build_object('refund_id', updated_refund.id,
      'amount_rp', updated_refund.amount_rp, 'provider_reference', p_provider_reference));
  return public.refund_request_payload(updated_refund.id);
end;
$$;
revoke all on function public.update_refund_result(uuid,text,text,jsonb) from public, anon, authenticated;
grant execute on function public.update_refund_result(uuid,text,text,jsonb) to service_role;

notify pgrst, 'reload schema';
commit;
