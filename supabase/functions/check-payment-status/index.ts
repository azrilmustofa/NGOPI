// Pemeriksaan status pembayaran oleh kasir langsung ke Midtrans.
// Deploy dengan Verify JWT OFF; token pengguna tetap diverifikasi di dalam fungsi.
import { createClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const MIDTRANS_SERVER_KEY = Deno.env.get("MIDTRANS_SERVER_KEY") ?? "";
const MIDTRANS_URL = MIDTRANS_SERVER_KEY.startsWith("SB-")
  ? "https://api.sandbox.midtrans.com"
  : "https://api.midtrans.com";
const admin = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false } });

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

type PaymentStatus = "PAID" | "PENDING" | "FAILED" | "CANCELLED" | "EXPIRED" | "REFUNDED";

function mapStatus(transactionStatus: string, fraudStatus?: string): PaymentStatus | null {
  switch (transactionStatus) {
    case "settlement": return "PAID";
    case "capture": return fraudStatus === "accept" ? "PAID" : fraudStatus === "challenge" ? "PENDING" : "FAILED";
    case "pending": return "PENDING";
    case "deny": return "FAILED";
    case "cancel": return "CANCELLED";
    case "expire": return "EXPIRED";
    case "refund": return "REFUNDED";
    default: return null;
  }
}

function methodGroup(paymentType?: string): string | null {
  if (!paymentType) return null;
  if (paymentType === "qris") return "QRIS";
  if (paymentType === "bank_transfer" || paymentType === "echannel") return "VIRTUAL_ACCOUNT";
  return "EWALLET";
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });
  if (!MIDTRANS_SERVER_KEY) return json(500, { error: "MIDTRANS_SERVER_KEY belum diatur" });

  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) return json(401, { error: "Belum login" });
  const auth = await admin.auth.getUser(token);
  const uid = auth.data.user?.id;
  if (auth.error || !uid) return json(401, { error: "Sesi tidak valid, silakan login ulang" });

  const staff = await admin.from("staff_user").select("uid")
    .eq("uid", uid).eq("is_active", true).in("role", ["CASHIER", "ADMIN", "OWNER"]).maybeSingle();
  if (staff.error || !staff.data) return json(403, { error: "Hanya kasir aktif yang dapat memeriksa pembayaran" });

  const body = await req.json().catch(() => null);
  if (!body || typeof body.order_id !== "string") return json(400, { error: "Pesanan tidak valid" });

  const order = await admin.from("customer_order").select("id, status")
    .eq("id", body.order_id).maybeSingle();
  if (order.error || !order.data) return json(404, { error: "Pesanan tidak ditemukan" });

  const attempt = await admin.from("payment_attempt")
    .select("id, order_id, provider_order_id, status, payment_method, amount_rp")
    .eq("order_id", body.order_id).order("created_at", { ascending: false }).limit(1).maybeSingle();
  if (attempt.error || !attempt.data) return json(404, { error: "Transaksi pembayaran belum tersedia" });

  const providerRes = await fetch(`${MIDTRANS_URL}/v2/${encodeURIComponent(attempt.data.provider_order_id)}/status`, {
    headers: { Accept: "application/json", Authorization: "Basic " + btoa(MIDTRANS_SERVER_KEY + ":") },
  });
  const provider = await providerRes.json().catch(() => null);
  if (!providerRes.ok) {
    return json(502, { error: String(provider?.status_message ?? "Midtrans tidak dapat dihubungi") });
  }

  // Snap token sudah dapat dibuat sebelum pelanggan memilih metode pembayaran. Pada tahap ini
  // Core API Midtrans dapat menjawab HTTP 200 dengan status_code 404/tanpa transaction_status.
  // Transaksi lokal tetap sah sebagai PENDING dan dapat dilanjutkan oleh pelanggan.
  if (!provider?.transaction_status) {
    const providerCode = String(provider?.status_code ?? "");
    if (providerCode === "404" || attempt.data.status === "PENDING") {
      return json(200, {
        order_status: order.data.status,
        payment_status: "PENDING",
        payment_method: attempt.data.payment_method,
        provider_order_id: attempt.data.provider_order_id,
      });
    }
    return json(422, { error: String(provider?.status_message ?? "Status Midtrans belum tersedia") });
  }

  const next = mapStatus(String(provider.transaction_status ?? ""), provider.fraud_status);
  if (!next) return json(422, { error: `Status Midtrans ${provider.transaction_status ?? "tidak dikenal"} belum didukung` });

  const now = new Date().toISOString();
  const patch: Record<string, unknown> = { status: next, updated_at: now };
  const method = methodGroup(provider.payment_type);
  if (method) patch.payment_method = method;
  if (next === "PAID") {
    patch.paid_at = now;
    patch.paid_source = "MIDTRANS_MANUAL_CHECK";
  }

  if (!["PAID", "REFUND_PENDING", "REFUNDED"].includes(attempt.data.status)) {
    const updatedAttempt = await admin.from("payment_attempt").update(patch).eq("id", attempt.data.id);
    if (updatedAttempt.error) return json(500, { error: "Gagal menyimpan status pembayaran" });
  }

  let orderStatus = order.data.status as string;
  if (next === "PAID" && orderStatus === "AWAITING_PAYMENT") {
    const updated = await admin.from("customer_order")
      .update({ status: "PAID", paid_at: now, updated_at: now })
      .eq("id", body.order_id).eq("status", "AWAITING_PAYMENT").select("status").maybeSingle();
    if (updated.error) return json(500, { error: "Gagal memperbarui pesanan" });
    orderStatus = updated.data?.status ?? orderStatus;
  } else if ((next === "EXPIRED" || next === "CANCELLED") && orderStatus === "AWAITING_PAYMENT") {
    const others = await admin.from("payment_attempt").select("id")
      .eq("order_id", body.order_id).neq("id", attempt.data.id).in("status", ["PENDING", "PAID"]);
    if (!others.error && !others.data?.length) {
      const reason = next === "EXPIRED" ? "Pembayaran kedaluwarsa" : "Pembayaran dibatalkan";
      const updated = await admin.from("customer_order")
        .update({ status: "CANCELLED", cancellation_reason: reason, updated_at: now })
        .eq("id", body.order_id).eq("status", "AWAITING_PAYMENT").select("status").maybeSingle();
      orderStatus = updated.data?.status ?? orderStatus;
    }
  }

  return json(200, {
    order_status: orderStatus,
    payment_status: next,
    payment_method: method ?? attempt.data.payment_method,
    provider_order_id: attempt.data.provider_order_id,
  });
});
