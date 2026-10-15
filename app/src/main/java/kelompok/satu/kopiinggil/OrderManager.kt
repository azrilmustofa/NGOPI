package kelompok.satu.kopiinggil

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class MetodeBayar(val label: String) {
    QRIS("QRIS"),
    VIRTUAL_ACCOUNT("Virtual account"),
    EWALLET("E-wallet"),
}

data class PlacedOrder(
    val number: String,
    val table: Int,
    val items: List<CartItem>,
    val subtotal: Int,
    val tax: Int,
    val total: Int,
    val method: MetodeBayar,
    val paidAt: String,
)

// Pembayaran belum terhubung ke provider: bayar langsung dianggap lunas dan pesanan diproses.
object OrderManager {
    var lastOrder: PlacedOrder? = null
        private set

    private var counter = 2840

    fun taxOf(subtotal: Int): Int = subtotal * 10 / 100

    fun payAndPlace(method: MetodeBayar): PlacedOrder {
        val subtotal = CartManager.getTotalPrice()
        val tax = taxOf(subtotal)
        counter++
        val order = PlacedOrder(
            number = "#NG-$counter",
            table = CartManager.tableNumber,
            items = CartManager.items.map { it.copy() },
            subtotal = subtotal,
            tax = tax,
            total = subtotal + tax,
            method = method,
            paidAt = SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(Date()),
        )
        lastOrder = order
        CartManager.clear()
        return order
    }
}

// "Susu segar · Es normal · Catatan: ..." untuk baris keranjang/pesanan.
fun CartItem.ringkasan(): String = buildList {
    addAll(opsi)
    if (catatan.isNotEmpty()) add("Catatan: $catatan")
}.joinToString(" · ").ifEmpty { "${hargaSatuan.toRupiah()} / porsi" }

// Mengisi baris "Nama (qty× · opsi) ... harga" ke container.
fun ViewGroup.bindOrderLines(items: List<CartItem>) {
    removeAllViews()
    val inflater = LayoutInflater.from(context)
    items.forEach { item ->
        val row = inflater.inflate(R.layout.item_order_line, this, false)
        val detail = buildList {
            add("${item.quantity}×")
            addAll(item.opsi)
        }.joinToString(" · ")
        row.findViewById<TextView>(R.id.tvLineName).text = "${item.barang.nama} ($detail)"
        row.findViewById<TextView>(R.id.tvLinePrice).text = (item.hargaSatuan * item.quantity).toRupiah()
        addView(row)
    }
}
