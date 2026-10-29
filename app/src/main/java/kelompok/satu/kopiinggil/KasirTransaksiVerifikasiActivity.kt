package kelompok.satu.kopiinggil

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
private data class TxDetailOrderDto(
    val id: String = "",
    @SerialName("public_id") val publicId: String = "",
    @SerialName("table_id") val tableId: String = "",
    val status: String = "",
    @SerialName("total_rp") val totalRp: Int = 0,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
private data class TxPaymentAttemptDto(
    @SerialName("order_id") val orderId: String = "",
    val status: String = "",
    @SerialName("payment_method") val paymentMethod: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class TxStatusHistoryDto(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("to_status") val toStatus: String = "",
    @SerialName("actor_type") val actorType: String = "",
    val note: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

class KasirTransaksiVerifikasiActivity : AppCompatActivity() {

    private var orderId: String = ""
    private var publicId: String = ""
    private var tableLabel: String = ""
    private var currentStatus: String = "AWAITING_PAYMENT"
    private var totalRp: Int = 0
    private var createdAtIso: String? = null

    private lateinit var tvEyebrow: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var tvNominal: TextView
    private lateinit var tvMetode: TextView
    private lateinit var tvProviderId: TextView
    private lateinit var tvStatusBadge: TextView
    private lateinit var llTimelineContainer: LinearLayout
    private lateinit var btnCheckMidtrans: AppCompatButton
    private lateinit var btnConfirmCash: AppCompatButton
    private lateinit var btnCancelOrder: AppCompatButton

    private val cancelLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            updateStatusUi("CANCELLED")
            loadOrderDetails()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kasir_transaksi_verifikasi)
        applyEdgeToEdge(R.id.main)

        orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        publicId = intent.getStringExtra(EXTRA_PUBLIC_ID).orEmpty()
        tableLabel = intent.getStringExtra(EXTRA_TABLE_LABEL).orEmpty().ifBlank { "Meja --" }
        currentStatus = intent.getStringExtra(EXTRA_STATUS).orEmpty().ifBlank { "AWAITING_PAYMENT" }
        totalRp = intent.getIntExtra(EXTRA_TOTAL_RP, 0)
        createdAtIso = intent.getStringExtra(EXTRA_CREATED_AT)

        tvEyebrow = findViewById(R.id.tvTxEyebrow)
        tvTitle = findViewById(R.id.tvTxTitle)
        tvSubtitle = findViewById(R.id.tvTxSubtitle)
        tvNominal = findViewById(R.id.tvTxNominal)
        tvMetode = findViewById(R.id.tvTxMetode)
        tvProviderId = findViewById(R.id.tvTxProviderId)
        tvStatusBadge = findViewById(R.id.tvTxStatusBadge)
        llTimelineContainer = findViewById(R.id.llTxTimelineContainer)
        btnCheckMidtrans = findViewById(R.id.btnCheckMidtrans)
        btnConfirmCash = findViewById(R.id.btnConfirmCash)
        btnCancelOrder = findViewById(R.id.btnCancelOrder)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnBackToTxList).setOnClickListener { finish() }

        btnCheckMidtrans.setOnClickListener { checkOnlineStatus() }
        btnConfirmCash.setOnClickListener { showCashConfirmationDialog() }
        btnCancelOrder.setOnClickListener { openCancellationScreen() }

        renderStaticInfo()
        loadOrderDetails()
    }

    private fun openCancellationScreen() {
        val intent = Intent(this, KasirPembatalanRefundActivity::class.java).apply {
            putExtra(KasirPembatalanRefundActivity.EXTRA_ORDER_ID, orderId)
            putExtra(KasirPembatalanRefundActivity.EXTRA_PUBLIC_ID, publicId)
            putExtra(KasirPembatalanRefundActivity.EXTRA_TABLE_LABEL, tableLabel)
            putExtra(KasirPembatalanRefundActivity.EXTRA_STATUS, currentStatus)
            putExtra(KasirPembatalanRefundActivity.EXTRA_TOTAL_RP, totalRp)
        }
        cancelLauncher.launch(intent)
    }

    private fun renderStaticInfo() {
        val dispPublicId = publicId.ifBlank { orderId.take(8) }
        tvEyebrow.text = "TRANSAKSI #$dispPublicId"
        tvSubtitle.text = "$tableLabel · Dibuat ${formatTime(createdAtIso)}"
        tvNominal.text = totalRp.toRupiah()
        tvProviderId.text = "MID-$dispPublicId"

        updateStatusUi(currentStatus)
    }

    private fun updateStatusUi(status: String) {
        currentStatus = status
        when (status.uppercase()) {
            "PAID", "ACCEPTED", "PREPARING", "READY" -> {
                tvTitle.text = "Pembayaran Terverifikasi"
                tvStatusBadge.text = "LUNAS"
                tvStatusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ngopi_primary))
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.white))
                btnConfirmCash.visibility = View.GONE
                btnCancelOrder.visibility = View.VISIBLE
                btnCheckMidtrans.text = "Perbarui Status Terbaru"
            }
            "COMPLETED" -> {
                tvTitle.text = "Pesanan Selesai"
                tvStatusBadge.text = "SELESAI"
                tvStatusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ngopi_primary))
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.white))
                btnConfirmCash.visibility = View.GONE
                btnCancelOrder.visibility = View.GONE
                btnCheckMidtrans.text = "Perbarui Status Terbaru"
            }
            "CANCELLED" -> {
                tvTitle.text = "Pesanan Dibatalkan"
                tvStatusBadge.text = "BATAL"
                tvStatusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ngopi_error))
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.white))
                btnConfirmCash.visibility = View.GONE
                btnCancelOrder.visibility = View.GONE
                btnCheckMidtrans.visibility = View.GONE
            }
            else -> {
                tvTitle.text = "Pembayaran Belum Pasti"
                tvStatusBadge.text = "TERTUNDA"
                tvStatusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ngopi_surface))
                tvStatusBadge.setTextColor(ContextCompat.getColor(this, R.color.ngopi_primary))
                btnConfirmCash.visibility = View.VISIBLE
                btnCancelOrder.visibility = View.VISIBLE
                btnCheckMidtrans.text = "Periksa ulang ke Midtrans"
            }
        }
    }

    private fun loadOrderDetails() {
        lifecycleScope.launch {
            try {
                // 1. Ambil order terbaru dari Supabase
                val order = supabase.postgrest["customer_order"].select {
                    filter { eq("id", orderId) }
                }.decodeList<TxDetailOrderDto>().firstOrNull()

                if (order != null) {
                    currentStatus = order.status
                    if (order.totalRp > 0) totalRp = order.totalRp
                    tvNominal.text = totalRp.toRupiah()
                    updateStatusUi(currentStatus)
                }

                // 2. Ambil payment attempt terakhir
                val attempt = try {
                    supabase.postgrest["payment_attempt"].select {
                        filter { eq("order_id", orderId) }
                        order("created_at", Order.DESCENDING)
                        limit(1)
                    }.decodeList<TxPaymentAttemptDto>().firstOrNull()
                } catch (_: Exception) {
                    null
                }

                if (attempt != null) {
                    tvMetode.text = attempt.paymentMethod?.uppercase() ?: "QRIS / ONLINE"
                    if (attempt.status.equals("PAID", ignoreCase = true) && !isPaidStatus(currentStatus)) {
                        updateStatusUi("PAID")
                    }
                } else {
                    tvMetode.text = "QRIS / TUNAI"
                }

                // 3. Ambil riwayat status
                loadTimeline()

            } catch (e: Exception) {
                android.util.Log.e("KasirTxVerifikasi", "Gagal memuat detail transaksi", e)
            }
        }
    }

    private suspend fun loadTimeline() {
        val histories = try {
            supabase.postgrest["order_status_history"].select {
                filter { eq("order_id", orderId) }
                order("created_at", Order.ASCENDING)
            }.decodeList<TxStatusHistoryDto>()
        } catch (_: Exception) {
            emptyList()
        }

        llTimelineContainer.removeAllViews()
        val inflater = LayoutInflater.from(this)

        // Event awal: pesanan dibuat
        addTimelineRow(inflater, formatTime(createdAtIso), "Pesanan dibuat pelanggan di $tableLabel")

        for (item in histories) {
            val actor = if (item.actorType.isNotBlank()) " oleh ${item.actorType}" else ""
            val noteText = if (!item.note.isNullOrBlank()) " (${item.note})" else ""
            val desc = "Status diubah ke ${item.toStatus}$actor$noteText"
            addTimelineRow(inflater, formatTime(item.createdAt), desc)
        }

        if (histories.isEmpty() && isAwaiting(currentStatus)) {
            addTimelineRow(inflater, formatTime(createdAtIso), "Menunggu status pembayaran provider")
        }
    }

    private fun addTimelineRow(inflater: LayoutInflater, timeStr: String, descStr: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 8, 0, 8)
        }

        val tvTime = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(160, LinearLayout.LayoutParams.WRAP_CONTENT)
            text = timeStr
            setTextColor(ContextCompat.getColor(this@KasirTransaksiVerifikasiActivity, R.color.ngopi_muted))
            textSize = 12f
        }

        val tvDesc = TextView(this).apply {
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            text = descStr
            setTextColor(ContextCompat.getColor(this@KasirTransaksiVerifikasiActivity, R.color.ngopi_ink))
            textSize = 12f
        }

        row.addView(tvTime)
        row.addView(tvDesc)
        llTimelineContainer.addView(row)
    }

    private fun checkOnlineStatus() {
        btnCheckMidtrans.isEnabled = false
        Toast.makeText(this, "Memeriksa status ke server...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val order = supabase.postgrest["customer_order"].select {
                    filter { eq("id", orderId) }
                }.decodeList<TxDetailOrderDto>().firstOrNull()

                btnCheckMidtrans.isEnabled = true

                if (order != null && isPaidStatus(order.status)) {
                    updateStatusUi(order.status)
                    loadTimeline()
                    Toast.makeText(this@KasirTransaksiVerifikasiActivity, "Pembayaran berhasil diverifikasi Lunas!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@KasirTransaksiVerifikasiActivity, "Status transaksi saat ini: ${order?.status ?: currentStatus}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                btnCheckMidtrans.isEnabled = true
                Toast.makeText(this@KasirTransaksiVerifikasiActivity, "Gagal memeriksa: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun showCashConfirmationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Pembayaran Tunai")
            .setMessage("Pelanggan di $tableLabel membayar tunai sebesar ${totalRp.toRupiah()} di kasir?\n\nSetelah dikonfirmasi, pesanan akan langsung diteruskan ke barista untuk diracik.")
            .setPositiveButton("Terima & Tandai Lunas") { _, _ ->
                markAsPaidByCash()
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun markAsPaidByCash() {
        btnConfirmCash.isEnabled = false
        lifecycleScope.launch {
            try {
                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                // 1. Update customer_order ke PAID
                supabase.postgrest["customer_order"].update(
                    buildJsonObject {
                        put("status", "PAID")
                        put("paid_at", nowIso)
                        put("updated_at", nowIso)
                    }
                ) {
                    filter { eq("id", orderId) }
                }

                // 2. Insert ke order_status_history
                try {
                    val currentUser = supabase.auth.currentUserOrNull()
                    supabase.postgrest["order_status_history"].insert(
                        buildJsonObject {
                            put("order_id", orderId)
                            if (currentUser != null) put("actor_uid", currentUser.id)
                            put("actor_type", "KASIR")
                            put("from_status", currentStatus)
                            put("to_status", "PAID")
                            put("note", "Pembayaran tunai diverifikasi langsung di kasir")
                        }
                    )
                } catch (_: Exception) {}

                updateStatusUi("PAID")
                loadTimeline()
                Toast.makeText(this@KasirTransaksiVerifikasiActivity, "Pembayaran tunai diterima! Pesanan masuk antrean barista.", Toast.LENGTH_LONG).show()

            } catch (e: Exception) {
                btnConfirmCash.isEnabled = true
                Toast.makeText(this@KasirTransaksiVerifikasiActivity, "Gagal konfirmasi: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun isPaidStatus(status: String): Boolean {
        return status.uppercase() in listOf("PAID", "ACCEPTED", "PREPARING", "READY", "COMPLETED")
    }

    private fun isAwaiting(status: String): Boolean {
        return status.uppercase() in listOf("AWAITING_PAYMENT", "PENDING", "")
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

    companion object {
        const val EXTRA_ORDER_ID = "extra_tx_order_id"
        const val EXTRA_PUBLIC_ID = "extra_tx_public_id"
        const val EXTRA_TABLE_LABEL = "extra_tx_table_label"
        const val EXTRA_STATUS = "extra_tx_status"
        const val EXTRA_TOTAL_RP = "extra_tx_total_rp"
        const val EXTRA_CREATED_AT = "extra_tx_created_at"
    }
}
