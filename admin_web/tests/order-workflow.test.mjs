import assert from 'node:assert/strict';
import { createHash, webcrypto } from 'node:crypto';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';
import { find, html, pageHarness } from './ui-harness.mjs';

test('admin actions follow every production stage and submit the displayed status to the server', async () => {
  let status = 'PAID';
  const page = pageHarness('pages/OrdersPage.tsx', (request) => {
    if (request.rpc) return { data: { status: request.params.p_status }, error: null };
    return { data: request.table === 'customer_order' ? [{ id: 'selected', status,
      public_id: 'NG-SELECTED', table_id: 'TBL-01', total_rp: 22000, created_at: '2026-11-09T00:00:00Z' }] : [], error: null };
  });
  let tree = await page.settle();
  for (const [label, next] of [['Terima Pesanan','ACCEPTED'], ['Mulai Buat','PREPARING'], ['Siap Disajikan','READY'], ['Selesaikan Order','COMPLETED']]) {
    const button = find(tree, (node) => node.type === 'button' && html(node).includes(label));
    assert.ok(button, `Missing ${label} at ${status}`);
    await button.props.onClick();
    const rpc = page.requests.filter((request) => request.rpc).at(-1);
    assert.equal(rpc.rpc, 'transition_order');
    assert.equal(rpc.params.p_order_id, 'selected');
    assert.equal(rpc.params.p_expected_status, status);
    assert.equal(rpc.params.p_status, next);
    status = next;
    tree = await page.settle();
  }
  page.cleanup();
});

function webhook(attemptStatus = 'PAID') {
  let handler;
  let orderError = true;
  const calls = [];
  const client = { from(table) {
    const call = { table };
    const query = {
      async then(resolve, reject) {
        calls.push(call);
        let result = { data: [], error: null };
        if (table === 'payment_attempt' && !call.update) {
          result.data = { id: 'attempt', order_id: 'order', amount_rp: 22000, status: attemptStatus };
        } else if (table === 'payment_attempt' && call.update) {
          result.data = [{ id: 'attempt' }];
        } else if (table === 'refund_request') {
          result.data = { id: 'refund', refund_key: 'REF-TEST' };
        } else if (table === 'customer_order') {
          result = orderError ? { data: null, error: { message: 'History temporarily unavailable' } }
            : { data: [{ id: 'order' }], error: null };
          orderError = false;
        }
        return Promise.resolve(result).then(resolve, reject);
      },
    };
    for (const method of ['select','eq','neq','not','in','update','upsert','maybeSingle']) {
      query[method] = (...args) => { if (method === 'update') call.update = args[0]; return query; };
    }
    return query;
  }, async rpc(name, params) {
    calls.push({ rpc: name, params });
    return { data: {}, error: null };
  } };
  const source = readFileSync(new URL('../../supabase/functions/midtrans-webhook/index.ts', import.meta.url), 'utf8');
  runInNewContext(ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } }).outputText, {
    exports: {}, require: () => ({ createClient: () => client }),
    Deno: { env: { get: () => 'test' }, serve: (fn) => { handler = fn; } },
    Response, TextEncoder, crypto: webcrypto, console: { error() {}, info() {} },
  });
  return { handler, calls };
}

test('signed payment retries recover an order update even after its payment attempt is already PAID', async () => {
  const endpoint = webhook();
  const body = { order_id: 'provider-order', status_code: '200', gross_amount: '22000.00', transaction_status: 'settlement',
    payment_type: 'qris', signature_key: createHash('sha512').update('provider-order20022000.00test').digest('hex') };
  const request = () => new Request('https://example.test/midtrans-webhook', { method: 'POST', body: JSON.stringify(body) });
  assert.equal((await endpoint.handler(request())).status, 500);
  assert.equal((await endpoint.handler(request())).status, 200);
  assert.equal(endpoint.calls.filter((call) => call.table === 'customer_order').length, 2);
  assert.equal(endpoint.calls.filter((call) => call.table === 'order_status_history').length, 0);
  body.signature_key = 'invalid';
  const before = endpoint.calls.length;
  assert.equal((await endpoint.handler(request())).status, 401);
  assert.equal(endpoint.calls.length, before);
});

test('signed refund notifications finalize the attempt and matching refund record', async () => {
  const endpoint = webhook('REFUND_PENDING');
  const body = { order_id: 'provider-order', status_code: '200', gross_amount: '22000.00',
    transaction_status: 'refund', refund_chargeback_id: 'MID-REF-1' };
  body.signature_key = createHash('sha512').update('provider-order20022000.00test').digest('hex');
  const response = await endpoint.handler(new Request('https://example.test/midtrans-webhook', {
    method: 'POST', body: JSON.stringify(body),
  }));
  assert.equal(response.status, 200);
  assert.equal(endpoint.calls.find((call) => call.table === 'payment_attempt' && call.update)?.update.status, 'REFUNDED');
  const finalized = endpoint.calls.find((call) => call.rpc === 'update_refund_result');
  assert.equal(finalized.params.p_status, 'REFUNDED');
  assert.equal(finalized.params.p_provider_reference, 'MID-REF-1');
});
