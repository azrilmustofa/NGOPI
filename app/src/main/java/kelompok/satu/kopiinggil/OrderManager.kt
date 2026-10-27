package kelompok.satu.kopiinggil

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.rpc
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

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

@Serializable
private data class OrderDto(
    val id: String,
    @SerialName("public_id") val publicId: String,
    @SerialName("table_id") val tableId: String = "",
    val status: String = "",
    @SerialName("subtotal_rp") val subtotal: Int = 0,
    @SerialName("tax_rp") val tax: Int = 0,
    @SerialName("total_rp") val total: Int = 0,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class OrderItemDto(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("name_snapshot") val nameSnapshot: String,
    @SerialName("unit_price_rp") val unitPrice: Int = 0,
    val quantity: Int = 1,
    val note: String? = null,
    @SerialName("line_total_rp") val lineTotal: Int = 0,
)

@Serializable
private data class PaymentAttemptDto(
    @SerialName("payment_method") val paymentMethod: String = "QRIS",
)

object OrderManager {
    var lastOrder: PlacedOrder? = null
        private set

    fun clear() {
        lastOrder = null
    }

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

    suspend fun fetchLatestOrder(): PlacedOrder? {
        val currentUser = supabase.auth.currentUserOrNull()
        if (currentUser == null) {
            lastOrder = null
            return null
        }

        return try {
            val orders = supabase.from("customer_order").select {
                filter { eq("owner_uid", currentUser.id) }
                order("created_at", Order.DESCENDING)
                limit(1)
            }.decodeList<OrderDto>()

            if (orders.isEmpty()) {
                lastOrder = null
                return null
            }

            val latest = orders.first()
            val items = supabase.from("order_item").select {
                filter { eq("order_id", latest.id) }
            }.decodeList<OrderItemDto>()

            val cartItems = items.map { itemDto ->
                CartItem(
                    barang = DataBarang(
                        id = itemDto.id,
                        nama = itemDto.nameSnapshot,
                        harga = itemDto.unitPrice,
                    ),
                    quantity = itemDto.quantity,
                    catatan = itemDto.note.orEmpty(),
                )
            }

            val tableNum = if (latest.tableId.isNotEmpty()) {
                try {
                    supabase.from("cafe_table").select {
                        filter { eq("id", latest.tableId) }
                    }.decodeSingleOrNull<CafeTable>()?.tableNumber ?: CartManager.tableNumber
                } catch (_: Exception) {
                    CartManager.tableNumber
                }
            } else {
                CartManager.tableNumber
            }

            val paymentMethod = try {
                val attempts = supabase.from("payment_attempt").select {
                    filter { eq("order_id", latest.id) }
                    limit(1)
                }.decodeList<PaymentAttemptDto>()
                when (attempts.firstOrNull()?.paymentMethod) {
                    "VIRTUAL_ACCOUNT" -> MetodeBayar.VIRTUAL_ACCOUNT
                    "EWALLET" -> MetodeBayar.EWALLET
                    else -> MetodeBayar.QRIS
                }
            } catch (_: Exception) {
                MetodeBayar.QRIS
            }

            val order = PlacedOrder(
                number = "#${latest.publicId}",
                table = tableNum,
                items = cartItems,
                subtotal = latest.subtotal,
                tax = latest.tax,
                total = latest.total,
                method = paymentMethod,
                paidAt = formatTime(latest.paidAt ?: latest.createdAt),
            )
            lastOrder = order
            order
        } catch (e: Exception) {
            lastOrder
        }
    }

    private fun formatTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(Date())
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val clean = isoString.substringBefore(".").substringBefore("+").substringBefore("Z")
            val date = parser.parse(clean)
            val formatter = SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).apply {
                timeZone = TimeZone.getDefault()
            }
            formatter.format(date ?: Date())
        } catch (_: Exception) {
            SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(Date())
        }
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
