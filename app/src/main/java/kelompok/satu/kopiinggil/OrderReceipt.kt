package kelompok.satu.kopiinggil

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Serializable
data class ReceiptLine(
    val name: String,
    val quantity: Int,
    @SerialName("line_total_rp") val lineTotalRp: Int,
    val note: String = "",
    val options: List<String> = emptyList(),
)

@Serializable
data class OrderReceipt(
    @SerialName("order_id") val orderId: String,
    @SerialName("public_id") val publicId: String,
    @SerialName("table_label") val tableLabel: String,
    val status: String,
    @SerialName("total_rp") val totalRp: Int,
    @SerialName("subtotal_rp") val subtotalRp: Int,
    @SerialName("tax_rp") val taxRp: Int,
    @SerialName("service_charge_rp") val serviceChargeRp: Int,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("customer_name") val customerName: String,
    @SerialName("phone_e164") val phoneE164: String,
    val items: List<ReceiptLine>,
    @SerialName("last_attempt_at") val lastAttemptAt: String? = null,
    @SerialName("last_attempt_status") val lastAttemptStatus: String? = null,
) {
    fun message(template: ReceiptTemplate?, reason: String): String {
        require(status in RECEIPT_STATUSES && orderId.isNotBlank() && publicId.isNotBlank() && items.isNotEmpty()) {
            "Data pesanan belum siap untuk dikirim"
        }
        return buildString {
            appendLine(template?.header ?: "☕ *KOPI INGGIL*")
            appendLine("*${template?.title ?: "NOTA TRANSAKSI PEMESANAN"}*")
            appendLine("No. Pesanan: *${publicId.trimStart('#')}*")
            appendLine("Meja: $tableLabel")
            appendLine("Nama: $customerName")
            appendLine("Dibayar: ${receiptTime(paidAt)}")
            appendLine("Pembayaran: LUNAS")
            appendLine("Status pesanan: $status")
            appendLine("----------------------------------------")
            items.forEach { item ->
                appendLine("• ${item.quantity}x ${item.name} (${item.lineTotalRp.toRupiah()})")
                if (item.options.isNotEmpty()) appendLine("  Pilihan: ${item.options.joinToString(", ")}")
                if (item.note.isNotBlank()) appendLine("  Catatan: ${item.note}")
            }
            appendLine("----------------------------------------")
            appendLine("Subtotal: ${subtotalRp.toRupiah()}")
            appendLine("Pajak: ${taxRp.toRupiah()}")
            appendLine("Biaya layanan: ${serviceChargeRp.toRupiah()}")
            appendLine("*TOTAL BAYAR: ${totalRp.toRupiah()}*")
            appendLine("Keterangan: $reason")
            appendLine(template?.footer ?: "Terima kasih telah memesan di Kopi Inggil!")
        }.trim()
    }
}

fun receiptTime(iso: String?): String = iso?.let {
    OffsetDateTime.parse(it).atZoneSameInstant(ZoneId.of("Asia/Jakarta"))
        .format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm 'WIB'", Locale.forLanguageTag("id-ID")))
} ?: "Belum tercatat"

suspend fun loadOrderReceipt(orderId: String): OrderReceipt = supabase.postgrest.rpc("get_order_receipt", buildJsonObject {
    put("p_order_id", orderId)
}).decodeAs<OrderReceipt>()
