export function adminError(error: unknown, fallback: string): string {
  if (typeof error === 'object' && error !== null && 'code' in error &&
      ['PGRST202', 'PGRST205', '42P01', '42883'].includes(String(error.code))) {
    return 'Fitur ini belum diaktifkan di server. Hubungi pengelola aplikasi.';
  }
  return error instanceof Error ? error.message : fallback;
}

export function dayAfter(date: string): string {
  const next = new Date(`${date}T00:00:00Z`);
  next.setUTCDate(next.getUTCDate() + 1);
  return next.toISOString().slice(0, 10);
}
