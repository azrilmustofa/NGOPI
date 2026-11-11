import assert from 'node:assert/strict';
import test from 'node:test';
import { byId, find, html, pageHarness, preview } from './ui-harness.mjs';

const event = { preventDefault() {} };
const report = { summary: { revenue_rp:55000, transactions:2, subtotal_rp:50000, discount_rp:1000,
  tax_rp:5000, service_charge_rp:0, cancelled:1, awaiting_payment:1 },
  daily:[{ day:'2026-11-09', transactions:2, revenue_rp:55000 }],
  methods:[{ method:'QRIS', transactions:2, revenue_rp:55000 }],
  products:[{ menu_item_id:'coffee', name:'Kopi Gula Aren', quantity:3, revenue_rp:50000 }] };
const form = (tree) => find(tree, (node) => node.type === 'form');
const button = (tree, text) => find(tree, (node) => node.type === 'button' && html(node).includes(text));

test('report loads real results, applies dates, and exports the loaded period rather than an unsaved draft', async () => {
  let download, blob;
  const page = pageHarness('pages/ReportsPage.tsx', () => ({ data:report, error:null }), {
    URL: { createObjectURL(value) { blob = value; return 'blob:test'; }, revokeObjectURL() {} },
    document: { body:{ appendChild() {} }, createElement: () => { download = { click() {}, remove() {} }; return download; } },
    setTimeout: (fn) => fn(),
  });
  assert.ok(html(page.render()).includes('Memuat laporan penjualan'));
  let tree = await page.settle();
  assert.ok(html(tree).includes('Kopi Gula Aren'));
  assert.ok(html(tree).includes('55.000'));
  byId(tree, 'report-from').props.onChange({ target:{ value:'2026-11-01' } });
  byId(tree, 'report-until').props.onChange({ target:{ value:'2026-11-09' } });
  form(page.render()).props.onSubmit(event);
  tree = await page.settle();
  assert.equal(page.requests.at(-1).params.p_from, '2026-11-01');
  assert.equal(page.requests.at(-1).params.p_until, '2026-11-09');
  preview('reports', tree);
  byId(tree, 'report-from').props.onChange({ target:{ value:'2026-10-01' } });
  button(page.render(), 'Unduh CSV').props.onClick();
  assert.equal(download.download, 'laporan-kopi-inggil-2026-11-01-2026-11-09.csv');
  assert.ok((await blob.text()).includes('"QRIS","2","55000"'));
  page.cleanup();
});

test('report errors do not render a misleading zero revenue or enable CSV', async () => {
  const page = pageHarness('pages/ReportsPage.tsx', () => ({ data:null, error:{ code:'PGRST202' } }));
  const tree = await page.settle();
  assert.ok(html(tree).includes('belum diaktifkan di server'));
  assert.ok(!html(tree).includes('Omzet pesanan lunas'));
  assert.equal(button(tree, 'Unduh CSV').props.disabled, true);
  page.cleanup();
});

test('report rejects reversed dates without sending a query', async () => {
  const page = pageHarness('pages/ReportsPage.tsx', () => ({ data:report, error:null }));
  const tree = await page.settle();
  byId(tree, 'report-from').props.onChange({ target:{ value:'2026-12-01' } });
  byId(tree, 'report-until').props.onChange({ target:{ value:'2026-11-01' } });
  form(page.render()).props.onSubmit(event);
  assert.ok(html(page.render()).includes('maksimal 367 hari'));
  assert.equal(page.requests.length, 1);
  page.cleanup();
});

test('audit filters on the server, paginates, and displays before/after values safely', async () => {
  const entry = { id:1, actor_uid:'admin', actor_name:'Admin contoh', action:'UPDATE', table_name:'menu_item', record_id:'coffee',
    created_at:'2026-11-09T03:30:00Z', old_data:{ base_price_rp:18000 }, new_data:{ base_price_rp:20000, name:'<script>alert(1)</script>' } };
  const page = pageHarness('pages/AuditPage.tsx', () => ({ data:[entry], count:51, error:null }));
  let tree = await page.settle();
  assert.ok(html(tree).includes('18000'));
  assert.ok(html(tree).includes('&lt;script&gt;'));
  assert.ok(!html(tree).includes('<script>'));
  preview('audit', tree);
  button(tree, 'Berikutnya').props.onClick();
  tree = await page.settle();
  assert.ok(page.requests.at(-1).operations.some((row) => row[0] === 'range' && row[1] === 50 && row[2] === 99));
  byId(tree, 'audit-from').props.onChange({ target:{ value:'2026-11-01' } });
  byId(tree, 'audit-until').props.onChange({ target:{ value:'2026-11-09' } });
  byId(tree, 'audit-table').props.onChange({ target:{ value:'menu_item' } });
  byId(tree, 'audit-actor').props.onChange({ target:{ value:'Admin' } });
  form(page.render()).props.onSubmit(event);
  await page.settle();
  const ops = page.requests.at(-1).operations;
  assert.ok(ops.some((row) => row[0] === 'range' && row[1] === 0));
  assert.ok(ops.some((row) => row[0] === 'eq' && row[1] === 'table_name' && row[2] === 'menu_item'));
  assert.ok(ops.some((row) => row[0] === 'ilike' && row[2] === '%Admin%'));
  assert.ok(ops.some((row) => row[0] === 'lt' && row[2] === '2026-11-10T00:00:00+07:00'));
  page.cleanup();
});

