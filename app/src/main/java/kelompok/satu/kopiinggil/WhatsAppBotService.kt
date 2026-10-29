package kelompok.satu.kopiinggil

import android.util.Log
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

@Serializable
private data class WahaSendTextRequest(
    val session: String,
    val chatId: String,
    val text: String,
)

object WhatsAppBotService {
    private const val TAG = "WhatsAppBot"
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // Kandidat endpoint WAHA:
    // 127.0.0.1:3000 (HP fisik via USB kabel: adb reverse tcp:3000 tcp:3000)
    // 192.168.0.117:3000 (IP host Mac saat ini di Wi-Fi)
    // 10.0.2.2:3000 (Emulator Android default)
    private val CANDIDATE_URLS = listOf(
        "http://127.0.0.1:3000",
        "http://192.168.0.117:3000",
        "http://10.0.2.2:3000",
    )

    @Volatile
    var activeBaseUrl: String? = null

    // Menyimpan nomor pesanan terakhir yang sudah berhasil dikirim struknya agar tidak spam
    @Volatile
    var lastSentOrderNumber: String? = null

    // Format nomor WhatsApp: ubah 08xxx / +628xxx / 8xxx menjadi 628xxx@c.us
    fun formatChatId(rawPhone: String): String? {
        val digits = rawPhone.filter(Char::isDigit)
        if (digits.length < 9) return null
        val normalized = when {
            digits.startsWith("62") -> digits
            digits.startsWith("0") -> "62" + digits.substring(1)
            else -> "62$digits"
        }
        return "$normalized@c.us"
    }

    fun buildReceiptText(order: PlacedOrder, customerName: String): String {
        val sb = StringBuilder()
        sb.appendLine("☕ *KOPI INGGIL*")
        sb.appendLine("Jl. Raya Cafe No. 1, Yogyakarta")
        sb.appendLine("----------------------------------------")
        sb.appendLine("*NOTA TRANSAKSI PEMESANAN*")
        sb.appendLine("No. Pesanan : *${order.number}*")
        sb.appendLine("Meja        : *Meja %02d (Dine-in)*".format(order.table))
        sb.appendLine("Waktu       : ${order.paidAt} WIB")
        sb.appendLine("Nama Akun   : $customerName")
        sb.appendLine("Pembayaran  : ${order.method.label} (LUNAS)")
        sb.appendLine("----------------------------------------")
        sb.appendLine("*RINCIAN PESANAN:*")
        for (item in order.items) {
            val lineTotal = item.hargaSatuan * item.quantity
            sb.appendLine("• *${item.quantity}x ${item.barang.nama}* (${lineTotal.toRupiah()})")
            if (item.opsi.isNotEmpty()) {
                sb.appendLine("   _Pilihan: ${item.opsi.joinToString(", ")}_")
            }
            if (item.catatan.isNotBlank()) {
                sb.appendLine("   _Catatan: ${item.catatan}_")
            }
        }
        sb.appendLine("----------------------------------------")
        sb.appendLine("Subtotal       : ${order.subtotal.toRupiah()}")
        sb.appendLine("Pajak & Servis : ${order.tax.toRupiah()}")
        sb.appendLine("*TOTAL BAYAR   : ${order.total.toRupiah()}*")
        sb.appendLine("----------------------------------------")
        sb.appendLine("Status: *${order.statusLabel.uppercase()}*")
        sb.appendLine()
        sb.appendLine("Pesanan Anda sedang disiapkan oleh barista kami.")
        sb.appendLine("Terima kasih telah memesan di Kopi Inggil! 🙏")
        return sb.toString().trim()
    }

