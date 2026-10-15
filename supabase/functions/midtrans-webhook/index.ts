// Edge Function: midtrans-webhook  (Verify JWT: OFF, Midtrans tidak mengirim token Supabase)
//
// URL ini dipasang di Midtrans: Settings -> Configuration -> Payment Notification URL.
// Keamanan ada di signature_key: SHA512(order_id + status_code + gross_amount + ServerKey).
// Semua perubahan status bersifat idempoten karena Midtrans bisa mengirim notifikasi yang sama berulang.
//
// Secret yang dibutuhkan: MIDTRANS_SERVER_KEY.

import { createClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const MIDTRANS_SERVER_KEY = Deno.env.get("MIDTRANS_SERVER_KEY") ?? "";

const admin = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false } });

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

async function sha512Hex(text: string): Promise<string> {
  const buf = await crypto.subtle.digest("SHA-512", new TextEncoder().encode(text));
  return Array.from(new Uint8Array(buf), (b) => b.toString(16).padStart(2, "0")).join("");
}

function safeEqual(a: string, b: string): boolean {
  if (a.length !== b.length) return false;
  let diff = 0;
  for (let i = 0; i < a.length; i++) diff |= a.charCodeAt(i) ^ b.charCodeAt(i);
  return diff === 0;
}

type PaymentStatus = "PAID" | "PENDING" | "FAILED" | "CANCELLED" | "EXPIRED";

function mapStatus(transactionStatus: string, fraudStatus?: string): PaymentStatus | null {
  switch (transactionStatus) {
    case "settlement":
      return "PAID";
    case "capture": // kartu kredit
      if (fraudStatus === "accept") return "PAID";
      return fraudStatus === "challenge" ? "PENDING" : "FAILED";
    case "pending":
      return "PENDING";
    case "deny":
      return "FAILED";
    case "cancel":
      return "CANCELLED";
    case "expire":
      return "EXPIRED";
    default:
      return null; // refund dll. belum ditangani
  }
}

// Riwayat status bersifat pelengkap: kegagalannya hanya dicatat.
async function addHistory(orderId: string, from: string, to: string, note: string) {
  const res = await admin.from("order_status_history").insert({
    order_id: orderId,
    actor_type: "SYSTEM",
    from_status: from,
    to_status: to,
    note,
  });
  if (res.error) console.error("order_status_history", res.error);
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });
  if (!MIDTRANS_SERVER_KEY) return json(500, { error: "MIDTRANS_SERVER_KEY belum diatur" });

  const n = await req.json().catch(() => null);
  if (
    !n || typeof n.order_id !== "string" || typeof n.status_code !== "string" ||
    typeof n.gross_amount !== "string" || typeof n.signature_key !== "string" ||
    typeof n.transaction_status !== "string"
  ) return json(400, { error: "Payload tidak valid" });

  const expected = await sha512Hex(n.order_id + n.status_code + n.gross_amount + MIDTRANS_SERVER_KEY);
  if (!safeEqual(expected, n.signature_key.toLowerCase())) return json(401, { error: "Signature tidak valid" });

  const attemptRes = await admin
    .from("payment_attempt")
    .select("id, order_id, amount_rp")
    .eq("provider_order_id", n.order_id)
    .maybeSingle();
  if (attemptRes.error) {
    console.error("baca payment_attempt", attemptRes.error);
    return json(500, { error: "Gagal membaca pembayaran" }); // Midtrans akan mengirim ulang
  }
  const attempt = attemptRes.data;
  if (!attempt) return json(200, { ignored: "order_id tidak dikenal" }); // mis. tombol Test di dashboard Midtrans

  if (Math.round(parseFloat(n.gross_amount)) !== attempt.amount_rp) {
    console.error("Nominal tidak cocok", n.order_id, n.gross_amount, attempt.amount_rp);
    return json(200, { ignored: "nominal tidak cocok" });
  }

  // Catat notifikasi mentah; duplikat diabaikan.
  const eventKey = `${n.order_id}:${n.status_code}:${n.transaction_status}:${n.fraud_status ?? ""}`;
  const ev = await admin.from("payment_event").upsert(
    {
      event_key: eventKey,
      payment_attempt_id: attempt.id,
      provider_order_id: n.order_id,
      provider_status: n.transaction_status,
      fraud_status: n.fraud_status ?? null,
      gross_amount_rp: attempt.amount_rp,
      raw_payload: n,
      processed_at: new Date().toISOString(),
    },
    { onConflict: "event_key", ignoreDuplicates: true },
  );
  if (ev.error) console.error("payment_event", ev.error);

  const next = mapStatus(n.transaction_status, n.fraud_status);
  if (!next) return json(200, { ignored: `status ${n.transaction_status} belum ditangani` });

  // Pembayaran yang sudah PAID (atau direfund) tidak boleh diturunkan oleh notifikasi telat.
  const patch: Record<string, unknown> = { status: next, updated_at: new Date().toISOString() };
  if (typeof n.payment_type === "string") patch.payment_method = n.payment_type;
  if (next === "PAID") {
    patch.paid_at = new Date().toISOString();
    patch.paid_source = "MIDTRANS_WEBHOOK";
  }
  const upd = await admin
    .from("payment_attempt")
    .update(patch)
    .eq("id", attempt.id)
    .not("status", "in", "(PAID,REFUND_PENDING,REFUNDED)")
    .select("id");
  if (upd.error) {
    console.error("update payment_attempt", upd.error);
    return json(500, { error: "Gagal memperbarui pembayaran" });
  }
  if (!upd.data?.length) return json(200, { ignored: "pembayaran sudah final" });

  if (next === "PAID") {
    const ord = await admin
      .from("customer_order")
      .update({ status: "PAID", paid_at: new Date().toISOString(), updated_at: new Date().toISOString() })
      .eq("id", attempt.order_id)
      .eq("status", "AWAITING_PAYMENT")
      .select("id");
    if (ord.error) {
      console.error("update customer_order", ord.error);
      return json(500, { error: "Gagal memperbarui pesanan" });
    }
    if (ord.data?.length) await addHistory(attempt.order_id, "AWAITING_PAYMENT", "PAID", "Pembayaran Midtrans berhasil");
    else console.error("Order tidak lagi AWAITING_PAYMENT saat pembayaran masuk", attempt.order_id);
  } else if (next === "EXPIRED" || next === "CANCELLED") {
    const reason = next === "EXPIRED" ? "Pembayaran kedaluwarsa" : "Pembayaran dibatalkan";
    const ord = await admin
      .from("customer_order")
      .update({ status: "CANCELLED", cancellation_reason: reason, updated_at: new Date().toISOString() })
      .eq("id", attempt.order_id)
      .eq("status", "AWAITING_PAYMENT")
      .select("id");
    if (ord.error) {
      console.error("update customer_order", ord.error);
      return json(500, { error: "Gagal memperbarui pesanan" });
    }
    if (ord.data?.length) await addHistory(attempt.order_id, "AWAITING_PAYMENT", "CANCELLED", reason);
  }

  return json(200, { ok: true });
});
