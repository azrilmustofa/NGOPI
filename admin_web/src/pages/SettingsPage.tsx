import React, { useEffect, useState } from 'react';
import {
  Save,
  Key,
  Percent,
  CheckCircle2,
  RotateCcw,
  Store,
} from 'lucide-react';
import {
  supabase,
  DEFAULT_SUPABASE_URL,
  DEFAULT_SUPABASE_KEY,
  updateSupabaseConfig,
  resetSupabaseConfig,
} from '../lib/supabase';
import type { AppSettings } from '../types';

export const SettingsPage: React.FC = () => {
  const [taxPct, setTaxPct] = useState<number>(10);
  const [servicePct, setServicePct] = useState<number>(0);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [saveSuccess, setSaveSuccess] = useState(false);

  // Supabase connection settings
  const [supabaseUrl, setSupabaseUrl] = useState(() => {
    return localStorage.getItem('ngopi_admin_supabase_url') || DEFAULT_SUPABASE_URL;
  });
  const [supabaseKey, setSupabaseKey] = useState(() => {
    return localStorage.getItem('ngopi_admin_supabase_key') || DEFAULT_SUPABASE_KEY;
  });
  const [configSaved, setConfigSaved] = useState(false);

  const loadSettings = async () => {
    try {
      const { data, error } = await supabase
        .from('app_settings')
        .select('*')
        .eq('id', 'default')
        .maybeSingle();

      if (!error && data) {
        const s = data as AppSettings;
        setTaxPct(Number(s.tax_percentage) || 0);
        setServicePct(Number(s.service_charge_percentage) || 0);
      }
    } catch (err) {
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadSettings();
  }, []);

  const handleSaveTaxSettings = async (e: React.FormEvent) => {
    e.preventDefault();
    setSaving(true);
    setSaveSuccess(false);

    try {
      // Upsert app_settings with id 'default'
      const { error } = await supabase.from('app_settings').upsert({
        id: 'default',
        tax_percentage: taxPct,
        service_charge_percentage: servicePct,
      });

      if (error) throw error;
      setSaveSuccess(true);
      setTimeout(() => setSaveSuccess(false), 3000);
    } catch (err: any) {
      alert('Gagal menyimpan pengaturan: ' + (err.message || 'Terjadi kesalahan'));
    } finally {
      setSaving(false);
    }
  };

  const handleSaveConnection = (e: React.FormEvent) => {
    e.preventDefault();
    updateSupabaseConfig(supabaseUrl.trim(), supabaseKey.trim());
    setConfigSaved(true);
    setTimeout(() => {
      setConfigSaved(false);
      window.location.reload();
    }, 1000);
  };

  const handleResetConnection = () => {
    if (confirm('Kembalikan kredensial Supabase ke pengaturan awal proyek?')) {
      resetSupabaseConfig();
      setSupabaseUrl(DEFAULT_SUPABASE_URL);
      setSupabaseKey(DEFAULT_SUPABASE_KEY);
      window.location.reload();
    }
  };

  return (
    <div className="p-8 max-w-4xl mx-auto space-y-8">
      <div>
        <h3 className="text-lg font-bold text-stone-900">Pengaturan Toko & Pajak</h3>
        <p className="text-xs text-stone-500">
          Konfigurasi biaya operasional kafe dan integrasi basis data Supabase.
        </p>
      </div>

      {/* Tax & Service Charge Form */}
      <div className="bg-white rounded-2xl border border-stone-200 p-6 shadow-xs space-y-5">
        <div className="flex items-center gap-2.5 pb-4 border-b border-stone-100">
          <div className="w-8 h-8 rounded-lg bg-amber-50 text-amber-600 flex items-center justify-center">
            <Percent className="w-4 h-4" />
          </div>
          <div>
            <h4 className="font-bold text-stone-900 text-sm">Pajak Restoran & Biaya Layanan</h4>
            <p className="text-xs text-stone-500">
              Persentase dihitung otomatis ke subtotal pesanan saat checkout pelanggan.
            </p>
          </div>
        </div>

        {loading ? (
          <div className="py-8 text-center text-xs text-stone-400">Memuat konfigurasi...</div>
        ) : (
          <form onSubmit={handleSaveTaxSettings} className="space-y-4">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  Pajak Restoran (PB1 %)
                </label>
                <div className="relative">
                  <input
                    type="number"
                    min={0}
                    max={50}
                    step={0.5}
                    value={taxPct}
                    onChange={(e) => setTaxPct(Number(e.target.value))}
                    className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                  />
                  <span className="absolute right-3.5 top-1/2 -translate-y-1/2 text-stone-400 text-sm font-semibold">
                    %
                  </span>
                </div>
                <p className="text-[11px] text-stone-400 mt-1">Standar PB1 daerah umumnya 10%.</p>
              </div>

              <div>
                <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  Biaya Layanan (Service Charge %)
                </label>
                <div className="relative">
                  <input
                    type="number"
                    min={0}
                    max={50}
                    step={0.5}
                    value={servicePct}
                    onChange={(e) => setServicePct(Number(e.target.value))}
                    className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
                  />
                  <span className="absolute right-3.5 top-1/2 -translate-y-1/2 text-stone-400 text-sm font-semibold">
                    %
                  </span>
                </div>
                <p className="text-[11px] text-stone-400 mt-1">Isi 0 jika tidak ada biaya layanan.</p>
              </div>
            </div>

            <div className="flex items-center justify-between pt-2">
              {saveSuccess ? (
                <div className="inline-flex items-center gap-1.5 text-xs text-emerald-700 font-semibold bg-emerald-50 px-3 py-1.5 rounded-lg border border-emerald-200">
                  <CheckCircle2 className="w-3.5 h-3.5" />
                  <span>Pengaturan berhasil disimpan!</span>
                </div>
              ) : (
                <div />
              )}

              <button
                type="submit"
                disabled={saving}
                className="inline-flex items-center gap-2 px-5 py-2.5 bg-amber-600 hover:bg-amber-700 text-white text-xs font-semibold rounded-xl transition-colors shadow-xs disabled:opacity-50"
              >
                <Save className="w-4 h-4" />
                <span>{saving ? 'Menyimpan...' : 'Simpan Biaya'}</span>
              </button>
            </div>
          </form>
        )}
      </div>

      {/* Supabase Connection Setup */}
      <div className="bg-white rounded-2xl border border-stone-200 p-6 shadow-xs space-y-5">
        <div className="flex items-center gap-2.5 pb-4 border-b border-stone-100">
          <div className="w-8 h-8 rounded-lg bg-stone-100 text-stone-700 flex items-center justify-center">
            <Key className="w-4 h-4" />
          </div>
          <div>
            <h4 className="font-bold text-stone-900 text-sm">Kredensial Supabase Kafe</h4>
            <p className="text-xs text-stone-500">
              Koneksi database yang dipakai panel admin web Kopi Inggil.
            </p>
          </div>
        </div>

        <form onSubmit={handleSaveConnection} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
              Project URL
            </label>
            <input
              type="text"
              required
              value={supabaseUrl}
              onChange={(e) => setSupabaseUrl(e.target.value)}
              className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-xs font-mono focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
              API Key (Anon Key / Publishable Key / Service Role)
            </label>
            <input
              type="text"
              required
              value={supabaseKey}
              onChange={(e) => setSupabaseKey(e.target.value)}
              className="w-full px-3.5 py-2.5 bg-stone-50 border border-stone-200 rounded-xl text-xs font-mono focus:outline-none focus:ring-2 focus:ring-amber-500/20 focus:border-amber-500"
            />
            <p className="text-[11px] text-stone-400 mt-1">
              Tips: Masukkan Service Role Key jika ingin mengedit tabel secara langsung tanpa batasan RLS.
            </p>
          </div>

          <div className="flex items-center justify-between pt-2">
            <button
              type="button"
              onClick={handleResetConnection}
              className="inline-flex items-center gap-1.5 text-xs text-stone-500 hover:text-stone-800 font-medium px-3 py-1.5 rounded-lg border border-stone-200 hover:bg-stone-50 transition-colors"
            >
              <RotateCcw className="w-3.5 h-3.5" />
              <span>Reset Default</span>
            </button>

            <button
              type="submit"
              className="inline-flex items-center gap-2 px-5 py-2.5 bg-stone-900 hover:bg-stone-800 text-white text-xs font-semibold rounded-xl transition-colors shadow-xs"
            >
              <Save className="w-4 h-4" />
              <span>{configSaved ? 'Tersimpan!' : 'Simpan Kredensial'}</span>
            </button>
          </div>
        </form>
      </div>

      {/* Outlet Information Card */}
      <div className="bg-stone-50 border border-stone-200 rounded-2xl p-6 flex items-start gap-4">
        <Store className="w-6 h-6 text-stone-500 flex-shrink-0 mt-0.5" />
        <div className="text-xs text-stone-600 space-y-1">
          <div className="font-bold text-stone-900 text-sm">Informasi Aplikasi Outlet</div>
          <p>Aplikasi: Kopi Inggil / NGOPI QR Dine-in</p>
          <p>Platform Pelanggan: Android Native (Kotlin + Compose / XML)</p>
          <p>Payment Gateway: Midtrans Snap & QRIS</p>
          <p>Backend: Supabase PostgreSQL & Edge Functions</p>
        </div>
      </div>
    </div>
  );
};
