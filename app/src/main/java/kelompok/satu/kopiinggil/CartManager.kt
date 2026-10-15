package kelompok.satu.kopiinggil

data class CartItem(
    val barang: DataBarang,
    var quantity: Int = 1
)

object CartManager {
    private val _items = mutableListOf<CartItem>()
    val items: List<CartItem> get() = _items

    var tableNumber: Int = 0

    fun addItem(barang: DataBarang) {
        val existing = _items.find { it.barang.id == barang.id }
        if (existing != null) {
            existing.quantity++
        } else {
            _items.add(CartItem(barang, 1))
        }
    }

    fun decreaseItem(barang: DataBarang) {
        val existing = _items.find { it.barang.id == barang.id }
        if (existing != null) {
            existing.quantity--
            if (existing.quantity <= 0) {
                _items.remove(existing)
            }
        }
    }

    fun removeItem(barang: DataBarang) {
        _items.removeAll { it.barang.id == barang.id }
    }

    fun clear() {
        _items.clear()
    }

    fun getTotalPrice(): Int {
        return _items.sumOf { it.barang.harga * it.quantity }
    }

    fun getTotalItemCount(): Int {
        return _items.sumOf { it.quantity }
    }
}

