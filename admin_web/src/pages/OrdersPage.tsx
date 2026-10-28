import React, { useEffect, useState } from 'react';
import {
  Clock,
  CheckCircle2,
  Search,
  Eye,
  Check,
  X,
  Coffee,
} from 'lucide-react';
import { supabase, formatRupiah } from '../lib/supabase';
import type { CustomerOrder, OrderItem, CafeTable, OrderStatus } from '../types';

export const OrdersPage: React.FC = () => {
  const [orders, setOrders] = useState<CustomerOrder[]>([]);
  const [tables, setTables] = useState<CafeTable[]>([]);
  const [itemsMap, setItemsMap] = useState<Record<string, OrderItem[]>>({});
  const [loading, setLoading] = useState(true);
  const [selectedFilter, setSelectedFilter] = useState<string>('SEMUA');
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedOrder, setSelectedOrder] = useState<CustomerOrder | null>(null);
  const [actionLoading, setActionLoading] = useState<string | null>(null);

  const loadData = async () => {
    try {
      const [ordersRes, tablesRes, itemsRes] = await Promise.all([
        supabase.from('customer_order').select('*').order('created_at', { ascending: false }),
        supabase.from('cafe_table').select('*'),
        supabase.from('order_item').select('*'),
      ]);

      if (ordersRes.data) setOrders(ordersRes.data as CustomerOrder[]);
      if (tablesRes.data) setTables(tablesRes.data as CafeTable[]);
      if (itemsRes.data) {
        const grouped: Record<string, OrderItem[]> = {};
        (itemsRes.data as OrderItem[]).forEach((item) => {
          if (!grouped[item.order_id]) grouped[item.order_id] = [];
          grouped[item.order_id].push(item);
        });
        setItemsMap(grouped);
      }
    } catch (err) {
      console.error('Error fetching orders:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();

    // Subscribe to realtime orders
    const channel = supabase
      .channel('admin-orders')
      .on(
        'postgres_changes',
        { event: '*', schema: 'public', table: 'customer_order' },
        () => {
          loadData();
        }
      )
      .subscribe();

    return () => {
      supabase.removeChannel(channel);
    };
  }, []);

  const tableMap = new Map(tables.map((t) => [t.id, t.label]));

  const updateOrderStatus = async (orderId: string, newStatus: OrderStatus) => {
    setActionLoading(orderId);
    try {
      const { error } = await supabase
        .from('customer_order')
        .update({ status: newStatus })
        .eq('id', orderId);

      if (error) {
        alert('Gagal mengubah status: ' + error.message);
      } else {
        setOrders((prev) =>
          prev.map((o) => (o.id === orderId ? { ...o, status: newStatus } : o))
        );
        if (selectedOrder?.id === orderId) {
          setSelectedOrder((prev) => (prev ? { ...prev, status: newStatus } : null));
        }
      }
    } catch (err) {
      console.error(err);
    } finally {
      setActionLoading(null);
    }
  };

  const cancelOrder = async (orderId: string) => {
    const reason = prompt('Masukkan alasan pembatalan:');
    if (!reason) return;

    setActionLoading(orderId);
    try {
      const { error } = await supabase
        .from('customer_order')
        .update({ status: 'CANCELLED', cancellation_reason: reason })
        .eq('id', orderId);

      if (error) {
        alert('Gagal membatalkan: ' + error.message);
      } else {
        loadData();
      }
    } catch (err) {
      console.error(err);
    } finally {
      setActionLoading(null);
    }
  };

  // Filter orders
  const filteredOrders = orders.filter((order) => {
    const matchFilter =
      selectedFilter === 'SEMUA'
        ? true
        : selectedFilter === 'BARU'
        ? order.status === 'PAID'
        : selectedFilter === 'PROSES'
        ? ['ACCEPTED', 'PREPARING'].includes(order.status)
        : selectedFilter === 'SIAP'
        ? order.status === 'READY'
        : selectedFilter === 'SELESAI'
        ? order.status === 'COMPLETED'
        : selectedFilter === 'BATAL'
        ? order.status === 'CANCELLED'
        : true;

    const query = searchQuery.toLowerCase();
    const publicId = (order.public_id || '').toLowerCase();
    const table = (tableMap.get(order.table_id) || order.table_id || '').toLowerCase();
    const matchQuery = publicId.includes(query) || table.includes(query);

    return matchFilter && matchQuery;
  });

  const getStatusBadge = (status: OrderStatus) => {
    switch (status) {
      case 'PAID':
        return <span className="px-3 py-1 text-xs font-bold rounded-full bg-blue-100 text-blue-700">BARU (PAID)</span>;
      case 'ACCEPTED':
        return <span className="px-3 py-1 text-xs font-bold rounded-full bg-indigo-100 text-indigo-700">DITERIMA</span>;
      case 'PREPARING':
        return <span className="px-3 py-1 text-xs font-bold rounded-full bg-amber-100 text-amber-800">DIBUAT</span>;
      case 'READY':
        return <span className="px-3 py-1 text-xs font-bold rounded-full bg-emerald-100 text-emerald-800">SIAP SAJI</span>;
      case 'COMPLETED':
        return <span className="px-3 py-1 text-xs font-bold rounded-full bg-stone-100 text-stone-600">SELESAI</span>;
      case 'CANCELLED':
        return <span className="px-3 py-1 text-xs font-bold rounded-full bg-red-100 text-red-700">DIBATALKAN</span>;
      default:
        return <span className="px-3 py-1 text-xs font-bold rounded-full bg-stone-100 text-stone-600">{status}</span>;
    }
  };

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      {/* Search & Filters */}
      <div className="flex flex-col md:flex-row gap-4 items-center justify-between">
        {/* Filter chips */}
        <div className="flex items-center gap-2 overflow-x-auto w-full md:w-auto pb-1">
          {[
            { id: 'SEMUA', label: 'Semua', count: orders.length },
            { id: 'BARU', label: 'Baru', count: orders.filter((o) => o.status === 'PAID').length },
            {
              id: 'PROSES',
              label: 'Diproses',
              count: orders.filter((o) => ['ACCEPTED', 'PREPARING'].includes(o.status)).length,
            },
            { id: 'SIAP', label: 'Siap', count: orders.filter((o) => o.status === 'READY').length },
            { id: 'SELESAI', label: 'Selesai', count: orders.filter((o) => o.status === 'COMPLETED').length },
            { id: 'BATAL', label: 'Batal', count: orders.filter((o) => o.status === 'CANCELLED').length },
          ].map((chip) => (
            <button
              key={chip.id}
              onClick={() => setSelectedFilter(chip.id)}
              className={`px-3.5 py-1.5 rounded-full text-xs font-medium whitespace-nowrap transition-colors flex items-center gap-1.5 ${
                selectedFilter === chip.id
                  ? 'bg-amber-600 text-white shadow-xs'
                  : 'bg-white text-stone-600 border border-stone-200 hover:bg-stone-50'
              }`}
            >
              <span>{chip.label}</span>
              <span
                className={`text-[10px] px-1.5 py-0.2 rounded-full font-bold ${
                  selectedFilter === chip.id ? 'bg-amber-800 text-white' : 'bg-stone-100 text-stone-500'
                }`}
              >
                {chip.count}
              </span>
            </button>
          ))}
        </div>

        {/* Search Input */}
        <div className="relative w-full md:w-72">
          <Search className="w-4 h-4 text-stone-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Cari ID pesanan / meja..."
            className="w-full pl-9 pr-4 py-2 bg-white border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
          />
        </div>
      </div>

      {/* Orders List / Cards */}
      {loading ? (
        <div className="py-20 text-center text-sm text-stone-400">Memuat data antrean pesanan...</div>
      ) : filteredOrders.length === 0 ? (
        <div className="bg-white rounded-2xl border border-stone-200 p-16 text-center shadow-xs">
          <Clock className="w-12 h-12 text-stone-300 mx-auto mb-3" />
          <h4 className="text-base font-bold text-stone-800">Tidak Ada Pesanan Ditemukan</h4>
          <p className="text-xs text-stone-400 max-w-sm mx-auto mt-1">
            Pesanan baru dari aplikasi pelanggan akan otomatis masuk secara real-time.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredOrders.map((order) => {
            const items = itemsMap[order.id] || [];
            const isBusy = actionLoading === order.id;

            return (
              <div
                key={order.id}
                className="bg-white rounded-2xl border border-stone-200 shadow-xs hover:border-amber-400 transition-all flex flex-col justify-between overflow-hidden"
              >
                {/* Header Card */}
                <div className="p-5 border-b border-stone-100 bg-stone-50/50">
                  <div className="flex items-center justify-between mb-2">
                    <span className="font-mono font-bold text-base text-stone-900">
                      #{order.public_id || order.id.slice(0, 8)}
                    </span>
                    {getStatusBadge(order.status)}
                  </div>
                  <div className="flex items-center justify-between text-xs text-stone-500">
                    <span className="font-semibold text-stone-700">
                      {tableMap.get(order.table_id) || order.table_id || 'Meja --'}
                    </span>
                    <span>
                      {order.created_at
                        ? new Date(order.created_at).toLocaleTimeString('id-ID', {
                            hour: '2-digit',
                            minute: '2-digit',
                          })
                        : '-'}
                    </span>
                  </div>
                </div>

                {/* Items preview */}
                <div className="p-5 flex-1 space-y-2">
                  <div className="text-xs font-semibold uppercase text-stone-400 tracking-wider mb-1">
                    Rincian Pesanan:
                  </div>
                  {items.length === 0 ? (
                    <div className="py-2 px-3 bg-stone-50 rounded-xl border border-dashed border-stone-200">
                      <p className="text-xs text-stone-500 font-medium">
                        {order.subtotal_rp > 0
                          ? `Total ${formatRupiah(order.subtotal_rp)} (Rincian item belum terekam di order_item)`
                          : 'Belum ada rincian item'}
                      </p>
                    </div>
                  ) : (
                    <ul className="space-y-1.5 text-xs text-stone-700">
                      {items.slice(0, 3).map((item) => (
                        <li key={item.id} className="flex justify-between items-start">
                          <span className="font-medium">
                            {item.quantity}× {item.name_snapshot}
                          </span>
                          <span className="text-stone-500">{formatRupiah(item.line_total_rp)}</span>
                        </li>
                      ))}
                      {items.length > 3 && (
                        <p className="text-[11px] text-stone-400 font-medium">
                          +{items.length - 3} item lainnya
                        </p>
                      )}
                    </ul>
                  )}
                  {order.cancellation_reason && (
                    <div className="mt-2 p-2 bg-red-50 text-red-700 text-xs rounded-lg">
                      Alasan batal: {order.cancellation_reason}
                    </div>
                  )}
                </div>

                {/* Total & Action Footer */}
                <div className="p-5 bg-stone-50/80 border-t border-stone-100 space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs text-stone-500">Total Tagihan</span>
                    <span className="font-bold text-base text-stone-900">
                      {formatRupiah(order.total_rp)}
                    </span>
                  </div>

                  {/* Operational workflow buttons */}
                  <div className="flex gap-2">
                    <button
                      onClick={() => setSelectedOrder(order)}
                      className="p-2 bg-white text-stone-700 hover:bg-stone-100 rounded-xl border border-stone-200 transition-colors text-xs font-medium flex items-center justify-center"
                      title="Lihat Detail Lengkap"
                    >
                      <Eye className="w-4 h-4" />
                    </button>

                    {order.status === 'PAID' && (
                      <button
                        disabled={isBusy}
                        onClick={() => updateOrderStatus(order.id, 'PREPARING')}
                        className="flex-1 py-2 px-3 bg-amber-600 hover:bg-amber-700 text-white font-medium rounded-xl text-xs transition-colors shadow-xs flex items-center justify-center gap-1.5 disabled:opacity-50"
                      >
                        <Coffee className="w-3.5 h-3.5" />
                        <span>Mulai Buat</span>
                      </button>
                    )}

                    {['ACCEPTED', 'PREPARING'].includes(order.status) && (
                      <button
                        disabled={isBusy}
                        onClick={() => updateOrderStatus(order.id, 'READY')}
                        className="flex-1 py-2 px-3 bg-emerald-600 hover:bg-emerald-700 text-white font-medium rounded-xl text-xs transition-colors shadow-xs flex items-center justify-center gap-1.5 disabled:opacity-50"
                      >
                        <Check className="w-3.5 h-3.5" />
                        <span>Siap Disajikan</span>
                      </button>
                    )}

                    {order.status === 'READY' && (
                      <button
                        disabled={isBusy}
                        onClick={() => updateOrderStatus(order.id, 'COMPLETED')}
                        className="flex-1 py-2 px-3 bg-stone-900 hover:bg-stone-800 text-white font-medium rounded-xl text-xs transition-colors shadow-xs flex items-center justify-center gap-1.5 disabled:opacity-50"
                      >
                        <CheckCircle2 className="w-3.5 h-3.5" />
                        <span>Selesaikan Order</span>
                      </button>
                    )}

                    {!['COMPLETED', 'CANCELLED'].includes(order.status) && (
                      <button
                        disabled={isBusy}
                        onClick={() => cancelOrder(order.id)}
                        className="p-2 text-stone-400 hover:text-red-600 hover:bg-red-50 rounded-xl transition-colors text-xs border border-transparent hover:border-red-200"
                        title="Batalkan Pesanan"
                      >
                        <X className="w-4 h-4" />
                      </button>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Order Detail Modal */}
      {selectedOrder && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-lg w-full p-6 shadow-2xl space-y-5 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between pb-3 border-b border-stone-100">
              <div>
                <h3 className="font-bold text-lg text-stone-900">
                  Pesanan #{selectedOrder.public_id || selectedOrder.id.slice(0, 8)}
                </h3>
                <p className="text-xs text-stone-500">
                  {tableMap.get(selectedOrder.table_id) || selectedOrder.table_id} ·{' '}
                  {selectedOrder.created_at
                    ? new Date(selectedOrder.created_at).toLocaleString('id-ID')
                    : '-'}
                </p>
              </div>
              <button
                onClick={() => setSelectedOrder(null)}
                className="p-2 text-stone-400 hover:text-stone-700 rounded-full hover:bg-stone-100"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {/* Status summary */}
            <div className="flex items-center justify-between p-3 bg-stone-50 rounded-xl">
              <span className="text-xs font-medium text-stone-600">Status Saat Ini:</span>
              {getStatusBadge(selectedOrder.status)}
            </div>

            {/* Items list */}
            <div className="space-y-3 max-h-60 overflow-y-auto pr-1">
              <h4 className="text-xs font-bold uppercase tracking-wider text-stone-400">
                Item Yang Dipesan:
              </h4>
              {(itemsMap[selectedOrder.id] || []).length === 0 ? (
                <div className="p-4 bg-stone-50 rounded-xl border border-dashed border-stone-200 text-center">
                  <p className="text-xs text-stone-500 font-medium">
                    Rincian menu belum tercatat di tabel <code>order_item</code> untuk order ini.
                  </p>
                  <p className="text-[11px] text-stone-400 mt-1">
                    (Pesanan dibuat sebelum skema order_item atau lewat pengujian tanpa insert line items).
                  </p>
                </div>
              ) : (
                (itemsMap[selectedOrder.id] || []).map((item) => (
                  <div
                    key={item.id}
                    className="p-3 bg-stone-50/70 border border-stone-100 rounded-xl space-y-1"
                  >
                    <div className="flex justify-between font-semibold text-sm text-stone-900">
                      <span>
                        {item.quantity}× {item.name_snapshot}
                      </span>
                      <span>{formatRupiah(item.line_total_rp)}</span>
                    </div>
                    {item.note && (
                      <p className="text-xs text-amber-700 italic">Catatan: "{item.note}"</p>
                    )}
                  </div>
                ))
              )}
            </div>

            {/* Financial breakdown */}
            <div className="p-4 bg-stone-50 rounded-2xl space-y-1.5 text-xs text-stone-600 border border-stone-100">
              <div className="flex justify-between">
                <span>Subtotal Menu</span>
                <span>{formatRupiah(selectedOrder.subtotal_rp)}</span>
              </div>
              {selectedOrder.tax_rp > 0 && (
                <div className="flex justify-between">
                  <span>Pajak (PB1)</span>
                  <span>{formatRupiah(selectedOrder.tax_rp)}</span>
                </div>
              )}
              {selectedOrder.service_charge_rp > 0 && (
                <div className="flex justify-between">
                  <span>Biaya Layanan</span>
                  <span>{formatRupiah(selectedOrder.service_charge_rp)}</span>
                </div>
              )}
              <div className="flex justify-between pt-2 border-t border-stone-200 font-bold text-sm text-stone-900">
                <span>Total Bayar</span>
                <span className="text-amber-700">{formatRupiah(selectedOrder.total_rp)}</span>
              </div>
            </div>

            {/* Close */}
            <button
              onClick={() => setSelectedOrder(null)}
              className="w-full py-2.5 bg-stone-900 hover:bg-stone-800 text-white rounded-xl text-sm font-semibold transition-colors"
            >
              Tutup Rincian
            </button>
          </div>
        </div>
      )}
    </div>
  );
};
