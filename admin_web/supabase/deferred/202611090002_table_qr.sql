begin;

alter table public.cafe_table add column if not exists qr_image_url text;

create table if not exists public.table_qr (
  table_id text primary key references public.cafe_table(id) on delete cascade,
  token uuid not null unique default gen_random_uuid(),
  updated_at timestamptz not null default now()
);
alter table public.table_qr enable row level security;
revoke all on public.table_qr from anon, authenticated;
grant select on public.table_qr to authenticated;
drop policy if exists admin_qr_read on public.table_qr;
create policy admin_qr_read on public.table_qr for select to authenticated using (public.is_active_admin());
create or replace function public.rotate_table_qr(p_table_id text)
returns uuid language plpgsql security definer set search_path = public
as $$
declare result uuid;
begin
  perform public.require_active_admin();
  perform 1 from public.cafe_table where id = p_table_id for update;
  if not found then raise exception 'Meja tidak ditemukan'; end if;
  insert into public.table_qr (table_id) values (p_table_id)
  on conflict (table_id) do update set token = gen_random_uuid(), updated_at = now()
  returning token into result;
  update public.cafe_table set qr_image_url = null where id = p_table_id and qr_image_url is not null;
  return result;
end;
$$;
revoke all on function public.rotate_table_qr(text) from public;
grant execute on function public.rotate_table_qr(text) to authenticated;

create or replace function public.resolve_table_qr(p_code text)
returns setof public.cafe_table language plpgsql stable security definer set search_path = public
as $$
begin
  if length(p_code) > 200 then return; end if;
  if trim(p_code) ~* '^NGOPI:[0-9a-f-]{36}$' then
    return query select t.* from public.cafe_table t join public.table_qr q on q.table_id = t.id
      where q.token::text = lower(substring(trim(p_code) from 7)) and t.is_active;
  else
    return query select t.* from public.cafe_table t
      where t.is_active and not exists (select 1 from public.table_qr q where q.table_id = t.id)
        and (upper(t.id) = upper(trim(p_code)) or upper(t.label) = upper(trim(p_code))) limit 1;
  end if;
end;
$$;
revoke all on function public.resolve_table_qr(text) from public;
grant execute on function public.resolve_table_qr(text) to anon, authenticated;

drop trigger if exists capture_admin_audit on public.table_qr;
create trigger capture_admin_audit after insert or update or delete on public.table_qr
  for each row execute function public.capture_admin_audit();

notify pgrst, 'reload schema';
commit;
