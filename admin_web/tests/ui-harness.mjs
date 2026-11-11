import assert from 'node:assert/strict';
import { existsSync, readFileSync, mkdirSync, writeFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { runInNewContext } from 'node:vm';
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import ts from 'typescript';

const require = createRequire(import.meta.url);

export function pageHarness(file, respond, globals = {}) {
  const states = [], effects = [], pending = [], requests = [], cache = new Map();
  let stateIndex = 0, effectIndex = 0;
  const hooks = { ...React,
    useRef(initial) { return hooks.useState({ current: initial })[0]; },
    useState(initial) {
      const index = stateIndex++;
      if (!(index in states)) states[index] = typeof initial === 'function' ? initial() : initial;
      return [states[index], (next) => { states[index] = typeof next === 'function' ? next(states[index]) : next; }];
    },
    useEffect(fn, deps) {
      const index = effectIndex++;
      if (!effects[index] || deps.some((value, i) => !Object.is(value, effects[index].deps[i]))) {
        effects[index]?.cleanup?.();
        effects[index] = { deps };
        pending.push(() => { effects[index].cleanup = fn(); });
      }
    },
  };
  const answer = (request) => { requests.push(request); return respond(request); };
  const supabase = {
    channel() {
      return { on() { return this; }, subscribe() { return this; } };
    },
    removeChannel() {},
    from(table) {
      const request = { table, operations: [] };
      const query = { then: (resolve, reject) => Promise.resolve(answer(request)).then(resolve, reject) };
      for (const method of ['select', 'order', 'eq', 'gte', 'lt', 'ilike', 'range', 'single', 'maybeSingle', 'update', 'upsert']) {
        query[method] = (...args) => { request.operations.push([method, ...args]); return query; };
      }
      return query;
    },
    rpc: (rpc, params) => Promise.resolve(answer({ rpc, params })),
    functions: { invoke: (name, options) => Promise.resolve(answer({ fn: name, ...options })) },
  };
  const load = (url) => {
    if (cache.has(url.href)) return cache.get(url.href);
    const module = { exports: {} };
    const source = readFileSync(url, 'utf8').replaceAll('import.meta.env', '({})');
    const compiled = ts.transpileModule(source, { compilerOptions: {
      module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.React, target: ts.ScriptTarget.ES2022, esModuleInterop: true,
    } }).outputText;
    runInNewContext(compiled, {
      module, exports: module.exports, Error, Date, Intl, URL, Blob, setTimeout, clearTimeout,
      localStorage: { getItem: () => null }, window: { location: { reload() {} } },
      confirm: () => true, alert() {}, Response,
      require(name) {
        if (name === 'react') return hooks;
        if (name.endsWith('/lib/supabase')) return { supabase,
          formatRupiah: (n) => new Intl.NumberFormat('id-ID', { style:'currency', currency:'IDR', maximumFractionDigits:0 }).format(n),
          DEFAULT_SUPABASE_URL:'https://example.supabase.co', DEFAULT_SUPABASE_KEY:'sb_publishable_test',
          updateSupabaseConfig() {}, resetSupabaseConfig() {},
        };
        if (name.endsWith('/context/AuthContext')) return { useAuth: () => ({ user:{ id:'admin' }, staffUser:{ name:'Admin pengujian', role:'ADMIN' }, signOut() {} }) };
        if (name.endsWith('/ReceiptTemplates') && !file.includes('ReceiptTemplates')) return { ReceiptTemplates: () => React.createElement('p', null, 'Editor template struk') };
        if (!name.startsWith('.')) return require(name);
        const dependency = ['.ts', '.tsx'].map((extension) => new URL(name + extension, url)).find((path) => existsSync(path));
        assert.ok(dependency, `Missing dependency ${name}`);
        return load(dependency);
      }, ...globals,
    }, { filename: url.pathname });
    cache.set(url.href, module.exports);
    return module.exports;
  };
  const name = file.split('/').at(-1).replace('.tsx', '');
  const Component = load(new URL('../src/' + file, import.meta.url))[name];
  const render = (props = {}) => { stateIndex = 0; effectIndex = 0; return Component(props); };
  return { requests, render,
    async settle() {
      await new Promise(setImmediate);
      let tree = render();
      while (pending.length) {
        pending.splice(0).forEach((run) => run());
        await new Promise(setImmediate);
        tree = render();
      }
      return tree;
    },
    cleanup: () => effects.forEach((effect) => effect.cleanup?.()),
  };
}

export function find(tree, predicate) {
  if (tree == null || typeof tree !== 'object') return null;
  if (Array.isArray(tree)) return tree.map((node) => find(node, predicate)).find(Boolean) || null;
  if (predicate(tree)) return tree;
  return find(React.Children.toArray(tree.props?.children), predicate);
}
export const byId = (tree, id) => {
  const node = find(tree, (node) => node.props?.id === id);
  assert.ok(node, `Missing #${id}`);
  return node;
};
export const html = (tree) => renderToStaticMarkup(tree);

export function preview(name, tree) {
  const folder = process.env.ADMIN_UI_PREVIEW_DIR;
  if (!folder) return;
  mkdirSync(folder, { recursive: true });
  const nav = pageHarness('components/Sidebar.tsx', () => ({})).render({ currentTab: name, onSelectTab() {} });
  const content = React.createElement('div', { className:'flex min-h-screen flex-col bg-stone-100 md:flex-row' },
    nav, React.createElement('main', { className:'min-w-0 flex-1' },
      React.createElement('p', { className:'bg-white p-4 text-sm text-stone-700' }, 'Pratinjau pengujian · Data contoh'), tree));
  writeFileSync(folder + '/' + name + '.html', '<!doctype html><html lang="id"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><link rel="stylesheet" href="/style.css"><title>Pratinjau Kopi Inggil</title><body>' + html(content) + '</body></html>');
}
