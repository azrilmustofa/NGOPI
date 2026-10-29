// Edge Function: retry-payment  (Verify JWT: OFF, login diperiksa di kode lewat auth.getUser)
//
// requestPaymentRetry: pelanggan menekan "Coba bayar lagi" setelah pembayaran gagal / pop-up kedaluwarsa.
// Membuat transaksi Midtrans baru untuk PESANAN YANG SAMA (bukan pesanan baru), dengan nominal yang sudah
// terkunci di customer_order. Aturannya:
//   - hanya pemilik pesanan, dan hanya selama pesanan masih AWAITING_PAYMENT
//   - satu transaksi aktif per pesanan: transaksi PENDING yang masih berlaku dipakai lagi; yang lama dibatalkan
//     dulu (di database + best-effort di Midtrans) sebelum membuat yang baru
//   - maksimal MAX_ATTEMPTS percobaan per pesanan
// Hasil: { status: "READY", redirect_url, expires_at } | { status: "PAID" } | { status: "CLOSED", message }
//
// Secret yang dibutuhkan: MIDTRANS_SERVER_KEY (SUPABASE_URL & SUPABASE_SERVICE_ROLE_KEY sudah otomatis ada).

import { createClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const MIDTRANS_SERVER_KEY = Deno.env.get("MIDTRANS_SERVER_KEY") ?? "";

const SANDBOX = MIDTRANS_SERVER_KEY.startsWith("SB-");
const SNAP_URL = SANDBOX
  ? "https://app.sandbox.midtrans.com/snap/v1/transactions"
  : "https://app.midtrans.com/snap/v1/transactions";
const CORE_URL = SANDBOX ? "https://api.sandbox.midtrans.com" : "https://api.midtrans.com";
const PAYMENT_WINDOW_MINUTES = 30; // sama dengan create-payment
const MAX_ATTEMPTS = 5;

const admin = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false } });
const midtransAuth = "Basic " + btoa(MIDTRANS_SERVER_KEY + ":");

class HttpError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function must<T>(res: { data: T | null; error: unknown }, message: string): T {
  if (res.error || res.data == null) {
    console.error(message, res.error);
    throw new HttpError(500, message);
  }
  return res.data;
}

async function authUser(req: Request): Promise<string> {
  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) throw new HttpError(401, "Belum login");
  const { data, error } = await admin.auth.getUser(token);
  if (error || !data.user) throw new HttpError(401, "Sesi tidak valid, silakan login ulang");
  return data.user.id;
}

const closed = (message: string) => ({ status: "CLOSED", message });

