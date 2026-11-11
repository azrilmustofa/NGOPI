import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import test from 'node:test';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';

const module = { exports: {} };
runInNewContext(ts.transpileModule(readFileSync(new URL('../src/lib/reports.ts', import.meta.url), 'utf8'), {
  compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 },
}).outputText, { module, exports: module.exports, Intl, Date });
const { jakartaDate, reportCsv } = module.exports;

test('date uses the Jakarta calendar at the UTC day boundary', () => {
  assert.equal(jakartaDate(new Date('2026-11-08T16:59:59Z')), '2026-11-08');
  assert.equal(jakartaDate(new Date('2026-11-08T17:00:00Z')), '2026-11-09');
});

test('CSV includes complete report sections, escapes cells, and neutralizes formulas', () => {
  const report = { summary: { revenue_rp:22000, transactions:1, subtotal_rp:20000, discount_rp:0,
    tax_rp:2000, service_charge_rp:0, cancelled:0, awaiting_payment:0 },
    daily:[{ day:'2026-11-09', transactions:1, revenue_rp:22000 }],
    methods:[{ method:'QRIS', transactions:1, revenue_rp:22000 }],
    products:[{ name:'=HYPERLINK("x")', quantity:1, revenue_rp:20000 }] };
  const csv = reportCsv(report, '2026-11-09', '2026-11-09');
  assert.ok(csv.startsWith('\uFEFF'));
  assert.ok(csv.includes('"QRIS","1","22000"'));
  assert.ok(csv.includes('"\'=HYPERLINK(""x"")","1","20000"'));
  assert.ok(csv.includes('"2026-11-09","1","22000"'));
});
