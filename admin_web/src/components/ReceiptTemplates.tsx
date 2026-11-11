import React, { useEffect, useState } from 'react';
import { supabase } from '../lib/supabase';
import { adminError } from '../lib/admin';
import type { ReceiptTemplate } from '../types';

export const ReceiptTemplates: React.FC = () => {
  const [templates, setTemplates] = useState<ReceiptTemplate[]>([]);
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const [saving, setSaving] = useState<string | null>(null);
  const [error, setError] = useState('');
  const [saved, setSaved] = useState('');

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const { data, error } = await supabase.from('receipt_template').select('*').order('id');
        if (error) throw error;
        if (!data || !['PAID', 'COMPLETED'].every((id) => data.some((row) => row.id === id))) {
          throw new Error('Template struk belum lengkap di server. Hubungi pengelola aplikasi.');
        }
        if (active) setTemplates(data as ReceiptTemplate[]);
      } catch (error) {
        if (active) setError(adminError(error, 'Gagal memuat template struk. Silakan coba lagi.'));
      } finally { if (active) setLoading(false); }
    };
    void load();
    return () => { active = false; };
  }, [reload]);

  const edit = (id: string, field: 'title' | 'header' | 'footer', value: string) => {
    setSaved('');
    setTemplates((rows) => rows.map((row) => row.id === id ? { ...row, [field]: value } : row));
  };
  const save = async (event: React.FormEvent, template: ReceiptTemplate) => {
    event.preventDefault();
    const payload = { title: template.title.trim(), header: template.header.trim(), footer: template.footer.trim() };
    if (!payload.title || !payload.header || !payload.footer) {
      setError('Judul, header, dan penutup wajib diisi.');
      return;
    }
    setSaving(template.id);
    setError('');
    setSaved('');
    try {
      const { data, error } = await supabase.from('receipt_template').update(payload).eq('id', template.id).select('*').single();
      if (error) throw error;
      if (!data) throw new Error('Template belum tersimpan. Silakan coba lagi.');
      setTemplates((rows) => rows.map((row) => row.id === template.id ? data as ReceiptTemplate : row));
      setSaved(`Template ${template.id === 'PAID' ? 'pesanan lunas' : 'pesanan selesai'} berhasil disimpan.`);
    } catch (error) { setError(adminError(error, 'Gagal menyimpan template. Silakan coba lagi.')); }
    finally { setSaving(null); }
  };

  return <section className="admin-panel space-y-5" aria-labelledby="receipt-templates-heading">
    <div><h3 id="receipt-templates-heading" className="admin-heading">Template struk WhatsApp</h3>
      <p className="text-sm text-stone-600">Ubah identitas dan pesan penutup struk. Nomor pesanan, rincian item, total, dan status tetap dicantumkan otomatis.</p></div>
    {error && <p role="alert" className="admin-error">{error}</p>}
    {saved && <p role="status" className="text-sm text-emerald-800">{saved}</p>}
    {loading ? <p role="status" className="text-sm text-stone-600">Memuat template struk...</p>
      : templates.length === 0 ? <button type="button" className="admin-button" onClick={() => { setLoading(true); setError(''); setReload((value) => value + 1); }}>Coba muat template lagi</button>
      : templates.map((template) => <form key={template.id} onSubmit={(event) => void save(event, template)} className="grid gap-5 border-t border-stone-200 pt-5 lg:grid-cols-2">
        <fieldset disabled={saving !== null} className="min-w-0 space-y-4">
          <legend className="mb-3 font-semibold text-stone-900">{template.id === 'PAID' ? 'Struk pesanan lunas' : 'Struk pesanan selesai'}</legend>
          <div><label htmlFor={`title-${template.id}`} className="admin-label">Judul struk</label>
            <input id={`title-${template.id}`} required maxLength={120} className="admin-input" value={template.title} onChange={(event) => edit(template.id, 'title', event.target.value)} /></div>
          <div><label htmlFor={`header-${template.id}`} className="admin-label">Header / identitas kafe</label>
            <textarea id={`header-${template.id}`} required maxLength={1000} rows={3} className="admin-input" value={template.header} onChange={(event) => edit(template.id, 'header', event.target.value)} /></div>
          <div><label htmlFor={`footer-${template.id}`} className="admin-label">Pesan penutup</label>
            <textarea id={`footer-${template.id}`} required maxLength={1000} rows={3} className="admin-input" value={template.footer} onChange={(event) => edit(template.id, 'footer', event.target.value)} /></div>
          <button className="admin-primary">{saving === template.id ? 'Menyimpan...' : 'Simpan template'}</button>
        </fieldset>
        <div className="min-w-0"><p className="admin-label">Pratinjau contoh struk</p>
          <pre className="whitespace-pre-wrap break-words rounded-lg border border-stone-300 bg-stone-50 p-4 text-sm leading-relaxed text-stone-800">{`${template.header}\n----------------------------------------\n*${template.title}*\nNo. Pesanan: CONTOH-001\nMeja: Meja contoh\nNama: Pelanggan contoh\nPembayaran: LUNAS\n----------------------------------------\n1x Menu contoh: Rp20.000\nTOTAL BAYAR: Rp22.000\nStatus: ${template.id === 'PAID' ? 'LUNAS' : 'SELESAI'}\n\n${template.footer}`}</pre>
        </div>
      </form>)}
  </section>;
};
