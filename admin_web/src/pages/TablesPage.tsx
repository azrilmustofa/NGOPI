import React, { useEffect, useRef, useState } from 'react';
import { Download, Edit2, Eye, Plus, Printer, QrCode, RefreshCw, Trash2, X } from 'lucide-react';
import { QRCodeSVG } from 'qrcode.react';
import { supabase } from '../lib/supabase';
import type { CafeTable } from '../types';

type TableQr = { table_id: string; token: string };

export const TablesPage: React.FC = () => {
  const [tables, setTables] = useState<CafeTable[]>([]);
  const [tokens, setTokens] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [rotatingId, setRotatingId] = useState<string | null>(null);
  const [showAddModal, setShowAddModal] = useState(false);
  const [editingTable, setEditingTable] = useState<CafeTable | null>(null);
  const [selectedTableForQr, setSelectedTableForQr] = useState<CafeTable | null>(null);
  const [tableNumber, setTableNumber] = useState(1);
  const [customLabel, setCustomLabel] = useState('');
  const [editIsActive, setEditIsActive] = useState(true);
  const qrDownloadRef = useRef<HTMLDivElement>(null);

  const qrValue = (tableId: string) => tokens[tableId] ? `NGOPI:${tokens[tableId]}` : '';

  const loadTables = async () => {
    setLoading(true);
    try {
      const [tableResult, qrResult] = await Promise.all([
        supabase.from('cafe_table').select('*').order('id', { ascending: true }),
        supabase.from('table_qr').select('table_id, token'),
      ]);
      if (tableResult.error) throw tableResult.error;
      if (qrResult.error) throw new Error(`QR meja belum siap: ${qrResult.error.message}`);
      setTables((tableResult.data ?? []) as CafeTable[]);
      setTokens(Object.fromEntries(((qrResult.data ?? []) as TableQr[]).map((row) => [row.table_id, row.token])));
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Gagal memuat meja dan QR');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { void loadTables(); }, []);

  const openAddModal = () => {
    const numbers = tables.map((table) => Number(table.id.match(/\d+/)?.[0] ?? 0));
    const next = numbers.length ? Math.max(...numbers) + 1 : 1;
    setTableNumber(next);
    setCustomLabel(`Meja ${next.toString().padStart(2, '0')}`);
    setShowAddModal(true);
  };

  const addTable = async (event: React.FormEvent) => {
    event.preventDefault();
    const id = `TBL-${tableNumber.toString().padStart(2, '0')}`;
    const label = customLabel.trim() || `Meja ${tableNumber.toString().padStart(2, '0')}`;
    setSaving(true);
    try {
      const inserted = await supabase.from('cafe_table').insert({ id, label, is_active: true });
      if (inserted.error) throw inserted.error;
      const rotated = await supabase.rpc('rotate_table_qr', { p_table_id: id });
      if (rotated.error) throw new Error(`Meja tersimpan, tetapi QR gagal dibuat: ${rotated.error.message}`);
      setShowAddModal(false);
      await loadTables();
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Gagal menambah meja');
    } finally {
      setSaving(false);
    }
  };

  const openEditModal = (table: CafeTable) => {
    setEditingTable(table);
    setCustomLabel(table.label);
    setEditIsActive(table.is_active);
  };

  const updateTable = async (event: React.FormEvent) => {
    event.preventDefault();
    if (!editingTable) return;
    setSaving(true);
    try {
      const result = await supabase.from('cafe_table').update({
        label: customLabel.trim() || editingTable.label,
        is_active: editIsActive,
      }).eq('id', editingTable.id);
      if (result.error) throw result.error;
      setEditingTable(null);
      await loadTables();
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Gagal memperbarui meja');
    } finally {
      setSaving(false);
    }
  };

  const toggleTableActive = async (table: CafeTable) => {
    const next = !table.is_active;
    setTables((current) => current.map((item) => item.id === table.id ? { ...item, is_active: next } : item));
    const result = await supabase.from('cafe_table').update({ is_active: next }).eq('id', table.id);
    if (result.error) {
      alert(`Gagal mengubah status meja: ${result.error.message}`);
      await loadTables();
    }
  };

  const rotateQr = async (table: CafeTable) => {
    const action = tokens[table.id] ? 'membuat QR lama langsung tidak berlaku' : 'membuat QR baru';
    if (!confirm(`Buat ulang QR ${table.label}? Tindakan ini akan ${action}.`)) return;
    setRotatingId(table.id);
    try {
      const result = await supabase.rpc('rotate_table_qr', { p_table_id: table.id });
      if (result.error || typeof result.data !== 'string') throw result.error ?? new Error('Token QR tidak diterima');
      setTokens((current) => ({ ...current, [table.id]: result.data as string }));
      alert(`QR baru ${table.label} berhasil dibuat. QR lama sudah tidak berlaku.`);
    } catch (error) {
      alert(error instanceof Error ? error.message : 'Gagal membuat ulang QR');
    } finally {
      setRotatingId(null);
    }
  };

  const deleteTable = async (table: CafeTable) => {
    if (!confirm(`Hapus meja "${table.label}" (${table.id})?`)) return;
    const result = await supabase.from('cafe_table').delete().eq('id', table.id);
    if (result.error) alert(`Gagal menghapus meja: ${result.error.message}`);
    else await loadTables();
  };

  const downloadQr = (table: CafeTable) => {
    const svg = qrDownloadRef.current?.querySelector('svg');
    if (!svg) return alert('QR belum tersedia');
    const source = new XMLSerializer().serializeToString(svg);
    const blobUrl = URL.createObjectURL(new Blob([source], { type: 'image/svg+xml;charset=utf-8' }));
    const image = new Image();
    image.onload = () => {
      const canvas = document.createElement('canvas');
      canvas.width = 1200;
      canvas.height = 1200;
      const context = canvas.getContext('2d');
      if (!context) return;
      context.fillStyle = '#ffffff';
      context.fillRect(0, 0, canvas.width, canvas.height);
      context.drawImage(image, 0, 0, canvas.width, canvas.height);
      URL.revokeObjectURL(blobUrl);
      canvas.toBlob((blob) => {
        if (!blob) return;
        const url = URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${table.id}-${table.label.replace(/[^a-z0-9]+/gi, '-').toLowerCase()}.png`;
        link.click();
        URL.revokeObjectURL(url);
      }, 'image/png');
    };
    image.onerror = () => { URL.revokeObjectURL(blobUrl); alert('Gagal membuat file QR'); };
    image.src = blobUrl;
  };

  const selectedValue = selectedTableForQr ? qrValue(selectedTableForQr.id) : '';

  return (
    <div className="admin-page space-y-6">
      <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div>
          <h3 className="text-lg font-bold text-stone-900">Daftar Meja & Kode QR</h3>
          <p className="text-sm text-stone-600">QR dibuat dari token server. Membuat ulang token otomatis menolak semua QR lama.</p>
        </div>
        <button onClick={openAddModal} className="admin-primary"><Plus className="h-4 w-4" />Tambah Meja</button>
      </div>

      {loading ? <div className="admin-empty">Memuat meja dan token QR…</div> : (
        <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
          {tables.map((table) => {
            const value = qrValue(table.id);
            return (
              <section key={table.id} className="admin-panel flex flex-col justify-between gap-4">
                <div>
                  <div className="flex items-center justify-between gap-3">
                    <span className="rounded bg-stone-100 px-2 py-1 font-mono text-xs font-bold">{table.id}</span>
                    <button onClick={() => void toggleTableActive(table)} className={`rounded-full border px-3 py-1 text-xs font-bold ${table.is_active ? 'border-emerald-300 bg-emerald-50 text-emerald-800' : 'border-stone-300 bg-stone-100 text-stone-600'}`}>
                      {table.is_active ? 'Aktif' : 'Nonaktif'}
                    </button>
                  </div>
                  <h4 className="mt-3 font-bold text-stone-900">{table.label}</h4>
                  <div className="mt-3 flex min-h-40 flex-col items-center justify-center rounded-xl border border-stone-200 bg-stone-50 p-3">
                    {value ? <QRCodeSVG value={value} size={128} level="H" includeMargin /> : <><QrCode className="h-12 w-12 text-stone-300" /><span className="mt-2 text-xs text-red-700">QR belum dibuat</span></>}
                    {value && <span className="mt-2 text-xs font-medium text-stone-600">Token server aktif</span>}
                  </div>
                </div>
                <div className="flex flex-wrap gap-2 border-t border-stone-200 pt-3">
                  <button disabled={!value} onClick={() => setSelectedTableForQr(table)} className="admin-button flex-1"><Eye className="h-4 w-4" />Lihat</button>
                  <button disabled={rotatingId === table.id} onClick={() => void rotateQr(table)} className="admin-button flex-1"><RefreshCw className="h-4 w-4" />{value ? 'Buat Ulang' : 'Buat QR'}</button>
                  <button onClick={() => openEditModal(table)} className="admin-button" aria-label={`Edit ${table.label}`}><Edit2 className="h-4 w-4" /></button>
                  <button onClick={() => void deleteTable(table)} className="admin-button text-red-700" aria-label={`Hapus ${table.label}`}><Trash2 className="h-4 w-4" /></button>
                </div>
              </section>
            );
          })}
        </div>
      )}

      {showAddModal && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <form onSubmit={addTable} className="admin-panel w-full max-w-md space-y-4 shadow-2xl">
            <div className="flex items-center justify-between"><h3 className="admin-heading">Tambah Meja Baru</h3><button type="button" onClick={() => setShowAddModal(false)}><X className="h-5 w-5" /></button></div>
            <div className="grid grid-cols-2 gap-3">
              <label className="admin-label">Nomor meja<input className="admin-input mt-1" type="number" min={1} required value={tableNumber} onChange={(event) => { const number = Number(event.target.value) || 1; setTableNumber(number); setCustomLabel(`Meja ${number.toString().padStart(2, '0')}`); }} /></label>
              <label className="admin-label">ID meja<input className="admin-input mt-1 font-mono" disabled value={`TBL-${tableNumber.toString().padStart(2, '0')}`} /></label>
            </div>
            <label className="admin-label">Label meja<input className="admin-input mt-1" required maxLength={100} value={customLabel} onChange={(event) => setCustomLabel(event.target.value)} /></label>
            <p className="rounded-lg bg-amber-50 p-3 text-sm text-amber-900">Token QR dibuat otomatis setelah meja disimpan.</p>
            <div className="flex gap-2"><button type="button" onClick={() => setShowAddModal(false)} className="admin-button flex-1">Batal</button><button disabled={saving} className="admin-primary flex-1">{saving ? 'Menyimpan…' : 'Simpan Meja'}</button></div>
          </form>
        </div>
      )}

      {editingTable && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <form onSubmit={updateTable} className="admin-panel w-full max-w-md space-y-4 shadow-2xl">
            <div className="flex items-center justify-between"><h3 className="admin-heading">Edit {editingTable.id}</h3><button type="button" onClick={() => setEditingTable(null)}><X className="h-5 w-5" /></button></div>
            <label className="admin-label">Label meja<input className="admin-input mt-1" required maxLength={100} value={customLabel} onChange={(event) => setCustomLabel(event.target.value)} /></label>
            <label className="flex items-center justify-between rounded-lg border border-stone-300 p-3 text-sm font-semibold">Meja aktif<input type="checkbox" checked={editIsActive} onChange={(event) => setEditIsActive(event.target.checked)} /></label>
            <p className="text-sm text-stone-600">QR dikelola melalui tombol Buat Ulang pada kartu meja.</p>
            <div className="flex gap-2"><button type="button" onClick={() => setEditingTable(null)} className="admin-button flex-1">Batal</button><button disabled={saving} className="admin-primary flex-1">{saving ? 'Menyimpan…' : 'Simpan'}</button></div>
          </form>
        </div>
      )}

      {selectedTableForQr && selectedValue && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4">
          <div className="admin-panel w-full max-w-sm space-y-4 shadow-2xl">
            <div className="no-print flex items-center justify-between"><h3 className="admin-heading">Stiker QR Meja</h3><button onClick={() => setSelectedTableForQr(null)}><X className="h-5 w-5" /></button></div>
            <div id="table-qr-print-area" className="rounded-2xl border-2 border-dashed border-amber-400 bg-amber-50 p-6 text-center">
              <img src="/logo.webp" alt="Kopi Inggil" className="mx-auto mb-2 h-12 w-12 rounded-xl" />
              <h4 className="font-bold">KOPI INGGIL</h4>
              <p className="mb-3 text-xs text-stone-600">Scan untuk pesan dan bayar dari meja</p>
              <div ref={qrDownloadRef} className="mx-auto flex w-fit items-center justify-center bg-white p-3"><QRCodeSVG value={selectedValue} size={220} level="H" includeMargin /></div>
              <div className="mt-3 text-lg font-bold uppercase">{selectedTableForQr.label}</div>
              <div className="font-mono text-xs text-stone-600">{selectedTableForQr.id}</div>
            </div>
            <div className="no-print flex gap-2">
              <button onClick={() => downloadQr(selectedTableForQr)} className="admin-button flex-1"><Download className="h-4 w-4" />Unduh PNG</button>
              <button onClick={() => window.print()} className="admin-primary flex-1"><Printer className="h-4 w-4" />Cetak</button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};
