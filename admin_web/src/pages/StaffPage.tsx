import React, { useEffect, useRef, useState } from 'react';
import {
  Users,
  Plus,
  ShieldCheck,
  Coffee,
  Receipt,
  X,
  Mail,
  Lock,
  User,
  CheckCircle2,
} from 'lucide-react';
import { supabase } from '../lib/supabase';
import { useAuth } from '../context/AuthContext';
import { adminError } from '../lib/admin';
import type { StaffUser } from '../types';

export const StaffPage: React.FC = () => {
  const { user } = useAuth();
  const [staffList, setStaffList] = useState<StaffUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [showAddModal, setShowAddModal] = useState(false);
  const addDialog = useRef<HTMLDialogElement>(null);

  // Form states
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [role, setRole] = useState<'ADMIN' | 'BARISTA' | 'CASHIER'>('BARISTA');
  const [saving, setSaving] = useState(false);
  const [successInfo, setSuccessInfo] = useState<{ email: string; role: string } | null>(null);
  const [error, setError] = useState('');
  const [updating, setUpdating] = useState<string | null>(null);

  const loadStaff = async () => {
    setLoading(true);
    setError('');
    try {
      const { data, error } = await supabase
        .from('staff_user')
        .select('*')
        .order('created_at', { ascending: false });

      if (error) throw error;
      if (data) setStaffList(data as StaffUser[]);
    } catch (err) {
      setError(adminError(err, 'Gagal memuat staf. Silakan coba lagi.'));
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadStaff();
  }, []);

  useEffect(() => {
    if (showAddModal) addDialog.current?.showModal();
  }, [showAddModal]);

  const handleAddStaff = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!name.trim() || !email.trim() || !password) { setError('Nama, email, dan kata sandi wajib diisi.'); return; }

    if (password.length < 8 || password.length > 128) {
      setError('Kata sandi harus 8–128 karakter');
      return;
    }

    setSaving(true);
    setError('');
    try {
      const { data, error } = await supabase.functions.invoke('admin-create-staff', {
        body: { name: name.trim(), email: email.trim(), password, role },
      });
      if (error) {
        const response = error.context instanceof Response
          ? await error.context.json().catch(() => null) : null;
        throw new Error(response?.error || error.message);
      }
      if (!data?.uid) throw new Error('Server tidak mengembalikan akun staf. Muat ulang daftar sebelum mencoba lagi.');

      setSuccessInfo({ email: email.trim(), role });
      setName('');
      setEmail('');
      setPassword('');
      setRole('BARISTA');
      setShowAddModal(false);
      loadStaff();
    } catch (err: any) {
      setError(adminError(err, 'Gagal membuat staf. Periksa data lalu coba lagi.'));
    } finally {
      setSaving(false);
    }
  };

  const updateStaff = async (staff: StaffUser, changes: Partial<Pick<StaffUser, 'role' | 'is_active'>>) => {
    if (staff.uid === user?.id || staff.role === 'OWNER') return;
    if (!confirm(`Perbarui akses ${staff.name}?`)) return;
    setUpdating(staff.uid); setError('');
    try {
      const { data, error } = await supabase.from('staff_user').update(changes).eq('uid', staff.uid).select('*').single();
      if (error) throw error;
      if (!data) throw new Error('Perubahan akses belum tersimpan. Muat ulang daftar staf.');
      setStaffList((rows) => rows.map((row) => row.uid === staff.uid ? data as StaffUser : row));
    } catch (error) { setError(adminError(error, 'Gagal memperbarui akses staf. Silakan coba lagi.')); }
    finally { setUpdating(null); }
  };

  const getRoleBadge = (r: string) => {
    switch (r) {
      case 'ADMIN':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-purple-50 text-purple-700 border border-purple-200">
            <ShieldCheck className="w-3 h-3" /> Admin
          </span>
        );
      case 'BARISTA':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-blue-50 text-blue-700 border border-blue-200">
            <Coffee className="w-3 h-3" /> Barista / Dapur
          </span>
        );
      case 'CASHIER':
        return (
          <span className="inline-flex items-center gap-1 px-2.5 py-0.5 rounded-full text-xs font-bold bg-emerald-50 text-emerald-700 border border-emerald-200">
            <Receipt className="w-3 h-3" /> Kasir
          </span>
        );
      default:
        return (
          <span className="px-2.5 py-0.5 rounded-full text-xs font-bold bg-stone-100 text-stone-600">
            {r}
          </span>
        );
    }
  };

  return (
    <div className="admin-page space-y-6">
      {error && !showAddModal && <div role="alert" className="admin-error">{error} <button type="button" className="underline" onClick={() => void loadStaff()}>Muat ulang daftar</button></div>}
      {/* Top Bar */}
      <div className="flex flex-col sm:flex-row gap-4 items-center justify-between">
        <div>
          <h3 className="text-lg font-bold text-stone-900">Manajemen Pengguna & Staf</h3>
          <p className="text-xs text-stone-500">
            Daftarkan akun staf baru untuk masuk ke aplikasi Android atau panel web sesuai peran.
          </p>
        </div>

        <button
          onClick={() => {
            setSuccessInfo(null);
            setError('');
            setShowAddModal(true);
          }}
          className="inline-flex items-center gap-2 px-4 py-2 bg-amber-800 hover:bg-amber-900 text-white text-sm font-medium rounded-xl transition-colors shadow-xs"
        >
          <Plus className="w-4 h-4" />
          <span>Tambah Akun Staf</span>
        </button>
      </div>

      {/* Success banner if just created */}
      {successInfo && (
        <div className="p-4 bg-emerald-50 border border-emerald-200 rounded-2xl flex items-center justify-between text-xs text-emerald-800">
          <div className="flex items-center gap-2.5">
            <CheckCircle2 className="w-4 h-4 text-emerald-600 flex-shrink-0" />
            <span>
              Akun <strong>{successInfo.email}</strong> berhasil dibuat dengan peran{' '}
              <strong>{successInfo.role}</strong>. Akun siap digunakan untuk login.
            </span>
          </div>
          <button
            onClick={() => setSuccessInfo(null)}
            className="text-stone-400 hover:text-stone-700 p-1"
          >
            <X className="w-4 h-4" />
          </button>
        </div>
      )}

      {/* Staff Table */}
      <div className="bg-white rounded-2xl border border-stone-200 shadow-xs overflow-x-auto">
        {loading ? (
          <div className="py-20 text-center text-sm text-stone-400">Memuat daftar staf...</div>
        ) : staffList.length === 0 ? (
          <div className="p-16 text-center">
            <Users className="w-12 h-12 text-stone-300 mx-auto mb-3" />
            <h4 className="text-base font-bold text-stone-800">Belum Ada Staf Terdaftar</h4>
          </div>
        ) : (
          <table className="w-full text-left text-sm">
            <thead className="bg-stone-50 border-b border-stone-200 text-xs font-semibold text-stone-500 uppercase tracking-wider">
              <tr>
                <th className="py-3.5 px-6">Nama Staf</th>
                <th className="py-3.5 px-6">Peran (Role)</th>
                <th className="py-3.5 px-6">Status Akun</th>
                <th className="py-3.5 px-6 text-right">Aksi</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-stone-100">
              {staffList.map((staff) => (
                <tr key={staff.uid} className="hover:bg-stone-50/70 transition-colors">
                  <td className="py-4 px-6 font-semibold text-stone-900 flex items-center gap-3">
                    <div className="w-8 h-8 rounded-full bg-stone-100 text-stone-600 flex items-center justify-center font-bold text-xs">
                      {staff.name ? staff.name.slice(0, 2).toUpperCase() : 'ST'}
                    </div>
                    <div>
                      <div>{staff.name || 'Tanpa Nama'}</div>
                      <div className="font-mono text-[10px] text-stone-400">UID: {staff.uid.slice(0, 13)}...</div>
                    </div>
                  </td>
                  <td className="py-4 px-6"><div className="space-y-2">{getRoleBadge(staff.role)}
                    <select className="admin-input" aria-label={`Peran ${staff.name}`} value={staff.role} disabled={updating !== null || staff.uid === user?.id || staff.role === 'OWNER'}
                      onChange={(event) => void updateStaff(staff, { role: event.target.value as StaffUser['role'] })}>
                      <option value="ADMIN">Admin</option><option value="BARISTA">Barista / Dapur</option><option value="CASHIER">Kasir</option>
                      {staff.role === 'OWNER' && <option value="OWNER">Pemilik</option>}
                    </select>{staff.uid === user?.id && <p className="text-xs text-stone-600">Akun Anda sendiri</p>}</div></td>
                  <td className="py-4 px-6">
                    <span
                      className={`px-2.5 py-0.5 text-xs font-semibold rounded-full border ${
                        staff.is_active
                          ? 'bg-emerald-50 text-emerald-700 border-emerald-200'
                          : 'bg-red-50 text-red-700 border-red-200'
                      }`}
                    >
                      {staff.is_active ? 'Aktif' : 'Nonaktif'}
                    </span>
                  </td>
                  <td className="py-4 px-6 text-right">
                    <button
                      disabled={updating !== null || staff.uid === user?.id || staff.role === 'OWNER'}
                      onClick={() => void updateStaff(staff, { is_active: !staff.is_active })}
                      className="text-xs text-stone-500 hover:text-stone-900 font-medium px-2 py-1 rounded-lg hover:bg-stone-100 transition-colors"
                    >
                      {updating === staff.uid ? 'Menyimpan...' : staff.is_active ? 'Nonaktifkan' : 'Aktifkan'}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>

      {/* Add Staff Modal */}
      {showAddModal && (
        <dialog ref={addDialog} aria-labelledby="add-staff-heading" className="m-auto w-[calc(100vw_-_2rem)] max-w-md border-0 bg-transparent p-0" onCancel={(event) => {
          if (saving) event.preventDefault(); else setShowAddModal(false);
        }}>
          <div className="bg-white rounded-3xl max-w-md w-full max-h-[90dvh] overflow-y-auto p-6 shadow-2xl space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-stone-100">
              <div>
                <h3 id="add-staff-heading" className="font-bold text-base text-stone-900">Buat Akun Staf Baru</h3>
                <p className="text-sm text-stone-600">Email dan kata sandi ini digunakan staf untuk masuk.</p>
              </div>
              <button
                disabled={saving}
                aria-label="Tutup form staf"
                onClick={() => setShowAddModal(false)}
                className="p-1 text-stone-400 hover:text-stone-700 rounded-full"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            <form onSubmit={handleAddStaff} className="space-y-4">
              {error && <p role="alert" className="admin-error">{error}</p>}
              <div>
                <label htmlFor="staff-name" className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  Nama Lengkap *
                </label>
                <div className="relative">
                  <User className="w-4 h-4 text-stone-400 absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    id="staff-name"
                    type="text"
                    required
                    maxLength={100}
                    autoComplete="name"
                    disabled={saving}
                    value={name}
                    onChange={(e) => setName(e.target.value)}
                    placeholder="Mis. Farrel Barista"
                    className="w-full pl-9 pr-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-800 focus:border-amber-500"
                  />
                </div>
              </div>

              <div>
                <label htmlFor="staff-email" className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  Email Login *
                </label>
                <div className="relative">
                  <Mail className="w-4 h-4 text-stone-400 absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    id="staff-email"
                    type="email"
                    required
                    maxLength={254}
                    autoComplete="email"
                    disabled={saving}
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    placeholder="barista@kopiinggil.com"
                    className="w-full pl-9 pr-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-800 focus:border-amber-500"
                  />
                </div>
              </div>

              <div>
                <label htmlFor="staff-password" className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  Kata Sandi Akun *
                </label>
                <div className="relative">
                  <Lock className="w-4 h-4 text-stone-400 absolute left-3 top-1/2 -translate-y-1/2" />
                  <input
                    id="staff-password"
                    type="password"
                    required
                    autoComplete="new-password"
                    disabled={saving}
                    minLength={8}
                    maxLength={128}
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    placeholder="Minimal 8 karakter"
                    className="w-full pl-9 pr-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-800 focus:border-amber-500"
                  />
                </div>
              </div>

              <div>
                <label htmlFor="staff-role" className="block text-xs font-semibold text-stone-700 uppercase tracking-wider mb-1">
                  Peran / Posisi *
                </label>
                <select
                  id="staff-role"
                  disabled={saving}
                  value={role}
                  onChange={(e) => setRole(e.target.value as any)}
                  className="w-full px-3.5 py-2 bg-stone-50 border border-stone-200 rounded-xl text-sm focus:outline-none focus:ring-2 focus:ring-amber-800 focus:border-amber-500"
                >
                  <option value="BARISTA">Barista / Kitchen</option>
                  <option value="CASHIER">Kasir</option>
                  <option value="ADMIN">Administrator</option>
                </select>
              </div>

              <div className="flex gap-2 pt-2">
                <button
                  type="button"
                  disabled={saving}
                  onClick={() => setShowAddModal(false)}
                  className="flex-1 py-2 bg-stone-100 hover:bg-stone-200 text-stone-700 font-semibold rounded-xl text-xs transition-colors"
                >
                  Batal
                </button>
                <button
                  type="submit"
                  disabled={saving}
                  className="flex-1 py-2 bg-amber-800 hover:bg-amber-900 text-white font-semibold rounded-xl text-xs transition-colors shadow-xs disabled:opacity-50"
                >
                  {saving ? 'Membuat Akun...' : 'Daftarkan Staf'}
                </button>
              </div>
            </form>
          </div>
        </dialog>
      )}
    </div>
  );
};
