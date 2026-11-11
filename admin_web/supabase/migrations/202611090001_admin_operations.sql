begin;

create or replace function public.is_active_admin()
returns boolean language sql stable security definer set search_path = public
as $$
  select exists (select 1 from public.staff_user
    where uid = auth.uid() and role::text in ('ADMIN', 'OWNER') and is_active = true);
$$;

create or replace function public.require_active_admin()
returns void language plpgsql stable security definer set search_path = public
as $$
begin
  if not public.is_active_admin() then
    raise exception 'Hanya ADMIN aktif atau OWNER aktif yang diizinkan' using errcode = '42501';
  end if;
end;
$$;
revoke all on function public.require_active_admin() from public;
grant execute on function public.require_active_admin() to authenticated;

create table if not exists public.receipt_template (
  id text primary key check (id in ('PAID', 'COMPLETED')),
  title text not null check (length(trim(title)) between 1 and 120),
  header text not null check (length(trim(header)) between 1 and 1000),
  footer text not null check (length(trim(footer)) between 1 and 1000),
  updated_at timestamptz not null default now()
);
insert into public.receipt_template (id, title, header, footer) values
  ('PAID', 'NOTA TRANSAKSI PEMESANAN', '☕ *KOPI INGGIL*' || chr(10) || 'Jl. Raya Cafe No. 1, Yogyakarta',
    'Pesanan Anda sedang disiapkan oleh barista kami.' || chr(10) || 'Terima kasih telah memesan di Kopi Inggil! 🙏'),
  ('COMPLETED', 'NOTA TRANSAKSI PEMESANAN', '☕ *KOPI INGGIL*' || chr(10) || 'Jl. Raya Cafe No. 1, Yogyakarta',
    'Pesanan Anda telah selesai dan siap dinikmati.' || chr(10) || 'Terima kasih telah memesan di Kopi Inggil! 🙏')
on conflict (id) do nothing;

create table if not exists public.audit_log (
  id uuid primary key default gen_random_uuid(),
  actor_uid uuid,
  action varchar not null,
  target_type varchar not null,
  target_id varchar not null,
  reason text,
  changes_summary jsonb,
  occurred_at timestamptz not null default now()
);
create index if not exists admin_audit_created_idx on public.audit_log (occurred_at desc, id desc);
create or replace view public.admin_audit_log with (security_invoker = true) as
  select a.id, a.actor_uid,
    coalesce(a.changes_summary->>'actor_name', s.name, 'Sistem') actor_name,
    a.action, a.target_type table_name, a.target_id record_id,
    coalesce(a.changes_summary->'before', a.changes_summary->'old_data') old_data,
    coalesce(a.changes_summary->'after', a.changes_summary->'new_data',
      case when a.changes_summary is not null or a.reason is not null
        then jsonb_build_object('rincian', a.changes_summary, 'alasan', a.reason) end) new_data,
    a.occurred_at created_at
  from public.audit_log a left join public.staff_user s on s.uid = a.actor_uid;
create index if not exists customer_order_report_idx on public.customer_order (created_at);
create index if not exists payment_attempt_report_idx on public.payment_attempt (order_id, paid_at desc, created_at desc);

alter table public.receipt_template enable row level security;
alter table public.audit_log enable row level security;
revoke all on public.receipt_template, public.audit_log, public.admin_audit_log from anon, authenticated;
grant select on public.audit_log, public.admin_audit_log to authenticated;
grant select, insert, update, delete on public.receipt_template to authenticated;
drop policy if exists template_read on public.receipt_template;
create policy template_read on public.receipt_template for select to authenticated using (true);
drop policy if exists admin_template_manage on public.receipt_template;
create policy admin_template_manage on public.receipt_template for all to authenticated
  using (public.is_active_admin()) with check (public.is_active_admin());
drop policy if exists admin_audit_read on public.audit_log;
create policy admin_audit_read on public.audit_log for select to authenticated using (public.is_active_admin());
drop policy if exists admin_audit_read_boundary on public.audit_log;
create policy admin_audit_read_boundary on public.audit_log as restrictive for select to authenticated using (public.is_active_admin());

