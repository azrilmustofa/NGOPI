import React, { useEffect, useState } from 'react';
import { ReceiptTemplates } from '../components/ReceiptTemplates';
import { adminError } from '../lib/admin';
import { supabase, DEFAULT_SUPABASE_URL, DEFAULT_SUPABASE_KEY, updateSupabaseConfig, resetSupabaseConfig } from '../lib/supabase';

export const SettingsPage: React.FC = () => {
  const [taxPct, setTaxPct] = useState(10);
  const [servicePct, setServicePct] = useState(0);
  const [loading, setLoading] = useState(true);
  const [reload, setReload] = useState(0);
  const [saving, setSaving] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [error, setError] = useState('');
  const [saved, setSaved] = useState(false);
  const [supabaseUrl, setSupabaseUrl] = useState(() => localStorage.getItem('ngopi_admin_supabase_url') || import.meta.env.VITE_SUPABASE_URL || DEFAULT_SUPABASE_URL);
  const [supabaseKey, setSupabaseKey] = useState(() => localStorage.getItem('ngopi_admin_supabase_key') || import.meta.env.VITE_SUPABASE_ANON_KEY || DEFAULT_SUPABASE_KEY);
  const [connectionError, setConnectionError] = useState('');

  useEffect(() => {
    let active = true;
    const load = async () => {
      try {
        const { data, error } = await supabase.from('app_settings').select('*').eq('id', 'default').maybeSingle();
        if (error) throw error;
        if (active && data) { setTaxPct(Number(data.tax_percentage)); setServicePct(Number(data.service_charge_percentage)); }
      } catch (error) {
        if (active) setLoadError(adminError(error, 'Gagal memuat pengaturan biaya. Coba muat lagi sebelum mengubahnya.'));
      } finally { if (active) setLoading(false); }
    };
    void load();
    return () => { active = false; };
  }, [reload]);

  const saveFees = async (event: React.FormEvent) => {
    event.preventDefault();
    if (![taxPct, servicePct].every((value) => Number.isFinite(value) && value >= 0 && value <= 50)) {
      setError('Pajak dan biaya layanan harus berupa angka antara 0 dan 50%.');
      return;
    }
    setSaving(true); setSaved(false); setError('');
    try {
      const { data, error } = await supabase.from('app_settings').upsert({
        id: 'default', tax_percentage: taxPct, service_charge_percentage: servicePct,
      }).select('*').single();
      if (error) throw error;
      if (!data) throw new Error('Pengaturan belum tersimpan. Silakan coba lagi.');
      setTaxPct(Number(data.tax_percentage)); setServicePct(Number(data.service_charge_percentage));
      setSaved(true);
    } catch (error) { setError(adminError(error, 'Gagal menyimpan biaya. Silakan coba lagi.')); }
    finally { setSaving(false); }
  };

  const saveConnection = (event: React.FormEvent) => {
    event.preventDefault(); setConnectionError('');
    try { updateSupabaseConfig(supabaseUrl.trim(), supabaseKey.trim()); window.location.reload(); }
    catch (error) { setConnectionError(adminError(error, 'Koneksi tidak valid. Periksa URL dan public key.')); }
  };

  return <div className="admin-page max-w-6xl space-y-6">
    <section className="admin-panel space-y-4" aria-labelledby="fees-heading">
      <div><h3 id="fees-heading" className="admin-heading">Pajak restoran & biaya layanan</h3>
        <p className="text-sm text-stone-600">Berlaku untuk perhitungan checkout baru. Nilai pada pesanan sebelumnya tetap mengikuti transaksi aslinya.</p></div>
      {loadError ? <><p role="alert" className="admin-error">{loadError}</p><button className="admin-button" onClick={() => { setLoading(true); setLoadError(''); setReload((value) => value + 1); }}>Muat ulang biaya</button></>
        : loading ? <p role="status" className="text-sm text-stone-600">Memuat pengaturan biaya...</p>
        : <form onSubmit={(event) => void saveFees(event)} className="space-y-4">
          <fieldset disabled={saving} className="grid gap-4 sm:grid-cols-2">
            <div><label htmlFor="tax-percentage" className="admin-label">Pajak restoran (PB1 %)</label>
              <input id="tax-percentage" type="number" required min={0} max={50} step="0.01" className="admin-input" value={taxPct} onChange={(event) => { setTaxPct(Number(event.target.value)); setSaved(false); }} /></div>
            <div><label htmlFor="service-percentage" className="admin-label">Biaya layanan (%)</label>
              <input id="service-percentage" type="number" required min={0} max={50} step="0.01" className="admin-input" value={servicePct} onChange={(event) => { setServicePct(Number(event.target.value)); setSaved(false); }} />
              <p className="mt-1 text-sm text-stone-600">Isi 0 jika tidak ada biaya layanan.</p></div>
          </fieldset>
          {error && <p role="alert" className="admin-error">{error}</p>}
          {saved && <p role="status" className="text-sm text-emerald-800">Pengaturan biaya berhasil disimpan.</p>}
          <button className="admin-primary" disabled={saving}>{saving ? 'Menyimpan...' : 'Simpan biaya'}</button>
        </form>}
    </section>
    <ReceiptTemplates />
    <section className="admin-panel space-y-4" aria-labelledby="connection-heading">
      <div><h3 id="connection-heading" className="admin-heading">Koneksi Supabase</h3>
        <p className="text-sm text-stone-600">Gunakan public key. Pengaturan koneksi disimpan pada browser ini.</p></div>
      <form onSubmit={saveConnection} className="space-y-4">
        <div><label htmlFor="supabase-url" className="admin-label">Project URL</label>
          <input id="supabase-url" required type="url" className="admin-input" value={supabaseUrl} onChange={(event) => setSupabaseUrl(event.target.value)} /></div>
        <div><label htmlFor="supabase-public-key" className="admin-label">Publishable key / anon key</label>
          <input id="supabase-public-key" required type="text" spellCheck={false} className="admin-input font-mono" value={supabaseKey} onChange={(event) => setSupabaseKey(event.target.value)} />
          <p className="mt-1 text-sm text-stone-600">Secret key dan service role key hanya digunakan di server.</p></div>
        {connectionError && <p role="alert" className="admin-error">{connectionError}</p>}
        <div className="flex flex-wrap gap-3"><button className="admin-primary">Simpan koneksi & muat ulang</button>
          <button type="button" className="admin-button" onClick={() => {
            if (confirm('Kembalikan koneksi browser ini ke proyek bawaan?')) { resetSupabaseConfig(); window.location.reload(); }
          }}>Reset koneksi bawaan</button></div>
      </form>
    </section>
  </div>;
};
