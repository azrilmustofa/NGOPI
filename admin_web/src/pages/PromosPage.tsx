import React, { useEffect, useState } from 'react';
import {
  Plus,
  Search,
  Edit2,
  Trash2,
  Check,
  X,
  AlertCircle,
  Tag,
  Clock,
  Sparkles,
  Calendar,
  Layers,
  Percent,
} from 'lucide-react';
import { supabase, formatRupiah } from '../lib/supabase';
import type { Promo, MenuItem, DiscountType } from '../types';

export const PromosPage: React.FC = () => {
  const [promos, setPromos] = useState<Promo[]>([]);
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchQuery, setSearchQuery] = useState('');
  const [statusFilter, setStatusFilter] = useState<'ALL' | 'ACTIVE' | 'UPCOMING' | 'EXPIRED'>('ALL');

  // Modal states
  const [showModal, setShowModal] = useState(false);
  const [editingPromo, setEditingPromo] = useState<Promo | null>(null);
  const [saving, setSaving] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  // Form states
  const [formMenuItemId, setFormMenuItemId] = useState('');
  const [formTitle, setFormTitle] = useState('');
  const [formDescription, setFormDescription] = useState('');
  const [formDiscountType, setFormDiscountType] = useState<DiscountType>('PERCENT');
  const [formDiscountValue, setFormDiscountValue] = useState<number>(20);
  const [formStartsAt, setFormStartsAt] = useState('');
  const [formEndsAt, setFormEndsAt] = useState('');
  const [formPriority, setFormPriority] = useState<number>(0);
  const [formIsActive, setFormIsActive] = useState<boolean>(true);

  // Helper date format for input type="datetime-local" (YYYY-MM-DDTHH:mm)
  const toLocalInputValue = (isoStr?: string): string => {
    const d = isoStr ? new Date(isoStr) : new Date();
    const pad = (n: number) => n.toString().padStart(2, '0');
    const yyyy = d.getFullYear();
    const mm = pad(d.getMonth() + 1);
    const dd = pad(d.getDate());
    const hh = pad(d.getHours());
    const min = pad(d.getMinutes());
    return `${yyyy}-${mm}-${dd}T${hh}:${min}`;
  };

  const loadData = async () => {
    try {
      setLoading(true);
      const [menuRes, promoRes] = await Promise.all([
        supabase.from('menu_item').select('*').order('name', { ascending: true }),
        supabase.from('promo').select('*').order('priority', { ascending: false }).order('created_at', { ascending: false }),
      ]);

      if (menuRes.data) {
        setMenuItems(menuRes.data as MenuItem[]);
      }
      if (promoRes.data) {
        setPromos(promoRes.data as Promo[]);
      }
    } catch (err: any) {
      console.error('Error fetching promos:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const openAddModal = () => {
    setEditingPromo(null);
    setErrorMessage(null);
    const defaultMenu = menuItems[0]?.id || '';
    setFormMenuItemId(defaultMenu);
    setFormTitle('Promo Hari Ini');
    setFormDescription('Diskon spesial Kopi Inggil');
    setFormDiscountType('PERCENT');
    setFormDiscountValue(20);

    const now = new Date();
    const endToday = new Date();
    endToday.setHours(23, 59, 0, 0);

    setFormStartsAt(toLocalInputValue(now.toISOString()));
    setFormEndsAt(toLocalInputValue(endToday.toISOString()));
    setFormPriority(0);
    setFormIsActive(true);
    setShowModal(true);
  };

  const openEditModal = (promo: Promo) => {
    setEditingPromo(promo);
    setErrorMessage(null);
    setFormMenuItemId(promo.menu_item_id);
    setFormTitle(promo.title);
    setFormDescription(promo.description || '');
    setFormDiscountType(promo.discount_type);
    setFormDiscountValue(promo.discount_value);
    setFormStartsAt(toLocalInputValue(promo.starts_at));
    setFormEndsAt(toLocalInputValue(promo.ends_at));
    setFormPriority(promo.priority);
    setFormIsActive(promo.is_active);
    setShowModal(true);
  };

  const selectedMenu = menuItems.find((m) => m.id === formMenuItemId);

  const calculateDiscount = () => {
    if (!selectedMenu) return { cut: 0, finalPrice: 0 };
    const base = selectedMenu.base_price_rp;
    let cut = 0;
    if (formDiscountType === 'PERCENT') {
      cut = Math.min(base, Math.round((base * (formDiscountValue || 0)) / 100));
    } else {
      cut = Math.min(base, formDiscountValue || 0);
    }
    return { cut, finalPrice: Math.max(0, base - cut) };
  };

  const handleSavePromo = async (e: React.FormEvent) => {
    e.preventDefault();
    setErrorMessage(null);

    if (!formMenuItemId) {
      setErrorMessage('Pilih menu yang mendapatkan promo.');
      return;
    }
    if (!formTitle.trim()) {
      setErrorMessage('Judul promo wajib diisi.');
      return;
    }
    if (!formStartsAt || !formEndsAt) {
      setErrorMessage('Tanggal mulai dan selesai wajib diisi.');
      return;
    }

    const startDate = new Date(formStartsAt);
    const endDate = new Date(formEndsAt);
    if (endDate <= startDate) {
      setErrorMessage('Waktu selesai harus setelah waktu mulai.');
      return;
    }

    if (formDiscountType === 'PERCENT' && (formDiscountValue <= 0 || formDiscountValue > 100)) {
      setErrorMessage('Nilai diskon persen harus antara 1 sampai 100.');
      return;
    }

    if (formDiscountType === 'AMOUNT' && formDiscountValue <= 0) {
      setErrorMessage('Nominal potongan rupiah harus lebih dari 0.');
      return;
    }

    setSaving(true);
    try {
      const payload = {
        menu_item_id: formMenuItemId,
        title: formTitle.trim(),
        description: formDescription.trim() || null,
        discount_type: formDiscountType,
        discount_value: Number(formDiscountValue),
        starts_at: startDate.toISOString(),
        ends_at: endDate.toISOString(),
        priority: Number(formPriority) || 0,
        is_active: formIsActive,
      };

      if (editingPromo) {
        const { error } = await supabase
          .from('promo')
          .update(payload)
          .eq('id', editingPromo.id);
        if (error) throw error;
      } else {
        const { error } = await supabase
          .from('promo')
          .insert(payload);
        if (error) throw error;
      }

      setShowModal(false);
      loadData();
    } catch (err: any) {
      console.error(err);
      setErrorMessage(err.message || 'Gagal menyimpan data promo');
    } finally {
      setSaving(false);
    }
  };

  const togglePromoActive = async (promo: Promo) => {
    try {
      const updated = !promo.is_active;
      // Optimistic
      setPromos((prev) =>
        prev.map((p) => (p.id === promo.id ? { ...p, is_active: updated } : p))
      );
      const { error } = await supabase
        .from('promo')
        .update({ is_active: updated })
        .eq('id', promo.id);
      if (error) {
        // Rollback
        setPromos((prev) =>
          prev.map((p) => (p.id === promo.id ? { ...p, is_active: promo.is_active } : p))
        );
        alert(`Gagal mengubah status: ${error.message}`);
      }
    } catch (err: any) {
      alert(err.message);
    }
  };

  const handleDeletePromo = async (id: string, title: string) => {
    if (!window.confirm(`Yakin ingin menghapus promo "${title}"?`)) return;
    try {
      const { error } = await supabase.from('promo').delete().eq('id', id);
      if (error) throw error;
      setPromos((prev) => prev.filter((p) => p.id !== id));
    } catch (err: any) {
      alert(`Gagal menghapus promo: ${err.message}`);
    }
  };

  // Helper status check
  const now = new Date();
  const getPromoStatus = (promo: Promo) => {
    if (!promo.is_active) return { label: 'Non-Aktif', color: 'bg-stone-200 text-stone-700 border-stone-300' };
    const starts = new Date(promo.starts_at);
    const ends = new Date(promo.ends_at);
    if (now < starts) return { label: 'Akan Datang', color: 'bg-blue-100 text-blue-700 border-blue-200' };
    if (now >= ends) return { label: 'Kedaluwarsa', color: 'bg-rose-100 text-rose-700 border-rose-200' };
    return { label: 'Sedang Aktif', color: 'bg-emerald-100 text-emerald-700 border-emerald-200' };
  };

  // Quick preset dates
  const setQuickPeriod = (type: 'today' | 'tomorrow' | 'week') => {
    const start = new Date();
    const end = new Date();
    if (type === 'today') {
      end.setHours(23, 59, 59, 999);
    } else if (type === 'tomorrow') {
      end.setDate(end.getDate() + 1);
      end.setHours(23, 59, 59, 999);
    } else if (type === 'week') {
      end.setDate(end.getDate() + 7);
      end.setHours(23, 59, 59, 999);
    }
    setFormStartsAt(toLocalInputValue(start.toISOString()));
    setFormEndsAt(toLocalInputValue(end.toISOString()));
  };

  const filteredPromos = promos.filter((promo) => {
    const menu = menuItems.find((m) => m.id === promo.menu_item_id);
    const query = searchQuery.toLowerCase();
    const matchQuery =
      promo.title.toLowerCase().includes(query) ||
      (promo.description && promo.description.toLowerCase().includes(query)) ||
      (menu && menu.name.toLowerCase().includes(query));

    if (!matchQuery) return false;

    const starts = new Date(promo.starts_at);
    const ends = new Date(promo.ends_at);
    const isCurrentlyActive = promo.is_active && now >= starts && now < ends;

    if (statusFilter === 'ACTIVE') return isCurrentlyActive;
    if (statusFilter === 'UPCOMING') return promo.is_active && now < starts;
    if (statusFilter === 'EXPIRED') return !promo.is_active || now >= ends;

    return true;
  });

  const activeCount = promos.filter(
    (p) => p.is_active && now >= new Date(p.starts_at) && now < new Date(p.ends_at)
  ).length;

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      {/* Top Banner & Quick Metrics */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-5">
        <div className="bg-white p-6 rounded-2xl border border-stone-200/80 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-stone-500">
              Promo Sedang Berjalan
            </p>
            <h3 className="text-2xl font-bold text-stone-900 mt-1">{activeCount} Promo</h3>
            <p className="text-xs text-stone-400 mt-1">Tampil di carousel banner aplikasi Android</p>
          </div>
          <div className="w-12 h-12 bg-amber-50 rounded-2xl flex items-center justify-center text-amber-600">
            <Sparkles className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-white p-6 rounded-2xl border border-stone-200/80 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold uppercase tracking-wider text-stone-500">
              Total Promo Terdaftar
            </p>
            <h3 className="text-2xl font-bold text-stone-900 mt-1">{promos.length} Kampanye</h3>
            <p className="text-xs text-stone-400 mt-1">Termasuk jadwal mendatang dan riwayat</p>
          </div>
          <div className="w-12 h-12 bg-stone-100 rounded-2xl flex items-center justify-center text-stone-600">
            <Tag className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-gradient-to-br from-amber-700 to-amber-900 p-6 rounded-2xl text-white shadow-md flex flex-col justify-between">
          <div>
            <span className="text-xs font-bold uppercase tracking-wider text-amber-200">
              Aturan Sistem Promo
            </span>
            <p className="text-sm font-medium mt-1 leading-snug">
              Diskon memotong harga dasar menu di katalog dan checkout pesanan secara otomatis.
            </p>
          </div>
          <button
            onClick={openAddModal}
            className="mt-4 inline-flex items-center justify-center gap-2 px-4 py-2 bg-white text-amber-900 font-semibold text-sm rounded-xl hover:bg-amber-50 transition-colors shadow-xs"
          >
            <Plus className="w-4 h-4" />
            Buat Promo Baru
          </button>
        </div>
      </div>

      {/* Control Bar: Search & Filters */}
      <div className="bg-white p-4 rounded-2xl border border-stone-200/80 shadow-xs flex flex-col sm:flex-row items-center justify-between gap-4">
        <div className="flex items-center gap-2 w-full sm:w-auto">
          <div className="relative w-full sm:w-80">
            <Search className="w-4 h-4 absolute left-3.5 top-1/2 -translate-y-1/2 text-stone-400" />
            <input
              type="text"
              placeholder="Cari judul promo atau menu..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              className="w-full pl-10 pr-4 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-hidden focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500 transition-all"
            />
          </div>
        </div>

        <div className="flex items-center gap-2 overflow-x-auto w-full sm:w-auto pb-1 sm:pb-0">
          {[
            { key: 'ALL', label: 'Semua' },
            { key: 'ACTIVE', label: 'Sedang Aktif' },
            { key: 'UPCOMING', label: 'Akan Datang' },
            { key: 'EXPIRED', label: 'Selesai / Non-Aktif' },
          ].map((tab) => (
            <button
              key={tab.key}
              onClick={() => setStatusFilter(tab.key as any)}
              className={`px-3.5 py-1.5 rounded-lg text-xs font-medium whitespace-nowrap transition-colors ${
                statusFilter === tab.key
                  ? 'bg-amber-600 text-white shadow-xs'
                  : 'bg-stone-100 text-stone-600 hover:bg-stone-200'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>
      </div>

      {/* Promos Content Grid */}
      {loading ? (
        <div className="p-12 text-center text-stone-400 bg-white rounded-2xl border border-stone-200">
          <div className="w-8 h-8 border-3 border-amber-600 border-t-transparent rounded-full animate-spin mx-auto mb-3" />
          <p className="text-sm">Memuat data promosi Kopi Inggil...</p>
        </div>
      ) : filteredPromos.length === 0 ? (
        <div className="p-12 text-center bg-white rounded-2xl border border-dashed border-stone-300">
          <div className="w-12 h-12 bg-amber-50 text-amber-600 rounded-full flex items-center justify-center mx-auto mb-3">
            <Tag className="w-6 h-6" />
          </div>
          <h3 className="text-base font-semibold text-stone-800">Belum ada promo</h3>
          <p className="text-xs text-stone-500 max-w-sm mx-auto mt-1">
            {searchQuery
              ? 'Tidak ada promo yang cocok dengan pencarian Anda.'
              : 'Tambahkan promo baru untuk memanjakan pelanggan dan meningkatkan penjualan menu.'}
          </p>
          {!searchQuery && (
            <button
              onClick={openAddModal}
              className="mt-4 px-4 py-2 bg-amber-600 text-white text-xs font-semibold rounded-xl hover:bg-amber-700 transition-colors"
            >
              + Buat Promo Pertama
            </button>
          )}
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5">
          {filteredPromos.map((promo) => {
            const menu = menuItems.find((m) => m.id === promo.menu_item_id);
            const status = getPromoStatus(promo);
            const discountCut =
              promo.discount_type === 'PERCENT'
                ? Math.round(((menu?.base_price_rp || 0) * promo.discount_value) / 100)
                : promo.discount_value;
            const finalPrice = Math.max(0, (menu?.base_price_rp || 0) - discountCut);

            return (
              <div
                key={promo.id}
                className="bg-white rounded-2xl border border-stone-200/90 shadow-xs hover:shadow-md transition-shadow overflow-hidden flex flex-col justify-between"
              >
                {/* Banner preview card mimicking Android app */}
                <div>
                  <div className="p-4 bg-gradient-to-r from-stone-900 via-stone-800 to-amber-950 text-white relative">
                    <div className="flex items-center justify-between gap-2 mb-2">
                      <span className="text-[11px] font-bold tracking-wider uppercase text-amber-400 bg-amber-950/60 px-2 py-0.5 rounded-md border border-amber-700/40">
                        {promo.title}
                      </span>
                      <span
                        className={`text-[10px] font-semibold px-2 py-0.5 rounded-full border ${status.color}`}
                      >
                        {status.label}
                      </span>
                    </div>

                    <div className="flex items-start justify-between gap-3 mt-1">
                      <div>
                        <div className="flex items-center gap-1.5">
                          <span className="text-xl font-black text-amber-400">
                            {promo.discount_type === 'PERCENT'
                              ? `-${promo.discount_value}%`
                              : `-${formatRupiah(promo.discount_value)}`}
                          </span>
                        </div>
                        {promo.description && (
                          <p className="text-xs text-stone-300 mt-0.5 line-clamp-1">
                            {promo.description}
                          </p>
                        )}
                      </div>
                      <div className="text-right">
                        <span className="text-[10px] text-stone-400 block">Prioritas Banner</span>
                        <span className="text-xs font-bold text-stone-200 flex items-center justify-end gap-1">
                          <Layers className="w-3 h-3 text-amber-400" /> {promo.priority}
                        </span>
                      </div>
                    </div>
                  </div>

                  {/* Menu relation info */}
                  <div className="p-4 space-y-3">
                    <div className="flex items-center gap-3">
                      {menu?.photo_url ? (
                        <img
                          src={menu.photo_url}
                          alt={menu.name}
                          className="w-12 h-12 rounded-xl object-cover border border-stone-200 flex-shrink-0"
                        />
                      ) : (
                        <div className="w-12 h-12 rounded-xl bg-stone-100 flex items-center justify-center text-stone-400 border border-stone-200 flex-shrink-0">
                          <Tag className="w-5 h-5" />
                        </div>
                      )}
                      <div className="min-w-0 flex-1">
                        <span className="text-[10px] font-semibold uppercase tracking-wider text-amber-700">
                          Menu Sasaran
                        </span>
                        <h4 className="font-semibold text-sm text-stone-900 truncate">
                          {menu ? menu.name : `Menu ID: ${promo.menu_item_id}`}
                        </h4>
                        <div className="flex items-center gap-2 mt-0.5">
                          {menu && (
                            <>
                              <span className="text-xs font-bold text-amber-700">
                                {formatRupiah(finalPrice)}
                              </span>
                              <span className="text-xs text-stone-400 line-through">
                                {formatRupiah(menu.base_price_rp)}
                              </span>
                            </>
                          )}
                        </div>
                      </div>
                    </div>

                    {/* Schedule dates */}
                    <div className="bg-stone-50 rounded-xl p-3 space-y-1 text-xs border border-stone-100">
                      <div className="flex items-center justify-between text-stone-600">
                        <span className="flex items-center gap-1.5 text-stone-500">
                          <Calendar className="w-3.5 h-3.5" /> Mulai:
                        </span>
                        <span className="font-medium text-stone-800">
                          {new Date(promo.starts_at).toLocaleString('id-ID', {
                            dateStyle: 'medium',
                            timeStyle: 'short',
                          })}
                        </span>
                      </div>
                      <div className="flex items-center justify-between text-stone-600">
                        <span className="flex items-center gap-1.5 text-stone-500">
                          <Clock className="w-3.5 h-3.5" /> Berakhir:
                        </span>
                        <span className="font-medium text-stone-800">
                          {new Date(promo.ends_at).toLocaleString('id-ID', {
                            dateStyle: 'medium',
                            timeStyle: 'short',
                          })}
                        </span>
                      </div>
                    </div>
                  </div>
                </div>

                {/* Card footer actions */}
                <div className="p-4 pt-0 border-t border-stone-100 mt-2 flex items-center justify-between gap-2">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => togglePromoActive(promo)}
                      className={`text-xs font-medium px-2.5 py-1 rounded-lg border transition-colors flex items-center gap-1 ${
                        promo.is_active
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100'
                          : 'bg-stone-100 text-stone-500 border-stone-200 hover:bg-stone-200'
                      }`}
                      title={promo.is_active ? 'Klik untuk menonaktifkan' : 'Klik untuk mengaktifkan'}
                    >
                      {promo.is_active ? <Check className="w-3 h-3" /> : <X className="w-3 h-3" />}
                      {promo.is_active ? 'Aktif' : 'Mati'}
                    </button>
                  </div>

                  <div className="flex items-center gap-1">
                    <button
                      onClick={() => openEditModal(promo)}
                      className="p-1.5 text-stone-500 hover:text-stone-900 hover:bg-stone-100 rounded-lg transition-colors"
                      title="Edit Promo"
                    >
                      <Edit2 className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => handleDeletePromo(promo.id, promo.title)}
                      className="p-1.5 text-stone-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                      title="Hapus Promo"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Modal Add / Edit Promo */}
      {showModal && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4 overflow-y-auto">
          <div className="bg-white rounded-3xl max-w-lg w-full p-6 shadow-2xl space-y-5 animate-in fade-in zoom-in-95 duration-150 my-8">
            <div className="flex items-center justify-between pb-3 border-b border-stone-100">
              <div className="flex items-center gap-2">
                <div className="w-9 h-9 rounded-xl bg-amber-100 text-amber-800 flex items-center justify-center font-bold">
                  <Tag className="w-5 h-5" />
                </div>
                <div>
                  <h3 className="font-bold text-lg text-stone-900">
                    {editingPromo ? 'Edit Promo Menu' : 'Tambah Promo Baru'}
                  </h3>
                  <p className="text-xs text-stone-500">
                    Diskon langsung memotong harga menu di aplikasi Android
                  </p>
                </div>
              </div>
              <button
                onClick={() => setShowModal(false)}
                className="p-2 text-stone-400 hover:text-stone-700 rounded-full hover:bg-stone-100 transition-colors"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            {errorMessage && (
              <div className="p-3 bg-red-50 border border-red-200 rounded-xl flex items-center gap-2 text-xs text-red-700">
                <AlertCircle className="w-4 h-4 flex-shrink-0" />
                <span>{errorMessage}</span>
              </div>
            )}

            <form onSubmit={handleSavePromo} className="space-y-4">
              {/* Menu Target Selection */}
              <div>
                <label className="block text-xs font-bold uppercase tracking-wider text-stone-600 mb-1.5">
                  Menu Target <span className="text-red-500">*</span>
                </label>
                <select
                  value={formMenuItemId}
                  onChange={(e) => setFormMenuItemId(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm font-medium text-stone-800 focus:outline-hidden focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                  required
                >
                  <option value="" disabled>
                    Pilih menu makanan/minuman
                  </option>
                  {menuItems.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name} ({formatRupiah(item.base_price_rp)})
                    </option>
                  ))}
                </select>
              </div>

              {/* Title & Description */}
              <div className="grid grid-cols-1 gap-3">
                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-stone-600 mb-1">
                    Judul Promo (Header Banner) <span className="text-red-500">*</span>
                  </label>
                  <input
                    type="text"
                    placeholder="Contoh: Promo Pagi, Flash Sale"
                    value={formTitle}
                    onChange={(e) => setFormTitle(e.target.value)}
                    className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-hidden focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                    required
                  />
                </div>
                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-stone-600 mb-1">
                    Deskripsi Singkat (Opsional)
                  </label>
                  <input
                    type="text"
                    placeholder="Contoh: Diskon khusus jam 07.00-11.00"
                    value={formDescription}
                    onChange={(e) => setFormDescription(e.target.value)}
                    className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-hidden focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                  />
                </div>
              </div>

              {/* Discount Type & Value */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-stone-600 mb-1">
                    Tipe Diskon
                  </label>
                  <div className="grid grid-cols-2 gap-1.5 p-1 bg-stone-100 rounded-xl">
                    <button
                      type="button"
                      onClick={() => setFormDiscountType('PERCENT')}
                      className={`py-1.5 text-xs font-semibold rounded-lg flex items-center justify-center gap-1 transition-all ${
                        formDiscountType === 'PERCENT'
                          ? 'bg-white text-stone-900 shadow-xs'
                          : 'text-stone-500 hover:text-stone-800'
                      }`}
                    >
                      <Percent className="w-3.5 h-3.5" />
                      Persen (%)
                    </button>
                    <button
                      type="button"
                      onClick={() => setFormDiscountType('AMOUNT')}
                      className={`py-1.5 text-xs font-semibold rounded-lg flex items-center justify-center gap-1 transition-all ${
                        formDiscountType === 'AMOUNT'
                          ? 'bg-white text-stone-900 shadow-xs'
                          : 'text-stone-500 hover:text-stone-800'
                      }`}
                    >
                      <Tag className="w-3.5 h-3.5" />
                      Nominal (Rp)
                    </button>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-stone-600 mb-1">
                    {formDiscountType === 'PERCENT' ? 'Nilai Persen (%)' : 'Potongan Rupiah (Rp)'}
                  </label>
                  <input
                    type="number"
                    min={1}
                    max={formDiscountType === 'PERCENT' ? 100 : 1000000}
                    value={formDiscountValue}
                    onChange={(e) => setFormDiscountValue(Number(e.target.value))}
                    className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-hidden focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                    required
                  />
                </div>
              </div>

              {/* Calculation Live Preview */}
              {selectedMenu && (
                <div className="p-3 bg-amber-50/70 border border-amber-200 rounded-xl text-xs flex items-center justify-between text-amber-950">
                  <div>
                    <span className="font-semibold block">{selectedMenu.name}</span>
                    <span className="text-stone-500">
                      Harga Normal: {formatRupiah(selectedMenu.base_price_rp)}
                    </span>
                  </div>
                  <div className="text-right">
                    <span className="text-stone-500 block">
                      Potongan: -{formatRupiah(calculateDiscount().cut)}
                    </span>
                    <span className="font-bold text-amber-700 text-sm">
                      Harga Promo: {formatRupiah(calculateDiscount().finalPrice)}
                    </span>
                  </div>
                </div>
              )}

              {/* Date & Time Range with quick presets */}
              <div className="space-y-2">
                <div className="flex items-center justify-between">
                  <label className="text-xs font-bold uppercase tracking-wider text-stone-600">
                    Masa Berlaku
                  </label>
                  <div className="flex items-center gap-1 text-[11px]">
                    <span className="text-stone-400">Pilih Cepat:</span>
                    <button
                      type="button"
                      onClick={() => setQuickPeriod('today')}
                      className="px-2 py-0.5 bg-stone-100 hover:bg-stone-200 rounded text-stone-700 font-medium"
                    >
                      Hari Ini
                    </button>
                    <button
                      type="button"
                      onClick={() => setQuickPeriod('tomorrow')}
                      className="px-2 py-0.5 bg-stone-100 hover:bg-stone-200 rounded text-stone-700 font-medium"
                    >
                      Besok
                    </button>
                    <button
                      type="button"
                      onClick={() => setQuickPeriod('week')}
                      className="px-2 py-0.5 bg-stone-100 hover:bg-stone-200 rounded text-stone-700 font-medium"
                    >
                      1 Minggu
                    </button>
                  </div>
                </div>

                <div className="grid grid-cols-2 gap-3">
                  <div>
                    <span className="text-[11px] text-stone-500 block mb-1">Mulai Dari:</span>
                    <input
                      type="datetime-local"
                      value={formStartsAt}
                      onChange={(e) => setFormStartsAt(e.target.value)}
                      className="w-full px-3 py-1.5 bg-stone-50 border border-stone-200 rounded-xl text-xs font-medium text-stone-800 focus:outline-hidden focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                      required
                    />
                  </div>
                  <div>
                    <span className="text-[11px] text-stone-500 block mb-1">Selesai Pada:</span>
                    <input
                      type="datetime-local"
                      value={formEndsAt}
                      onChange={(e) => setFormEndsAt(e.target.value)}
                      className="w-full px-3 py-1.5 bg-stone-50 border border-stone-200 rounded-xl text-xs font-medium text-stone-800 focus:outline-hidden focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                      required
                    />
                  </div>
                </div>
              </div>

              {/* Priority & Active Switch */}
              <div className="grid grid-cols-2 gap-3 pt-1 border-t border-stone-100">
                <div>
                  <label className="block text-xs font-bold uppercase tracking-wider text-stone-600 mb-1">
                    Prioritas Banner (0 - 99)
                  </label>
                  <input
                    type="number"
                    min={0}
                    max={99}
                    value={formPriority}
                    onChange={(e) => setFormPriority(Number(e.target.value))}
                    className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-hidden focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                  />
                  <span className="text-[10px] text-stone-400 mt-0.5 block">
                    Angka lebih besar tampil paling depan di banner carousel.
                  </span>
                </div>

                <div className="flex flex-col justify-center">
                  <label className="block text-xs font-bold uppercase tracking-wider text-stone-600 mb-2">
                    Status Aktif
                  </label>
                  <label className="relative inline-flex items-center cursor-pointer">
                    <input
                      type="checkbox"
                      checked={formIsActive}
                      onChange={(e) => setFormIsActive(e.target.checked)}
                      className="sr-only peer"
                    />
                    <div className="w-11 h-6 bg-stone-200 peer-focus:outline-hidden rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-stone-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
                    <span className="ml-3 text-xs font-medium text-stone-700">
                      {formIsActive ? 'Promo Aktif' : 'Non-Aktif (Mati)'}
                    </span>
                  </label>
                </div>
              </div>

              {/* Action buttons */}
              <div className="pt-3 border-t border-stone-100 flex items-center justify-end gap-3">
                <button
                  type="button"
                  onClick={() => setShowModal(false)}
                  className="px-4 py-2.5 text-stone-600 hover:text-stone-800 text-sm font-semibold rounded-xl hover:bg-stone-100 transition-colors"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="px-5 py-2.5 bg-amber-600 hover:bg-amber-700 disabled:opacity-50 text-white text-sm font-semibold rounded-xl shadow-xs transition-colors flex items-center gap-2"
                >
                  {saving && (
                    <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin" />
                  )}
                  {editingPromo ? 'Simpan Perubahan' : 'Terbitkan Promo'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
