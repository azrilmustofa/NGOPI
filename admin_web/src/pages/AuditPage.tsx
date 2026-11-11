import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { adminError, dayAfter } from '../lib/admin';
import { jakartaDate } from '../lib/reports';
import type { AdminAuditEntry } from '../types';

const TABLES: Record<string, string> = {
  menu_item: 'Menu', menu_category: 'Kategori', cafe_table: 'Meja', staff_user: 'Staf',
  app_settings: 'Pajak & biaya', receipt_template: 'Template struk', table_qr: 'QR meja',
};
const ACTIONS: Record<string, string> = { INSERT: 'Tambah', UPDATE: 'Ubah', DELETE: 'Hapus' };
const FIELDS: Record<string, string> = {
  name: 'Nama', label: 'Label', role: 'Peran', is_active: 'Aktif', is_available: 'Tersedia',
  base_price_rp: 'Harga dasar (Rp)', tax_percentage: 'Pajak (%)', service_charge_percentage: 'Biaya layanan (%)',
  title: 'Judul', header: 'Header', footer: 'Penutup', qr_image_changed: 'Gambar QR berubah',
};
const PAGE_SIZE = 50;
const displayValue = (value: unknown) => value == null ? '—'
  : typeof value === 'boolean' ? value ? 'Ya' : 'Tidak'
  : typeof value === 'object' ? JSON.stringify(value, null, 2) : String(value);

