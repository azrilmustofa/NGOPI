import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { after, test } from 'node:test';
import { PGlite } from '@electric-sql/pglite';

const db = new PGlite();
after(() => db.close());
const adminId = '00000000-0000-0000-0000-000000000001';
const cashierId = '00000000-0000-0000-0000-000000000002';
const inactiveId = '00000000-0000-0000-0000-000000000003';
await db.exec(`
  create role anon; create role authenticated;
  create schema auth;
  create function auth.uid() returns uuid language sql stable as
    $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
  grant usage on schema public, auth to anon, authenticated;
  grant execute on function auth.uid() to anon, authenticated;
  create type staff_role as enum ('BARISTA','CASHIER','ADMIN','OWNER');
  create table staff_user (uid uuid primary key, name text, role staff_role, is_active boolean);
  create table audit_log (id uuid primary key default gen_random_uuid(), actor_uid uuid, action varchar,
    target_type varchar, target_id varchar, reason text, changes_summary jsonb, occurred_at timestamptz default now());
  insert into audit_log (actor_uid, action, target_type, target_id, reason) values
    ('${adminId}', 'LEGACY_EVENT', 'app_settings', 'default', 'Riwayat sebelum migrasi');
  create table cafe_table (id text primary key, label text, is_active boolean);
  create table menu_category (id text primary key, name text);
  create table menu_item (id text primary key, name text, base_price_rp integer);
  create table app_settings (id text primary key, tax_percentage numeric, service_charge_percentage numeric);
  create table customer_order (id text primary key, status text, created_at timestamptz,
    subtotal_rp integer, discount_rp integer, tax_rp integer, service_charge_rp integer, total_rp integer);
  create table order_item (order_id text references customer_order, menu_item_id text, name_snapshot text,
    quantity integer, line_total_rp integer);
  create table payment_attempt (id text, order_id text, status text, payment_method text, paid_at timestamptz, created_at timestamptz);
  insert into staff_user values
    ('${adminId}', 'Admin Test', 'ADMIN', true),
    ('${cashierId}', 'Kasir Test', 'CASHIER', true),
    ('${inactiveId}', 'Admin Nonaktif', 'ADMIN', false);
  insert into cafe_table values ('TBL-01', 'Meja 01', true), ('TBL-02', 'Meja 02', false);
  insert into menu_item values ('coffee','Kopi',18000);
  insert into app_settings values ('default',10,0);
  alter table menu_item enable row level security;
  create policy legacy_open on menu_item for all to public using (true) with check (true);
  create policy legacy_staff_read on staff_user for select to authenticated using (true);
  grant all on menu_item to anon, authenticated;
`);
const migration = readFileSync(new URL('../supabase/migrations/202611090001_admin_operations.sql', import.meta.url), 'utf8');
await db.exec(migration);
await db.exec(migration);

const coreQrTable = (await db.query("select to_regclass('public.table_qr') name")).rows[0].name;
await login(adminId);
const coreReport = (await db.query("select admin_sales_report('2026-11-09','2026-11-09') report")).rows[0].report;
const coreTemplateCount = (await db.query('select * from receipt_template')).rows.length;
const coreLegacyCount = (await db.query("select * from admin_audit_log where action='LEGACY_EVENT'")).rows.length;
await db.exec('reset role');
const deferred = readFileSync(new URL('../supabase/deferred/202611090002_table_qr.sql', import.meta.url), 'utf8');
await db.exec(deferred);
await db.exec(deferred);

test('No. 28–30 migration works without installing deferred QR features', () => {
  assert.equal(coreQrTable, null);
  assert.equal(coreReport.summary.transactions, 0);
  assert.equal(coreTemplateCount, 2);
  assert.equal(coreLegacyCount, 1);
});

async function login(uid, role = 'authenticated') {
  await db.exec('reset role');
  await db.query("select set_config('request.jwt.claim.sub', $1, false)", [uid ?? '']);
  await db.exec(`set role ${role}`);
}