    fun buildCompletedReceiptText(
        orderNumber: String,
        tableLabel: String,
        paidAtTime: String,
        customerName: String,
        itemsSummary: List<String>,
        totalRp: Int,
    ): String {
        val sb = StringBuilder()
        sb.appendLine("☕ *KOPI INGGIL*")
        sb.appendLine("Jl. Raya Cafe No. 1, Yogyakarta")
        sb.appendLine("----------------------------------------")
        sb.appendLine("*NOTA TRANSAKSI PEMESANAN*")
        sb.appendLine("No. Pesanan : *$orderNumber*")
        sb.appendLine("Meja        : *$tableLabel*")
        sb.appendLine("Waktu       : $paidAtTime WIB")
        sb.appendLine("Nama Akun   : $customerName")
        sb.appendLine("Pembayaran  : LUNAS")
        sb.appendLine("----------------------------------------")
        sb.appendLine("*RINCIAN PESANAN:*")
        for (item in itemsSummary) {
            sb.appendLine(item)
        }
        sb.appendLine("----------------------------------------")
        sb.appendLine("*TOTAL BAYAR   : ${totalRp.toRupiah()}*")
        sb.appendLine("----------------------------------------")
        sb.appendLine("Status: *SELESAI*")
        sb.appendLine()
        sb.appendLine("Pesanan Anda telah selesai dan siap dinikmati.")
        sb.appendLine("Terima kasih telah memesan di Kopi Inggil! 🙏")
        return sb.toString().trim()
    }

    suspend fun sendTextMessage(
        rawPhone: String,
        message: String,
        orderNumber: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        val chatId = formatChatId(rawPhone)
            ?: return@withContext Result.failure(IllegalStateException("Nomor WhatsApp tidak valid"))

        val payload = json.encodeToString(WahaSendTextRequest(session = "default", chatId = chatId, text = message))

        val endpointsToTry = buildList {
            activeBaseUrl?.let { add(it) }
            addAll(CANDIDATE_URLS.filter { it != activeBaseUrl })
        }

        var lastError: Exception? = null
        for (baseUrl in endpointsToTry) {
            try {
                val url = URL("$baseUrl/api/sendText")
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 3000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                }
                conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
                val code = conn.responseCode
                val responseBody = (if (code in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() }.orEmpty()
                conn.disconnect()

                if (code in 200..299) {
                    activeBaseUrl = baseUrl
                    if (orderNumber != null) lastSentOrderNumber = orderNumber
                    Log.i(TAG, "Nota berhasil dikirim ke $chatId via $baseUrl: $responseBody")
                    return@withContext Result.success(responseBody)
                } else {
                    Log.w(TAG, "WAHA merespons HTTP $code dari $baseUrl: $responseBody")
                    activeBaseUrl = baseUrl
                    val userMsg = when {
                        responseBody.contains("SCAN_QR_CODE") || responseBody.contains("STARTING") ->
                            "Bot WhatsApp belum login (buka http://localhost:3000/dashboard untuk scan QR)"
                        responseBody.contains("Session name is required") ->
                            "Parameter session belum terkirim ke WAHA"
                        else -> responseBody.ifBlank { "Bot WAHA merespons HTTP $code" }
                    }
                    lastError = Exception(userMsg)
                    break
                }
            } catch (e: Exception) {
                Log.d(TAG, "Koneksi ke $baseUrl gagal: ${e.message}")
                lastError = e
            }
        }

        Result.failure(lastError ?: Exception("Tidak dapat terhubung ke bot WhatsApp WAHA"))
    }

    suspend fun sendReceipt(
        order: PlacedOrder,
        targetPhone: String? = null,
        targetName: String? = null,
    ): Result<String> {
        val user = supabase.auth.currentUserOrNull()
        val metadata = user?.userMetadata

        val name = targetName?.takeIf { it.isNotBlank() }
            ?: metadata?.get("full_name")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: user?.email.orEmpty().substringBefore("@").ifBlank { "Pelanggan" }

        val rawPhone = targetPhone?.takeIf { it.isNotBlank() }
            ?: metadata?.get("phone")?.jsonPrimitive?.content.orEmpty()

        if (formatChatId(rawPhone) == null) {
            return Result.failure(IllegalStateException("Nomor WhatsApp belum terdaftar di profil akun"))
        }

        val message = buildReceiptText(order, name)
        return sendTextMessage(rawPhone, message, order.number)
    }
}