export const AuditPage: React.FC = () => {
  const [from, setFrom] = useState(() => `${jakartaDate().slice(0, 7)}-01`);
  const [until, setUntil] = useState(() => jakartaDate());
  const [table, setTable] = useState('');
  const [action, setAction] = useState('');
  const [actor, setActor] = useState('');
  const [filters, setFilters] = useState({ from, until, table, action, actor });
  const [page, setPage] = useState(0);
  const [rows, setRows] = useState<AdminAuditEntry[]>([]);
  const [count, setCount] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        let query = supabase.from('admin_audit_log').select('*', { count: 'exact' })
          .gte('created_at', `${filters.from}T00:00:00+07:00`)
          .lt('created_at', `${dayAfter(filters.until)}T00:00:00+07:00`)
          .order('created_at', { ascending: false }).order('id', { ascending: false });
        if (filters.table) query = query.eq('table_name', filters.table);
        if (filters.action) query = query.eq('action', filters.action);
        if (filters.actor) query = query.ilike('actor_name', `%${filters.actor}%`);
        const { data, count, error } = await query.range(page * PAGE_SIZE, (page + 1) * PAGE_SIZE - 1);
        if (error) throw error;
        if (active) { setRows((data || []) as AdminAuditEntry[]); setCount(count || 0); }
      } catch (error) {
        if (active) { setError(adminError(error, 'Gagal memuat log audit. Silakan coba lagi.')); setCount(0); }
      } finally { if (active) setLoading(false); }
    };
    void load();
    return () => { active = false; };
  }, [filters, page]);

  const applyFilters = (event: React.FormEvent) => {
    event.preventDefault();
    if (!from || !until || from > until) { setError('Periksa tanggal awal dan akhir.'); return; }
    setLoading(true); setRows([]); setError('');
    setPage(0);
    setFilters({ from, until, table, action, actor: actor.trim() });
  };

  return <div className="admin-page space-y-6">
    <form onSubmit={applyFilters} className="admin-panel grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
      <div><label htmlFor="audit-from" className="admin-label">Dari tanggal (WIB)</label>
        <input id="audit-from" required type="date" className="admin-input" value={from} onChange={(event) => setFrom(event.target.value)} /></div>
      <div><label htmlFor="audit-until" className="admin-label">Sampai tanggal (WIB)</label>
        <input id="audit-until" required type="date" min={from} className="admin-input" value={until} onChange={(event) => setUntil(event.target.value)} /></div>
      <div><label htmlFor="audit-table" className="admin-label">Bagian yang diubah</label>
        <select id="audit-table" className="admin-input" value={table} onChange={(event) => setTable(event.target.value)}><option value="">Semua bagian</option>
          {Object.entries(TABLES).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
      <div><label htmlFor="audit-action" className="admin-label">Tindakan</label>
        <select id="audit-action" className="admin-input" value={action} onChange={(event) => setAction(event.target.value)}><option value="">Semua tindakan</option>
          {Object.entries(ACTIONS).map(([value, label]) => <option key={value} value={value}>{label}</option>)}</select></div>
      <div><label htmlFor="audit-actor" className="admin-label">Nama pelaku</label>
        <input id="audit-actor" type="search" className="admin-input" value={actor} onChange={(event) => setActor(event.target.value)} placeholder="Cari nama staf" maxLength={100} /></div>
      <button className="admin-primary self-end" disabled={loading}>{loading ? 'Memuat...' : 'Terapkan filter'}</button>
    </form>
    {error && <p role="alert" className="admin-error">{error}</p>}
    <section className="admin-panel" aria-labelledby="audit-list-heading">
      <h3 id="audit-list-heading" className="admin-heading">Riwayat perubahan</h3>
      <p className="mb-4 text-sm text-stone-600">Aktivitas dicatat otomatis sejak fitur audit diaktifkan. Riwayat ini tidak dapat diubah atau dihapus lewat panel.</p>
      {loading ? <p role="status" className="admin-empty">Memuat log audit...</p>
        : rows.length === 0 ? <p className="admin-empty">Tidak ada aktivitas pada filter ini.</p>
        : <ol className="divide-y divide-stone-200">{rows.map((entry) => {
          const fields = [...new Set([...Object.keys(entry.old_data || {}), ...Object.keys(entry.new_data || {})])]
            .filter((field) => JSON.stringify(entry.old_data?.[field]) !== JSON.stringify(entry.new_data?.[field]));
          return <li key={entry.id} className="py-5 first:pt-0 last:pb-0">
            <div className="flex flex-wrap justify-between gap-2">
              <p className="font-semibold text-stone-900">{entry.actor_name} · {ACTIONS[entry.action] || entry.action} {TABLES[entry.table_name] || entry.table_name}</p>
              <time dateTime={entry.created_at} className="text-sm text-stone-600">{new Date(entry.created_at).toLocaleString('id-ID', { timeZone: 'Asia/Jakarta' })} WIB</time>
            </div>
            <p className="mt-1 break-all text-sm text-stone-600">ID data: {entry.record_id}</p>
            <p className="mt-1 break-all text-xs text-stone-600">ID pelaku: {entry.actor_uid || 'Sistem'}</p>
            <details className="mt-3"><summary className="cursor-pointer py-1 text-sm font-medium text-amber-900">Lihat perubahan</summary>
              <div className="mt-2 overflow-x-auto"><table className="admin-table table-fixed"><caption className="sr-only">Nilai sebelum dan sesudah perubahan</caption>
                <thead><tr><th>Data</th><th>Sebelum</th><th>Sesudah</th></tr></thead>
                <tbody>{fields.map((field) => <tr key={field}><th className="break-words font-medium">{FIELDS[field] || field}</th>
                  <td className="whitespace-pre-wrap break-words">{displayValue(entry.old_data?.[field])}</td>
                  <td className="whitespace-pre-wrap break-words">{displayValue(entry.new_data?.[field])}</td></tr>)}</tbody>
              </table></div>
            </details>
          </li>;
        })}</ol>}
      <div className="mt-5 flex flex-wrap items-center justify-between gap-3 border-t border-stone-200 pt-4">
        <p role="status" className="text-sm text-stone-600">{loading ? 'Memuat jumlah aktivitas...' : `${count} aktivitas · Halaman ${page + 1} dari ${Math.max(1, Math.ceil(count / PAGE_SIZE))}`}</p>
        <div className="flex gap-2"><button className="admin-button" disabled={loading || page === 0} onClick={() => { setLoading(true); setRows([]); setError(''); setPage((value) => value - 1); }}>Sebelumnya</button>
          <button className="admin-button" disabled={loading || (page + 1) * PAGE_SIZE >= count} onClick={() => { setLoading(true); setRows([]); setError(''); setPage((value) => value + 1); }}>Berikutnya</button></div>
      </div>
    </section>
  </div>;
};
