import React, { useEffect, useState } from 'react';
import {
  TrendingUp,
  ShoppingBag,
  Clock,
  Coffee,
  CheckCircle2,
  AlertCircle,
  ArrowRight,
} from 'lucide-react';
import { supabase, formatRupiah } from '../lib/supabase';
import type { CustomerOrder, MenuItem, CafeTable } from '../types';
import type { NavTab } from '../components/Sidebar';

interface DashboardProps {
  onNavigate: (tab: NavTab) => void;
}

export const DashboardPage: React.FC<DashboardProps> = ({ onNavigate }) => {
  const [loading, setLoading] = useState(true);
  const [orders, setOrders] = useState<CustomerOrder[]>([]);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [tables, setTables] = useState<CafeTable[]>([]);

  const loadData = async () => {
    setLoading(true);
    try {
      const [ordersRes, menuRes, tablesRes] = await Promise.all([
        supabase.from('customer_order').select('*').order('created_at', { ascending: false }),
        supabase.from('menu_item').select('*'),
        supabase.from('cafe_table').select('*'),
      ]);

      if (ordersRes.data) setOrders(ordersRes.data as CustomerOrder[]);
      if (menuRes.data) setMenuItems(menuRes.data as MenuItem[]);
      if (tablesRes.data) setTables(tablesRes.data as CafeTable[]);
    } catch (err) {
      console.error('Failed to load dashboard data:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  // Compute metrics
  const paidOrders = orders.filter((o) => o.status !== 'CANCELLED' && o.status !== 'AWAITING_PAYMENT');
  const totalRevenue = paidOrders.reduce((sum, o) => sum + (o.total_rp || 0), 0);
  const activeOrders = orders.filter((o) =>
    ['PAID', 'ACCEPTED', 'PREPARING', 'READY'].includes(o.status)
  );
  const completedOrders = orders.filter((o) => o.status === 'COMPLETED');

  const availableMenuCount = menuItems.filter((m) => m.is_available && m.is_active).length;
  const tableMap = new Map(tables.map((t) => [t.id, t.label]));

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'PAID':
        return <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-blue-100 text-blue-700">Baru (PAID)</span>;
      case 'ACCEPTED':
        return <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-indigo-100 text-indigo-700">Diterima</span>;
      case 'PREPARING':
        return <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-amber-100 text-amber-700">Sedang Dibuat</span>;
      case 'READY':
        return <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-emerald-100 text-emerald-700">Siap Saji</span>;
      case 'COMPLETED':
        return <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-stone-100 text-stone-700">Selesai</span>;
      case 'CANCELLED':
        return <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-red-100 text-red-700">Dibatalkan</span>;
      default:
        return <span className="px-2.5 py-1 text-xs font-semibold rounded-full bg-stone-100 text-stone-600">{status}</span>;
    }
  };

  return (
    <div className="p-8 space-y-8 max-w-7xl mx-auto">
      {/* Stat Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-5">
        {/* Total Omzet */}
        <div className="bg-white p-5 rounded-2xl border border-stone-200 shadow-xs flex items-center gap-3.5 min-w-0">
          <div className="w-11 h-11 rounded-xl bg-amber-50 text-amber-600 flex items-center justify-center flex-shrink-0">
            <TrendingUp className="w-5 h-5" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[11px] font-medium text-stone-500 uppercase tracking-wider truncate">Total Omzet</p>
            <h3 className="text-xl xl:text-2xl font-bold text-stone-900 mt-0.5 truncate tracking-tight" title={formatRupiah(totalRevenue)}>
              {formatRupiah(totalRevenue)}
            </h3>
            <p className="text-[11px] text-stone-500 mt-0.5 truncate">{paidOrders.length} transaksi sukses</p>
          </div>
        </div>

        {/* Antrean Aktif */}
        <div className="bg-white p-5 rounded-2xl border border-stone-200 shadow-xs flex items-center gap-3.5 min-w-0">
          <div className="w-11 h-11 rounded-xl bg-blue-50 text-blue-600 flex items-center justify-center flex-shrink-0">
            <Clock className="w-5 h-5" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[11px] font-medium text-stone-500 uppercase tracking-wider truncate">Antrean Aktif</p>
            <h3 className="text-xl xl:text-2xl font-bold text-stone-900 mt-0.5 truncate tracking-tight">{activeOrders.length}</h3>
            <p className="text-[11px] text-stone-500 mt-0.5 truncate">Perlu diproses dapur/bar</p>
          </div>
        </div>

        {/* Pesanan Selesai */}
        <div className="bg-white p-5 rounded-2xl border border-stone-200 shadow-xs flex items-center gap-3.5 min-w-0">
          <div className="w-11 h-11 rounded-xl bg-emerald-50 text-emerald-600 flex items-center justify-center flex-shrink-0">
            <CheckCircle2 className="w-5 h-5" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[11px] font-medium text-stone-500 uppercase tracking-wider truncate">Pesanan Selesai</p>
            <h3 className="text-xl xl:text-2xl font-bold text-stone-900 mt-0.5 truncate tracking-tight">{completedOrders.length}</h3>
            <p className="text-[11px] text-stone-500 mt-0.5 truncate">Dari total {orders.length} order</p>
          </div>
        </div>

        {/* Menu Tersedia */}
        <div className="bg-white p-5 rounded-2xl border border-stone-200 shadow-xs flex items-center gap-3.5 min-w-0">
          <div className="w-11 h-11 rounded-xl bg-stone-100 text-stone-700 flex items-center justify-center flex-shrink-0">
            <Coffee className="w-5 h-5" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[11px] font-medium text-stone-500 uppercase tracking-wider truncate">Menu Siap Jual</p>
            <h3 className="text-xl xl:text-2xl font-bold text-stone-900 mt-0.5 truncate tracking-tight">{availableMenuCount}</h3>
            <p className="text-[11px] text-stone-500 mt-0.5 truncate">Dari {menuItems.length} total menu</p>
          </div>
        </div>
      </div>

      {/* Quick shortcuts & live order feed */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-8">
        {/* Recent Orders Table (2 cols) */}
        <div className="lg:col-span-2 bg-white rounded-2xl border border-stone-200 shadow-xs p-6">
          <div className="flex items-center justify-between mb-5">
            <div>
              <h3 className="text-lg font-bold text-stone-900">Pesanan Masuk Terbaru</h3>
              <p className="text-xs text-stone-500">Daftar transaksi real-time dari pelanggan</p>
            </div>
            <button
              onClick={() => onNavigate('orders')}
              className="inline-flex items-center gap-1.5 text-xs font-semibold text-amber-600 hover:text-amber-700 transition-colors"
            >
              Lihat Semua Antrean <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>

          {loading ? (
            <div className="py-12 text-center text-sm text-stone-400">Memuat data pesanan...</div>
          ) : orders.length === 0 ? (
            <div className="py-12 text-center">
              <ShoppingBag className="w-10 h-10 text-stone-300 mx-auto mb-2" />
              <p className="text-sm font-medium text-stone-600">Belum ada pesanan masuk</p>
              <p className="text-xs text-stone-400 mt-1">
                Pesanan yang dibuat pelanggan lewat scan QR meja akan langsung muncul di sini.
              </p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm">
                <thead>
                  <tr className="border-b border-stone-100 text-xs font-semibold text-stone-400 uppercase tracking-wider">
                    <th className="pb-3">Kode Order</th>
                    <th className="pb-3">Meja</th>
                    <th className="pb-3">Status</th>
                    <th className="pb-3 text-right">Total</th>
                    <th className="pb-3 text-right">Waktu</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-stone-100">
                  {orders.slice(0, 6).map((order) => (
                    <tr key={order.id} className="hover:bg-stone-50/70 transition-colors">
                      <td className="py-3 font-semibold text-stone-900">
                        #{order.public_id || order.id.slice(0, 8)}
                      </td>
                      <td className="py-3 text-stone-600">
                        {tableMap.get(order.table_id) || order.table_id || 'Meja --'}
                      </td>
                      <td className="py-3">{getStatusBadge(order.status)}</td>
                      <td className="py-3 text-right font-medium text-stone-900">
                        {formatRupiah(order.total_rp)}
                      </td>
                      <td className="py-3 text-right text-xs text-stone-400">
                        {order.created_at
                          ? new Date(order.created_at).toLocaleTimeString('id-ID', {
                              hour: '2-digit',
                              minute: '2-digit',
                            })
                          : '-'}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </div>

        {/* Action Shortcuts & Operational status */}
        <div className="space-y-6">
          {/* Quick action card */}
          <div className="bg-stone-900 text-white rounded-2xl p-6 shadow-sm">
            <h3 className="font-bold text-base mb-1">Aksi Cepat Admin</h3>
            <p className="text-xs text-stone-400 mb-5">Operasi cepat manajemen kafe</p>

            <div className="space-y-2.5">
              <button
                onClick={() => onNavigate('menu')}
                className="w-full flex items-center justify-between p-3 rounded-xl bg-stone-800 hover:bg-stone-750 text-stone-200 text-sm font-medium transition-colors"
              >
                <div className="flex items-center gap-3">
                  <Coffee className="w-4 h-4 text-amber-400" />
                  <span>Kelola Katalog & Menu</span>
                </div>
                <ArrowRight className="w-4 h-4 text-stone-400" />
              </button>

              <button
                onClick={() => onNavigate('tables')}
                className="w-full flex items-center justify-between p-3 rounded-xl bg-stone-800 hover:bg-stone-750 text-stone-200 text-sm font-medium transition-colors"
              >
                <div className="flex items-center gap-3">
                  <Coffee className="w-4 h-4 text-amber-400" />
                  <span>Generate & Cetak QR Meja</span>
                </div>
                <ArrowRight className="w-4 h-4 text-stone-400" />
              </button>

              <button
                onClick={() => onNavigate('orders')}
                className="w-full flex items-center justify-between p-3 rounded-xl bg-stone-800 hover:bg-stone-750 text-stone-200 text-sm font-medium transition-colors"
              >
                <div className="flex items-center gap-3">
                  <ShoppingBag className="w-4 h-4 text-amber-400" />
                  <span>Buka Monitor Dapur / Bar</span>
                </div>
                <ArrowRight className="w-4 h-4 text-stone-400" />
              </button>
            </div>
          </div>

          {/* Out-of-stock items warning */}
          <div className="bg-white rounded-2xl border border-stone-200 p-6 shadow-xs">
            <div className="flex items-center gap-2 mb-3">
              <AlertCircle className="w-4 h-4 text-amber-600" />
              <h4 className="font-bold text-sm text-stone-900">Perhatian Stok Menu</h4>
            </div>

            {menuItems.filter((m) => !m.is_available).length === 0 ? (
              <p className="text-xs text-stone-500">Semua item menu saat ini siap dipesan pelanggan.</p>
            ) : (
              <div className="space-y-2">
                <p className="text-xs text-amber-700 font-medium">
                  {menuItems.filter((m) => !m.is_available).length} item ditandai sedang habis:
                </p>
                <ul className="text-xs text-stone-600 space-y-1">
                  {menuItems
                    .filter((m) => !m.is_available)
                    .slice(0, 4)
                    .map((item) => (
                      <li key={item.id} className="flex items-center justify-between">
                        <span>• {item.name}</span>
                        <span className="text-red-600 font-semibold text-[11px]">Habis</span>
                      </li>
                    ))}
                </ul>
              </div>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};
