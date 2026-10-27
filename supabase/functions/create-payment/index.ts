// Edge Function: create-payment  (Verify JWT: OFF, login diperiksa di kode lewat auth.getUser)
//
// Dipanggil app saat pelanggan menekan "Bayar". Semua harga dihitung ulang di sini dari database,
// harga dari app tidak dipercaya. Alurnya:
//   1. cek user login + meja aktif + menu/opsi valid
//   2. hitung subtotal, pajak, service charge dari app_settings
//   3. buat customer_order (AWAITING_PAYMENT), order_item, order_item_option, payment_attempt
//   4. minta Snap token ke Midtrans, simpan redirect_url, kembalikan ke app (dibuka app sebagai pop-up)
//
// Secret yang dibutuhkan: MIDTRANS_SERVER_KEY (SUPABASE_URL & SUPABASE_SERVICE_ROLE_KEY sudah otomatis ada).

import { createClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const MIDTRANS_SERVER_KEY = Deno.env.get("MIDTRANS_SERVER_KEY") ?? "";

// Key sandbox diawali "SB-"; selain itu dianggap production.
const SNAP_URL = MIDTRANS_SERVER_KEY.startsWith("SB-")
  ? "https://app.sandbox.midtrans.com/snap/v1/transactions"
  : "https://app.midtrans.com/snap/v1/transactions";
const PAYMENT_WINDOW_MINUTES = 30;

const admin = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false } });

type ItemInput = { menu_item_id: string; quantity: number; note?: string; option_ids?: string[] };
type Body = { table_id: string; checkout_key: string; items: ItemInput[] };

class HttpError extends Error {
  constructor(public status: number, message: string) {
    super(message);
  }
}

const json = (status: number, body: unknown) =>
  new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

// Error supabase-js berupa objek biasa, bukan Error: bungkus supaya jadi respons 500 yang rapi.
function must<T>(res: { data: T | null; error: unknown }, message: string): T {
  if (res.error || res.data == null) {
    console.error(message, res.error);
    throw new HttpError(500, message);
  }
  return res.data;
}

function newPublicId(): string {
  const alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"; // tanpa huruf/angka yang mirip
  const bytes = crypto.getRandomValues(new Uint8Array(6));
  return "NG-" + Array.from(bytes, (b) => alphabet[b % alphabet.length]).join("");
}

async function authUser(req: Request): Promise<string> {
  const token = req.headers.get("Authorization")?.replace(/^Bearer\s+/i, "");
  if (!token) throw new HttpError(401, "Belum login");
  const { data, error } = await admin.auth.getUser(token);
  if (error || !data.user) throw new HttpError(401, "Sesi tidak valid, silakan login ulang");
  return data.user.id;
}

function parseBody(raw: any): Body {
  const bad = () => new HttpError(400, "Data pesanan tidak valid");
  if (
    !raw || typeof raw.table_id !== "string" || !raw.table_id ||
    typeof raw.checkout_key !== "string" || raw.checkout_key.length < 8 || raw.checkout_key.length > 64 ||
    !Array.isArray(raw.items) || raw.items.length === 0 || raw.items.length > 50
  ) throw bad();
  for (const it of raw.items) {
    if (typeof it?.menu_item_id !== "string" || !Number.isInteger(it.quantity) || it.quantity < 1 || it.quantity > 99) throw bad();
    if (it.note != null && typeof it.note !== "string") throw bad();
    if (it.option_ids != null && (!Array.isArray(it.option_ids) || it.option_ids.some((o: unknown) => typeof o !== "string"))) throw bad();
  }
  return raw as Body;
}

// Order dengan checkout_key yang sama (tap ganda / retry) mengembalikan pembayaran yang sama.
async function existingResponse(uid: string, checkoutKey: string) {
  const { data: order } = await admin
    .from("customer_order")
    .select("id, public_id, status, subtotal_rp, tax_rp, service_charge_rp, total_rp")
    .eq("checkout_key", checkoutKey)
    .eq("owner_uid", uid)
    .maybeSingle();
  if (!order) return null;
  const { data: attempt } = await admin
    .from("payment_attempt")
    .select("id, redirect_url")
    .eq("order_id", order.id)
    .order("created_at", { ascending: false })
    .limit(1)
    .maybeSingle();
  if (!attempt?.redirect_url) throw new HttpError(409, "Pesanan sudah dibuat tetapi pembayaran belum siap, coba lagi sebentar");
  return {
    order_id: order.id,
    public_id: order.public_id,
    status: order.status,
    payment_attempt_id: attempt.id,
    redirect_url: attempt.redirect_url,
    subtotal_rp: order.subtotal_rp,
    tax_rp: order.tax_rp,
    service_charge_rp: order.service_charge_rp,
    total_rp: order.total_rp,
  };
}