const templates = ['PAID','COMPLETED'].map((id) => ({ id, title:'NOTA TRANSAKSI', header:'KOPI INGGIL', footer:'Terima kasih.', updated_at:'2026-11-09T00:00:00Z' }));
test('template edits appear in preview and persist only after server confirmation', async () => {
  const page = pageHarness('components/ReceiptTemplates.tsx', (request) => {
    const update = request.operations.find((row) => row[0] === 'update');
    return { data:update ? { ...templates[0], ...update[1] } : templates, error:null };
  });
  let tree = await page.settle();
  byId(tree, 'header-PAID').props.onChange({ target:{ value:'  Kopi Inggil Cabang Utama  ' } });
  tree = page.render();
  assert.ok(html(tree).includes('Kopi Inggil Cabang Utama'));
  preview('settings', tree);
  await form(tree).props.onSubmit(event);
  tree = await page.settle();
  assert.ok(html(tree).includes('berhasil disimpan'));
  const update = page.requests.at(-1).operations.find((row) => row[0] === 'update')[1];
  assert.equal(update.header, 'Kopi Inggil Cabang Utama');
  assert.ok(!('updated_at' in update));
  page.cleanup();
});

test('failed template save retains draft and never claims success', async () => {
  const page = pageHarness('components/ReceiptTemplates.tsx', (request) => request.operations.some((row) => row[0] === 'update')
    ? { data:null, error:new Error('Akses ditolak') } : { data:templates, error:null });
  let tree = await page.settle();
  byId(tree, 'footer-PAID').props.onChange({ target:{ value:'Draft penutup baru' } });
  await form(page.render()).props.onSubmit(event);
  tree = await page.settle();
  assert.ok(html(tree).includes('Akses ditolak'));
  assert.ok(html(tree).includes('Draft penutup baru'));
  assert.ok(!html(tree).includes('berhasil disimpan'));
  page.cleanup();
});

test('fee load errors prevent editing invented defaults', async () => {
  const page = pageHarness('pages/SettingsPage.tsx', () => ({ data:null, error:new Error('Offline') }));
  const tree = await page.settle();
  assert.ok(html(tree).includes('Offline'));
  assert.equal(find(tree, (node) => node.props?.id === 'tax-percentage'), null);
  page.cleanup();
});

test('fees validate bounds and confirm the saved server values', async () => {
  const fees = { id:'default', tax_percentage:10, service_charge_percentage:0 };
  const page = pageHarness('pages/SettingsPage.tsx', (request) => ({ data:request.operations.find((row) => row[0] === 'upsert')?.[1] || fees, error:null }));
  let tree = await page.settle();
  byId(tree, 'tax-percentage').props.onChange({ target:{ value:'51' } });
  await form(page.render()).props.onSubmit(event);
  assert.equal(page.requests.length, 1);
  byId(page.render(), 'tax-percentage').props.onChange({ target:{ value:'11.25' } });
  await form(page.render()).props.onSubmit(event);
  tree = await page.settle();
  assert.ok(html(tree).includes('Pengaturan biaya berhasil disimpan'));
  assert.equal(page.requests.at(-1).operations.find((row) => row[0] === 'upsert')[1].tax_percentage, 11.25);
  page.cleanup();
});

test('staff access changes require a confirmed server row and cannot disable the current admin', async () => {
  const staff = [{ uid:'admin', name:'Admin', role:'ADMIN', is_active:true }, { uid:'cashier', name:'Kasir', role:'CASHIER', is_active:true }];
  const page = pageHarness('pages/StaffPage.tsx', (request) => {
    const update = request.operations.find((row) => row[0] === 'update');
    return { data:update ? { ...staff[1], ...update[1] } : staff, error:null };
  });
  let tree = await page.settle();
  const ownRole = find(tree, (node) => node.props?.['aria-label'] === 'Peran Admin');
  assert.equal(ownRole.props.disabled, true);
  const otherRole = find(tree, (node) => node.props?.['aria-label'] === 'Peran Kasir');
  await otherRole.props.onChange({ target:{ value:'BARISTA' } });
  await new Promise(setImmediate);
  tree = page.render();
  assert.equal(find(tree, (node) => node.props?.['aria-label'] === 'Peran Kasir').props.value, 'BARISTA');
  assert.ok(page.requests.at(-1).operations.some((row) => row[0] === 'single'));
  page.cleanup();
});
