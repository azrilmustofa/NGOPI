import type { SalesReport } from '../types';

export function jakartaDate(date = new Date()): string {
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Jakarta', year: 'numeric', month: '2-digit', day: '2-digit',
  }).format(date);
}

export function reportCsv(report: SalesReport, from: string, until: string): string {
  const rows: (string | number)[][] = [
    ['Laporan Kopi Inggil (WIB)', from, until],
    ['Metrik', 'Nilai'],
    ['Omzet', report.summary.revenue_rp],
    ['Transaksi lunas', report.summary.transactions],
    ['Subtotal setelah diskon', report.summary.subtotal_rp],
    ['Diskon', report.summary.discount_rp],
    ['Pajak', report.summary.tax_rp],
    ['Service charge', report.summary.service_charge_rp],
    ['Pesanan dibatalkan', report.summary.cancelled],
    ['Menunggu pembayaran', report.summary.awaiting_payment],
    [], ['Tanggal (WIB)', 'Transaksi', 'Omzet'],
    ...report.daily.map((row) => [row.day, row.transactions, row.revenue_rp]),
    [], ['Metode pembayaran', 'Transaksi', 'Omzet'],
    ...report.methods.map((row) => [row.method, row.transactions, row.revenue_rp]),
    [], ['Menu', 'Jumlah terjual', 'Penjualan menu'],
    ...report.products.map((row) => [row.name, row.quantity, row.revenue_rp]),
  ];
  return '\uFEFF' + rows.map((row) => row.map((value) => {
    const text = String(value);
    const safe = typeof value === 'string' && /^[\s]*[=+@-]/.test(text) ? "'" + text : text;
    return '"' + safe.replaceAll('"', '""') + '"';
  }).join(',')).join('\r\n');
}
