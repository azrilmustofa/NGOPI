import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { runInNewContext } from 'node:vm';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ts from 'typescript';

function loadModule(file, dependencies, globals = {}) {
  const { outputText } = ts.transpileModule(readFileSync(new URL(file, import.meta.url), 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.React, esModuleInterop: true },
  });
  const module = { exports: {} };
  runInNewContext(outputText, {
    module, exports: module.exports,
    require: (name) => {
      assert.ok(name in dependencies, `Missing test dependency: ${name}`);
      return dependencies[name];
    },
    ...globals,
  }, { filename: file });
  return module.exports;
}

function authHarness(query) {
  const states = [];
  const timers = new Set();
  let cursor = 0;
  let effect;
  let cleanup;
  let callback;
  const hooks = {
    ...React,
    useState: (initial) => {
      const index = cursor++;
      if (!(index in states)) states[index] = initial;
      return [states[index], (next) => { states[index] = next; }];
    },
    useEffect: (fn) => { effect ??= fn; },
  };
  const { AuthProvider } = loadModule('../src/context/AuthContext.tsx', {
    react: hooks,
    '../lib/supabase': { supabase: {
      auth: { onAuthStateChange: (fn) => {
        callback = fn;
        return { data: { subscription: { unsubscribe() {} } } };
      } },
      from: () => ({ select: () => ({ eq: (_column, uid) => ({ maybeSingle: () => query(uid) }) }) }),
    } },
  }, {
    setTimeout: (fn) => { timers.add(fn); return fn; },
    clearTimeout: (fn) => timers.delete(fn),
  });
  const render = () => {
    cursor = 0;
    return AuthProvider({ children: null }).props.value;
  };
  render();
  cleanup = effect();
  return {
    render, cleanup,
    emit: (session, event = 'SIGNED_IN') => callback(event, session),
    flush: () => {
      const pending = [...timers];
      timers.clear();
      return Promise.all(pending.map((fn) => fn()));
    },
  };
}

const user = { id: 'admin-1' };
const admin = { uid: user.id, name: 'Admin', role: 'ADMIN', is_active: true };

function renderAccess(auth) {
  const dependencies = {
    react: React,
    './context/AuthContext': { AuthProvider: ({ children }) => children, useAuth: () => auth },
    './lib/supabase': { supabase: {} },
  };
  for (const name of ['DashboardPage', 'OrdersPage', 'MenuPage', 'PromosPage', 'TablesPage', 'StaffPage', 'SettingsPage', 'LoginPage', 'ReportsPage', 'AuditPage']) {
    dependencies[`./pages/${name}`] = {
      [name]: ({ accessError }) => React.createElement('p', null, accessError || name),
    };
  }
  for (const name of ['Header', 'Sidebar']) {
    dependencies[`./components/${name}`] = { [name]: () => null };
  }
  const { default: App } = loadModule('../src/App.tsx', dependencies);
  return renderToStaticMarkup(React.createElement(App));
}

for (const [name, state, expected] of [
  ['signed out', { user: null, staffUser: null }, 'LoginPage'],
  ['active admin', { user, staffUser: admin }, 'DashboardPage'],
  ['active owner', { user, staffUser: { ...admin, role: 'OWNER' } }, 'DashboardPage'],
  ['inactive owner', { user, staffUser: { ...admin, role: 'OWNER', is_active: false } }, 'Akses admin belum terverifikasi'],
  ['barista', { user, staffUser: { ...admin, role: 'BARISTA' } }, 'Akses admin belum terverifikasi'],
  ['cashier', { user, staffUser: { ...admin, role: 'CASHIER' } }, 'Akses admin belum terverifikasi'],
  ['inactive admin', { user, staffUser: { ...admin, is_active: false } }, 'Akses admin belum terverifikasi'],
  ['missing profile', { user, staffUser: null }, 'Akses admin belum terverifikasi'],
  ['another user profile', { user, staffUser: { ...admin, uid: 'other' } }, 'Akses admin belum terverifikasi'],
  ['invalid active flag', { user, staffUser: { ...admin, is_active: 'true' } }, 'Akses admin belum terverifikasi'],
  ['verification pending', { user, staffUser: admin, loading: true }, 'Memuat Kopi Inggil Admin'],
]) {
  test(`panel access: ${name}`, () => {
    const html = renderAccess({ loading: false, ...state });
    assert.ok(html.includes(expected));
    if (expected !== 'DashboardPage') assert.ok(!html.includes('DashboardPage'));
  });
}

test('restored session waits for the staff query before granting access', async () => {
  const auth = authHarness(async () => ({ data: admin, error: null }));
  assert.equal(auth.render().loading, true);
  auth.emit({ user }, 'INITIAL_SESSION');
  assert.equal(auth.render().loading, true);
  assert.equal(auth.render().staffUser, null);
  await auth.flush();
  assert.equal(auth.render().staffUser, admin);
  assert.equal(auth.render().loading, false);
  auth.cleanup();
});

for (const [name, query] of [
  ['missing profile', async () => ({ data: null, error: null })],
  ['query error', async () => ({ data: admin, error: new Error('RLS denied') })],
  ['network failure', async () => { throw new Error('Offline'); }],
  ['mismatched uid', async () => ({ data: { ...admin, uid: 'other' }, error: null })],
]) {
  test(`profile verification denies ${name}`, async () => {
    const auth = authHarness(query);
    auth.emit({ user }, 'INITIAL_SESSION');
    await auth.flush();
    assert.equal(auth.render().staffUser, null);
    assert.equal(auth.render().loading, false);
    assert.ok(!renderAccess(auth.render()).includes('DashboardPage'));
    auth.cleanup();
  });
}

test('sign-out and account changes invalidate pending profile responses', async () => {
  let resolve;
  const auth = authHarness(() => new Promise((done) => { resolve = done; }));
  auth.emit({ user });
  const firstRequest = auth.flush();
  const resolveFirst = resolve;
  auth.emit({ user: { id: 'cashier-1' } });
  const secondRequest = auth.flush();
  resolveFirst({ data: admin, error: null });
  await firstRequest;
  assert.equal(auth.render().staffUser, null);
  assert.equal(auth.render().loading, true);
  auth.emit(null, 'SIGNED_OUT');
  resolve({ data: { ...admin, uid: 'cashier-1' }, error: null });
  await secondRequest;
  assert.equal(auth.render().user, null);
  assert.equal(auth.render().staffUser, null);
  assert.equal(auth.render().loading, false);
  auth.cleanup();
});

test('session refresh rechecks deactivated admin access', async () => {
  let profile = admin;
  const auth = authHarness(async () => ({ data: profile, error: null }));
  auth.emit({ user });
  await auth.flush();
  assert.ok(renderAccess(auth.render()).includes('DashboardPage'));
  profile = { ...admin, is_active: false };
  auth.emit({ user }, 'TOKEN_REFRESHED');
  assert.equal(auth.render().staffUser, null);
  await auth.flush();
  assert.ok(!renderAccess(auth.render()).includes('DashboardPage'));
  auth.cleanup();
});
