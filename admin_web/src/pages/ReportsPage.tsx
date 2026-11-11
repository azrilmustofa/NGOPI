import React, { useEffect, useState } from 'react';
import { Download } from 'lucide-react';
import { supabase, formatRupiah } from '../lib/supabase';
import { jakartaDate, reportCsv } from '../lib/reports';
import { adminError } from '../lib/admin';
import type { SalesReport } from '../types';

export const ReportsPage: React.FC = () => {
  const [until, setUntil] = useState(() => jakartaDate());
  const [from, setFrom] = useState(() => `${jakartaDate().slice(0, 7)}-01`);
  const [period, setPeriod] = useState({ from, until });
  const [report, setReport] = useState<SalesReport | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const { data, error } = await supabase.rpc('admin_sales_report', {
          p_from: period.from, p_until: period.until,
        });
        if (error) throw error;
        if (!data) throw new Error('Laporan tidak tersedia. Silakan coba lagi.');
        if (active) setReport(data as SalesReport);
      } catch (error) {
        if (active) setError(adminError(error, 'Gagal memuat laporan. Silakan coba lagi.'));
      } finally {
        if (active) setLoading(false);
      }
    };
    void load();
    return () => { active = false; };
  }, [period]);

  const applyPeriod = (event: React.FormEvent) => {
    event.preventDefault();
    const days = (Date.parse(until) - Date.parse(from)) / 86400000;
    if (!Number.isFinite(days) || days < 0 || days > 366) {
      setError('Pilih tanggal akhir setelah tanggal awal, maksimal 367 hari.');
      return;
    }
    setLoading(true); setReport(null); setError('');
    setPeriod({ from, until });
  };

  const exportReport = () => {
    if (!report || loading) return;
    const url = URL.createObjectURL(new Blob([reportCsv(report, period.from, period.until)], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = `laporan-kopi-inggil-${period.from}-${period.until}.csv`;
    document.body.appendChild(link);
    link.click();
    link.remove();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  return (
    <div className="admin-page space-y-6">
      <form onSubmit={applyPeriod} className="admin-panel flex flex-wrap items-end gap-4">
        <div><label htmlFor="report-from" className="admin-label">Dari tanggal (WIB)</label>
          <input id="report-from" className="admin-input" type="date" required value={from} onChange={(event) => setFrom(event.target.value)} /></div>
        <div><label htmlFor="report-until" className="admin-label">Sampai tanggal (WIB)</label>
          <input id="report-until" className="admin-input" type="date" required min={from} value={until} onChange={(event) => setUntil(event.target.value)} /></div>
        <button className="admin-primary" disabled={loading}>{loading ? 'Memuat...' : 'Tampilkan laporan'}</button>
        <button type="button" className="admin-button md:ml-auto" disabled={!report || loading} onClick={exportReport}>
          <Download className="h-4 w-4" aria-hidden="true" /> Unduh CSV</button>
      </form>
      {error && <p role="alert" className="admin-error">{error}</p>}
      {loading && <p role="status" className="text-stone-600">Memuat laporan penjualan...</p>}
      {report && !loading && <>
        <section className="admin-panel" aria-labelledby="report-summary">
          <div className="flex flex-wrap items-start justify-between gap-4">
            <div><h3 id="report-summary" className="text-sm font-semibold text-stone-600">Omzet pesanan lunas</h3>
              <p className="mt-1 text-3xl font-bold tracking-tight text-stone-900 break-words">{formatRupiah(report.summary.revenue_rp)}</p>
              <p className="mt-2 text-sm text-stone-600">{period.from} s.d. {period.until} · WIB</p></div>
            <p className="text-lg font-semibold text-stone-900">{report.summary.transactions} transaksi lunas</p>
          </div>
          <p className="mt-4 text-sm text-stone-600">Berdasarkan waktu pesanan dibuat dan status saat laporan dibuka. Omzet termasuk pajak dan biaya layanan. Pesanan batal dan belum dibayar tidak masuk omzet.</p>
          <dl className="mt-5 grid grid-cols-1 gap-x-8 gap-y-3 border-t border-stone-200 pt-5 sm:grid-cols-2 lg:grid-cols-3 text-sm">
            {[
              ['Subtotal setelah diskon', formatRupiah(report.summary.subtotal_rp)],
              ['Diskon diberikan', formatRupiah(report.summary.discount_rp)],
              ['Pajak', formatRupiah(report.summary.tax_rp)],
              ['Biaya layanan', formatRupiah(report.summary.service_charge_rp)],
              ['Pesanan dibatalkan', report.summary.cancelled],
              ['Menunggu pembayaran', report.summary.awaiting_payment],
            ].map(([label, value]) => <div key={label} className="flex justify-between gap-4"><dt className="text-stone-600">{label}</dt><dd className="font-semibold tabular-nums">{value}</dd></div>)}
          </dl>
        </section>
        {report.summary.transactions === 0 && <p className="admin-panel text-stone-600">Belum ada transaksi lunas pada periode ini. Pilih periode lain untuk melihat penjualan.</p>}
        <div className="grid gap-6 lg:grid-cols-2">
          <section className="admin-panel min-w-0"><h3 className="admin-heading">Penjualan per hari</h3>
            <div className="overflow-x-auto"><table className="admin-table"><caption className="sr-only">Jumlah transaksi dan omzet harian dalam WIB</caption>
              <thead><tr><th>Tanggal (WIB)</th><th className="text-right">Transaksi</th><th className="text-right">Omzet</th></tr></thead>
              <tbody>{report.daily.map((row) => <tr key={row.day}><td>{row.day}</td><td className="text-right">{row.transactions}</td><td className="text-right">{formatRupiah(row.revenue_rp)}</td></tr>)}</tbody>
            </table></div>{!report.daily.length && <p className="admin-empty">Belum ada penjualan harian.</p>}
          </section>
          <section className="admin-panel min-w-0"><h3 className="admin-heading">Metode pembayaran</h3>
            <div className="overflow-x-auto"><table className="admin-table"><caption className="sr-only">Transaksi lunas berdasarkan metode pembayaran</caption>
              <thead><tr><th>Metode</th><th className="text-right">Transaksi</th><th className="text-right">Omzet</th></tr></thead>
              <tbody>{report.methods.map((row) => <tr key={row.method}><td>{row.method === 'TIDAK_TERCATAT' ? 'Tidak tercatat' : row.method}</td><td className="text-right">{row.transactions}</td><td className="text-right">{formatRupiah(row.revenue_rp)}</td></tr>)}</tbody>
            </table></div>{!report.methods.length && <p className="admin-empty">Belum ada pembayaran.</p>}
            <p className="mt-3 text-sm text-stone-600">Setiap pesanan dihitung sekali dari percobaan pembayaran lunas terakhir.</p>
          </section>
        </div>
        <section className="admin-panel"><h3 className="admin-heading">Menu terlaris</h3>
          <div className="overflow-x-auto"><table className="admin-table"><caption className="sr-only">Menu diurutkan berdasarkan jumlah terjual</caption>
            <thead><tr><th>Menu</th><th className="text-right">Jumlah terjual</th><th className="text-right">Penjualan menu</th></tr></thead>
            <tbody>{report.products.map((row) => <tr key={`${row.menu_item_id}:${row.name}`}><td>{row.name}</td><td className="text-right">{row.quantity}</td><td className="text-right">{formatRupiah(row.revenue_rp)}</td></tr>)}</tbody>
          </table></div>{!report.products.length && <p className="admin-empty">Belum ada rincian menu terjual.</p>}
          <p className="mt-3 text-sm text-stone-600">Nama dan harga mengikuti catatan saat transaksi. Penjualan menu tidak termasuk pajak dan biaya layanan.</p>
        </section>
      </>}
    </div>
  );
};
