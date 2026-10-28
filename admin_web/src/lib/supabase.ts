import { createClient } from '@supabase/supabase-js';

export const DEFAULT_SUPABASE_URL = 'https://uvaeklvwkxibwcrhofku.supabase.co';
export const DEFAULT_SUPABASE_KEY = 'sb_publishable_j2vxSiilkJy3XbwCOlCgxg_k5KSJi9d';

const storedUrl = localStorage.getItem('ngopi_admin_supabase_url') || import.meta.env.VITE_SUPABASE_URL || DEFAULT_SUPABASE_URL;
const storedKey = localStorage.getItem('ngopi_admin_supabase_key') || import.meta.env.VITE_SUPABASE_ANON_KEY || DEFAULT_SUPABASE_KEY;

export let supabase = createClient(storedUrl, storedKey);

export function updateSupabaseConfig(url: string, key: string) {
  localStorage.setItem('ngopi_admin_supabase_url', url);
  localStorage.setItem('ngopi_admin_supabase_key', key);
  supabase = createClient(url, key);
}

export function resetSupabaseConfig() {
  localStorage.removeItem('ngopi_admin_supabase_url');
  localStorage.removeItem('ngopi_admin_supabase_key');
  supabase = createClient(DEFAULT_SUPABASE_URL, DEFAULT_SUPABASE_KEY);
}

export function formatRupiah(amount: number): string {
  return new Intl.NumberFormat('id-ID', {
    style: 'currency',
    currency: 'IDR',
    minimumFractionDigits: 0,
    maximumFractionDigits: 0,
  }).format(amount);
}