test('only active admins can rotate QR or access financial reports', async () => {
  for (const uid of [cashierId, inactiveId, '00000000-0000-0000-0000-000000000099']) {
    await login(uid);
    await assert.rejects(db.query("select rotate_table_qr('TBL-01')"), /ADMIN aktif/);
    await assert.rejects(db.query("select admin_sales_report('2026-11-01','2026-11-09')"), /ADMIN aktif/);
    assert.equal((await db.query('select * from admin_audit_log')).rows.length, 0);
    assert.equal((await db.query('select * from table_qr')).rows.length, 0);
    assert.equal((await db.query('select * from staff_user')).rows.length, uid === cashierId || uid === inactiveId ? 1 : 0);
  }
});

test('old permissive policies cannot permit non-admin master-data writes', async () => {
  await login(cashierId);
  assert.equal((await db.query("update menu_item set base_price_rp=1 where id='coffee' returning *")).rows.length, 0);
  await assert.rejects(db.query("insert into menu_item values ('bad','Bad',1)"), /row-level security/);
  assert.equal((await db.query(`update staff_user set role='ADMIN' where uid='${cashierId}' returning *`)).rows.length, 0);
  await login(null, 'anon');
  await assert.rejects(db.query("update menu_item set base_price_rp=1 where id='coffee'"), /permission denied/);
});

test('QR rotation invalidates old tokens and legacy IDs without exposing tokens', async () => {
  await login(null, 'anon');
  assert.equal((await db.query("select * from resolve_table_qr('TBL-01')")).rows.length, 1);
  assert.equal((await db.query("select * from resolve_table_qr('TBL-02')")).rows.length, 0);
  await assert.rejects(db.query('select * from table_qr'), /permission denied/);
  await login(adminId);
  const first = (await db.query("select rotate_table_qr('TBL-01') token")).rows[0].token;
  const second = (await db.query("select rotate_table_qr('TBL-01') token")).rows[0].token;
  assert.notEqual(first, second);
  await login(null, 'anon');
  for (const code of ['TBL-01', 'Meja 01', `NGOPI:${first}`, 'NGOPI:not-a-token']) {
    assert.equal((await db.query('select * from resolve_table_qr($1)', [code])).rows.length, 0);
  }
  assert.equal((await db.query('select * from resolve_table_qr($1)', [`NGOPI:${second}`])).rows[0].id, 'TBL-01');
  await login(adminId);
  await db.query("update cafe_table set is_active=false where id='TBL-01'");
  assert.equal((await db.query('select * from resolve_table_qr($1)', [`NGOPI:${second}`])).rows.length, 0);
});

test('database records actor and before/after values; clients cannot forge audit records', async () => {
  await login(adminId);
  await db.query("update menu_item set base_price_rp=20000 where id='coffee'");
  const { rows: [entry] } = await db.query("select * from admin_audit_log where table_name='menu_item' order by id desc limit 1");
  assert.equal(entry.actor_uid, adminId);
  assert.equal(entry.actor_name, 'Admin Test');
  assert.equal(entry.old_data.base_price_rp, 18000);
  assert.equal(entry.new_data.base_price_rp, 20000);
  assert.equal(entry.action, 'UPDATE');
  assert.ok(entry.created_at);
  const { rows: qrLogs } = await db.query("select * from admin_audit_log where table_name='table_qr'");
  assert.equal(qrLogs.length, 2);
  assert.ok(qrLogs.every((row) => !('token' in row.new_data)));
  await assert.rejects(db.query("delete from admin_audit_log"), /permission denied|cannot delete from view/);
  await assert.rejects(db.query("insert into admin_audit_log (actor_name, action, table_name, record_id) values ('Fake','UPDATE','menu_item','coffee')"), /permission denied|cannot insert into view/);
  await assert.rejects(db.query("delete from audit_log"), /permission denied/);
  await assert.rejects(db.query("insert into audit_log (action, target_type, target_id) values ('UPDATE','menu_item','coffee')"), /permission denied/);
});

test('receipt templates persist and are read-only for non-admins', async () => {
  await login(adminId);
  await db.query("update receipt_template set footer='Terima kasih kembali.' where id='COMPLETED'");
  await assert.rejects(db.query("update receipt_template set title='' where id='COMPLETED'"), /check constraint/);
  await login(cashierId);
  assert.equal((await db.query("select footer from receipt_template where id='COMPLETED'")).rows[0].footer, 'Terima kasih kembali.');
  assert.equal((await db.query("update receipt_template set footer='Spoof' returning *")).rows.length, 0);
});

