package kelompok.satu.kopiinggil

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

enum class MetodeBayar(val label: String) {
    QRIS("QRIS"),
    VIRTUAL_ACCOUNT("Virtual account"),
    EWALLET("E-wallet");

    companion object {
        // payment_type dari Midtrans (mis. "qris", "gopay", "bank_transfer") atau nilai lama di database.
        fun fromProvider(type: String?): MetodeBayar = when (type?.lowercase()) {
            "gopay", "shopeepay", "dana", "ovo", "linkaja", "ewallet" -> EWALLET
            "bank_transfer", "echannel", "permata", "bca_klikpay", "virtual_account" -> VIRTUAL_ACCOUNT
            else -> QRIS
        }
    }
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
    val status: String = "PAID",
) {
    // Teks status untuk pelanggan; status server: PAID -> ACCEPTED -> PREPARING -> READY -> COMPLETED.
    val statusLabel: String
        get() = when (status.uppercase()) {
            "PREPARING", "BREWING", "DIBUAT" -> "Sedang diracik"
            "READY", "SIAP" -> "Siap diantar"
            "COMPLETED" -> "Selesai"
            "CANCELLED" -> "Dibatalkan"
            else -> "Diproses"
        }
}

// Pesanan sudah dibuat di server (AWAITING_PAYMENT) dan menunggu pembayaran Midtrans selesai.
data class PendingPayment(
    val orderId: String,
    val publicId: String,
    val redirectUrl: String,
    val expiresAtMillis: Long,
    val table: Int,
    val items: List<CartItem>,
    val subtotal: Int,
    val tax: Int,
    val total: Int,
)

enum class PaymentState { PENDING, PAID, FAILED }

@Serializable
private data class ItemParam(
    @SerialName("menu_item_id") val menuItemId: String,
    val quantity: Int,
    val note: String,
    @SerialName("option_ids") val optionIds: List<String>,
)

@Serializable
private data class CreatePaymentParams(
    @SerialName("table_id") val tableId: String,
    @SerialName("checkout_key") val checkoutKey: String,
    val items: List<ItemParam>,
    // Nama & WA tidak dikirim: server mengambilnya dari akun yang login dan menyimpannya di private_customer.
    @SerialName("consent_wa") val consentWa: Boolean,
    @SerialName("consent_promo") val consentPromo: Boolean,
)

@Serializable
private data class CreatePaymentResult(
    @SerialName("order_id") val orderId: String,
    @SerialName("public_id") val publicId: String,
    @SerialName("redirect_url") val redirectUrl: String,
    @SerialName("subtotal_rp") val subtotal: Int,
    @SerialName("tax_rp") val tax: Int,
    @SerialName("service_charge_rp") val service: Int = 0,
    @SerialName("total_rp") val total: Int,
)

@Serializable
private data class QuoteItemParam(
    @SerialName("menu_item_id") val menuItemId: String,
    val quantity: Int,
    @SerialName("option_ids") val optionIds: List<String>,
    @SerialName("unit_price_rp") val unitPrice: Int,
)

@Serializable
private data class QuoteCartParams(val items: List<QuoteItemParam>)

@Serializable
data class QuoteOption(
    val id: String,
    @SerialName("price_delta_rp") val priceDelta: Int,
)

// Hasil cek satu baris keranjang dari quote-cart. index = urutan di CartManager.items saat dikirim.
@Serializable
data class QuoteLine(
    val index: Int,
    val status: String,
    val name: String = "",
    val reason: String? = null,
    @SerialName("base_price_rp") val basePrice: Int = 0,
    val options: List<QuoteOption> = emptyList(),
    @SerialName("unit_price_rp") val unitPrice: Int = 0,
) {
    val isUnavailable: Boolean get() = status == "UNAVAILABLE"
    val isChanged: Boolean get() = status != "OK"
}

// Hitungan resmi keranjang dari server (sebelum pesanan dibuat). Total hanya dari baris yang masih tersedia.
@Serializable
data class CartQuote(
    val lines: List<QuoteLine>,
    @SerialName("subtotal_rp") val subtotal: Int,
    @SerialName("tax_rp") val tax: Int,
    @SerialName("service_charge_rp") val service: Int = 0,
    @SerialName("total_rp") val total: Int,
    @SerialName("tax_percentage") val taxPercentage: Double = 0.0,
    @SerialName("service_charge_percentage") val servicePercentage: Double = 0.0,
) {
    val hasChanges: Boolean get() = lines.any { it.isChanged }
}

