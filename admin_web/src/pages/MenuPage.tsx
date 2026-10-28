import React, { useEffect, useState } from 'react';
import {
  Plus,
  Search,
  Edit2,
  Trash2,
  Check,
  X,
  Image as ImageIcon,
  AlertCircle,
  FolderPlus,
} from 'lucide-react';
import { supabase, formatRupiah } from '../lib/supabase';
import type { MenuItem, MenuCategory } from '../types';

export const MenuPage: React.FC = () => {
  const [menuItems, setMenuItems] = useState<MenuItem[]>([]);
  const [categories, setCategories] = useState<MenuCategory[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState<string>('ALL');
  const [searchQuery, setSearchQuery] = useState('');

  // Modal states
  const [showItemModal, setShowItemModal] = useState(false);
  const [editingItem, setEditingItem] = useState<MenuItem | null>(null);
  const [showCategoryModal, setShowCategoryModal] = useState(false);

  // Form states
  const [formName, setFormName] = useState('');
  const [formCategoryId, setFormCategoryId] = useState('');
  const [formPrice, setFormPrice] = useState<number>(20000);
  const [formDesc, setFormDesc] = useState('');
  const [formPhotoUrl, setFormPhotoUrl] = useState('');
  const [formAllergy, setFormAllergy] = useState('');
  const [formIsAvailable, setFormIsAvailable] = useState(true);
  const [formIsActive, setFormIsActive] = useState(true);

  // Category form state
  const [newCategoryName, setNewCategoryName] = useState('');

  const [saving, setSaving] = useState(false);

  const loadData = async () => {
    try {
      const [catRes, menuRes] = await Promise.all([
        supabase.from('menu_category').select('*').order('display_order', { ascending: true }),
        supabase.from('menu_item').select('*').order('created_at', { ascending: false }),
      ]);

      if (catRes.data) setCategories(catRes.data as MenuCategory[]);
      if (menuRes.data) setMenuItems(menuRes.data as MenuItem[]);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const openAddItemModal = () => {
    setEditingItem(null);
    setFormName('');
    setFormCategoryId(categories[0]?.id || 'CAT-01');
    setFormPrice(20000);
    setFormDesc('');
    setFormPhotoUrl('');
    setFormAllergy('');
    setFormIsAvailable(true);
    setFormIsActive(true);
    setShowItemModal(true);
  };

  const openEditItemModal = (item: MenuItem) => {
    setEditingItem(item);
    setFormName(item.name);
    setFormCategoryId(item.category_id);
    setFormPrice(item.base_price_rp);
    setFormDesc(item.description || '');
    setFormPhotoUrl(item.photo_url || '');
    setFormAllergy(item.allergy_info || '');
    setFormIsAvailable(item.is_available);
    setFormIsActive(item.is_active);
    setShowItemModal(true);
  };

  const handleSaveItem = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!formName.trim()) return alert('Nama menu wajib diisi');

    setSaving(true);
    try {
      if (editingItem) {
        // Update
        const { data, error } = await supabase
          .from('menu_item')
          .update({
            name: formName.trim(),
            category_id: formCategoryId,
            base_price_rp: formPrice,
            description: formDesc.trim(),
            photo_url: formPhotoUrl.trim(),
            allergy_info: formAllergy.trim() || null,
            is_available: formIsAvailable,
            is_active: formIsActive,
          })
          .eq('id', editingItem.id)
          .select();

        if (error || !data || data.length === 0) {
          throw new Error('Terhalang Row-Level Security (RLS). Jalankan supabase/enable_admin_crud.sql atau gunakan Service Role Key.');
        }
      } else {
        // Insert new
        const newId = 'MENU-' + Math.floor(1000 + Math.random() * 9000);
        const { data, error } = await supabase.from('menu_item').insert({
          id: newId,
          name: formName.trim(),
          category_id: formCategoryId,
          base_price_rp: formPrice,
          description: formDesc.trim(),
          photo_url: formPhotoUrl.trim(),
          allergy_info: formAllergy.trim() || null,
          is_available: formIsAvailable,
          is_active: formIsActive,
        }).select();

        if (error || !data || data.length === 0) {
          throw new Error('Terhalang Row-Level Security (RLS). Jalankan supabase/enable_admin_crud.sql atau gunakan Service Role Key.');
        }
      }

      setShowItemModal(false);
      loadData();
    } catch (err: any) {
      alert('Gagal menyimpan menu: ' + (err.message || 'Terjadi kesalahan'));
    } finally {
      setSaving(false);
    }
  };

  const toggleAvailability = async (item: MenuItem) => {
    const nextVal = !item.is_available;
    // Optimistic UI update
    setMenuItems((prev) =>
      prev.map((m) => (m.id === item.id ? { ...m, is_available: nextVal } : m))
    );

    const { data, error } = await supabase
      .from('menu_item')
      .update({ is_available: nextVal })
      .eq('id', item.id)
      .select();

    if (error || !data || data.length === 0) {
      alert(
        'Gagal update status stok ke Supabase (Terhalang RLS Policy).\n\n' +
        'Solusi:\n' +
        '1. Masukkan Supabase Service Role Key di menu Pengaturan, ATAU\n' +
        '2. Jalankan file SQL: supabase/enable_admin_crud.sql di Supabase Dashboard.'
      );
      loadData();
    }
  };

  const deleteItem = async (item: MenuItem) => {
    if (!confirm(`Hapus menu "${item.name}"?`)) return;

    const { error } = await supabase.from('menu_item').delete().eq('id', item.id);
    if (error) {
      alert('Gagal menghapus: ' + error.message);
    } else {
      setMenuItems((prev) => prev.filter((m) => m.id !== item.id));
    }
  };

  const handleAddCategory = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!newCategoryName.trim()) return;

    setSaving(true);
    try {
      const catId = 'CAT-' + (categories.length + 1).toString().padStart(2, '0');
      const { error } = await supabase.from('menu_category').insert({
        id: catId,
        name: newCategoryName.trim(),
        display_order: categories.length + 1,
        is_active: true,
      });

      if (error) throw error;
      setNewCategoryName('');
      setShowCategoryModal(false);
      loadData();
    } catch (err: any) {
      alert('Gagal menambah kategori: ' + err.message);
    } finally {
      setSaving(false);
    }
  };

  const categoryMap = new Map(categories.map((c) => [c.id, c.name]));

  const filteredItems = menuItems.filter((item) => {
    const matchCat = selectedCategory === 'ALL' || item.category_id === selectedCategory;
    const matchQuery =
      item.name.toLowerCase().includes(searchQuery.toLowerCase()) ||
      (item.description || '').toLowerCase().includes(searchQuery.toLowerCase());
    return matchCat && matchQuery;
  });

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      {/* Top Toolbar */}
      <div className="flex flex-col md:flex-row gap-4 items-center justify-between">
        {/* Search */}
        <div className="relative w-full md:w-80">
          <Search className="w-4 h-4 text-stone-400 absolute left-3 top-1/2 -translate-y-1/2" />
          <input
            type="text"
            value={searchQuery}
            onChange={(e) => setSearchQuery(e.target.value)}
            placeholder="Cari nama menu / deskripsi..."
            className="w-full pl-9 pr-4 py-2 bg-white border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
          />
        </div>

        {/* Action buttons */}
        <div className="flex items-center gap-3 w-full md:w-auto">
          <button
            onClick={() => setShowCategoryModal(true)}
            className="flex-1 md:flex-none inline-flex items-center justify-center gap-2 px-4 py-2 bg-white border border-stone-200 hover:bg-stone-50 text-stone-700 text-sm font-medium rounded-xl transition-colors"
          >
            <FolderPlus className="w-4 h-4 text-stone-500" />
            <span>Kategori Baru</span>
          </button>

          <button
            onClick={openAddItemModal}
            className="flex-1 md:flex-none inline-flex items-center justify-center gap-2 px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white text-sm font-medium rounded-xl transition-colors shadow-xs"
          >
            <Plus className="w-4 h-4" />
            <span>Tambah Menu</span>
          </button>
        </div>
      </div>

      {/* Category Pills */}
      <div className="flex items-center gap-2 overflow-x-auto pb-2">
        <button
          onClick={() => setSelectedCategory('ALL')}
          className={`px-4 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-colors ${
            selectedCategory === 'ALL'
              ? 'bg-stone-900 text-white'
              : 'bg-white text-stone-600 border border-stone-200 hover:bg-stone-50'
          }`}
        >
          Semua Menu ({menuItems.length})
        </button>
        {categories.map((cat) => (
          <button
            key={cat.id}
            onClick={() => setSelectedCategory(cat.id)}
            className={`px-4 py-1.5 rounded-full text-xs font-semibold whitespace-nowrap transition-colors ${
              selectedCategory === cat.id
                ? 'bg-amber-600 text-white shadow-xs'
                : 'bg-white text-stone-600 border border-stone-200 hover:bg-stone-50'
            }`}
          >
            {cat.name} ({menuItems.filter((m) => m.category_id === cat.id).length})
          </button>
        ))}
      </div>

      {/* Menu Grid */}
      {loading ? (
        <div className="py-20 text-center text-sm text-stone-400">Memuat katalog menu...</div>
      ) : filteredItems.length === 0 ? (
        <div className="bg-white rounded-2xl border border-stone-200 p-16 text-center shadow-xs">
          <AlertCircle className="w-12 h-12 text-stone-300 mx-auto mb-3" />
          <h4 className="text-base font-bold text-stone-800">Menu Tidak Ditemukan</h4>
          <p className="text-xs text-stone-400 max-w-sm mx-auto mt-1">
            Gunakan tombol "Tambah Menu" untuk memasukkan item kopi atau makanan baru.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {filteredItems.map((item) => (
            <div
              key={item.id}
              className={`bg-white rounded-2xl border transition-all flex flex-col justify-between overflow-hidden shadow-xs hover:shadow-md ${
                !item.is_available ? 'border-amber-300 bg-amber-50/20' : 'border-stone-200'
              }`}
            >
              {/* Photo & badges */}
              <div className="relative aspect-4/3 w-full bg-stone-100 overflow-hidden">
                {item.photo_url ? (
                  <img
                    src={item.photo_url}
                    alt={item.name}
                    className="w-full h-full object-cover"
                    onError={(e) => {
                      // Fallback image
                      (e.target as HTMLElement).style.display = 'none';
                    }}
                  />
                ) : (
                  <div className="w-full h-full flex flex-col items-center justify-center text-stone-300">
                    <ImageIcon className="w-10 h-10" />
                    <span className="text-xs mt-1">Foto belum ada</span>
                  </div>
                )}

                {/* Badges */}
                <div className="absolute top-3 left-3 flex flex-col gap-1">
                  <span className="px-2.5 py-1 bg-stone-900/80 backdrop-blur-xs text-white text-[11px] font-semibold rounded-lg">
                    {categoryMap.get(item.category_id) || item.category_id}
                  </span>
                  {!item.is_available && (
                    <span className="px-2.5 py-1 bg-red-600 text-white text-[11px] font-bold rounded-lg shadow-sm">
                      STOK HABIS
                    </span>
                  )}
                </div>
              </div>

              {/* Content info */}
              <div className="p-4 flex-1 flex flex-col justify-between space-y-3">
                <div>
                  <h4 className="font-bold text-stone-900 text-base">{item.name}</h4>
                  <p className="text-xs text-stone-500 line-clamp-2 mt-1 leading-relaxed">
                    {item.description || 'Tidak ada deskripsi.'}
                  </p>
                  {item.allergy_info && (
                    <p className="text-[11px] text-amber-700 mt-2 font-medium bg-amber-50 px-2 py-0.5 rounded-md inline-block">
                      ⚠️ {item.allergy_info}
                    </p>
                  )}
                </div>

                <div className="pt-3 border-t border-stone-100 flex items-center justify-between">
                  <span className="font-bold text-base text-amber-700">
                    {formatRupiah(item.base_price_rp)}
                  </span>

                  {/* Toggle availability switch */}
                  <button
                    onClick={() => toggleAvailability(item)}
                    className={`px-3 py-1 text-xs font-semibold rounded-full border transition-colors ${
                      item.is_available
                        ? 'bg-emerald-50 text-emerald-700 border-emerald-200 hover:bg-emerald-100'
                        : 'bg-red-50 text-red-700 border-red-200 hover:bg-red-100'
                    }`}
                  >
                    {item.is_available ? 'Tersedia' : 'Habis'}
                  </button>
                </div>
              </div>

              {/* Action buttons */}
              <div className="px-4 py-3 bg-stone-50 border-t border-stone-100 flex items-center justify-end gap-2">
                <button
                  onClick={() => openEditItemModal(item)}
                  className="p-1.5 text-stone-600 hover:text-amber-700 hover:bg-stone-200 rounded-lg transition-colors"
                  title="Edit Menu"
                >
                  <Edit2 className="w-4 h-4" />
                </button>
                <button
                  onClick={() => deleteItem(item)}
                  className="p-1.5 text-stone-400 hover:text-red-600 hover:bg-red-50 rounded-lg transition-colors"
                  title="Hapus Menu"
                >
                  <Trash2 className="w-4 h-4" />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Add / Edit Item Modal */}
      {showItemModal && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-lg w-full p-6 shadow-2xl space-y-5 animate-in fade-in zoom-in-95 duration-150 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b border-stone-100">
              <h3 className="font-bold text-lg text-stone-900">
                {editingItem ? 'Edit Menu' : 'Tambah Menu Baru'}
              </h3>
              <button
                onClick={() => setShowItemModal(false)}
                className="p-2 text-stone-400 hover:text-stone-700 rounded-full hover:bg-stone-100"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <form onSubmit={handleSaveItem} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  Nama Menu *
                </label>
                <input
                  type="text"
                  required
                  value={formName}
                  onChange={(e) => setFormName(e.target.value)}
                  placeholder="Mis. Kopi Susu Aren"
                  className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                />
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div>
                  <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                    Kategori *
                  </label>
                  <select
                    value={formCategoryId}
                    onChange={(e) => setFormCategoryId(e.target.value)}
                    className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                  >
                    {categories.map((cat) => (
                      <option key={cat.id} value={cat.id}>
                        {cat.name}
                      </option>
                    ))}
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                    Harga (Rp) *
                  </label>
                  <input
                    type="number"
                    required
                    min={0}
                    step={1000}
                    value={formPrice}
                    onChange={(e) => setFormPrice(Number(e.target.value))}
                    className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  Deskripsi Menu
                </label>
                <textarea
                  rows={2}
                  value={formDesc}
                  onChange={(e) => setFormDesc(e.target.value)}
                  placeholder="Penjelasan bahan & rasa kopi..."
                  className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  URL Foto Produk
                </label>
                <input
                  type="url"
                  value={formPhotoUrl}
                  onChange={(e) => setFormPhotoUrl(e.target.value)}
                  placeholder="https://images.unsplash.com/..."
                  className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  Informasi Alergen (Opsional)
                </label>
                <input
                  type="text"
                  value={formAllergy}
                  onChange={(e) => setFormAllergy(e.target.value)}
                  placeholder="Mis. Mengandung susu sapi, kacang"
                  className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                />
              </div>

              <div className="flex items-center gap-6 pt-2">
                <label className="flex items-center gap-2 cursor-pointer text-xs font-semibold text-stone-700">
                  <input
                    type="checkbox"
                    checked={formIsAvailable}
                    onChange={(e) => setFormIsAvailable(e.target.checked)}
                    className="w-4 h-4 text-amber-600 rounded border-stone-300 focus:ring-amber-500"
                  />
                  <span>Tersedia (Ready Stock)</span>
                </label>

                <label className="flex items-center gap-2 cursor-pointer text-xs font-semibold text-stone-700">
                  <input
                    type="checkbox"
                    checked={formIsActive}
                    onChange={(e) => setFormIsActive(e.target.checked)}
                    className="w-4 h-4 text-amber-600 rounded border-stone-300 focus:ring-amber-500"
                  />
                  <span>Tampilkan di Aplikasi</span>
                </label>
              </div>

              <div className="pt-4 flex gap-3">
                <button
                  type="button"
                  onClick={() => setShowItemModal(false)}
                  className="flex-1 py-2.5 bg-stone-100 hover:bg-stone-200 text-stone-700 font-semibold rounded-xl text-sm transition-colors"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="flex-1 py-2.5 bg-amber-600 hover:bg-amber-700 text-white font-semibold rounded-xl text-sm transition-colors shadow-xs flex items-center justify-center gap-2 disabled:opacity-50"
                >
                  <Check className="w-4 h-4" />
                  <span>{saving ? 'Menyimpan...' : 'Simpan Menu'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Category Modal */}
      {showCategoryModal && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-sm w-full p-6 shadow-2xl space-y-4 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between pb-2 border-b border-stone-100">
              <h3 className="font-bold text-base text-stone-900">Tambah Kategori Menu</h3>
              <button
                onClick={() => setShowCategoryModal(false)}
                className="p-1 text-stone-400 hover:text-stone-700 rounded-full"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleAddCategory} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  Nama Kategori
                </label>
                <input
                  type="text"
                  required
                  value={newCategoryName}
                  onChange={(e) => setNewCategoryName(e.target.value)}
                  placeholder="Mis. Dessert / Cemilan"
                  className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                />
              </div>

              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={() => setShowCategoryModal(false)}
                  className="flex-1 py-2 bg-stone-100 hover:bg-stone-200 text-stone-700 font-semibold rounded-xl text-xs transition-colors"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="flex-1 py-2 bg-amber-600 hover:bg-amber-700 text-white font-semibold rounded-xl text-xs transition-colors shadow-xs disabled:opacity-50"
                >
                  {saving ? 'Menyimpan...' : 'Simpan Kategori'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
