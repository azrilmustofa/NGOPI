package kelompok.satu.kopiinggil

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
private data class StaffOrderDetailDto(
    val id: String = "",
    @SerialName("public_id") val publicId: String = "",
    @SerialName("table_id") val tableId: String = "",
    val status: String = "",
    @SerialName("total_rp") val totalRp: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
private data class StaffOrderItemDetailDto(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("name_snapshot") val nameSnapshot: String = "",
    val quantity: Int = 1,
    val note: String? = null,
)

@Serializable
private data class StaffOrderItemOptionDto(
    val id: String = "",
    @SerialName("order_item_id") val orderItemId: String = "",
    @SerialName("option_name_snapshot") val optionNameSnapshot: String = "",
)

@Serializable
private data class OrderStatusUpdateDto(
    val status: String,
    @SerialName("updated_at") val updatedAt: String,
)

@Serializable
private data class OrderStatusHistoryDto(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("to_status") val toStatus: String = "",
    @SerialName("actor_type") val actorType: String = "",
    @SerialName("created_at") val createdAt: String? = null,
)

class StaffDetailProduksiActivity : AppCompatActivity() {

    private var orderId: String = ""
    private var publicId: String = ""
    private var tableLabel: String = ""
    private var currentStatus: String = "PAID"
    private var createdAtIso: String? = null
    private var paidAtIso: String? = null
    private var updatedAtIso: String? = null

    private var items = listOf<StaffOrderItemDetailDto>()
    private var optionsMap = mapOf<String, List<StaffOrderItemOptionDto>>()
    private var statusHistories = listOf<OrderStatusHistoryDto>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        publicId = intent.getStringExtra(EXTRA_PUBLIC_ID).orEmpty()
        tableLabel = intent.getStringExtra(EXTRA_TABLE_LABEL).orEmpty().ifBlank { "Meja --" }
        currentStatus = intent.getStringExtra(EXTRA_STATUS).orEmpty().ifBlank { "PAID" }
        createdAtIso = intent.getStringExtra(EXTRA_CREATED_AT)

        if (orderId.isBlank()) {
            Toast.makeText(this, "Data pesanan tidak valid", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        render()
        loadDataFromSupabase()
    }

    private fun loadDataFromSupabase() {
        lifecycleScope.launch {
            try {
                // Ambil data order terbaru
                val order = try {
                    supabase.postgrest["customer_order"].select {
                        filter { eq("id", orderId) }
                    }.decodeList<StaffOrderDetailDto>().firstOrNull()
                } catch (_: Exception) {
                    null
                }

                if (order != null) {
                    if (order.publicId.isNotBlank()) publicId = order.publicId
                    currentStatus = order.status
                    createdAtIso = order.createdAt
                    paidAtIso = order.paidAt
                    updatedAtIso = order.updatedAt
                }

                // Ambil order item
                items = try {
                    supabase.postgrest["order_item"].select {
                        filter { eq("order_id", orderId) }
                    }.decodeList()
                } catch (_: Exception) {
                    emptyList()
                }

                // Ambil opsi kustomisasi tiap item
                val itemIds = items.map { it.id }
                optionsMap = if (itemIds.isNotEmpty()) {
                    try {
                        supabase.postgrest["order_item_option"].select {
                            filter { isIn("order_item_id", itemIds) }
                        }.decodeList<StaffOrderItemOptionDto>().groupBy { it.orderItemId }
                    } catch (_: Exception) {
                        emptyMap()
                    }
                } else {
                    emptyMap()
                }

                // Ambil riwayat status
                statusHistories = try {
                    supabase.postgrest["order_status_history"].select {
                        filter { eq("order_id", orderId) }
                        order("created_at", Order.ASCENDING)
                    }.decodeList()
                } catch (_: Exception) {
                    emptyList()
                }

                render()
            } catch (e: Exception) {
                android.util.Log.e("StaffDetail", "Error loading order details", e)
            }
        }
    }

    private fun render() {
        when (currentStatus.uppercase()) {
            "PREPARING", "BREWING", "DIBUAT" -> renderPreparingState()
            "READY", "SIAP" -> renderReadyState()
            "COMPLETED" -> renderCompletedState()
            else -> renderPaidState()
        }
    }

    // TAHAP 1: Pesanan Baru Lunas (PAID / BARU / ACCEPTED)
    private fun renderPaidState() {
        setContentView(R.layout.activity_staff_detail_produksi)
        applyEdgeToEdge(R.id.main)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        val elapsed = calculateElapsedMinutes(createdAtIso)
        findViewById<TextView>(R.id.tvStaffDetailEyebrow).text = "$tableLabel · $elapsed MENIT"
        findViewById<TextView>(R.id.tvStaffDetailTitle).text = "#${publicId.ifBlank { orderId.take(8) }}"
        findViewById<TextView>(R.id.tvStaffDetailSubtitle).text =
            "Dibayar ${formatTime(paidAtIso ?: createdAtIso)} · Masuk antrean setelah pembayaran terverifikasi"

        findViewById<TextView>(R.id.tvStaffCurrentStatusBadge).apply {
            text = currentStatus.uppercase()
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this@StaffDetailProduksiActivity, R.color.ngopi_primary))
            setTextColor(ContextCompat.getColor(this@StaffDetailProduksiActivity, R.color.white))
        }

        findViewById<TextView>(R.id.tvStaffStatusInstruction).text =
            "Terima pesanan untuk mulai diproses. Semua perubahan dicatat atas nama staf."

        val container = findViewById<LinearLayout>(R.id.llStaffDetailItems)
        populateItems(container)

        val btnPrimary = findViewById<AppCompatButton>(R.id.btnStaffActionPrimary)
        val btnSecondary = findViewById<AppCompatButton>(R.id.btnStaffActionSecondary)

        btnPrimary.text = "Mulai racik (PREPARING)"
        btnPrimary.setOnClickListener {
            btnPrimary.isEnabled = false
            updateStatusTo("PREPARING") { success ->
                btnPrimary.isEnabled = true
                if (success) {
                    Toast.makeText(this@StaffDetailProduksiActivity, "Pesanan #$publicId mulai diracik", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnSecondary.setOnClickListener { finish() }
    }

    // TAHAP 2: Pesanan Sedang Diracik (PREPARING / BREWING / DIBUAT)
    private fun renderPreparingState() {
        setContentView(R.layout.activity_staff_lanjutkan_produksi)
        applyEdgeToEdge(R.id.main)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.tvStaffLiveEyebrow).text = "ORDER #${publicId.ifBlank { orderId.take(8) }}"
        findViewById<TextView>(R.id.tvStaffLiveSubtitle).text =
            "$tableLabel · Diterima oleh staf pukul ${formatTime(updatedAtIso ?: paidAtIso)}"

        val totalQty = items.sumOf { it.quantity }
        findViewById<TextView>(R.id.tvStaffLiveItemsCount).text =
            if (totalQty > 0) "$totalQty minuman sedang diracik" else "Minuman sedang diracik"

        val timerText = "Mulai diracik pukul ${formatTime(updatedAtIso ?: paidAtIso)}"
        findViewById<TextView>(R.id.tvStaffLiveTimer).text = timerText

        val container = findViewById<LinearLayout>(R.id.llStaffLiveItems)
        populateItems(container)

        // Render riwayat status jika ada
        val historyContainer = findViewById<LinearLayout>(R.id.llStaffStatusHistory)
        populateStatusHistory(historyContainer)

        val btnMarkReady = findViewById<AppCompatButton>(R.id.btnMarkReady)
        val btnBackToQueue = findViewById<AppCompatButton>(R.id.btnBackToQueue)

        btnMarkReady.text = "Tandai siap diantar (READY)"
        btnMarkReady.setOnClickListener {
            btnMarkReady.isEnabled = false
            updateStatusTo("READY") { success ->
                btnMarkReady.isEnabled = true
                if (success) {
                    Toast.makeText(this@StaffDetailProduksiActivity, "Pesanan #$publicId siap diantar!", Toast.LENGTH_SHORT).show()
                }
            }
        }

        btnBackToQueue.setOnClickListener { finish() }
    }

    // TAHAP 3: Pesanan Siap Diantar (READY / SIAP)
    private fun renderReadyState() {
        setContentView(R.layout.activity_staff_siap_selesai)
        applyEdgeToEdge(R.id.main)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.tvStaffHandoffEyebrow).text = "$tableLabel · #${publicId.ifBlank { orderId.take(8) }}"
        findViewById<TextView>(R.id.tvStaffHandoffSubtitle).text = "Status READY · Siap diserahkan ke pelanggan"

        val tvTable = findViewById<TextView>(R.id.tvStaffHandoffTable)
        tvTable?.text = tableLabel

        val container = findViewById<LinearLayout>(R.id.llStaffHandoffItems)
        populateItems(container)

        val btnMarkCompleted = findViewById<AppCompatButton>(R.id.btnMarkCompleted)
        val btnBackToQueue = findViewById<AppCompatButton>(R.id.btnBackToQueueHandoff)

        btnMarkCompleted.text = "Tandai selesai (COMPLETED)"
        btnMarkCompleted.isEnabled = true
        btnMarkCompleted.alpha = 1f
        btnMarkCompleted.setOnClickListener {
            btnMarkCompleted.isEnabled = false
            updateStatusTo("COMPLETED") { success ->
                btnMarkCompleted.isEnabled = true
                if (success) {
                    Toast.makeText(this@StaffDetailProduksiActivity, "Pesanan #$publicId selesai!", Toast.LENGTH_LONG).show()
                    finish()
                }
            }
        }

        btnBackToQueue.setOnClickListener { finish() }
    }

    // TAHAP 4: Pesanan Sudah Selesai (COMPLETED)
    private fun renderCompletedState() {
        setContentView(R.layout.activity_staff_siap_selesai)
        applyEdgeToEdge(R.id.main)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<TextView>(R.id.tvStaffHandoffEyebrow).text = "$tableLabel · #${publicId.ifBlank { orderId.take(8) }}"
        findViewById<TextView>(R.id.tvStaffHandoffSubtitle).text = "Status COMPLETED · Pesanan telah selesai"

        val tvTable = findViewById<TextView>(R.id.tvStaffHandoffTable)
        tvTable?.text = tableLabel

        val container = findViewById<LinearLayout>(R.id.llStaffHandoffItems)
        populateItems(container)

        val btnMarkCompleted = findViewById<AppCompatButton>(R.id.btnMarkCompleted)
        val btnBackToQueue = findViewById<AppCompatButton>(R.id.btnBackToQueueHandoff)

        btnMarkCompleted.text = "Pesanan Sudah Selesai"
        btnMarkCompleted.isEnabled = false
        btnMarkCompleted.alpha = 0.5f

        btnBackToQueue.setOnClickListener { finish() }
    }

    private fun populateItems(container: LinearLayout) {
        container.removeAllViews()
        val inflater = LayoutInflater.from(this)

        if (items.isEmpty()) {
            val emptyTv = TextView(this).apply {
                text = "Memuat rincian menu..."
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.ngopi_muted))
                setPadding(0, 24, 0, 24)
            }
            container.addView(emptyTv)
            return
        }

        items.forEachIndexed { index, item ->
            val row = inflater.inflate(R.layout.item_staff_production_line, container, false)
            val tvQty = row.findViewById<TextView>(R.id.tvStaffItemQty)
            val tvName = row.findViewById<TextView>(R.id.tvStaffItemName)
            val tvOptions = row.findViewById<TextView>(R.id.tvStaffItemOptions)
            val tvNote = row.findViewById<TextView>(R.id.tvStaffItemNote)
            val divider = row.findViewById<View>(R.id.vStaffItemDivider)

            tvQty.text = "${item.quantity}×"
            tvName.text = item.nameSnapshot

            val opts = optionsMap[item.id].orEmpty().map { it.optionNameSnapshot }
            if (opts.isNotEmpty()) {
                tvOptions.text = opts.joinToString(" · ")
                tvOptions.visibility = View.VISIBLE
            } else {
                tvOptions.visibility = View.GONE
            }

            if (!item.note.isNullOrBlank()) {
                tvNote.text = "Catatan: ${item.note}"
                tvNote.visibility = View.VISIBLE
            } else {
                tvNote.visibility = View.GONE
            }

            divider.visibility = if (index == items.lastIndex) View.GONE else View.VISIBLE

            container.addView(row)
        }
    }

    private fun populateStatusHistory(container: LinearLayout?) {
        if (container == null) return
        container.removeAllViews()

        if (statusHistories.isNotEmpty()) {
            statusHistories.forEach { hist ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 8)
                }
                val tvTime = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(160, ViewGroup.LayoutParams.WRAP_CONTENT)
                    text = formatTime(hist.createdAt)
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@StaffDetailProduksiActivity, R.color.ngopi_muted))
                }
                val tvDesc = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    text = "${hist.toStatus} · ${hist.actorType.lowercase()}"
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(
                        if (hist.toStatus.equals("PREPARING", true))
                            ContextCompat.getColor(this@StaffDetailProduksiActivity, R.color.ngopi_primary)
                        else
                            ContextCompat.getColor(this@StaffDetailProduksiActivity, R.color.ngopi_ink)
                    )
                }
                row.addView(tvTime)
                row.addView(tvDesc)
                container.addView(row)
            }
        } else {
            // Tampilan default jika belum ada row riwayat
            val defaultTimes = listOf(
                formatTime(paidAtIso ?: createdAtIso) to "PAID · otomatis",
                formatTime(updatedAtIso ?: paidAtIso) to "PREPARING · staf"
            )
            defaultTimes.forEach { (time, desc) ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, 8, 0, 8)
                }
                val tvTime = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(160, ViewGroup.LayoutParams.WRAP_CONTENT)
                    text = time
                    textSize = 12f
                    setTextColor(ContextCompat.getColor(this@StaffDetailProduksiActivity, R.color.ngopi_muted))
                }
                val tvDesc = TextView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    text = desc
                    textSize = 12f
                    setTypeface(typeface, android.graphics.Typeface.BOLD)
                    setTextColor(
                        if (desc.contains("PREPARING"))
                            ContextCompat.getColor(this@StaffDetailProduksiActivity, R.color.ngopi_primary)
                        else
                            ContextCompat.getColor(this@StaffDetailProduksiActivity, R.color.ngopi_ink)
                    )
                }
                row.addView(tvTime)
                row.addView(tvDesc)
                container.addView(row)
            }
        }
    }

    private fun updateStatusTo(newStatus: String, onComplete: (Boolean) -> Unit) {
        lifecycleScope.launch {
            try {
                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                supabase.postgrest["customer_order"].update(
                    OrderStatusUpdateDto(status = newStatus, updatedAt = nowIso)
                ) {
                    filter {
                        eq("id", orderId)
                    }
                }

                // Catat riwayat status secara best-effort
                try {
                    val currentUser = supabase.auth.currentUserOrNull()
                    supabase.postgrest["order_status_history"].insert(
                        buildJsonObject {
                            put("order_id", orderId)
                            if (currentUser != null) put("actor_uid", currentUser.id)
                            put("actor_type", "STAFF")
                            put("from_status", currentStatus)
                            put("to_status", newStatus)
                            put("note", "Diperbarui oleh barista")
                        }
                    )
                } catch (_: Exception) {}

                currentStatus = newStatus
                updatedAtIso = nowIso
                render()
                onComplete(true)
            } catch (e: Exception) {
                android.util.Log.e("StaffDetail", "Gagal memperbarui status order", e)
                Toast.makeText(this@StaffDetailProduksiActivity, "Gagal memperbarui status: ${e.localizedMessage ?: "Kesalahan server"}", Toast.LENGTH_LONG).show()
                onComplete(false)
            }
        }
    }

    private fun calculateElapsedMinutes(isoString: String?): Long {
        if (isoString.isNullOrBlank()) return 0
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val clean = isoString.substringBefore(".").substringBefore("+").substringBefore("Z")
            val date = parser.parse(clean) ?: return 0
            val diffMs = System.currentTimeMillis() - date.time
            (diffMs / 60_000L).coerceAtLeast(0)
        } catch (_: Exception) {
            0
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

    companion object {
        const val EXTRA_ORDER_ID = "extra_order_id"
        const val EXTRA_PUBLIC_ID = "extra_public_id"
        const val EXTRA_TABLE_LABEL = "extra_table_label"
        const val EXTRA_STATUS = "extra_status"
        const val EXTRA_CREATED_AT = "extra_created_at"
    }
}
