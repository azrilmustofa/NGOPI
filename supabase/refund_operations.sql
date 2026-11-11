-- No. 23: pembatalan, pencatatan refund, referensi provider, dan audit.
-- Aman dijalankan setelah support_refund_operations.sql bagian bantuan (No. 21).
alter type public.payment_status add value if not exists 'REFUNDED' after 'REFUND_PENDING';

begin;

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
  ) from public.refund_request r
  join public.customer_order o on o.id = r.order_id
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
  if nullif(trim(coalesce(p_reason, '')), '') is null
     or length(trim(p_reason)) not between 3 and 255 then
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
  update public.customer_order
    set status = 'CANCELLED', cancellation_reason = trim(p_reason)
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
  values (
    auth.uid(), 'REFUND_' || initial_status, 'customer_order', p_order_id::text, trim(p_reason),
    jsonb_build_object(
      'actor_name', (select name from public.staff_user where uid = auth.uid()),
      'amount_rp', selected_order.total_rp, 'refund_id', existing_refund.id
    )
  );
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
declare
  updated_refund public.refund_request%rowtype;
  attempt_status public.payment_attempt.status%type;
begin
  if auth.uid() is not null
     or current_setting('role', true) not in ('service_role','postgres','none') then
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
  values (
    null, 'REFUND_' || p_status, 'customer_order', updated_refund.order_id::text,
    updated_refund.reason,
    jsonb_build_object('refund_id', updated_refund.id, 'amount_rp', updated_refund.amount_rp,
      'provider_reference', p_provider_reference)
  );
  return public.refund_request_payload(updated_refund.id);
end;
$$;
revoke all on function public.update_refund_result(uuid,text,text,jsonb) from public, anon, authenticated;
grant execute on function public.update_refund_result(uuid,text,text,jsonb) to service_role;

notify pgrst, 'reload schema';
commit;
