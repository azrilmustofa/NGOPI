import { createClient } from "npm:@supabase/supabase-js@2";
import { JWT } from "npm:google-auth-library@10";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const FIREBASE_PROJECT_ID = Deno.env.get("FIREBASE_PROJECT_ID") ?? "";
const FIREBASE_CLIENT_EMAIL = Deno.env.get("FIREBASE_CLIENT_EMAIL") ?? "";
const FIREBASE_PRIVATE_KEY = (Deno.env.get("FIREBASE_PRIVATE_KEY") ?? "").replace(/\\n/g, "\n");
const admin = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false } });

type PushEvent = {
  id: string;
  order_id: string;
  user_id: string | null;
  audience: "CUSTOMER" | "STAFF";
  status: string;
};

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

function copy(status: string, publicId: string, audience: PushEvent["audience"]) {
  if (audience === "STAFF") {
    return { title: `Pesanan baru #${publicId}`, body: "Pembayaran terverifikasi. Buka antrean produksi." };
  }
  const messages: Record<string, [string, string]> = {
    PAID: ["Pembayaran berhasil", `Pesanan #${publicId} sudah masuk antrean.`],
    ACCEPTED: ["Pesanan diterima", `Barista menerima pesanan #${publicId}.`],
    PREPARING: ["Pesanan sedang diracik", `Barista sedang membuat pesanan #${publicId}.`],
    READY: ["Pesanan siap diantar", `Pesanan #${publicId} sudah siap.`],
    COMPLETED: ["Pesanan selesai", `Pesanan #${publicId} telah selesai.`],
    CANCELLED: ["Pesanan dibatalkan", `Pesanan #${publicId} telah dibatalkan.`],
  };
  const [title, body] = messages[status] ?? ["Status pesanan berubah", `Buka pesanan #${publicId} untuk melihat status terbaru.`];
  return { title, body };
}

async function accessToken() {
  const client = new JWT({
    email: FIREBASE_CLIENT_EMAIL,
    key: FIREBASE_PRIVATE_KEY,
    scopes: ["https://www.googleapis.com/auth/firebase.messaging"],
  });
  const token = await client.getAccessToken();
  if (!token.token) throw new Error("Firebase access token tidak tersedia");
  return token.token;
}

async function tokensFor(event: PushEvent): Promise<string[]> {
  if (event.audience === "CUSTOMER") {
    if (!event.user_id) return [];
    const devices = await admin.from("push_device").select("token").eq("user_id", event.user_id);
    if (devices.error) throw devices.error;
    return devices.data.map((device) => device.token as string);
  }
  const staff = await admin.from("staff_user").select("uid").eq("is_active", true).eq("role", "BARISTA");
  if (staff.error) throw staff.error;
  const ids = staff.data.map((user) => user.uid as string);
  if (!ids.length) return [];
  const devices = await admin.from("push_device").select("token").in("user_id", ids);
  if (devices.error) throw devices.error;
  return devices.data.map((device) => device.token as string);
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });
  if (!FIREBASE_PROJECT_ID || !FIREBASE_CLIENT_EMAIL || !FIREBASE_PRIVATE_KEY) {
    return json(500, { error: "Secret Firebase belum lengkap" });
  }
  const payload = await req.json().catch(() => null);
  const eventId = payload?.record?.id;
  if (typeof eventId !== "string") return json(400, { error: "Event notifikasi tidak valid" });

  const claimed = await admin.rpc("claim_push_event", { p_event_id: eventId });
  if (claimed.error) return json(500, { error: "Gagal mengambil event notifikasi" });
  if (!claimed.data) return json(200, { ignored: "Event sudah diproses" });
  const event = claimed.data as PushEvent;

  try {
    const order = await admin.from("customer_order").select("public_id").eq("id", event.order_id).maybeSingle();
    if (order.error || !order.data) throw order.error ?? new Error("Pesanan tidak ditemukan");
    const devices = await tokensFor(event);
    const message = copy(event.status, String(order.data.public_id), event.audience);
    const bearer = devices.length ? await accessToken() : "";
    const failures: string[] = [];

    for (const token of devices) {
      const response = await fetch(
        `https://fcm.googleapis.com/v1/projects/${encodeURIComponent(FIREBASE_PROJECT_ID)}/messages:send`,
        {
          method: "POST",
          headers: { Authorization: `Bearer ${bearer}`, "Content-Type": "application/json" },
          body: JSON.stringify({
            message: {
              token,
              data: {
                title: message.title,
                body: message.body,
                audience: event.audience,
                order_public_id: String(order.data.public_id),
                status: event.status,
              },
              android: { priority: "HIGH", ttl: "300s" },
            },
          }),
        },
      );
      if (!response.ok) {
        const error = await response.text();
        if (error.includes("UNREGISTERED")) await admin.from("push_device").delete().eq("token", token);
        else failures.push(`${response.status}: ${error.slice(0, 200)}`);
      }
    }

    await admin.rpc("complete_push_event", {
      p_event_id: event.id,
      p_error: failures.length ? failures.join("; ") : devices.length ? null : "Tidak ada perangkat terdaftar",
    });
    return json(200, { sent: devices.length - failures.length, failed: failures.length });
  } catch (error) {
    console.error("order-push", error);
    await admin.rpc("complete_push_event", { p_event_id: event.id, p_error: "Pengiriman notifikasi gagal" });
    return json(500, { error: "Pengiriman notifikasi gagal" });
  }
});
