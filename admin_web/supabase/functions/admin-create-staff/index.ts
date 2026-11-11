import { createClient } from 'https://esm.sh/@supabase/supabase-js@2';

const admin = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, {
  auth: { persistSession: false, autoRefreshToken: false },
});
const headers = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, apikey, content-type, x-client-info',
  'Access-Control-Allow-Methods': 'POST, OPTIONS',
  'Content-Type': 'application/json',
};
const json = (status: number, body: unknown) => new Response(JSON.stringify(body), { status, headers });

Deno.serve(async (req: Request) => {
  if (req.method === 'OPTIONS') return new Response(null, { status: 204, headers });
  if (req.method !== 'POST') return json(405, { error: 'Gunakan POST' });
  try {
    const token = req.headers.get('Authorization')?.replace(/^Bearer\s+/i, '');
    if (!token) return json(401, { error: 'Silakan login kembali' });
    const { data: auth, error: authError } = await admin.auth.getUser(token);
    if (authError || !auth.user) return json(401, { error: 'Sesi tidak valid' });
    const { data: staff, error: staffError } = await admin.from('staff_user')
      .select('role, is_active').eq('uid', auth.user.id).maybeSingle();
    if (staffError) return json(503, { error: 'Gagal memverifikasi akses admin' });
    if (!staff || !['ADMIN', 'OWNER'].includes(staff.role) || staff.is_active !== true) return json(403, { error: 'Hanya ADMIN/OWNER aktif yang diizinkan' });

    const text = await req.text();
    if (text.length > 4096) return json(400, { error: 'Data staf terlalu panjang' });
    let body;
    try { body = JSON.parse(text); } catch { return json(400, { error: 'Data staf tidak valid' }); }
    if (!body || typeof body.name !== 'string' || !body.name.trim() || body.name.trim().length > 100 ||
        typeof body.email !== 'string' || !body.email.trim() || body.email.length > 254 ||
        typeof body.password !== 'string' || body.password.length < 8 || body.password.length > 128 ||
        !['ADMIN', 'BARISTA', 'CASHIER'].includes(body.role)) {
      return json(400, { error: 'Isi nama, email, peran yang valid, dan kata sandi 8–128 karakter' });
    }
    const { data: created, error: createError } = await admin.auth.admin.createUser({
      email: body.email.trim(), password: body.password, email_confirm: true,
      user_metadata: { name: body.name.trim() },
    });
    if (createError || !created.user) return json(400, { error: 'Akun tidak dapat dibuat. Periksa email dan kata sandi, atau gunakan email lain.' });

    const caller = createClient(Deno.env.get('SUPABASE_URL')!, Deno.env.get('SUPABASE_SERVICE_ROLE_KEY')!, {
      global: { headers: { Authorization: `Bearer ${token}` } },
      auth: { persistSession: false, autoRefreshToken: false },
    });
    let profileError: unknown;
    try {
      const result = await caller.from('staff_user').insert({
        uid: created.user.id, name: body.name.trim(), role: body.role, is_active: true,
      });
      profileError = result.error;
    } catch (error) { profileError = error; }
    if (profileError) {
      let cleanupError: unknown;
      try { cleanupError = (await admin.auth.admin.deleteUser(created.user.id)).error; }
      catch (error) { cleanupError = error; }
      if (cleanupError) console.error('Gagal membersihkan akun staf tanpa profil', created.user.id);
      return json(500, { error: cleanupError
        ? 'Profil gagal disimpan. Akun belum dapat dipakai; hubungi pengelola database sebelum mencoba lagi.'
        : 'Profil gagal disimpan; akun baru dibatalkan. Silakan coba lagi.' });
    }
    return json(201, { uid: created.user.id });
  } catch (error) {
    console.error('admin-create-staff', error instanceof Error ? error.message : 'Unknown error');
    return json(500, { error: 'Gagal membuat staf. Silakan coba lagi.' });
  }
});