@Serializable
private data class OrderDto(
    val id: String = "",
    @SerialName("public_id") val publicId: String = "",
    @SerialName("table_id") val tableId: String? = null,
    val status: String = "",
    @SerialName("subtotal_rp") val subtotal: Int = 0,
    @SerialName("tax_rp") val tax: Int = 0,
    @SerialName("total_rp") val total: Int = 0,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class OrderItemDto(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("name_snapshot") val nameSnapshot: String = "",
    @SerialName("unit_price_rp") val unitPrice: Int = 0,
    val quantity: Int = 1,
    val note: String? = null,
    @SerialName("line_total_rp") val lineTotal: Int = 0,
)

@Serializable
private data class PaymentAttemptDto(
    @SerialName("order_id") val orderId: String = "",
    val status: String = "",
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
)

class PaymentException(message: String) : Exception(message)

object OrderManager {
    var lastOrder: PlacedOrder? = null
        private set

    var orderHistory: List<PlacedOrder> = emptyList()
        private set

    var selectedOrder: PlacedOrder? = null

    var pendingPayment: PendingPayment? = null
        private set

    private val json = Json { ignoreUnknownKeys = true }

    // Sama dengan masa berlaku transaksi di create-payment (30 menit).
    private const val PAYMENT_WINDOW_MS = 30 * 60_000L

    // Pesanan AWAITING_PAYMENT / CANCELLED belum jadi pesanan: tidak ditampilkan sebagai "diproses".
    // Nilai harus sesuai enum order_status di PostgreSQL Supabase (PAID, ACCEPTED, PREPARING, READY, COMPLETED).
    private val PAID_STATUSES = listOf("PAID", "ACCEPTED", "PREPARING", "READY", "COMPLETED")

    // Transaksi gagal/kedaluwarsa tidak bisa dilanjutkan; pembayaran berikutnya membuat transaksi baru.
    fun discardPending() {
        pendingPayment = null
    }

    fun clear() {
        lastOrder = null
        orderHistory = emptyList()
        selectedOrder = null
        pendingPayment = null
    }

    // Perkiraan untuk tampilan checkout; angka resmi (dari app_settings) datang dari server.
    fun taxOf(subtotal: Int): Int = subtotal * 10 / 100

    // Cek keranjang ke server (Edge Function quote-cart): harga terbaru, stok, pajak, dan service charge.
    // Tidak membuat pesanan; dipakai Checkout untuk menampilkan total resmi dan layar "harga berubah".
    suspend fun quoteCart(): CartQuote {
        val body = json.encodeToString(
            QuoteCartParams(
                CartManager.items.map { item ->
                    QuoteItemParam(item.barang.id, item.quantity, item.pilihan.map { it.id }, item.hargaSatuan)
                }
            )
        )
        val raw = callFunction("quote-cart", body)
        return try {
            json.decodeFromString<CartQuote>(raw)
        } catch (e: SerializationException) {
            android.util.Log.e("OrderManager", "Respons quote-cart tidak sesuai: $raw", e)
            throw PaymentException("Respons server tidak sesuai. Pastikan fungsi quote-cart sudah di-deploy.")
        }
    }

    // Membuat pesanan + transaksi Midtrans Snap (Edge Function create-payment). Pembayaran BELUM lunas di sini:
    // status lunas hanya ditentukan webhook di server. checkoutKey menjaga agar coba ulang tidak membuat pesanan ganda
    // (kunci yang sama mengembalikan transaksi dan redirect_url yang sama).
    suspend fun createPayment(checkoutKey: String, consentPromo: Boolean): PendingPayment {
        val items = CartManager.items.map { it.copy() }
        val body = json.encodeToString(
            CreatePaymentParams(
                tableId = CartManager.tableId,
                checkoutKey = checkoutKey,
                items = items.map { item ->
                    ItemParam(item.barang.id, item.quantity, item.catatan, item.pilihan.map { it.id })
                },
                consentWa = true,
                consentPromo = consentPromo,
            )
        )
        val raw = callFunction("create-payment", body)
        val result = try {
            json.decodeFromString<CreatePaymentResult>(raw)
        } catch (e: SerializationException) {
            android.util.Log.e("OrderManager", "Respons create-payment tidak sesuai: $raw", e)
            throw PaymentException("Respons server pembayaran tidak sesuai. Pastikan fungsi create-payment versi terbaru sudah di-deploy.")
        }
        val pending = PendingPayment(
            orderId = result.orderId,
            publicId = result.publicId,
            redirectUrl = result.redirectUrl,
            expiresAtMillis = System.currentTimeMillis() + PAYMENT_WINDOW_MS,
            table = CartManager.tableNumber,
            items = items,
            subtotal = result.subtotal,
            tax = result.tax + result.service,
            total = result.total,
        )
        pendingPayment = pending
        return pending
    }

    // Membaca status percobaan pembayaran terakhir dari server. Kalau lunas, pesanan disimpan sebagai lastOrder
    // dan keranjang dikosongkan.
    suspend fun checkPayment(): PaymentState {
        val pending = pendingPayment ?: return PaymentState.FAILED
        val attempt = supabase.from("payment_attempt").select {
            filter { eq("order_id", pending.orderId) }
            order("created_at", Order.DESCENDING)
            limit(1)
        }.decodeList<PaymentAttemptDto>().firstOrNull() ?: return PaymentState.PENDING

        android.util.Log.d("OrderManager", "cek bayar ${pending.publicId} attempt=${attempt.status}")
        return when (attempt.status) {
            "PAID" -> {
                val order = PlacedOrder(
                    number = "#${pending.publicId}",
                    table = pending.table,
                    items = pending.items,
                    subtotal = pending.subtotal,
                    tax = pending.tax,
                    total = pending.total,
                    method = MetodeBayar.fromProvider(attempt.paymentMethod),
                    paidAt = formatTime(attempt.paidAt),
                )
                lastOrder = order
                orderHistory = listOf(order) + orderHistory.filterNot { it.number == order.number }
                pendingPayment = null
                CartManager.clear()
                PaymentState.PAID
            }
            "FAILED", "CANCELLED", "EXPIRED" -> PaymentState.FAILED
            else -> PaymentState.PENDING
        }
    }

    // Memanggil Edge Function dengan sesi login pelanggan; mengembalikan body JSON atau melempar PaymentException.
    private suspend fun callFunction(name: String, body: String): String = withContext(Dispatchers.IO) {
        val token = supabase.auth.currentAccessTokenOrNull() ?: throw PaymentException("Sesi login berakhir, silakan login ulang")
        val conn = URL("$SUPABASE_URL/functions/v1/$name").openConnection() as HttpURLConnection
        try {
            conn.requestMethod = "POST"
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.doOutput = true
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.setRequestProperty("apikey", SUPABASE_KEY)
            conn.outputStream.use { it.write(body.toByteArray()) }

            val ok = conn.responseCode in 200..299
            val text = (if (ok) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (!ok) {
                val message = runCatching { json.parseToJsonElement(text).jsonObject["error"]?.jsonPrimitive?.content }.getOrNull()
                throw PaymentException(message ?: "Server menolak permintaan (${conn.responseCode})")
            }
            text
        } catch (e: PaymentException) {
            throw e
        } catch (e: Exception) {
            throw PaymentException("Tidak dapat terhubung ke server")
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchOrders(): List<PlacedOrder> {
        val currentUser = supabase.auth.currentUserOrNull()
        if (currentUser == null) {
            lastOrder = null
            orderHistory = emptyList()
            return emptyList()
        }

        return try {
            val orders = supabase.from("customer_order").select {
                filter {
                    eq("owner_uid", currentUser.id)
                    isIn("status", PAID_STATUSES)
                }
                order("created_at", Order.DESCENDING)
            }.decodeList<OrderDto>()

            if (orders.isEmpty()) {
                lastOrder = null
                orderHistory = emptyList()
                return emptyList()
            }

            val orderIds = orders.map { it.id }

            val itemsMap: Map<String, List<OrderItemDto>> = try {
                if (orderIds.isNotEmpty()) {
                    supabase.from("order_item").select {
                        filter { isIn("order_id", orderIds) }
                    }.decodeList<OrderItemDto>().groupBy { it.orderId }
                } else emptyMap()
            } catch (e: Exception) {
                android.util.Log.e("OrderManager", "Gagal memuat item pesanan", e)
                emptyMap()
            }

            val tableMap: Map<String, CafeTable> = try {
                supabase.from("cafe_table").select().decodeList<CafeTable>().associateBy { it.id }
            } catch (_: Exception) {
                emptyMap()
            }

            val attemptsMap: Map<String, List<PaymentAttemptDto>> = try {
                if (orderIds.isNotEmpty()) {
                    supabase.from("payment_attempt").select {
                        filter { isIn("order_id", orderIds) }
                        order("created_at", Order.DESCENDING)
                    }.decodeList<PaymentAttemptDto>().groupBy { it.orderId }
                } else emptyMap()
            } catch (_: Exception) {
                emptyMap()
            }

            val result = orders.map { orderDto ->
                val items = itemsMap[orderDto.id].orEmpty().map { itemDto ->
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

                val tableNum = if (!orderDto.tableId.isNullOrEmpty()) {
                    tableMap[orderDto.tableId]?.tableNumber ?: CartManager.tableNumber
                } else {
                    CartManager.tableNumber
                }

                val paymentMethod = MetodeBayar.fromProvider(attemptsMap[orderDto.id]?.firstOrNull()?.paymentMethod)

                PlacedOrder(
                    number = "#${orderDto.publicId}",
                    table = tableNum,
                    items = items,
                    subtotal = orderDto.subtotal,
                    tax = orderDto.tax,
                    total = orderDto.total,
                    method = paymentMethod,
                    paidAt = formatTime(orderDto.paidAt ?: orderDto.createdAt),
                    status = orderDto.status,
                )
            }

            orderHistory = result
            lastOrder = result.firstOrNull()
            result
        } catch (e: Exception) {
            android.util.Log.e("OrderManager", "Gagal memuat riwayat pesanan", e)
            orderHistory
        }
    }

    suspend fun fetchLatestOrder(): PlacedOrder? {
        fetchOrders()
        return lastOrder
    }

    private fun formatTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(Date())
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val clean = isoString.substringBefore(".").substringBefore("+").substringBefore("Z")
            val date = parser.parse(clean) ?: Date()
            val isToday = android.text.format.DateUtils.isToday(date.time)
            val pattern = if (isToday) "HH.mm" else "d MMM, HH.mm"
            val formatter = SimpleDateFormat(pattern, Locale.forLanguageTag("id-ID")).apply {
                timeZone = TimeZone.getDefault()
            }
            formatter.format(date)
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
