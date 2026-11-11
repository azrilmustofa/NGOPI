import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

function load(name, globals = {}) {
  const module = { exports:{} };
  runInNewContext(ts.transpileModule(readFileSync(new URL(`../src/lib/${name}.ts`, import.meta.url), 'utf8'), {
    compilerOptions:{ module:ts.ModuleKind.CommonJS, target:ts.ScriptTarget.ES2022 },
  }).outputText, { module, exports:module.exports, Date, Error, ...globals });
  return module.exports;
}
const { isPublicSupabaseKey } = load('publicKey', { atob });
const { dayAfter, adminError } = load('admin');

test('browser config accepts public keys and rejects privileged or malformed keys', () => {
  const jwt = (payload) => `header.${Buffer.from(JSON.stringify(payload)).toString('base64url')}.signature`;
  assert.equal(isPublicSupabaseKey('sb_publishable_example123'), true);
  assert.equal(isPublicSupabaseKey(jwt({ role:'anon' })), true);
  for (const key of ['sb_secret_example', jwt({ role:'service_role' }), jwt({ role:'authenticated' }), 'bad', 'a.invalid.c', '']) {
    assert.equal(isPublicSupabaseKey(key), false);
  }
});
test('audit date end is exclusive across leap days and year changes', () => {
  assert.equal(dayAfter('2024-02-28'), '2024-02-29');
  assert.equal(dayAfter('2024-02-29'), '2024-03-01');
  assert.equal(dayAfter('2026-12-31'), '2027-01-01');
});
test('missing backend features show an actionable error instead of implementation details', () => {
  assert.match(adminError({ code:'PGRST202' }, 'fallback'), /belum diaktifkan/);
  assert.equal(adminError(new Error('Offline'), 'fallback'), 'Offline');
  assert.equal(adminError(null, 'fallback'), 'fallback');
});
