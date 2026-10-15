package kelompok.satu.kopiinggil

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
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

@Serializable
private data class ItemParam(
    @SerialName("menu_item_id") val menuItemId: String,
    val quantity: Int,
    val note: String,
    @SerialName("option_ids") val optionIds: List<String>,
)

@Serializable
private data class CreateOrderParams(
    @SerialName("p_table_id") val tableId: String,
    @SerialName("p_checkout_key") val checkoutKey: String,
    @SerialName("p_payment_method") val method: String,
    @SerialName("p_items") val items: List<ItemParam>,
)

@Serializable
private data class CreateOrderResult(
    @SerialName("public_id") val publicId: String,
    @SerialName("subtotal_rp") val subtotal: Int,
    @SerialName("tax_rp") val tax: Int,
    @SerialName("total_rp") val total: Int,
)

// Pembayaran belum terhubung ke provider: bayar langsung dianggap lunas, tetapi pesanan disimpan
// ke Supabase lewat function create_order (lihat supabase/create_order.sql).
object OrderManager {
    var lastOrder: PlacedOrder? = null
        private set

    // Perkiraan untuk tampilan checkout; angka resmi (dari app_settings) datang dari server.
    fun taxOf(subtotal: Int): Int = subtotal * 10 / 100

    // checkoutKey menjaga agar coba ulang setelah gagal jaringan tidak membuat pesanan ganda.
    suspend fun payAndPlace(method: MetodeBayar, checkoutKey: String): PlacedOrder {
        val items = CartManager.items.map { it.copy() }
        val result = supabase.postgrest.rpc(
            "create_order",
            CreateOrderParams(
                tableId = CartManager.tableId,
                checkoutKey = checkoutKey,
                method = method.name,
                items = items.map { item ->
                    ItemParam(item.barang.id, item.quantity, item.catatan, item.pilihan.map { it.id })
                },
            ),
        ).decodeAs<CreateOrderResult>()
        val order = PlacedOrder(
            number = "#${result.publicId}",
            table = CartManager.tableNumber,
            items = items,
            subtotal = result.subtotal,
            tax = result.tax,
            total = result.total,
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