async function handle(req: Request) {
  if (!MIDTRANS_SERVER_KEY) throw new HttpError(500, "MIDTRANS_SERVER_KEY belum diatur");
  const uid = await authUser(req);
  const body = parseBody(await req.json().catch(() => null));

  const again = await existingResponse(uid, body.checkout_key);
  if (again) return again;

  // --- meja ---
  const tableRes = await admin.from("cafe_table").select("id").eq("id", body.table_id).eq("is_active", true).maybeSingle();
  if (tableRes.error || !tableRes.data) throw new HttpError(400, "Meja tidak valid atau tidak aktif");

  // --- menu ---
  const itemIds = [...new Set(body.items.map((i) => i.menu_item_id))];
  const menuRows = must(
    await admin.from("menu_item").select("id, name, base_price_rp, is_available, is_active").in("id", itemIds),
    "Gagal membaca menu",
  );
  const menuById = new Map(menuRows.map((m) => [m.id as string, m]));
  for (const id of itemIds) {
    const m = menuById.get(id);
    if (!m || !m.is_active || !m.is_available) throw new HttpError(400, `Menu tidak tersedia: ${m?.name ?? id}`);
  }

  // --- opsi: grup yang menempel ke tiap menu, aturan min/max, dan harga tambahan ---
  const optionIds = [...new Set(body.items.flatMap((i) => i.option_ids ?? []))];
  const links = must(
    await admin.from("menu_item_option_group").select("menu_item_id, option_group_id").in("menu_item_id", itemIds),
    "Gagal membaca opsi menu",
  );
  const groupIds = [...new Set(links.map((l) => l.option_group_id as string))];
  const groups = groupIds.length
    ? must(await admin.from("option_group").select("id, name, min_selection, max_selection").in("id", groupIds), "Gagal membaca grup opsi")
    : [];
  const options = optionIds.length
    ? must(
      await admin.from("menu_option").select("id, name, price_delta_rp, is_available, option_group_id").in("id", optionIds),
      "Gagal membaca opsi",
    )
    : [];
  const groupById = new Map(groups.map((g) => [g.id as string, g]));
  const optionById = new Map(options.map((o) => [o.id as string, o]));
  const groupsOfItem = new Map<string, string[]>();
  for (const l of links) {
    const list = groupsOfItem.get(l.menu_item_id as string) ?? [];
    list.push(l.option_group_id as string);
    groupsOfItem.set(l.menu_item_id as string, list);
  }

  // --- hitung tiap baris ---
  type Line = {
    id: string;
    menuItemId: string;
    name: string;
    unitPrice: number;
    quantity: number;
    note: string | null;
    chosen: { id: string; groupName: string; name: string; delta: number }[];
  };
  const lines: Line[] = body.items.map((it) => {
    const menu = menuById.get(it.menu_item_id)!;
    const allowedGroups = groupsOfItem.get(it.menu_item_id) ?? [];
    const chosenIds = [...new Set(it.option_ids ?? [])];
    const perGroup = new Map<string, number>();
    const chosen = chosenIds.map((oid) => {
      const o = optionById.get(oid);
      if (!o || !o.is_available || !allowedGroups.includes(o.option_group_id as string)) {
        throw new HttpError(400, `Opsi tidak valid untuk ${menu.name}`);
      }
      perGroup.set(o.option_group_id as string, (perGroup.get(o.option_group_id as string) ?? 0) + 1);
      return {
        id: o.id as string,
        groupName: groupById.get(o.option_group_id as string)?.name as string,
        name: o.name as string,
        delta: o.price_delta_rp as number,
      };
    });
    for (const gid of allowedGroups) {
      const g = groupById.get(gid);
      if (!g) continue;
      const n = perGroup.get(gid) ?? 0;
      // max_selection < 1 dianggap tanpa batas atas.
      if (n < (g.min_selection as number) || ((g.max_selection as number) >= 1 && n > (g.max_selection as number))) {
        throw new HttpError(400, `Pilihan "${g.name}" untuk ${menu.name} belum sesuai aturan`);
      }
    }
    const unitPrice = (menu.base_price_rp as number) + chosen.reduce((s, c) => s + c.delta, 0);
    return {
      id: crypto.randomUUID(),
      menuItemId: it.menu_item_id,
      name: menu.name as string,
      unitPrice,
      quantity: it.quantity,
      note: it.note?.trim() ? it.note.trim().slice(0, 300) : null,
      chosen,
    };
  });

  // --- total ---
  const settings = must(
    await admin.from("app_settings").select("tax_percentage, service_charge_percentage").eq("id", "default").maybeSingle(),
    "Pengaturan pajak belum ada",
  );
  const subtotal = lines.reduce((s, l) => s + l.unitPrice * l.quantity, 0);
  const tax = Math.round((subtotal * Number(settings.tax_percentage)) / 100);
  const service = Math.round((subtotal * Number(settings.service_charge_percentage)) / 100);
  const total = subtotal + tax + service;

  // --- customer_order (public_id dibuat di sini; ulangi kalau bentrok) ---
  let orderId = "";
  let publicId = "";
  for (let attempt = 0; attempt < 3 && !orderId; attempt++) {
    publicId = newPublicId();
    const ins = await admin
      .from("customer_order")
      .insert({
        public_id: publicId,
        table_id: body.table_id,
        owner_uid: uid,
        checkout_key: body.checkout_key,
        subtotal_rp: subtotal,
        tax_rp: tax,
        service_charge_rp: service,
        total_rp: total,
      })
      .select("id")
      .single();
    if (!ins.error) {
      orderId = ins.data.id as string;
    } else if (ins.error.code === "23505") {
      // Bisa checkout_key kembar (dua request bersamaan) atau public_id kembar.
      const dup = await existingResponse(uid, body.checkout_key);
      if (dup) return dup;
    } else {
      console.error("insert customer_order", ins.error);
      throw new HttpError(500, "Gagal membuat pesanan");
    }
  }
  if (!orderId) throw new HttpError(500, "Gagal membuat nomor pesanan");

  const cancelOrder = (reason: string) =>
    admin.from("customer_order").update({ status: "CANCELLED", cancellation_reason: reason }).eq("id", orderId);

  try {
    const itemsIns = await admin.from("order_item").insert(
      lines.map((l) => ({
        id: l.id,
        order_id: orderId,
        menu_item_id: l.menuItemId,
        name_snapshot: l.name,
        unit_price_rp: l.unitPrice,
        quantity: l.quantity,
        note: l.note,
        line_total_rp: l.unitPrice * l.quantity,
      })),
    );
    if (itemsIns.error) throw itemsIns.error;

    const optionRows = lines.flatMap((l) =>
      l.chosen.map((c) => ({
        order_item_id: l.id,
        menu_option_id: c.id,
        option_group_name_snapshot: c.groupName,
        option_name_snapshot: c.name,
        price_delta_rp: c.delta,
      }))
    );
    if (optionRows.length) {
      const optIns = await admin.from("order_item_option").insert(optionRows);
      if (optIns.error) throw optIns.error;
    }

    // Riwayat status bersifat pelengkap: kegagalannya tidak boleh menggagalkan pembayaran.
    const hist = await admin.from("order_status_history").insert({
      order_id: orderId,
      actor_uid: uid,
      actor_type: "CUSTOMER",
      to_status: "AWAITING_PAYMENT",
      note: "Pesanan dibuat",
    });
    if (hist.error) console.error("order_status_history", hist.error);

    // --- payment_attempt + Snap ---
    // order_id Midtrans harus unik per percobaan, jadi dibuat dari public_id + akhiran acak.
    const providerOrderId = `${publicId}-${crypto.randomUUID().slice(0, 8)}`;
    const expiresAt = new Date(Date.now() + PAYMENT_WINDOW_MINUTES * 60_000).toISOString();
    const attemptIns = await admin
      .from("payment_attempt")
      .insert({ order_id: orderId, provider_order_id: providerOrderId, amount_rp: total, expires_at: expiresAt })
      .select("id")
      .single();
    if (attemptIns.error) throw attemptIns.error;
    const attemptId = attemptIns.data.id as string;

    const itemDetails = lines.map((l) => ({
      id: l.menuItemId.slice(0, 50),
      price: l.unitPrice,
      quantity: l.quantity,
      name: (l.chosen.length ? `${l.name} (${l.chosen.map((c) => c.name).join(", ")})` : l.name).slice(0, 50),
    }));
    if (tax > 0) itemDetails.push({ id: "TAX", price: tax, quantity: 1, name: "Pajak" });
    if (service > 0) itemDetails.push({ id: "SERVICE", price: service, quantity: 1, name: "Service charge" });

    const snapRes = await fetch(SNAP_URL, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Accept: "application/json",
        Authorization: "Basic " + btoa(MIDTRANS_SERVER_KEY + ":"),
      },
      body: JSON.stringify({
        transaction_details: { order_id: providerOrderId, gross_amount: total },
        item_details: itemDetails,
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

    return {
      order_id: orderId,
      public_id: publicId,
      status: "AWAITING_PAYMENT",
      payment_attempt_id: attemptId,
      redirect_url: snap.redirect_url as string,
      subtotal_rp: subtotal,
      tax_rp: tax,
      service_charge_rp: service,
      total_rp: total,
    };
  } catch (e) {
    await cancelOrder("Gagal menyiapkan pembayaran");
    throw e instanceof HttpError ? e : new HttpError(500, "Gagal menyimpan pesanan");
  }
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });
  try {
    return json(200, await handle(req));
  } catch (e) {
    if (e instanceof HttpError) return json(e.status, { error: e.message });
    console.error("create-payment", e);
    return json(500, { error: "Terjadi kesalahan server" });
  }
});
