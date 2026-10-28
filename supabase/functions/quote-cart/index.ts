// Edge Function: quote-cart  (Verify JWT: OFF, login diperiksa di kode lewat auth.getUser)
//
// Dipanggil app saat membuka Checkout, SEBELUM pesanan dibuat. Menghitung ulang keranjang dari database
// dengan aturan yang sama seperti create-payment, tapi tidak menyimpan apa pun. Untuk tiap baris dikembalikan:
//   OK            harga sama dengan yang tampil di app
//   PRICE_CHANGED harga menu/opsi berubah (base_price_rp + options berisi harga terbaru)
//   UNAVAILABLE   menu/opsi habis atau nonaktif (reason berisi penjelasan)
// Total (subtotal, pajak, service charge) hanya dari baris yang masih tersedia.
//
// Catatan: hitungan harga di sini harus tetap sama dengan create-payment/index.ts.

import { createClient } from "npm:@supabase/supabase-js@2";

const SUPABASE_URL = Deno.env.get("SUPABASE_URL")!;
const SERVICE_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

const admin = createClient(SUPABASE_URL, SERVICE_KEY, { auth: { persistSession: false } });

// unit_price_rp = harga satuan yang sedang tampil di app (menu + opsi), untuk dibandingkan.
type ItemInput = { menu_item_id: string; quantity: number; option_ids?: string[]; unit_price_rp: number };
type Body = { items: ItemInput[] };

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

function parseBody(raw: any): Body {
  const bad = () => new HttpError(400, "Data keranjang tidak valid");
  if (!raw || !Array.isArray(raw.items) || raw.items.length === 0 || raw.items.length > 50) throw bad();
  for (const it of raw.items) {
    if (typeof it?.menu_item_id !== "string" || !Number.isInteger(it.quantity) || it.quantity < 1 || it.quantity > 99) throw bad();
    if (!Number.isInteger(it.unit_price_rp)) throw bad();
    if (it.option_ids != null && (!Array.isArray(it.option_ids) || it.option_ids.some((o: unknown) => typeof o !== "string"))) throw bad();
  }
  return raw as Body;
}

async function handle(req: Request) {
  await authUser(req);
  const body = parseBody(await req.json().catch(() => null));

  // --- menu (tanpa filter tersedia: menu habis tetap dibaca supaya namanya bisa ditampilkan) ---
  const itemIds = [...new Set(body.items.map((i) => i.menu_item_id))];
  const menuRows = must(
    await admin.from("menu_item").select("id, name, base_price_rp, is_available, is_active").in("id", itemIds),
    "Gagal membaca menu",
  );
  const menuById = new Map(menuRows.map((m) => [m.id as string, m]));

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

  // --- periksa tiap baris ---
  const lines = body.items.map((it, index) => {
    const menu = menuById.get(it.menu_item_id);
    const name = (menu?.name as string) ?? "Menu";
    const unavailable = (reason: string) => ({ index, status: "UNAVAILABLE", name, reason });

    if (!menu || !menu.is_active) return unavailable("Menu sudah tidak dijual");
    if (!menu.is_available) return unavailable("Stok habis");

    const allowedGroups = groupsOfItem.get(it.menu_item_id) ?? [];
    const chosenIds = [...new Set(it.option_ids ?? [])];
    const perGroup = new Map<string, number>();
    const chosen: { id: string; price_delta_rp: number }[] = [];
    for (const oid of chosenIds) {
      const o = optionById.get(oid);
      if (!o || !allowedGroups.includes(o.option_group_id as string)) return unavailable("Pilihan sudah tidak tersedia");
      if (!o.is_available) return unavailable(`Pilihan "${o.name}" habis`);
      perGroup.set(o.option_group_id as string, (perGroup.get(o.option_group_id as string) ?? 0) + 1);
      chosen.push({ id: o.id as string, price_delta_rp: o.price_delta_rp as number });
    }
    for (const gid of allowedGroups) {
      const g = groupById.get(gid);
      if (!g) continue;
      const n = perGroup.get(gid) ?? 0;
      // max_selection < 1 dianggap tanpa batas atas (sama seperti create-payment).
      if (n < (g.min_selection as number) || ((g.max_selection as number) >= 1 && n > (g.max_selection as number))) {
        return unavailable(`Pilihan "${g.name}" perlu dipilih ulang`);
      }
    }

    const basePrice = menu.base_price_rp as number;
    const unitPrice = basePrice + chosen.reduce((s, c) => s + c.price_delta_rp, 0);
    return {
      index,
      status: unitPrice === it.unit_price_rp ? "OK" : "PRICE_CHANGED",
      name,
      base_price_rp: basePrice,
      options: chosen,
      unit_price_rp: unitPrice,
      quantity: it.quantity,
    };
  });

  // --- total dari baris yang masih tersedia ---
  const settings = must(
    await admin.from("app_settings").select("tax_percentage, service_charge_percentage").eq("id", "default").maybeSingle(),
    "Pengaturan pajak belum ada",
  );
  const subtotal = lines.reduce((s, l) => s + ("unit_price_rp" in l ? l.unit_price_rp * l.quantity : 0), 0);
  const tax = Math.round((subtotal * Number(settings.tax_percentage)) / 100);
  const service = Math.round((subtotal * Number(settings.service_charge_percentage)) / 100);

  return {
    lines,
    subtotal_rp: subtotal,
    tax_rp: tax,
    service_charge_rp: service,
    total_rp: subtotal + tax + service,
    tax_percentage: Number(settings.tax_percentage),
    service_charge_percentage: Number(settings.service_charge_percentage),
  };
}

Deno.serve(async (req) => {
  if (req.method !== "POST") return json(405, { error: "Method not allowed" });
  try {
    return json(200, await handle(req));
  } catch (e) {
    if (e instanceof HttpError) return json(e.status, { error: e.message });
    console.error("quote-cart", e);
    return json(500, { error: "Gagal menghitung keranjang" });
  }
});
