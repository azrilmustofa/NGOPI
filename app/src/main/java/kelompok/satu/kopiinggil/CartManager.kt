package kelompok.satu.kopiinggil

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

// Satu pilihan yang dipilih pelanggan, lengkap dengan id supaya bisa disimpan ke order_item_option.
@Serializable
data class OpsiDipilih(
    val id: String,
    val namaGrup: String,
    val nama: String,
    val selisihHarga: Int,
)

@Serializable
data class CartItem(
    val barang: DataBarang,
    var quantity: Int = 1,
    // Pilihan (mis. "Oat milk", "Less sugar") dan catatan barista.
    val pilihan: List<OpsiDipilih> = emptyList(),
    val catatan: String = "",
) {
    val opsi: List<String> get() = pilihan.map { it.nama }
    val hargaOpsi: Int get() = pilihan.sumOf { it.selisihHarga }
    val hargaSatuan: Int get() = barang.harga + hargaOpsi
}

// Isi keranjang yang disimpan di HP. Disimpan per akun + meja (lihat CartManager.storageKey).
@Serializable
private data class SavedCart(
    val savedAt: Long,
    val items: List<CartItem>,
)

object CartManager {
    private val _items = mutableListOf<CartItem>()
    val items: List<CartItem> get() = _items

    var tableNumber: Int = 0

    // uuid cafe_table.id dari hasil scan QR; kosong kalau validasi QR dilewati.
    var tableId: String = ""

    private var prefs: SharedPreferences? = null
    private var ownerUid = ""
    private val json = Json { ignoreUnknownKeys = true }

    private const val PREFS = "cart"
    // Keranjang yang sudah lama (mis. kunjungan kemarin) tidak dipulihkan.
    private const val MAX_AGE_MS = 6 * 60 * 60_000L

    // Satu keranjang per akun per meja: pindah meja lalu kembali, keranjang meja awal tetap ada.
    private val storageKey: String
        get() = "$ownerUid|" + tableId.ifEmpty { "meja-$tableNumber" }

    // Dipanggil saat masuk ke meja (HomeActivity): pulihkan keranjang meja ini milik akun ini.
    fun restore(context: Context, tableNumber: Int, tableId: String, ownerUid: String) {
        val p = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs = p
        this.tableNumber = tableNumber
        this.tableId = tableId
        this.ownerUid = ownerUid
        _items.clear()

        // Buang keranjang lama dari semua meja sekalian.
        val now = System.currentTimeMillis()
        val editor = p.edit()
        p.all.forEach { (key, value) ->
            val saved = (value as? String)?.let { runCatching { json.decodeFromString<SavedCart>(it) }.getOrNull() }
            if (saved == null || now - saved.savedAt >= MAX_AGE_MS) editor.remove(key)
            else if (key == storageKey) _items.addAll(saved.items)
        }
        editor.apply()
    }

    // Lepas keranjang dari memori tanpa menghapus yang tersimpan (pindah layar/login ulang).
    fun unload() {
        _items.clear()
    }

    // Logout: hapus semua keranjang tersimpan milik akun ini.
    fun clearAll() {
        _items.clear()
        val p = prefs ?: return
        val editor = p.edit()
        p.all.keys.filter { it.startsWith("$ownerUid|") }.forEach(editor::remove)
        editor.apply()
    }

    private fun persist() {
        val p = prefs ?: return
        if (_items.isEmpty()) {
            p.edit().remove(storageKey).apply()
        } else {
            val saved = SavedCart(System.currentTimeMillis(), _items.toList())
            p.edit().putString(storageKey, json.encodeToString(saved)).apply()
        }
    }

    // Baris yang sama = menu, pilihan, dan catatan yang sama.
    fun addItem(
        barang: DataBarang,
        quantity: Int = 1,
        pilihan: List<OpsiDipilih> = emptyList(),
        catatan: String = "",
    ) {
        val existing = _items.find { it.barang.id == barang.id && it.pilihan == pilihan && it.catatan == catatan }
        if (existing != null) {
            existing.quantity += quantity
        } else {
            _items.add(CartItem(barang, quantity, pilihan, catatan))
        }
        persist()
    }

    fun increaseItem(item: CartItem) {
        item.quantity++
        persist()
    }

    fun decreaseItem(item: CartItem) {
        item.quantity--
        if (item.quantity <= 0) _items.remove(item)
        persist()
    }

    fun decreaseItem(barang: DataBarang) {
        val existing = _items.find { it.barang.id == barang.id }
        if (existing != null) decreaseItem(existing)
    }

    fun removeItem(barang: DataBarang) {
        _items.removeAll { it.barang.id == barang.id }
        persist()
    }

    // Terapkan hasil quote-cart: harga diperbarui ke harga server, baris yang habis dihapus.
    // Diabaikan kalau keranjang sudah berubah sejak dicek (jumlah baris berbeda).
    fun applyQuote(quote: CartQuote): Boolean {
        if (quote.lines.size != _items.size) return false
        val updated = quote.lines.sortedBy { it.index }.mapNotNull { line ->
            val item = _items[line.index]
            if (line.isUnavailable) return@mapNotNull null
            val deltas = line.options.associate { it.id to it.priceDelta }
            item.copy(
                barang = item.barang.copy(harga = line.basePrice),
                pilihan = item.pilihan.map { it.copy(selisihHarga = deltas[it.id] ?: it.selisihHarga) },
            )
        }
        _items.clear()
        _items.addAll(updated)
        persist()
        return true
    }

    // Kosongkan keranjang meja ini, termasuk yang tersimpan (sudah dibayar).
    fun clear() {
        _items.clear()
        persist()
    }

    fun getTotalPrice(): Int {
        return _items.sumOf { it.hargaSatuan * it.quantity }
    }

    fun getTotalItemCount(): Int {
        return _items.sumOf { it.quantity }
    }
}
