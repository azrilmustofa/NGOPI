import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { after, test } from 'node:test';
import { runInNewContext } from 'node:vm';
import { PGlite } from '@electric-sql/pglite';
import ts from 'typescript';

const db = new PGlite();
after(() => db.close());
const customer = '00000000-0000-0000-0000-000000000001';
const customer2 = '00000000-0000-0000-0000-000000000002';
const cashier = '00000000-0000-0000-0000-000000000003';
const barista = '00000000-0000-0000-0000-000000000004';

await db.exec(`
  create role anon; create role authenticated; create role service_role bypassrls;
  create schema auth;
  create table auth.users (id uuid primary key);
  insert into auth.users values ('${customer}'),('${customer2}'),('${cashier}'),('${barista}');
  create function auth.uid() returns uuid language sql stable as
    $$ select nullif(current_setting('request.jwt.claim.sub', true), '')::uuid $$;
  grant usage on schema public, auth to anon, authenticated, service_role;
  create type order_status as enum ('AWAITING_PAYMENT','PAID','ACCEPTED','PREPARING','READY','COMPLETED','CANCELLED');
  create table staff_user (uid uuid primary key, role text, is_active boolean);
  insert into staff_user values ('${cashier}','CASHIER',true),('${barista}','BARISTA',true);
  create table customer_order (id uuid primary key default gen_random_uuid(), public_id text,
    owner_uid uuid references auth.users, status order_status not null);
  grant all on staff_user, customer_order to public, anon, authenticated, service_role;
`);
await db.exec(readFileSync(new URL('../../supabase/push_notifications.sql', import.meta.url), 'utf8'));
await db.exec(readFileSync(new URL('../../supabase/push_notifications.sql', import.meta.url), 'utf8'));

async function login(uid, role = 'authenticated') {
  await db.exec('reset role');
  await db.query("select set_config('request.jwt.claim.sub', $1, false)", [uid ?? '']);
  await db.exec(`set role ${role}`);
}

test('tokens are private, follow the current account, and can be removed by that account', async () => {
  const token = 'firebase-token-long-enough-for-validation';
  await login(customer);
  await db.query('select register_push_token($1)', [token]);
  await assert.rejects(db.query('select * from push_device'), /permission denied/);
  await login(customer2);
  await db.query('select register_push_token($1)', [token]);
  await login(null, 'service_role');
  assert.equal((await db.query('select user_id from push_device where token=$1', [token])).rows[0].user_id, customer2);
  await login(customer2);
  await db.query('select unregister_push_token($1)', [token]);
  await login(null, 'service_role');
  assert.equal((await db.query('select count(*)::int n from push_device')).rows[0].n, 0);
});

test('paid orders notify the customer and barista; assisted cashier orders do not notify the cashier as customer', async () => {
  await login(null, 'service_role');
  const normal = (await db.query("insert into customer_order(public_id,owner_uid,status) values ('NG-PUSH',$1,'AWAITING_PAYMENT') returning id", [customer])).rows[0].id;
  await db.query("update customer_order set status='PAID' where id=$1", [normal]);
  assert.deepEqual(
    (await db.query('select audience,status,user_id from push_event where order_id=$1 order by audience', [normal])).rows,
    [
      { audience: 'CUSTOMER', status: 'PAID', user_id: customer },
      { audience: 'STAFF', status: 'PAID', user_id: null },
    ],
  );
  const assisted = (await db.query("insert into customer_order(public_id,owner_uid,status) values ('NG-ASSIST',$1,'AWAITING_PAYMENT') returning id", [cashier])).rows[0].id;
  await db.query("update customer_order set status='PAID' where id=$1", [assisted]);
  assert.deepEqual((await db.query('select audience from push_event where order_id=$1', [assisted])).rows, [{ audience: 'STAFF' }]);
  const event = (await db.query("select id from push_event where order_id=$1 and audience='CUSTOMER'", [normal])).rows[0].id;
  assert.equal((await db.query('select claim_push_event($1) event', [event])).rows[0].event.status, 'PAID');
  assert.equal((await db.query('select claim_push_event($1) event', [event])).rows[0].event, null);
});

function edgeFunction(event) {
  let handler;
  const requests = [];
  const completed = [];
  const client = {
    async rpc(name, params) {
      if (name === 'claim_push_event') return { data: event, error: null };
      completed.push(params);
      return { data: null, error: null };
    },
    from(table) {
      const state = { table };
      const query = {
        select() { return query; }, eq(column, value) { state[column] = value; return query; },
        in(column, value) { state[column] = value; return query; }, delete() { state.deleting = true; return query; },
        maybeSingle() { state.single = true; return query; },
        then(resolve, reject) {
          let result;
          if (table === 'customer_order') result = { data: { public_id: 'NG-PUSH' }, error: null };
          else if (table === 'staff_user') result = { data: [{ uid: barista }], error: null };
          else result = { data: state.deleting ? null : [{ token: 'device-token' }], error: null };
          return Promise.resolve(result).then(resolve, reject);
        },
      };
      return query;
    },
  };
  const source = readFileSync(new URL('../../supabase/functions/order-push/index.ts', import.meta.url), 'utf8');
  runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText, {
    exports: {},
    require: (name) => name.includes('supabase-js')
      ? { createClient: () => client }
      : { JWT: class { async getAccessToken() { return { token: 'access-token' }; } } },
    Deno: {
      env: { get: (name) => ({ SUPABASE_URL: 'https://example.supabase.co', SUPABASE_SERVICE_ROLE_KEY: 'secret',
        FIREBASE_PROJECT_ID: 'pttngopi', FIREBASE_CLIENT_EMAIL: 'firebase@example.test', FIREBASE_PRIVATE_KEY: 'key' })[name] },
      serve: (fn) => { handler = fn; },
    },
    Response, Request, URL, console,
    fetch: async (url, options) => {
      requests.push({ url: String(url), body: JSON.parse(options.body) });
      return new Response('{"name":"message-id"}', { status: 200 });
    },
  });
  return { handler, requests, completed };
}

test('edge function sends an authenticated high-priority FCM message for the claimed event', async () => {
  const endpoint = edgeFunction({ id: 'event', order_id: 'order', user_id: customer, audience: 'CUSTOMER', status: 'READY' });
  const response = await endpoint.handler(new Request('https://example.test/order-push', {
    method: 'POST', body: JSON.stringify({ record: { id: 'event' } }),
  }));
  assert.equal(response.status, 200);
  assert.equal(endpoint.requests[0].url, 'https://fcm.googleapis.com/v1/projects/pttngopi/messages:send');
  assert.equal(endpoint.requests[0].body.message.android.priority, 'HIGH');
  assert.deepEqual(endpoint.requests[0].body.message.data, {
    title: 'Pesanan siap diantar', body: 'Pesanan #NG-PUSH sudah siap.', audience: 'CUSTOMER',
    order_public_id: 'NG-PUSH', status: 'READY',
  });
  assert.equal(endpoint.completed.length, 1);
});