-- Restrictive policies also constrain any older permissive write policies.
do $$
declare target text;
begin
  foreach target in array array['menu_item','menu_category','cafe_table','staff_user','app_settings'] loop
    execute format('alter table public.%I enable row level security', target);
    execute format('revoke insert, update, delete on public.%I from anon', target);
    execute format('grant select, insert, update, delete on public.%I to authenticated', target);
    execute format('drop policy if exists admin_operations_manage on public.%I', target);
    execute format('create policy admin_operations_manage on public.%I for all to authenticated using (public.is_active_admin()) with check (public.is_active_admin())', target);
    execute format('drop policy if exists admin_operations_insert on public.%I', target);
    execute format('create policy admin_operations_insert on public.%I as restrictive for insert to authenticated with check (public.is_active_admin())', target);
    execute format('drop policy if exists admin_operations_update on public.%I', target);
    execute format('create policy admin_operations_update on public.%I as restrictive for update to authenticated using (public.is_active_admin()) with check (public.is_active_admin())', target);
    execute format('drop policy if exists admin_operations_delete on public.%I', target);
    execute format('create policy admin_operations_delete on public.%I as restrictive for delete to authenticated using (public.is_active_admin())', target);
  end loop;
end;
$$;
drop policy if exists staff_user_select_self on public.staff_user;
create policy staff_user_select_self on public.staff_user for select to authenticated using (uid = auth.uid());
drop policy if exists staff_user_read_boundary on public.staff_user;
create policy staff_user_read_boundary on public.staff_user as restrictive for select to authenticated
  using (uid = auth.uid() or public.is_active_admin());

create or replace function public.protect_current_admin()
returns trigger language plpgsql set search_path = public
as $$
begin
  if tg_op <> 'DELETE' and new.role::text = 'OWNER' and auth.uid() is not null then
    if not exists (select 1 from public.staff_user where uid = auth.uid() and role::text = 'OWNER' and is_active) then
      raise exception 'Hanya pemilik aktif yang dapat menetapkan peran OWNER';
    end if;
  end if;
  if tg_op <> 'INSERT' and old.role::text = 'OWNER' and auth.uid() is not null then
    if not exists (select 1 from public.staff_user where uid = auth.uid() and role::text = 'OWNER' and is_active) then
      raise exception 'Akses pemilik hanya dapat diubah oleh pemilik aktif';
    end if;
  end if;
  if tg_op <> 'INSERT' and old.uid = auth.uid() and old.role::text in ('ADMIN', 'OWNER') and old.is_active then
    if tg_op = 'DELETE' then raise exception 'Akun admin yang sedang digunakan tidak dapat dihapus'; end if;
    if new.role is distinct from old.role or new.is_active is distinct from true then
      raise exception 'Akun admin yang sedang digunakan tidak dapat diturunkan atau dinonaktifkan';
    end if;
  end if;
  return case when tg_op = 'DELETE' then old else new end;
end;
$$;
drop trigger if exists protect_current_admin on public.staff_user;
create trigger protect_current_admin before insert or update or delete on public.staff_user
  for each row execute function public.protect_current_admin();

do $$
begin
  if not exists (select 1 from pg_constraint where conrelid = 'public.app_settings'::regclass and conname = 'admin_fee_bounds') then
    alter table public.app_settings add constraint admin_fee_bounds check (
      tax_percentage is not null and tax_percentage between 0 and 50
      and service_charge_percentage is not null and service_charge_percentage between 0 and 50
    );
  end if;
end;
$$;

create or replace function public.stamp_receipt_template()
returns trigger language plpgsql set search_path = public
as $$
begin
  new.updated_at := now();
  return new;
end;
$$;
drop trigger if exists stamp_receipt_template on public.receipt_template;
create trigger stamp_receipt_template before update on public.receipt_template
  for each row execute function public.stamp_receipt_template();

