import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { after, test } from 'node:test';
import { PGlite } from '@electric-sql/pglite';

const db = new PGlite();
after(() => db.close());
const staff = {
  ADMIN: '00000000-0000-0000-0000-000000000001',
  CASHIER: '00000000-0000-0000-0000-000000000002',
  BARISTA: '00000000-0000-0000-0000-000000000003',
  INACTIVE: '00000000-0000-0000-0000-000000000004',
  CUSTOMER: '00000000-0000-0000-0000-000000000005',
};
await db.exec(`
  create role anon; create role authenticated; create role service_role bypassrls;
  create schema auth;
  create function auth.uid() returns uuid language sql stable as
    $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
  grant usage on schema public, auth to anon, authenticated, service_role;
  create type order_status as enum ('AWAITING_PAYMENT','PAID','ACCEPTED','PREPARING','READY','COMPLETED','CANCELLED');
  create type actor_type as enum ('CUSTOMER','STAFF','SYSTEM');
  create type staff_role as enum ('BARISTA','CASHIER','ADMIN','OWNER');
  create table staff_user (uid uuid primary key, name text, role staff_role, is_active boolean);
  insert into staff_user values
    ('${staff.ADMIN}','Admin','ADMIN',true), ('${staff.CASHIER}','Kasir','CASHIER',true),
    ('${staff.BARISTA}','Barista','BARISTA',true), ('${staff.INACTIVE}','Nonaktif','ADMIN',false);
  create table cafe_table (id text primary key, label text);
  insert into cafe_table values ('TBL-01','Meja 01');
  create table customer_order (id uuid primary key default gen_random_uuid(), public_id text default 'NG-TEST',
    table_id text default 'TBL-01', status order_status not null, owner_uid uuid,
    subtotal_rp integer default 20000, tax_rp integer default 2000, service_charge_rp integer default 1000,
    total_rp integer default 23000, cancellation_reason text, updated_at timestamptz default now(),
    paid_at timestamptz, completed_at timestamptz);
  create table order_status_history (id uuid primary key default gen_random_uuid(), order_id uuid references customer_order,
    actor_uid uuid, actor_type actor_type not null, from_status order_status, to_status order_status not null, note text,
    created_at timestamptz default now());
  create table private_customer (order_id uuid references customer_order, name text, phone_e164 text);
  create table order_item (id uuid primary key default gen_random_uuid(), order_id uuid references customer_order,
    name_snapshot text, quantity integer, line_total_rp integer, note text);
  create table order_item_option (id uuid primary key default gen_random_uuid(), order_item_id uuid references order_item,
    option_name_snapshot text);
  create table audit_log (id uuid primary key default gen_random_uuid(), actor_uid uuid, action text,
    target_type text, target_id text, reason text, changes_summary jsonb, occurred_at timestamptz default now());
  alter table private_customer enable row level security;
  alter table audit_log enable row level security;
  grant select on private_customer, audit_log to authenticated;
  grant all on customer_order, order_status_history to public, anon, authenticated, service_role;
  grant update (total_rp), insert (status) on customer_order to authenticated;
  create function public.create_order(text,text,text,jsonb) returns jsonb language sql security definer
    as $$ select '{"status":"PAID"}'::jsonb $$;
  grant execute on function public.create_order(text,text,text,jsonb) to authenticated;
`);
const migration = readFileSync(new URL('../../supabase/order_operations.sql', import.meta.url), 'utf8');
await db.exec(migration);
await db.exec(migration);

async function login(uid, role = 'authenticated') {
  await db.exec('reset role');
  await db.query("select set_config('request.jwt.claim.sub', $1, false)", [uid ?? '']);
  await db.exec(`set role ${role}`);
}
async function newOrder(status = 'PAID') {
  await login(null, 'service_role');
  return (await db.query('insert into customer_order(status) values ($1) returning id', [status])).rows[0].id;
}
const move = (id, before, next, note = 'Pengujian sementara') => db.query(
  'select transition_order($1,$2,$3,$4) result', [id, before, next, note],
);

test('production requires payment and cannot skip or reverse a stage; stale taps do not add history', async () => {
  const unpaid = await newOrder('AWAITING_PAYMENT');
  const id = await newOrder();
  await login(staff.BARISTA);
  await assert.rejects(move(unpaid, 'AWAITING_PAYMENT', 'PREPARING'), /tidak diizinkan/);
  await assert.rejects(move(unpaid, 'AWAITING_PAYMENT', 'PAID'), /tidak diizinkan/);
  await assert.rejects(move(id, 'PAID', 'PREPARING'), /tidak diizinkan/);
  await move(id, 'PAID', 'ACCEPTED');
  await assert.rejects(move(id, 'PAID', 'ACCEPTED'), /sudah berubah/);
  await assert.rejects(move(id, 'ACCEPTED', 'PAID'), /tidak diizinkan/);
  await move(id, 'ACCEPTED', 'PREPARING');
  await move(id, 'PREPARING', 'READY');
  await move(id, 'READY', 'COMPLETED');
  await assert.rejects(move(id, 'COMPLETED', 'READY'), /tidak diizinkan/);
  const rows = (await db.query('select * from order_status_history where order_id=$1 order by created_at,id', [id])).rows;
  assert.equal(rows.length, 4);
  assert.ok(rows.every((row) => row.actor_uid === staff.BARISTA && row.actor_type === 'STAFF'));
  assert.equal((await db.query('select completed_at from customer_order where id=$1', [id])).rows[0].completed_at != null, true);
});