test('fee validation and current-admin protection also apply to direct database writes', async () => {
  await login(adminId);
  await assert.rejects(db.query("update app_settings set tax_percentage=-1 where id='default'"), /check constraint/);
  await assert.rejects(db.query("update app_settings set service_charge_percentage=51 where id='default'"), /check constraint/);
  await assert.rejects(db.query(`update staff_user set is_active=false where uid='${adminId}'`), /sedang digunakan/);
  await assert.rejects(db.query(`update staff_user set role='CASHIER' where uid='${adminId}'`), /sedang digunakan/);
  await assert.rejects(db.query(`update staff_user set role='OWNER' where uid='${adminId}'`), /Hanya pemilik/);
  await assert.rejects(db.query(`delete from staff_user where uid='${adminId}'`), /sedang digunakan/);
  await db.query("update app_settings set tax_percentage=11.25 where id='default'");
  const entry = (await db.query("select * from admin_audit_log where table_name='app_settings' order by created_at desc, id desc limit 1")).rows[0];
  assert.equal(entry.actor_uid, adminId);
  assert.equal(entry.new_data.tax_percentage, 11.25);
});

test('report respects WIB dates, excludes unpaid/cancelled orders, and deduplicates payment attempts', async () => {
  await login(adminId);
  await db.exec('reset role');
  await db.exec(`
    insert into customer_order values
      ('paid','PAID','2026-11-08 17:00:00Z',20000,1000,2000,0,22000),
      ('complete','COMPLETED','2026-11-09 16:59:59Z',30000,0,3000,0,33000),
      ('before','PAID','2026-11-08 16:59:59Z',90000,0,0,0,90000),
      ('after','PAID','2026-11-09 17:00:00Z',90000,0,0,0,90000),
      ('unpaid','AWAITING_PAYMENT','2026-11-09 00:00:00Z',80000,0,0,0,80000),
      ('cancel','CANCELLED','2026-11-09 00:00:00Z',70000,0,0,0,70000);
    insert into order_item values ('paid','coffee','Kopi',2,20000), ('complete','coffee','Kopi',3,30000);
    insert into payment_attempt values
      ('a1','paid','FAILED','QRIS',null,'2026-11-08 17:00:00Z'),
      ('a2','paid','PAID','QRIS','2026-11-08 17:01:00Z','2026-11-08 17:01:00Z'),
      ('a3','paid','PAID','QRIS','2026-11-08 17:02:00Z','2026-11-08 17:02:00Z');
  `);
  await login(adminId);
  const report = (await db.query("select admin_sales_report('2026-11-09','2026-11-09') report")).rows[0].report;
  assert.deepEqual(report.summary, { revenue_rp:55000, subtotal_rp:50000, discount_rp:1000, tax_rp:5000,
    service_charge_rp:0, transactions:2, cancelled:1, awaiting_payment:1 });
  assert.equal(report.products[0].quantity, 5);
  assert.equal(report.products[0].revenue_rp, 50000);
  assert.equal(report.methods.reduce((sum, row) => sum + row.transactions, 0), 2);
  assert.equal(report.methods.find((row) => row.method === 'QRIS').revenue_rp, 22000);
  assert.deepEqual(report.daily, [{ day:'2026-11-09', transactions:2, revenue_rp:55000 }]);
  const empty = (await db.query("select admin_sales_report('2020-01-01','2020-01-01') report")).rows[0].report;
  assert.equal(empty.summary.revenue_rp, 0);
  assert.deepEqual(empty.products, []);
  await assert.rejects(db.query("select admin_sales_report('2026-11-09','2026-11-08')"), /periode/);
});

test('production verification script checks both roles and rolls back its test changes', async () => {
  await db.exec('reset role');
  const before = (await db.query("select footer from receipt_template where id='PAID'")).rows[0].footer;
  await db.exec(readFileSync(new URL('../supabase/verify_admin_operations.sql', import.meta.url), 'utf8'));
  assert.equal((await db.query("select footer from receipt_template where id='PAID'")).rows[0].footer, before);
});