create or replace function public.capture_admin_audit()
returns trigger language plpgsql security definer set search_path = public
as $$
declare before_row jsonb; after_row jsonb;
begin
  before_row := case when tg_op <> 'INSERT' then to_jsonb(old) - 'token' - 'qr_image_url' end;
  after_row := case when tg_op <> 'DELETE' then to_jsonb(new) - 'token' - 'qr_image_url' end;
  if tg_op = 'UPDATE' and before_row = after_row and tg_table_name <> 'table_qr' then
    if to_jsonb(old)->'qr_image_url' is not distinct from to_jsonb(new)->'qr_image_url' then return new; end if;
    after_row := after_row || '{"qr_image_changed":true}'::jsonb;
  end if;
  insert into public.audit_log (id, actor_uid, action, target_type, target_id, reason, changes_summary, occurred_at)
  values (gen_random_uuid(), auth.uid(), tg_op, tg_table_name,
    coalesce(after_row->>'id', before_row->>'id', after_row->>'uid', before_row->>'uid', after_row->>'table_id', before_row->>'table_id'),
    'Perubahan dari operasional kafe',
    jsonb_build_object('actor_name', coalesce((select name from public.staff_user where uid = auth.uid()), 'Sistem'),
      'before', before_row, 'after', after_row), now());
  return case when tg_op = 'DELETE' then old else new end;
end;
$$;
revoke all on function public.capture_admin_audit() from public;
do $$
declare target text;
begin
  foreach target in array array['menu_item','menu_category','cafe_table','staff_user','app_settings','receipt_template'] loop
    execute format('drop trigger if exists capture_admin_audit on public.%I', target);
    execute format('create trigger capture_admin_audit after insert or update or delete on public.%I for each row execute function public.capture_admin_audit()', target);
  end loop;
end;
$$;

create or replace function public.admin_sales_report(p_from date, p_until date)
returns jsonb language plpgsql stable security definer set search_path = public
as $$
declare result jsonb;
begin
  perform public.require_active_admin();
  if p_from is null or p_until is null or p_until < p_from or p_until - p_from > 366 then
    raise exception 'Pilih periode laporan maksimal 367 hari';
  end if;
  with period as (
    select * from public.customer_order
    where created_at >= (p_from::timestamp at time zone 'Asia/Jakarta')
      and created_at < ((p_until + 1)::timestamp at time zone 'Asia/Jakarta')
  ), paid as (
    select * from period where status::text in ('PAID','ACCEPTED','PREPARING','READY','COMPLETED')
  ), methods as (
    select coalesce(a.payment_method, 'TIDAK_TERCATAT') method, count(*) transactions, sum(o.total_rp) revenue_rp
    from paid o left join lateral (
      select payment_method::text from public.payment_attempt
      where order_id = o.id and status::text = 'PAID'
      order by paid_at desc nulls last, created_at desc, id desc limit 1
    ) a on true group by coalesce(a.payment_method, 'TIDAK_TERCATAT')
  ), products as (
    select i.menu_item_id, i.name_snapshot name, sum(i.quantity) quantity, sum(i.line_total_rp) revenue_rp
    from public.order_item i join paid o on o.id = i.order_id
    group by i.menu_item_id, i.name_snapshot
  ), daily as (
    select (created_at at time zone 'Asia/Jakarta')::date as "day", count(*) transactions, sum(total_rp) revenue_rp
    from paid group by 1
  )
  select jsonb_build_object(
    'summary', jsonb_build_object(
      'revenue_rp', coalesce((select sum(total_rp) from paid),0),
      'subtotal_rp', coalesce((select sum(subtotal_rp) from paid),0),
      'discount_rp', coalesce((select sum(discount_rp) from paid),0),
      'tax_rp', coalesce((select sum(tax_rp) from paid),0),
      'service_charge_rp', coalesce((select sum(service_charge_rp) from paid),0),
      'transactions', (select count(*) from paid),
      'cancelled', (select count(*) from period where status::text = 'CANCELLED'),
      'awaiting_payment', (select count(*) from period where status::text = 'AWAITING_PAYMENT')),
    'methods', coalesce((select jsonb_agg(to_jsonb(m) order by revenue_rp desc, method) from methods m), '[]'::jsonb),
    'products', coalesce((select jsonb_agg(to_jsonb(p) order by quantity desc, name) from products p), '[]'::jsonb),
    'daily', coalesce((select jsonb_agg(to_jsonb(d) order by "day") from daily d), '[]'::jsonb)
  ) into result;
  return result;
end;
$$;
revoke all on function public.admin_sales_report(date,date) from public;
grant execute on function public.admin_sales_report(date,date) to authenticated;

notify pgrst, 'reload schema';
commit;
