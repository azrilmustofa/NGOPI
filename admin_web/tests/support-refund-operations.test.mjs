import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { after, test } from 'node:test';
import { PGlite } from '@electric-sql/pglite';

const db = new PGlite();
after(() => db.close());
const uid = {
  CUSTOMER: '00000000-0000-0000-0000-000000000001',
  CASHIER: '00000000-0000-0000-0000-000000000002',
  BARISTA: '00000000-0000-0000-0000-000000000003',
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
  create type payment_status as enum ('CREATING','PENDING','PAID','FAILED','CANCELLED','EXPIRED','REFUND_PENDING','REFUNDED');
  create table staff_user (uid uuid primary key, name text, role staff_role, is_active boolean);
  insert into staff_user values ('${uid.CASHIER}','Kasir','CASHIER',true), ('${uid.BARISTA}','Barista','BARISTA',true);
  create table cafe_table (id text primary key, label text, is_active boolean not null default true);
  insert into cafe_table values ('TBL-01','Meja 01',true), ('TBL-OFF','Meja Tutup',false);
  create table customer_order (id uuid primary key default gen_random_uuid(), public_id text unique,
    table_id text references cafe_table, status order_status not null, owner_uid uuid,
    subtotal_rp integer default 20000, tax_rp integer default 2000, service_charge_rp integer default 1000,
    total_rp integer default 23000, cancellation_reason text, updated_at timestamptz default now(),
    created_at timestamptz default now(), paid_at timestamptz, completed_at timestamptz);
  create table payment_attempt (id uuid primary key default gen_random_uuid(), order_id uuid references customer_order,
    provider_order_id text, status payment_status not null, payment_method text, amount_rp integer,
    created_at timestamptz default now(), updated_at timestamptz default now(), paid_at timestamptz);
  create table order_status_history (id uuid primary key default gen_random_uuid(), order_id uuid references customer_order,
    actor_uid uuid, actor_type actor_type not null, from_status order_status, to_status order_status not null,
    note text, created_at timestamptz default now());
  create table private_customer (order_id uuid references customer_order, name text, phone_e164 text);
  create table order_item (id uuid primary key default gen_random_uuid(), order_id uuid references customer_order,
    name_snapshot text, quantity integer, line_total_rp integer, note text);
  create table order_item_option (id uuid primary key default gen_random_uuid(), order_item_id uuid references order_item,
    option_name_snapshot text);
  create table audit_log (id uuid primary key default gen_random_uuid(), actor_uid uuid, action text,
    target_type text, target_id text, reason text, changes_summary jsonb, occurred_at timestamptz default now());
  grant all on customer_order, order_status_history, payment_attempt, audit_log to service_role;
`);
await db.exec(readFileSync(new URL('../../supabase/order_operations.sql', import.meta.url), 'utf8'));
const migration = readFileSync(new URL('../../supabase/support_refund_operations.sql', import.meta.url), 'utf8');
await db.exec(migration);
await db.exec(migration);

async function login(user, role = 'authenticated') {
  await db.exec('reset role');
  await db.query("select set_config('request.jwt.claim.sub', $1, false)", [user ?? '']);
  await db.exec(`set role ${role}`);
}

async function order(status, publicId, paid = false) {
  await login(null, 'service_role');
  return (await db.query(`insert into customer_order(public_id,table_id,status,owner_uid,paid_at)
    values ($1,'TBL-01',$2,$3,$4) returning id`, [publicId, status, uid.CUSTOMER, paid ? new Date() : null])).rows[0].id;
}

test('customer calls are merged during cooldown and cashier handles the OPEN → ACKNOWLEDGED → RESOLVED flow', async () => {
  const orderId = await order('PAID', 'NG-HELP', true);
  await login(uid.CUSTOMER);
  const first = (await db.query("select request_support('PAYMENT','TBL-01','NG-HELP') result")).rows[0].result;
  const repeated = (await db.query("select request_support('ORDER','TBL-01','NG-HELP') result")).rows[0].result;
  assert.equal(first.id, repeated.id);
  assert.equal(repeated.merged, true);
  assert.equal(repeated.repeat_count, 1);
  assert.ok(repeated.cooldown_seconds > 0);
  await assert.rejects(db.query("select request_support('OTHER','TBL-OFF',null)"), /Meja tidak tersedia/);

  await login(uid.BARISTA);
  await assert.rejects(db.query('select list_active_support_requests()'), /kasir atau admin aktif/);
  await login(uid.CASHIER);
  const queue = (await db.query('select list_active_support_requests() queue')).rows[0].queue;
  assert.equal(queue.length, 1);
  assert.equal(queue[0].order_id, orderId);
  const accepted = (await db.query("select update_support_request($1,'OPEN','ACKNOWLEDGED') result", [first.id])).rows[0].result;
  assert.equal(accepted.status, 'ACKNOWLEDGED');
  await assert.rejects(db.query("select update_support_request($1,'OPEN','ACKNOWLEDGED')", [first.id]), /sudah berubah/);
  const resolved = (await db.query("select update_support_request($1,'ACKNOWLEDGED','RESOLVED') result", [first.id])).rows[0].result;
  assert.equal(resolved.status, 'RESOLVED');
  await db.exec('reset role');
  assert.equal((await db.query("select count(*)::int n from audit_log where target_type='support_request'", [])).rows[0].n, 2);
});

test('online cancellation creates one refund, keeps staff history, and records the provider result', async () => {
  const orderId = await order('PAID', 'NG-REFUND', true);
  await login(null, 'service_role');
  await db.query(`insert into payment_attempt(order_id,provider_order_id,status,payment_method,amount_rp,paid_at)
    values ($1,'MID-NG-REFUND','PAID','QRIS',23000,now())`, [orderId]);
  await login(uid.CASHIER);
  const requested = (await db.query("select request_order_refund($1,'PAID','Pelanggan salah memilih meja') result", [orderId])).rows[0].result;
  assert.equal(requested.refund_status, 'REQUESTED');
  assert.equal(requested.provider_order_id, 'MID-NG-REFUND');
  await db.exec('reset role');
  assert.equal((await db.query('select status from customer_order where id=$1', [orderId])).rows[0].status, 'CANCELLED');
  assert.equal((await db.query('select actor_uid from order_status_history where order_id=$1', [orderId])).rows[0].actor_uid, uid.CASHIER);
  await login(uid.CASHIER);
  const duplicate = (await db.query("select request_order_refund($1,'CANCELLED','Tidak membuat refund kedua') result", [orderId])).rows[0].result;
  assert.equal(duplicate.id, requested.id);

  await login(null, 'service_role');
  await db.query("select update_refund_result($1,'PROCESSING',null,'{}')", [requested.id]);
  const done = (await db.query("select update_refund_result($1,'REFUNDED','MID-REF-1','{\"status_code\":\"200\"}') result", [requested.id])).rows[0].result;
  assert.equal(done.refund_status, 'REFUNDED');
  assert.equal(done.provider_reference, 'MID-REF-1');
  assert.equal((await db.query('select status from payment_attempt where order_id=$1', [orderId])).rows[0].status, 'REFUNDED');
});

test('unpaid orders need no refund, cash payments require manual refund, and customers cannot cancel', async () => {
  const unpaid = await order('AWAITING_PAYMENT', 'NG-UNPAID');
  const cash = await order('PAID', 'NG-CASH', true);
  await login(uid.CUSTOMER);
  await assert.rejects(db.query("select request_order_refund($1,'AWAITING_PAYMENT','Batal dari pelanggan')", [unpaid]), /kasir atau admin aktif/);
  await login(uid.CASHIER);
  assert.equal((await db.query("select request_order_refund($1,'AWAITING_PAYMENT','Pelanggan tidak jadi pesan') result", [unpaid])).rows[0].result.refund_status, 'NOT_REQUIRED');
  assert.equal((await db.query("select request_order_refund($1,'PAID','Kembalikan pembayaran tunai') result", [cash])).rows[0].result.refund_status, 'MANUAL_REQUIRED');
});
