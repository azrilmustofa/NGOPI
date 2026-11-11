begin;

-- Permintaan bantuan QR boleh masuk walaupun QR tidak lagi membuka meja.
-- Alur pemesanan tetap memakai resolve_table_qr, sehingga tidak ikut dilonggarkan.
alter table public.support_request alter column table_id drop not null;

alter table public.support_request
  drop constraint if exists support_request_topic_check;
alter table public.support_request
  add constraint support_request_topic_check
  check (topic in ('PAYMENT','ORDER','RECEIPT','OTHER','QR'));

drop index if exists public.support_request_one_active;
create unique index support_request_one_active
  on public.support_request (customer_uid, (coalesce(table_id, '__INVALID_QR__')))
  where status in ('OPEN','ACKNOWLEDGED');

create or replace function public.support_request_payload(p_request_id uuid)
returns jsonb language sql stable security definer set search_path = public
as $$
  select jsonb_build_object(
    'id', r.id, 'order_id', r.order_id, 'public_id', o.public_id,
    'table_id', coalesce(r.table_id, ''),
    'table_label', coalesce(t.label, r.table_id, 'Lokasi QR tidak diketahui'),
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

create or replace function public.request_invalid_qr_support(p_code text)
returns jsonb language plpgsql security definer set search_path = public
as $$
declare
  normalized_code text := trim(coalesce(p_code, ''));
  token_text text;
  table_hint text;
  selected_table text;
  active_request public.support_request%rowtype;
  cooldown integer;
begin
  if auth.uid() is null then
    raise exception 'Silakan login ulang' using errcode = '42501';
  end if;
  if normalized_code = '' or length(normalized_code) > 500 then
    raise exception 'Kode QR tidak valid';
  end if;

  -- Token yang masih tercatat tetap dapat menunjukkan lokasi walau mejanya nonaktif.
  if normalized_code ~* '^NGOPI:[0-9a-f-]{36}$' then
    token_text := lower(substring(normalized_code from 7));
    select q.table_id into selected_table
      from public.table_qr q where q.token::text = token_text limit 1;
  end if;

  -- Dukung kode meja langsung dan QR lama/URL yang masih memuat TBL-xx.
  if selected_table is null then
    select t.id into selected_table from public.cafe_table t
      where upper(t.id) = upper(normalized_code)
         or upper(t.label) = upper(normalized_code)
      limit 1;
  end if;
  if selected_table is null then
    table_hint := substring(upper(normalized_code) from '(TBL-[0-9]+)');
    if table_hint is not null then
      select t.id into selected_table from public.cafe_table t
        where upper(t.id) = table_hint limit 1;
    end if;
  end if;

  select * into active_request from public.support_request
    where customer_uid = auth.uid()
      and table_id is not distinct from selected_table
      and status in ('OPEN','ACKNOWLEDGED')
    for update;

  if found then
    cooldown := greatest(0, 60 - floor(extract(epoch from (now() - active_request.last_requested_at)))::integer);
    if cooldown = 0 then
      update public.support_request set
        topic = 'QR', repeat_count = repeat_count + 1,
        last_requested_at = now(), updated_at = now()
      where id = active_request.id;
      cooldown := 60;
    end if;
    return public.support_request_payload(active_request.id)
      || jsonb_build_object('merged', true, 'cooldown_seconds', cooldown);
  end if;

  insert into public.support_request (customer_uid, table_id, topic)
  values (auth.uid(), selected_table, 'QR')
  returning * into active_request;
  return public.support_request_payload(active_request.id)
    || jsonb_build_object('merged', false, 'cooldown_seconds', 60);
exception when unique_violation then
  select * into active_request from public.support_request
    where customer_uid = auth.uid()
      and table_id is not distinct from selected_table
      and status in ('OPEN','ACKNOWLEDGED')
    limit 1;
  return public.support_request_payload(active_request.id)
    || jsonb_build_object('merged', true, 'cooldown_seconds', 60);
end;
$$;
revoke all on function public.request_invalid_qr_support(text) from public, anon;
grant execute on function public.request_invalid_qr_support(text) to authenticated;

notify pgrst, 'reload schema';
commit;
