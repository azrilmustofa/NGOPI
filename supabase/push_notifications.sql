begin;

create table if not exists public.push_device (
  token text primary key check (length(token) between 20 and 4096),
  user_id uuid not null references auth.users(id) on delete cascade,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

alter table public.push_device enable row level security;
revoke all on public.push_device from public, anon, authenticated;
grant all on public.push_device to service_role;

create or replace function public.register_push_token(p_token text)
returns void language plpgsql security definer set search_path = public
as $$
begin
  if auth.uid() is null then
    raise exception 'Silakan login ulang' using errcode = '42501';
  end if;
  if p_token is null or length(p_token) not between 20 and 4096 then
    raise exception 'Token notifikasi tidak valid';
  end if;
  insert into public.push_device (token, user_id)
  values (p_token, auth.uid())
  on conflict (token) do update set user_id = excluded.user_id, updated_at = now();
end;
$$;
revoke all on function public.register_push_token(text) from public, anon;
grant execute on function public.register_push_token(text) to authenticated;

create or replace function public.unregister_push_token(p_token text)
returns void language sql security definer set search_path = public
as $$
  delete from public.push_device where token = p_token and user_id = auth.uid();
$$;
revoke all on function public.unregister_push_token(text) from public, anon;
grant execute on function public.unregister_push_token(text) to authenticated;

create table if not exists public.push_event (
  id uuid primary key default gen_random_uuid(),
  order_id uuid not null references public.customer_order(id) on delete cascade,
  user_id uuid references auth.users(id) on delete cascade,
  audience text not null check (audience in ('CUSTOMER', 'STAFF')),
  status text not null,
  created_at timestamptz not null default now(),
  processing_at timestamptz,
  processed_at timestamptz,
  last_error text,
  unique (order_id, audience, status)
);

alter table public.push_event enable row level security;
revoke all on public.push_event from public, anon, authenticated;
grant all on public.push_event to service_role;

create or replace function public.enqueue_order_push()
returns trigger language plpgsql security definer set search_path = public
as $$
begin
  if new.owner_uid is not null
    and new.status::text in ('PAID','ACCEPTED','PREPARING','READY','COMPLETED','CANCELLED')
    and not exists (select 1 from public.staff_user where uid = new.owner_uid and is_active)
  then
    insert into public.push_event (order_id, user_id, audience, status)
    values (new.id, new.owner_uid, 'CUSTOMER', new.status::text)
    on conflict (order_id, audience, status) do nothing;
  end if;

  if new.status::text = 'PAID' then
    insert into public.push_event (order_id, audience, status)
    values (new.id, 'STAFF', new.status::text)
    on conflict (order_id, audience, status) do nothing;
  end if;
  return new;
end;
$$;
revoke all on function public.enqueue_order_push() from public;
drop trigger if exists enqueue_order_push on public.customer_order;
create trigger enqueue_order_push after update of status on public.customer_order
  for each row when (old.status is distinct from new.status)
  execute function public.enqueue_order_push();

create or replace function public.claim_push_event(p_event_id uuid)
returns jsonb language plpgsql security definer set search_path = public
as $$
declare claimed public.push_event%rowtype;
begin
  update public.push_event set processing_at = now(), last_error = null
    where id = p_event_id and processed_at is null
      and (processing_at is null or processing_at < now() - interval '5 minutes')
    returning * into claimed;
  if not found then return null; end if;
  return to_jsonb(claimed);
end;
$$;
revoke all on function public.claim_push_event(uuid) from public, anon, authenticated;
grant execute on function public.claim_push_event(uuid) to service_role;

create or replace function public.complete_push_event(p_event_id uuid, p_error text default null)
returns void language sql security definer set search_path = public
as $$
  update public.push_event set processed_at = now(), last_error = left(p_error, 1000)
    where id = p_event_id and processed_at is null;
$$;
revoke all on function public.complete_push_event(uuid,text) from public, anon, authenticated;
grant execute on function public.complete_push_event(uuid,text) to service_role;

notify pgrst, 'reload schema';
commit;
