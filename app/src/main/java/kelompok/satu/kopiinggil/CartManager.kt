package kelompok.satu.kopiinggil

// Satu pilihan yang dipilih pelanggan, lengkap dengan id supaya bisa disimpan ke order_item_option.
data class OpsiDipilih(
    val id: String,
    val namaGrup: String,
    val nama: String,
    val selisihHarga: Int,
)

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

object CartManager {
    private val _items = mutableListOf<CartItem>()
    val items: List<CartItem> get() = _items

    var tableNumber: Int = 0

    // uuid cafe_table.id dari hasil scan QR; kosong kalau validasi QR dilewati.
    var tableId: String = ""

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
    }

    fun increaseItem(item: CartItem) {
        item.quantity++
    }

    fun decreaseItem(item: CartItem) {
        item.quantity--
        if (item.quantity <= 0) _items.remove(item)
    }

    fun decreaseItem(barang: DataBarang) {
        val existing = _items.find { it.barang.id == barang.id }
        if (existing != null) decreaseItem(existing)
    }

    fun removeItem(barang: DataBarang) {
        _items.removeAll { it.barang.id == barang.id }
    }

    fun clear() {
        _items.clear()
    }

    fun getTotalPrice(): Int {
        return _items.sumOf { it.hargaSatuan * it.quantity }
    }

    fun getTotalItemCount(): Int {
        return _items.sumOf { it.quantity }
    }
}