test('customers, inactive staff, and cashier production actions are rejected; direct writes cannot bypass RPC', async () => {
  const id = await newOrder();
  for (const uid of [staff.INACTIVE, staff.CUSTOMER, null]) {
    await login(uid);
    await assert.rejects(move(id, 'PAID', 'ACCEPTED'), /staf aktif/);
  }
  await login(staff.CASHIER);
  await assert.rejects(move(id, 'PAID', 'ACCEPTED'), /tidak diizinkan/);
  await assert.rejects(db.query('update customer_order set status=\'COMPLETED\' where id=$1', [id]), /permission denied/);
  await assert.rejects(db.query('update customer_order set total_rp=1 where id=$1', [id]), /permission denied/);
  await assert.rejects(db.query("insert into customer_order(status) values ('PAID')"), /permission denied/);
  await assert.rejects(db.query("insert into order_status_history(order_id,actor_type,to_status) values ($1,'STAFF','COMPLETED')", [id]), /permission denied/);
  await login(staff.CUSTOMER);
  await assert.rejects(db.query("select create_order('TBL-01','old-key','QRIS','[]')"), /permission denied/);
  await login(null, 'anon');
  await assert.rejects(move(id, 'PAID', 'ACCEPTED'), /permission denied/);
});

test('cash payment and cancellation record authoritative staff, reason, and server timestamps', async () => {
  const id = await newOrder('AWAITING_PAYMENT');
  await login(staff.CASHIER);
  await move(id, 'AWAITING_PAYMENT', 'PAID', 'Tunai diterima kasir');
  assert.ok((await db.query('select paid_at from customer_order where id=$1', [id])).rows[0].paid_at);
  await assert.rejects(move(id, 'PAID', 'CANCELLED', ' '), /wajib diisi/);
  await move(id, 'PAID', 'CANCELLED', 'Pelanggan membatalkan');
  assert.equal((await db.query('select cancellation_reason from customer_order where id=$1', [id])).rows[0].cancellation_reason, 'Pelanggan membatalkan');
  await assert.rejects(move(id, 'CANCELLED', 'PAID'), /tidak diizinkan/);
  assert.equal((await db.query('select count(*)::int n from order_status_history where order_id=$1', [id])).rows[0].n, 2);
});

test('a history failure rolls back the state change; service payment updates also capture history', async () => {
  const id = await newOrder('AWAITING_PAYMENT');
  await db.query("update customer_order set status='PAID' where id=$1", [id]);
  assert.equal((await db.query('select actor_type from order_status_history where order_id=$1', [id])).rows[0].actor_type, 'SYSTEM');
  await login(null);
  await db.exec('reset role');
  await db.exec("alter table order_status_history add constraint simulated_failure check (to_status <> 'ACCEPTED') not valid");
  await login(staff.BARISTA);
  await assert.rejects(move(id, 'PAID', 'ACCEPTED'), /simulated_failure/);
  assert.equal((await db.query('select status from customer_order where id=$1', [id])).rows[0].status, 'PAID');
  await db.exec('reset role');
  await db.exec('alter table order_status_history drop constraint simulated_failure');
});

test('receipt RPC restricts private data to active cashiers/admins and returns the selected order snapshots', async () => {
  const unpaid = await newOrder('AWAITING_PAYMENT');
  const id = await newOrder();
  await login(null);
  await db.exec('reset role');
  await db.query("insert into private_customer values ($1,'Pelanggan pesanan','+6281234567890'), ($2,'Belum bayar','+6281234567891')", [id, unpaid]);
  const itemId = (await db.query("insert into order_item(order_id,name_snapshot,quantity,line_total_rp,note) values ($1,'Kopi snapshot',2,20000,'Tanpa es') returning id", [id])).rows[0].id;
  await db.query("insert into order_item_option(order_item_id,option_name_snapshot) values ($1,'Less sugar')", [itemId]);
  for (const uid of [staff.CUSTOMER, staff.BARISTA, staff.INACTIVE]) {
    await login(uid);
    await assert.rejects(db.query('select get_order_receipt($1)', [id]), /kasir atau admin aktif/);
  }
  await login(staff.CASHIER);
  assert.equal((await db.query('select * from private_customer')).rows.length, 0);
  await assert.rejects(db.query('select get_order_receipt($1)', [unpaid]), /belum lunas/);
  const receipt = (await db.query('select get_order_receipt($1) receipt', [id])).rows[0].receipt;
  assert.equal(receipt.order_id, id);
  assert.equal(receipt.customer_name, 'Pelanggan pesanan');
  assert.equal(receipt.phone_e164, '+6281234567890');
  assert.equal(receipt.table_label, 'Meja 01');
  assert.equal(receipt.total_rp, 23000);
  assert.deepEqual(receipt.items, [{ name: 'Kopi snapshot', quantity: 2, line_total_rp: 20000, note: 'Tanpa es', options: ['Less sugar'] }]);
  for (const result of ['FAILED','SENT','OPENED']) {
    await db.query('select record_receipt_resend($1,$2,$3,$4)', [id, result, '6281234567890', 'Permintaan pelanggan']);
  }
  await assert.rejects(db.query("select record_receipt_resend($1,'DELIVERED','invalid','Alasan')", [id]), /tidak valid/);
  const reloaded = (await db.query('select get_order_receipt($1) receipt', [id])).rows[0].receipt;
  assert.ok(reloaded.last_attempt_at);
  assert.equal(reloaded.last_attempt_status, 'OPENED');
  assert.equal((await db.query('select count(*)::int n from order_status_history where order_id=$1', [id])).rows[0].n, 0);
  await move(id, 'PAID', 'CANCELLED', 'Batal sebelum kirim ulang');
  await assert.rejects(db.query('select get_order_receipt($1)', [id]), /dibatalkan/);
});
