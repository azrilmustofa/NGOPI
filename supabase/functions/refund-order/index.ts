import { createClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const ANON_KEY = Deno.env.get("SUPABASE_ANON_KEY")!;
const MIDTRANS_SERVER_KEY = Deno.env.get("MIDTRANS_SERVER_KEY") ?? "";
const MIDTRANS_URL = MIDTRANS_SERVER_KEY.startsWith("SB-")
  ? "https://api.sandbox.midtrans.com"
  : "https://api.midtrans.com";
const admin = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false } });

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

async function handle(req: Request) {
  if (!MIDTRANS_SERVER_KEY) return json(500, { error: "MIDTRANS_SERVER_KEY belum diatur" });
  const authorization = req.headers.get("Authorization") ?? "";
  const token = authorization.replace(/^Bearer\s+/i, "");
  if (!token) return json(401, { error: "Belum login" });
  const auth = await admin.auth.getUser(token);
  if (auth.error || !auth.data.user) return json(401, { error: "Sesi tidak valid, silakan login ulang" });

  const body = await req.json().catch(() => null);
  if (typeof body?.order_id !== "string" || typeof body?.expected_status !== "string" ||
    typeof body?.reason !== "string") return json(400, { error: "Data pembatalan tidak valid" });

  const caller = createClient(SUPABASE_URL, ANON_KEY, {
    auth: { persistSession: false },
    global: { headers: { Authorization: authorization } },
  });
  const started = await caller.rpc("request_order_refund", {
    p_order_id: body.order_id,
    p_expected_status: body.expected_status,
    p_reason: body.reason,
  });
  if (started.error || !started.data) {
    console.error("request_order_refund", started.error);
    return json(400, { error: started.error?.message ?? "Pembatalan ditolak server" });
  }
  const refund = started.data as Record<string, unknown>;
  const status = String(refund.refund_status ?? "");
  if (!["REQUESTED", "PROCESSING", "FAILED"].includes(status)) return json(200, refund);

  const refundId = String(refund.id);
  const providerOrderId = String(refund.provider_order_id ?? "");
  if (!providerOrderId) return json(500, { error: "Referensi pembayaran tidak ditemukan", refund });

  await admin.rpc("update_refund_result", {
    p_refund_id: refundId,
    p_status: "PROCESSING",
    p_provider_reference: null,
    p_provider_response: null,
  });

  let providerResponse: Record<string, unknown> = {};
  try {
    const response = await fetch(`${MIDTRANS_URL}/v2/${encodeURIComponent(providerOrderId)}/refund`, {
      method: "POST",
      headers: {
        Accept: "application/json",
        "Content-Type": "application/json",
        Authorization: "Basic " + btoa(MIDTRANS_SERVER_KEY + ":"),
      },
      body: JSON.stringify({
        refund_key: refund.refund_key,
        amount: refund.amount_rp,
        reason: refund.reason,
      }),
      signal: AbortSignal.timeout(30_000),
    });
    providerResponse = await response.json().catch(() => ({}));
    const success = response.ok && String(providerResponse.status_code ?? "200").startsWith("2");
    const reference = String(providerResponse.refund_chargeback_id ?? providerResponse.refund_id ??
      providerResponse.transaction_id ?? refund.refund_key ?? "");
    const saved = await admin.rpc("update_refund_result", {
      p_refund_id: refundId,
      p_status: success ? "REFUNDED" : "FAILED",
      p_provider_reference: reference || null,
      p_provider_response: providerResponse,
    });
    if (saved.error) {
      console.error("update_refund_result", saved.error);
      return json(500, { error: "Hasil refund belum dapat disimpan" });
    }
    if (!success) {
      console.error("Midtrans refund ditolak", response.status, providerResponse);
      return json(502, { error: String(providerResponse.status_message ?? "Refund ditolak Midtrans"), refund: saved.data });
    }
    return json(200, saved.data);
  } catch (error) {
    console.error("Midtrans refund gagal", error);
    await admin.rpc("update_refund_result", {
      p_refund_id: refundId,
      p_status: "FAILED",
      p_provider_reference: null,
      p_provider_response: { error: "Midtrans tidak dapat dihubungi" },
    });
    return json(502, { error: "Pesanan dibatalkan, tetapi refund belum berhasil. Coba lagi dari detail transaksi." });
  }
}

Deno.serve((req) => req.method === "POST" ? handle(req) : json(405, { error: "Method not allowed" }));
