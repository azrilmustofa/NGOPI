package kelompok.satu.kopiinggil

data class CartItem(
    val barang: DataBarang,
    var quantity: Int = 1,
    // Ringkasan pilihan (mis. "Oat milk", "Less sugar"), catatan barista, dan tambahan harga per porsi.
    val opsi: List<String> = emptyList(),
    val catatan: String = "",
    val hargaOpsi: Int = 0,
) {
    val hargaSatuan: Int get() = barang.harga + hargaOpsi
}

object CartManager {
    private val _items = mutableListOf<CartItem>()
    val items: List<CartItem> get() = _items

    var tableNumber: Int = 0

    // Baris yang sama = menu, pilihan, dan catatan yang sama.
    fun addItem(
        barang: DataBarang,
        quantity: Int = 1,
        opsi: List<String> = emptyList(),
        catatan: String = "",
        hargaOpsi: Int = 0,
    ) {
        val existing = _items.find { it.barang.id == barang.id && it.opsi == opsi && it.catatan == catatan }
        if (existing != null) {
            existing.quantity += quantity
        } else {
            _items.add(CartItem(barang, quantity, opsi, catatan, hargaOpsi))
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
