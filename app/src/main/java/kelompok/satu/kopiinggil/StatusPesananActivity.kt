package kelompok.satu.kopiinggil

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable

@Serializable
private data class OrderStatusPollingDto(
    val status: String = "",
)

// Pesanan diproses: tampil setelah pembayaran.
// Mengirim nota ke WhatsApp pelanggan dan menampilkan status pengiriman.
class StatusPesananActivity : AppCompatActivity() {

    private lateinit var tvReceiptNote: TextView
    private lateinit var ivReceiptIcon: ImageView
    private lateinit var cardReceipt: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status_pesanan)
        applyEdgeToEdge(R.id.main)

        val order = OrderManager.selectedOrder ?: OrderManager.lastOrder
        if (order == null) {
            finish()
            return
        }

        findViewById<TextView>(R.id.tvOrderNumber).text = "ORDER ${order.number}"
        findViewById<TextView>(R.id.tvStatusTitle).text = when (order.status) {
            "PREPARING", "BREWING", "DIBUAT" -> "Sedang diracik"
            "READY", "SIAP" -> "Siap diantar"
            "COMPLETED" -> "Pesanan selesai"
            "CANCELLED" -> "Pesanan dibatalkan"
            else -> "Pesanan diproses"
        }
        findViewById<TextView>(R.id.tvStatusSubtitle).text =
            if (order.table > 0) "Meja %02d · Dibayar via %s".format(order.table, order.method.label)
            else "Dibayar via %s".format(order.method.label)
        findViewById<TextView>(R.id.tvPaidTotal).text = order.total.toRupiah()
        findViewById<TextView>(R.id.tvOrderSummaryTotal).text = order.total.toRupiah()
        findViewById<TextView>(R.id.tvStepPaymentTime).text = "${order.paidAt} · Tervalidasi"
        findViewById<TextView>(R.id.tvStepOrderTime).text = "${order.paidAt} · Barista mulai bekerja"
        findViewById<TextView>(R.id.tvStepBrewTime).text = "Diperbarui ${order.paidAt}"
        findViewById<ViewGroup>(R.id.llOrderItems).bindOrderLines(order.items)

        tvReceiptNote = findViewById(R.id.tvReceiptNote)
        ivReceiptIcon = findViewById(R.id.ivReceiptIcon)
        cardReceipt = findViewById(R.id.cardReceipt)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnOrderAgain).setOnClickListener { finish() }
        findViewById<View>(R.id.btnCallCashier).setOnClickListener {
            Toast.makeText(this, R.string.invalid_call_cashier_todo, Toast.LENGTH_SHORT).show()
        }

        val extraPhone = intent.getStringExtra("EXTRA_CUSTOMER_PHONE")
        val extraName = intent.getStringExtra("EXTRA_CUSTOMER_NAME")

        cardReceipt.setOnClickListener {
            sendWhatsAppReceipt(order, userInitiated = true, targetPhone = extraPhone, targetName = extraName)
        }

        // Kirim otomatis jika belum pernah terkirim untuk pesanan ini
        if (WhatsAppBotService.lastSentOrderNumber != order.number) {
            sendWhatsAppReceipt(order, userInitiated = false, targetPhone = extraPhone, targetName = extraName)
        } else {
            showReceiptSuccess()
        }

        startStatusPolling(order.number)
    }

    private fun startStatusPolling(orderNumber: String) {
        val cleanNumber = orderNumber.removePrefix("#").trim()
        lifecycleScope.launch {
            while (isActive) {
                delay(4000)
                try {
                    val orderList = supabase.postgrest["customer_order"].select {
                        filter { eq("public_id", cleanNumber) }
                    }.decodeList<OrderStatusPollingDto>()

                    val current = orderList.firstOrNull()
                    if (current != null && current.status.isNotBlank()) {
                        val titleTv = findViewById<TextView>(R.id.tvStatusTitle)
                        titleTv?.text = when (current.status.uppercase()) {
                            "PREPARING", "BREWING", "DIBUAT" -> "Sedang diracik"
                            "READY", "SIAP" -> "Siap diantar"
                            "COMPLETED" -> "Pesanan selesai"
                            "CANCELLED" -> "Pesanan dibatalkan"
                            else -> "Pesanan diproses"
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }

    private fun sendWhatsAppReceipt(
        order: PlacedOrder,
        userInitiated: Boolean,
        targetPhone: String? = null,
        targetName: String? = null,
    ) {
        tvReceiptNote.text = "Mengirim nota ke nomor WhatsApp akun Anda..."
        ivReceiptIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ngopi_muted))

        lifecycleScope.launch {
            val result = WhatsAppBotService.sendReceipt(order, targetPhone = targetPhone, targetName = targetName)
            if (result.isSuccess) {
                showReceiptSuccess()
                if (userInitiated) {
                    Toast.makeText(this@StatusPesananActivity, "Nota berhasil dikirim ke WhatsApp!", Toast.LENGTH_SHORT).show()
                }
            } else {
                val err = result.exceptionOrNull()?.message.orEmpty()
                ivReceiptIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this@StatusPesananActivity, R.color.ngopi_error))
                tvReceiptNote.text = "Nota WhatsApp belum terkirim. Ketuk untuk coba kirim lagi."
                if (userInitiated) {
                    Toast.makeText(this@StatusPesananActivity, err.ifBlank { "Gagal mengirim nota" }, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showReceiptSuccess() {
        ivReceiptIcon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ngopi_primary))
        tvReceiptNote.text = "Nota transaksi telah dikirim ke nomor WhatsApp di akun Anda."
    }

    override fun onDestroy() {
        super.onDestroy()
        OrderManager.selectedOrder = null
    }
}