async function handle(req: Request) {
  if (!MIDTRANS_SERVER_KEY) throw new HttpError(500, "MIDTRANS_SERVER_KEY belum diatur");
  const uid = await authUser(req);
  const body = await req.json().catch(() => null);
  if (typeof body?.order_id !== "string" || !body.order_id) throw new HttpError(400, "Data pesanan tidak valid");

  const order = must(
    await admin
      .from("customer_order")
      .select("id, public_id, status, subtotal_rp, tax_rp, service_charge_rp, total_rp")
      .eq("id", body.order_id)
      .eq("owner_uid", uid)
      .maybeSingle(),
    "Gagal membaca pesanan",
  );
  if (!order) throw new HttpError(404, "Pesanan tidak ditemukan");
  if (order.status !== "AWAITING_PAYMENT") {
    if (order.status === "CANCELLED") return closed("Waktu pembayaran pesanan ini sudah habis. Silakan bayar lagi untuk membuat pesanan baru.");
    return { status: "PAID" }; // sudah lunas (PAID / diproses / selesai)
  }

  const attempts = must(
    await admin
      .from("payment_attempt")
      .select("id, status, provider_order_id, redirect_url, expires_at")
      .eq("order_id", order.id)
      .order("created_at", { ascending: false }),
    "Gagal membaca pembayaran",
  );
  if (attempts.some((a) => a.status === "PAID")) return { status: "PAID" };

  // Satu transaksi aktif: yang masih berlaku dipakai lagi (mis. tombol ditekan dua kali).
  const now = Date.now();
  const active = attempts.find((a) => a.status === "PENDING");
  if (active?.redirect_url && new Date(active.expires_at as string).getTime() > now + 60_000) {
    return { status: "READY", redirect_url: active.redirect_url, expires_at: active.expires_at };
  }
  if (attempts.length >= MAX_ATTEMPTS) {
    await cancelOrder(order.id, "Batas percobaan pembayaran tercapai");
    return closed("Batas percobaan pembayaran untuk pesanan ini sudah tercapai. Silakan bayar lagi untuk membuat pesanan baru.");
  }

  // Tutup transaksi lama yang masih menggantung supaya tidak bisa terbayar dua kali.
  for (const a of attempts.filter((a) => a.status === "PENDING" || a.status === "CREATING")) {
    await admin.from("payment_attempt").update({ status: "CANCELLED", updated_at: new Date().toISOString() })
      .eq("id", a.id).eq("status", a.status);
    // Best-effort: transaksi yang belum memilih metode di Snap memang belum ada di Midtrans (404).
    await fetch(`${CORE_URL}/v2/${encodeURIComponent(a.provider_order_id as string)}/cancel`, {
      method: "POST",
      headers: { Accept: "application/json", Authorization: midtransAuth },
    }).catch((e) => console.error("cancel Midtrans", a.provider_order_id, e));
  }

  // --- transaksi baru, nominal dari pesanan (harga sudah terkunci saat pesanan dibuat) ---
  const providerOrderId = `${order.public_id}-${crypto.randomUUID().slice(0, 8)}`;
  const expiresAt = new Date(now + PAYMENT_WINDOW_MINUTES * 60_000).toISOString();
  const attemptIns = await admin
    .from("payment_attempt")
    .insert({ order_id: order.id, provider_order_id: providerOrderId, amount_rp: order.total_rp, expires_at: expiresAt })
    .select("id")
    .single();
  if (attemptIns.error) {
    // 23505 = index satu-transaksi-aktif: request lain baru saja membuat transaksi; pakai yang itu.
    if (attemptIns.error.code === "23505") {
      const { data: other } = await admin.from("payment_attempt").select("redirect_url, expires_at")
        .eq("order_id", order.id).eq("status", "PENDING").maybeSingle();
      if (other?.redirect_url) return { status: "READY", redirect_url: other.redirect_url, expires_at: other.expires_at };
    }
    console.error("insert payment_attempt", attemptIns.error);
    throw new HttpError(500, "Gagal menyiapkan pembayaran ulang");
  }
  const attemptId = attemptIns.data.id as string;

  const items = must(
    await admin.from("order_item").select("menu_item_id, name_snapshot, unit_price_rp, quantity").eq("order_id", order.id),
    "Gagal membaca item pesanan",
  );
  const itemDetails = items.map((i) => ({
    id: String(i.menu_item_id).slice(0, 50),
    price: i.unit_price_rp as number,
    quantity: i.quantity as number,
    name: String(i.name_snapshot).slice(0, 50),
  }));
  if (order.tax_rp > 0) itemDetails.push({ id: "TAX", price: order.tax_rp, quantity: 1, name: "Pajak" });
  if (order.service_charge_rp > 0) itemDetails.push({ id: "SERVICE", price: order.service_charge_rp, quantity: 1, name: "Service charge" });
  // Midtrans menolak kalau jumlah item_details tidak sama dengan gross_amount; kirim tanpa rincian kalau begitu.
  const itemsTotal = itemDetails.reduce((s, i) => s + i.price * i.quantity, 0);

  const snapRes = await fetch(SNAP_URL, {
    method: "POST",
    headers: { "Content-Type": "application/json", Accept: "application/json", Authorization: midtransAuth },
    body: JSON.stringify({
      transaction_details: { order_id: providerOrderId, gross_amount: order.total_rp },
      ...(itemsTotal === order.total_rp ? { item_details: itemDetails } : {}),
      expiry: { unit: "minutes", duration: PAYMENT_WINDOW_MINUTES },
    }),
  });
  const snap = await snapRes.json().catch(() => null);
  if (!snapRes.ok || !snap?.token || !snap?.redirect_url) {
    console.error("Midtrans Snap gagal", snapRes.status, snap);
    await admin.from("payment_attempt").update({ status: "FAILED" }).eq("id", attemptId);
    throw new HttpError(502, "Gagal menghubungi Midtrans");
  }

  const upd = await admin
    .from("payment_attempt")
    .update({ status: "PENDING", snap_token: snap.token, redirect_url: snap.redirect_url, updated_at: new Date().toISOString() })
    .eq("id", attemptId);
  if (upd.error) throw upd.error;

  const hist = await admin.from("order_status_history").insert({
    order_id: order.id,
    actor_uid: uid,
    actor_type: "CUSTOMER",
    from_status: "AWAITING_PAYMENT",
    to_status: "AWAITING_PAYMENT",
    note: `Coba bayar lagi (percobaan ke-${attempts.length + 1})`,
  });
  if (hist.error) console.error("order_status_history", hist.error);

  return { status: "READY", redirect_url: snap.redirect_url as string, expires_at: expiresAt };
}

async function cancelOrder(orderId: string, reason: string) {
  const res = await admin
    .from("customer_order")
    .update({ status: "CANCELLED", cancellation_reason: reason, updated_at: new Date().toISOString() })
    .eq("id", orderId)
    .eq("status", "AWAITING_PAYMENT");
  if (res.error) console.error("cancel order", res.error);
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });
  try {
    return json(200, await handle(req));
  } catch (e) {
    if (e instanceof HttpError) return json(e.status, { error: e.message });
    console.error("retry-payment", e);
    return json(500, { error: "Terjadi kesalahan server" });
  }
});
