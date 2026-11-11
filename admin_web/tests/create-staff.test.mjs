import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

function endpoint({ role = 'ADMIN', active = true, profileError = null, cleanupError = null, insertThrows = false } = {}) {
  let handler;
  const calls = [];
  const clients = [];
  const admin = {
    auth: {
      getUser: async () => ({ data: { user: { id: 'caller' } }, error: null }),
      admin: {
        createUser: async (payload) => { calls.push(['create', payload]); return { data: { user: { id: 'new-staff' } }, error: null }; },
        deleteUser: async (uid) => { calls.push(['delete', uid]); return { error: cleanupError }; },
      },
    },
    from: () => ({
      select: () => ({ eq: () => ({ maybeSingle: async () => ({ data: { role, is_active: active }, error: null }) }) }),
      insert: async (payload) => { calls.push(['insert', payload]); if (insertThrows) throw new Error('Network failed'); return { error: profileError }; },
    }),
  };
  const source = readFileSync(new URL('../supabase/functions/admin-create-staff/index.ts', import.meta.url), 'utf8');
  runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText, {
    exports: {}, require: () => ({ createClient: (_url, _key, options) => { clients.push(options); return admin; } }),
    Deno: { env: { get: () => 'test' }, serve: (fn) => { handler = fn; } },
    Response, console,
  });
  return { handler, calls, clients };
}
const body = { name: 'Barista', email: 'barista@example.com', password: 'password-test', role: 'BARISTA' };
const request = (payload = body, auth = true) => new Request('https://example.test/admin-create-staff', {
  method: 'POST', headers: auth ? { Authorization: 'Bearer test' } : {}, body: JSON.stringify(payload),
});

test('staff creation rejects unauthenticated, non-admin, and inactive callers', async () => {
  const anonymous = endpoint();
  assert.equal((await anonymous.handler(request(body, false))).status, 401);
  for (const options of [{ role:'CASHIER' }, { role:'BARISTA' }, { active:false }]) {
    const api = endpoint(options);
    assert.equal((await api.handler(request())).status, 403);
    assert.equal(api.calls.length, 0);
  }
});

test('admin creates auth user and staff profile without trusting user metadata roles', async () => {
  const api = endpoint();
  assert.equal((await api.handler(request())).status, 201);
  assert.equal(api.calls[0][1].email_confirm, true);
  assert.equal(api.calls[0][1].user_metadata.role, undefined);
  assert.equal(api.calls[1][1].role, 'BARISTA');
  assert.equal(api.clients.at(-1).global.headers.Authorization, 'Bearer test');
});

test('invalid roles and short passwords are rejected before creating users', async () => {
  const api = endpoint();
  assert.equal((await api.handler(request({ ...body, role:'OWNER' }))).status, 400);
  assert.equal((await api.handler(request({ ...body, password:'short' }))).status, 400);
  assert.equal(api.calls.length, 0);
});

test('owner can create a cashier using the production CASHIER enum', async () => {
  const api = endpoint({ role:'OWNER' });
  assert.equal((await api.handler(request({ ...body, role:'CASHIER' }))).status, 201);
  assert.equal(api.calls.at(-1)[1].role, 'CASHIER');
});

test('profile failure rolls back the new auth account and surfaces cleanup failure', async () => {
  for (const cleanupError of [null, { message:'Cleanup failed' }]) {
    const api = endpoint({ profileError: new Error('Insert failed'), cleanupError });
    const response = await api.handler(request());
    assert.equal(response.status, 500);
    assert.deepEqual(api.calls.at(-1), ['delete', 'new-staff']);
    const result = await response.json();
    assert.ok(result.error.includes(cleanupError ? 'hubungi pengelola' : 'dibatalkan'));
  }
});

test('a thrown network error while saving the profile also cleans up the auth account', async () => {
  const api = endpoint({ insertThrows:true });
  assert.equal((await api.handler(request())).status, 500);
  assert.deepEqual(api.calls.at(-1), ['delete', 'new-staff']);
});
