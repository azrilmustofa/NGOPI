import React, { useEffect, useState, useRef } from 'react';
import {
  Plus,
  QrCode,
  Printer,
  X,
  Eye,
  Upload,
  Edit2,
  Trash2,
} from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { supabase } from '../lib/supabase';
import type { CafeTable } from '../types';

export const TablesPage: React.FC = () => {
  const [tables, setTables] = useState<CafeTable[]>([]);
  const [loading, setLoading] = useState(true);
  const [showAddModal, setShowAddModal] = useState(false);
  const [showEditModal, setShowEditModal] = useState(false);
  const [editingTable, setEditingTable] = useState<CafeTable | null>(null);
  const [selectedTableForQr, setSelectedTableForQr] = useState<CafeTable | null>(null);

  // Form states
  const [tableNumber, setTableNumber] = useState<number>(1);
  const [customLabel, setCustomLabel] = useState<string>('');
  const [qrImageUrl, setQrImageUrl] = useState<string>('');
  const [editIsActive, setEditIsActive] = useState<boolean>(true);
  const [saving, setSaving] = useState(false);

  const printAreaRef = useRef<HTMLDivElement>(null);

  const loadTables = async () => {
    try {
      const { data, error } = await supabase
        .from('cafe_table')
        .select('*')
        .order('id', { ascending: true });

      if (error) throw error;
      if (data) setTables(data as CafeTable[]);
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadTables();
  }, []);

  const openAddModal = () => {
    // Cari nomor meja berikutnya yang belum terpakai
    const existingNums = tables.map((t) => {
      const m = t.id.match(/\d+/);
      return m ? parseInt(m[0], 10) : 0;
    });
    const nextNum = existingNums.length > 0 ? Math.max(...existingNums) + 1 : 1;

    setTableNumber(nextNum);
    setCustomLabel(`Meja ${nextNum.toString().padStart(2, '0')}`);
    setQrImageUrl('');
    setShowAddModal(true);
  };

  const openEditModal = (table: CafeTable) => {
    setEditingTable(table);
    const m = table.id.match(/\d+/);
    setTableNumber(m ? parseInt(m[0], 10) : 1);
    setCustomLabel(table.label);
    setQrImageUrl(table.qr_image_url || '');
    setEditIsActive(table.is_active);
    setShowEditModal(true);
  };

  // Upload gambar QR dari perangkat (dikonversi ke data URL / disimpan langsung ke database)
  const handleFileUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (file.size > 2 * 1024 * 1024) {
      alert('Ukuran gambar maksimal 2MB');
      return;
    }

    const reader = new FileReader();
    reader.onload = () => {
      if (typeof reader.result === 'string') {
        setQrImageUrl(reader.result);
      }
    };
    reader.readAsDataURL(file);
  };

  const handleAddTable = async (e: React.FormEvent) => {
    e.preventDefault();
    const id = `TBL-${tableNumber.toString().padStart(2, '0')}`;
    const label = customLabel.trim() || `Meja ${tableNumber.toString().padStart(2, '0')}`;

    setSaving(true);
    try {
      // Siapkan payload
      const payload: any = {
        id: id,
        label: label,
        is_active: true,
      };

      // Coba masukkan dengan qr_image_url jika ada
      if (qrImageUrl) {
        payload.qr_image_url = qrImageUrl;
      }

      let { error } = await supabase.from('cafe_table').insert(payload);

      // Fallback jika kolom qr_image_url belum dieksekusi di skema Postgres
      if (error && error.message.includes('qr_image_url')) {
        delete payload.qr_image_url;
        const res = await supabase.from('cafe_table').insert(payload);
        error = res.error;
      }

      if (error) throw error;

      setShowAddModal(false);
      loadTables();
    } catch (err: any) {
      alert('Gagal menambah meja: ' + (err.message || 'Terjadi kesalahan'));
    } finally {
      setSaving(false);
    }
  };

  const handleUpdateTable = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingTable) return;

    setSaving(true);
    try {
      const payload: any = {
        label: customLabel.trim() || editingTable.label,
        is_active: editIsActive,
      };
      if (qrImageUrl) {
        payload.qr_image_url = qrImageUrl;
      }

      let { error } = await supabase
        .from('cafe_table')
        .update(payload)
        .eq('id', editingTable.id);

      if (error && error.message.includes('qr_image_url')) {
        delete payload.qr_image_url;
        const res = await supabase
          .from('cafe_table')
          .update(payload)
          .eq('id', editingTable.id);
        error = res.error;
      }

      if (error) throw error;

      setShowEditModal(false);
      loadTables();
    } catch (err: any) {
      alert('Gagal memperbarui meja: ' + err.message);
    } finally {
      setSaving(false);
    }
  };

  const toggleTableActive = async (table: CafeTable) => {
    const nextVal = !table.is_active;
    setTables((prev) =>
      prev.map((t) => (t.id === table.id ? { ...t, is_active: nextVal } : t))
    );

    const { error } = await supabase
      .from('cafe_table')
      .update({ is_active: nextVal })
      .eq('id', table.id);

    if (error) {
      alert('Gagal update status meja: ' + error.message);
      loadTables();
    }
  };

  const deleteTable = async (table: CafeTable) => {
    if (!confirm(`Hapus meja "${table.label}" (${table.id})?`)) return;

    const { error } = await supabase.from('cafe_table').delete().eq('id', table.id);
    if (error) {
      alert('Gagal menghapus meja: ' + error.message);
    } else {
      setTables((prev) => prev.filter((t) => t.id !== table.id));
    }
  };

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="p-8 max-w-7xl mx-auto space-y-6">
      {/* Top Bar */}
      <div className="flex flex-col sm:flex-row gap-4 items-center justify-between">
        <div>
          <h3 className="text-lg font-bold text-stone-900">Daftar Meja & Kode QR</h3>
          <p className="text-xs text-stone-500">
            ID meja otomatis mengikuti nomor meja (contoh: Meja 1 → <code>TBL-01</code>). Anda dapat memasukkan gambar QR khusus atau menggunakan QR otomatis sistem.
          </p>
        </div>

        <button
          onClick={openAddModal}
          className="inline-flex items-center gap-2 px-4 py-2 bg-amber-600 hover:bg-amber-700 text-white text-sm font-medium rounded-xl transition-colors shadow-xs"
        >
          <Plus className="w-4 h-4" />
          <span>Tambah Meja Baru</span>
        </button>
      </div>

      {/* Tables Grid */}
      {loading ? (
        <div className="py-20 text-center text-sm text-stone-400">Memuat data meja...</div>
      ) : tables.length === 0 ? (
        <div className="bg-white rounded-2xl border border-stone-200 p-16 text-center shadow-xs">
          <QrCode className="w-12 h-12 text-stone-300 mx-auto mb-3" />
          <h4 className="text-base font-bold text-stone-800">Belum Ada Meja Terdaftar</h4>
          <p className="text-xs text-stone-400 max-w-sm mx-auto mt-1">
            Klik tombol "Tambah Meja Baru" untuk mendaftarkan meja kafe.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 md:grid-cols-3 lg:grid-cols-4 gap-6">
          {tables.map((table) => {
            const hasCustomQr = Boolean(table.qr_image_url);

            return (
              <div
                key={table.id}
                className="bg-white rounded-2xl border border-stone-200 p-5 shadow-xs hover:border-amber-400 transition-all flex flex-col justify-between"
              >
                <div>
                  <div className="flex items-center justify-between mb-4">
                    <span className="font-mono text-xs font-bold px-2 py-0.5 rounded bg-stone-100 text-stone-700">
                      {table.id}
                    </span>
                    <button
                      type="button"
                      onClick={() => toggleTableActive(table)}
                      className={`cursor-pointer px-3 py-1 text-xs font-bold rounded-full border transition-all shadow-xs flex items-center gap-1.5 ${
                        table.is_active
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-300 hover:bg-emerald-100'
                          : 'bg-stone-100 text-stone-500 border-stone-300 hover:bg-stone-200'
                      }`}
                      title="Klik untuk mengubah status aktif / nonaktif meja"
                    >
                      <span className={`w-2 h-2 rounded-full ${table.is_active ? 'bg-emerald-500' : 'bg-stone-400'}`} />
                      <span>{table.is_active ? 'Aktif' : 'Nonaktif'}</span>
                    </button>
                  </div>

                  <h4 className="font-bold text-stone-900 text-base mb-3">{table.label}</h4>

                  {/* QR Box Preview */}
                  <div className="bg-stone-50 p-4 rounded-xl flex flex-col items-center justify-center border border-stone-100 mb-4 min-h-[160px]">
                    {hasCustomQr ? (
                      <img
                        src={table.qr_image_url!}
                        alt={`QR ${table.label}`}
                        className="w-28 h-28 object-contain rounded-lg"
                      />
                    ) : (
                      <QRCodeSVG value={table.id} size={110} level="M" />
                    )}
                    <span className="text-[10px] text-stone-400 mt-2 font-mono">
                      {hasCustomQr ? 'QR Khusus Diupload' : 'QR Otomatis (' + table.id + ')'}
                    </span>
                  </div>
                </div>

                {/* Actions */}
                <div className="pt-3 border-t border-stone-100 flex items-center justify-between">
                  <div className="flex items-center gap-2">
                    <button
                      onClick={() => setSelectedTableForQr(table)}
                      className="inline-flex items-center gap-1 text-xs font-semibold text-amber-600 hover:text-amber-700"
                      title="Lihat / Cetak"
                    >
                      <Eye className="w-3.5 h-3.5" />
                      <span>Cetak</span>
                    </button>
                    <button
                      onClick={() => openEditModal(table)}
                      className="inline-flex items-center gap-1 text-xs font-medium text-stone-600 hover:text-stone-900"
                      title="Edit QR atau Label"
                    >
                      <Edit2 className="w-3 h-3" />
                      <span>Edit</span>
                    </button>
                  </div>

                  <button
                    onClick={() => deleteTable(table)}
                    className="text-xs text-stone-400 hover:text-red-600 transition-colors"
                  >
                    <Trash2 className="w-3.5 h-3.5" />
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Add Table Modal */}
      {showAddModal && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full p-6 shadow-2xl space-y-4 animate-in fade-in zoom-in-95 duration-150 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-2 border-b border-stone-100">
              <h3 className="font-bold text-base text-stone-900">Tambah Meja & QR Baru</h3>
              <button
                onClick={() => setShowAddModal(false)}
                className="p-1 text-stone-400 hover:text-stone-700 rounded-full"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleAddTable} className="space-y-4">
              {/* Nomor Meja -> ID otomatis */}
              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                    Nomor Meja *
                  </label>
                  <input
                    type="number"
                    min={1}
                    required
                    value={tableNumber}
                    onChange={(e) => {
                      const num = parseInt(e.target.value, 10) || 1;
                      setTableNumber(num);
                      setCustomLabel(`Meja ${num.toString().padStart(2, '0')}`);
                    }}
                    className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm font-bold focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                    ID di Database
                  </label>
                  <input
                    type="text"
                    disabled
                    value={`TBL-${tableNumber.toString().padStart(2, '0')}`}
                    className="w-full px-3.5 py-2 bg-stone-100 border border-stone-200 rounded-xl text-sm font-mono text-stone-600 font-bold"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  Nama / Label Meja *
                </label>
                <input
                  type="text"
                  required
                  value={customLabel}
                  onChange={(e) => setCustomLabel(e.target.value)}
                  placeholder="Mis. Meja 03 — Outdoor"
                  className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                />
              </div>

              {/* Upload QR Image */}
              <div className="space-y-2 pt-2 border-t border-stone-100">
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider">
                  Gambar QR Code Meja
                </label>
                <p className="text-[11px] text-stone-400">
                  Upload file foto/stiker QR yang sudah Anda miliki, atau kosongkan untuk generate QR otomatis.
                </p>

                {qrImageUrl ? (
                  <div className="p-3 bg-stone-50 rounded-xl border border-stone-200 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <img src={qrImageUrl} alt="QR Preview" className="w-14 h-14 object-contain rounded-lg bg-white border" />
                      <span className="text-xs text-emerald-700 font-semibold">Gambar QR Siap Disimpan</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => setQrImageUrl('')}
                      className="text-xs text-red-600 hover:underline"
                    >
                      Hapus
                    </button>
                  </div>
                ) : (
                  <label className="border-2 border-dashed border-stone-200 hover:border-amber-500 rounded-2xl p-4 flex flex-col items-center justify-center cursor-pointer bg-stone-50/50 hover:bg-amber-50/30 transition-colors">
                    <Upload className="w-6 h-6 text-stone-400 mb-1" />
                    <span className="text-xs font-medium text-stone-600">Pilih file gambar QR</span>
                    <span className="text-[10px] text-stone-400 mt-0.5">PNG, JPG, WEBP (Maks. 2MB)</span>
                    <input
                      type="file"
                      accept="image/*"
                      onChange={handleFileUpload}
                      className="hidden"
                    />
                  </label>
                )}
              </div>

              <div className="flex gap-2 pt-3">
                <button
                  type="button"
                  onClick={() => setShowAddModal(false)}
                  className="flex-1 py-2 bg-stone-100 hover:bg-stone-200 text-stone-700 font-semibold rounded-xl text-xs transition-colors"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="flex-1 py-2 bg-amber-600 hover:bg-amber-700 text-white font-semibold rounded-xl text-xs transition-colors shadow-xs disabled:opacity-50"
                >
                  {saving ? 'Menyimpan...' : 'Simpan Meja'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* Edit Table Modal */}
      {showEditModal && editingTable && (
        <div className="fixed inset-0 z-50 bg-black/50 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-md w-full p-6 shadow-2xl space-y-4 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between pb-2 border-b border-stone-100">
              <h3 className="font-bold text-base text-stone-900">Edit Meja & QR: {editingTable.id}</h3>
              <button
                onClick={() => setShowEditModal(false)}
                className="p-1 text-stone-400 hover:text-stone-700 rounded-full"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleUpdateTable} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  Label Meja *
                </label>
                <input
                  type="text"
                  required
                  value={customLabel}
                  onChange={(e) => setCustomLabel(e.target.value)}
                  className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                />
              </div>

              {/* Status Meja Toggle di Modal Edit */}
              <div className="flex items-center justify-between p-3 bg-stone-50 rounded-xl border border-stone-200">
                <div>
                  <div className="text-xs font-semibold text-stone-800">Status Meja</div>
                  <div className="text-[11px] text-stone-400">Meja aktif dapat dipesan oleh pelanggan</div>
                </div>
                <label className="relative inline-flex items-center cursor-pointer">
                  <input
                    type="checkbox"
                    checked={editIsActive}
                    onChange={(e) => setEditIsActive(e.target.checked)}
                    className="sr-only peer"
                  />
                  <div className="w-11 h-6 bg-stone-300 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-stone-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-emerald-600"></div>
                </label>
              </div>

              {/* Upload QR Image */}
              <div className="space-y-2 pt-2 border-t border-stone-100">
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider">
                  Ganti / Masukkan Gambar QR Code
                </label>

                {qrImageUrl ? (
                  <div className="p-3 bg-stone-50 rounded-xl border border-stone-200 flex items-center justify-between">
                    <div className="flex items-center gap-3">
                      <img src={qrImageUrl} alt="QR Preview" className="w-14 h-14 object-contain rounded-lg bg-white border" />
                      <span className="text-xs text-emerald-700 font-semibold">Gambar QR Terpasang</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => setQrImageUrl('')}
                      className="text-xs text-red-600 hover:underline"
                    >
                      Hapus
                    </button>
                  </div>
                ) : (
                  <label className="border-2 border-dashed border-stone-200 hover:border-amber-500 rounded-2xl p-4 flex flex-col items-center justify-center cursor-pointer bg-stone-50/50 hover:bg-amber-50/30 transition-colors">
                    <Upload className="w-6 h-6 text-stone-400 mb-1" />
                    <span className="text-xs font-medium text-stone-600">Pilih gambar QR baru</span>
                    <input
                      type="file"
                      accept="image/*"
                      onChange={handleFileUpload}
                      className="hidden"
                    />
                  </label>
                )}
              </div>

              <div className="flex gap-2 pt-3">
                <button
                  type="button"
                  onClick={() => setShowEditModal(false)}
                  className="flex-1 py-2 bg-stone-100 hover:bg-stone-200 text-stone-700 font-semibold rounded-xl text-xs transition-colors"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="flex-1 py-2 bg-amber-600 hover:bg-amber-700 text-white font-semibold rounded-xl text-xs transition-colors shadow-xs disabled:opacity-50"
                >
                  {saving ? 'Menyimpan...' : 'Perbarui Meja'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}

      {/* QR Print / Preview Modal */}
      {selectedTableForQr && (
        <div className="fixed inset-0 z-50 bg-black/60 backdrop-blur-xs flex items-center justify-center p-4">
          <div className="bg-white rounded-3xl max-w-sm w-full p-6 shadow-2xl space-y-5 animate-in fade-in zoom-in-95 duration-150">
            <div className="flex items-center justify-between pb-2 border-b border-stone-100">
              <h3 className="font-bold text-base text-stone-900">Stiker QR Meja</h3>
              <button
                onClick={() => setSelectedTableForQr(null)}
                className="p-1 text-stone-400 hover:text-stone-700 rounded-full"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* Print Card Template */}
            <div
              ref={printAreaRef}
              className="bg-amber-50/50 border-2 border-dashed border-amber-300 rounded-2xl p-6 flex flex-col items-center text-center shadow-xs"
            >
              <img
                src="/logo.webp"
                alt="Kopi Inggil"
                className="w-12 h-12 rounded-xl object-cover bg-[#FAF8F0] p-1 border border-amber-200 mb-2 shadow-xs"
              />
              <h4 className="font-bold text-stone-900 text-base">KOPI INGGIL</h4>
              <p className="text-[11px] text-stone-500 mb-4">Pesan & Bayar Langsung Dari Meja</p>

              <div className="p-3 bg-white rounded-xl shadow-xs border border-stone-200 mb-3 flex items-center justify-center min-w-[160px] min-h-[160px]">
                {selectedTableForQr.qr_image_url ? (
                  <img
                    src={selectedTableForQr.qr_image_url}
                    alt={selectedTableForQr.label}
                    className="w-40 h-40 object-contain rounded-lg"
                  />
                ) : (
                  <QRCodeSVG
                    value={selectedTableForQr.id}
                    size={150}
                    level="H"
                    includeMargin={true}
                  />
                )}
              </div>

              <div className="font-bold text-lg text-stone-900 tracking-wide uppercase">
                {selectedTableForQr.label}
              </div>
              <div className="font-mono text-xs text-stone-400 mt-0.5">
                Kode Meja: {selectedTableForQr.id}
              </div>
            </div>

            <div className="flex gap-2">
              <button
                onClick={handlePrint}
                className="flex-1 py-2.5 bg-stone-900 hover:bg-stone-800 text-white font-semibold rounded-xl text-xs transition-colors flex items-center justify-center gap-2"
              >
                <Printer className="w-4 h-4" />
                <span>Cetak Lembar QR</span>
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
