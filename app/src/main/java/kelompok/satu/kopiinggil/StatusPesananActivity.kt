package kelompok.satu.kopiinggil

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Status pesanan pelanggan: "06 · Pesanan diproses" (timeline 5 tahap dari status asli), lalu otomatis berganti ke
// "10 · Pesanan selesai" begitu barista menandai COMPLETED. Status diambil ulang dari server tiap beberapa detik
// selama layar terlihat; kalau gagal, penanda offline muncul dan status terakhir tetap tampil.
// Juga mengirim nota ke WhatsApp pelanggan dan menampilkan status pengirimannya.
class StatusPesananActivity : AppCompatActivity() {

    private lateinit var order: PlacedOrder
    private var progress = OrderProgress("PAID", emptyMap())
    private var offline = false
    private var lastUpdated = now()

    @LayoutRes
    private var shownLayout = 0

    private var extraPhone: String? = null
    private var extraName: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        order = OrderManager.selectedOrder ?: OrderManager.lastOrder ?: run {
            finish()
            return
        }
        progress = OrderProgress(order.status, mapOf("PAID" to order.paidAt))
        extraPhone = intent.getStringExtra("EXTRA_CUSTOMER_PHONE")
        extraName = intent.getStringExtra("EXTRA_CUSTOMER_NAME")

        render()

        // Kirim otomatis jika belum pernah terkirim untuk pesanan ini
        if (progress.status != "COMPLETED") {
            if (WhatsAppBotService.lastSentOrderNumber != order.number) {
                sendWhatsAppReceipt(order, userInitiated = false, targetPhone = extraPhone, targetName = extraName)
            } else {
                showReceiptSuccess()
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                // Tiap 5 detik, dan langsung begitu koneksi tersambung lagi; berhenti kalau selesai/batal.
                pollEvery(POLL_INTERVAL_MS) {
                    refresh()
                    progress.status !in FINAL_STATUSES
                }
            }
        }
    }

    private suspend fun refresh() {
        try {
            progress = OrderManager.fetchProgress(order)
            order = order.copy(status = progress.status)
            lastUpdated = now()
            offline = false
        } catch (e: Exception) {
            android.util.Log.w("StatusPesanan", "Gagal memperbarui status", e)
            offline = true
        }
        render()
    }

    private fun render() {
        if (progress.status == "COMPLETED") renderDone() else renderProgress()
    }

    // Ganti layout hanya kalau berbeda; true = baru diganti (bagian statis perlu diisi lagi).
    private fun useLayout(@LayoutRes res: Int): Boolean {
        if (shownLayout == res) return false
        setContentView(res)
        applyEdgeToEdge(R.id.main)
        shownLayout = res
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<TextView>(R.id.tvOrderNumber).text = getString(R.string.order_number_label, order.number)
        findViewById<ViewGroup>(R.id.llOrderItems).bindOrderLines(order.items)
        return true
    }

    private fun renderProgress() {
        if (useLayout(R.layout.activity_status_pesanan)) {
            findViewById<TextView>(R.id.tvPaidTotal).text = order.total.toRupiah()
            findViewById<TextView>(R.id.tvOrderSummaryTotal).text = order.total.toRupiah()
            findViewById<View>(R.id.btnOrderAgain).setOnClickListener { backToHome(R.id.nav_menu) }
            findViewById<View>(R.id.btnCallCashier).setOnClickListener {
                Toast.makeText(this, R.string.invalid_call_cashier_todo, Toast.LENGTH_SHORT).show()
            }
            findViewById<View>(R.id.cardReceipt).setOnClickListener {
                sendWhatsAppReceipt(order, userInitiated = true, targetPhone = extraPhone, targetName = extraName)
            }
            if (WhatsAppBotService.lastSentOrderNumber == order.number) showReceiptSuccess()
        }

        findViewById<TextView>(R.id.tvStatusTitle).setText(
            when (progress.status) {
                "ACCEPTED" -> R.string.status_title_accepted
                "PREPARING" -> R.string.status_title_preparing
                "READY" -> R.string.status_title_ready
                "CANCELLED" -> R.string.status_title_cancelled
                else -> R.string.status_title_paid
            }
        )
        findViewById<TextView>(R.id.tvStatusSubtitle).text = listOfNotNull(
            if (order.table > 0) getString(R.string.status_table, order.table) else null,
            getString(R.string.status_paid_via, order.method.label),
        ).joinToString(" · ")

        findViewById<View>(R.id.bannerOffline).visibility = if (offline) View.VISIBLE else View.GONE
        findViewById<TextView>(R.id.tvOfflineNote).text = getString(R.string.status_offline, lastUpdated)

        renderTimeline(findViewById(R.id.llTimeline))
    }

    // Lima tahap; PAID/ACCEPTED berarti menunggu tahap berikutnya, PREPARING/READY berarti tahap itu sedang berjalan.
    private fun renderTimeline(container: LinearLayout) {
        val status = progress.status
        val current = when (status) {
            "PAID" -> 1
            "ACCEPTED", "PREPARING" -> 2
            "READY" -> 3
            "COMPLETED" -> 5
            else -> 1
        }
        val t = progress.stepTimes
        val acceptedAt = t["ACCEPTED"] ?: t["PREPARING"]

        container.removeAllViews()
        STEP_TITLES.forEachIndexed { i, title ->
            val state = when {
                status == "CANCELLED" -> if (i == 0) StepState.DONE else StepState.WAITING
                i < current -> StepState.DONE
                i == current -> StepState.CURRENT
                else -> StepState.WAITING
            }
            val time: String? = if (state == StepState.WAITING) getString(R.string.status_step_waiting) else when (i) {
                0 -> t["PAID"]?.let { getString(R.string.status_step_paid_time, it) }
                1 -> if (state == StepState.CURRENT) getString(R.string.status_step_waiting_barista)
                else acceptedAt?.let { getString(R.string.status_step_accepted_time, it) }
                2 -> when {
                    state == StepState.CURRENT && status == "ACCEPTED" -> getString(R.string.status_step_next)
                    state == StepState.CURRENT -> t["PREPARING"]?.let { getString(R.string.status_step_preparing_time, it) }
                    else -> t["PREPARING"]?.let { getString(R.string.status_step_prepared_time, it) }
                }
                3 -> t["READY"]?.let { getString(R.string.status_step_ready_time, it) }
                else -> t["COMPLETED"]?.let { getString(R.string.status_step_done_time, it) }
            }
            container.addView(stepView(container, title, time, state))
        }
    }

    private fun stepView(parent: ViewGroup, title: Int, time: String?, state: StepState): View {
        val row = LayoutInflater.from(this).inflate(R.layout.item_status_step, parent, false)
        val icon = row.findViewById<ImageView>(R.id.ivStepIcon)
        val tvTitle = row.findViewById<TextView>(R.id.tvStepTitle)
        val tvTime = row.findViewById<TextView>(R.id.tvStepTime)

        icon.setImageResource(
            when (state) {
                StepState.DONE -> R.drawable.ic_check_circle
                StepState.CURRENT -> R.drawable.ic_clock
                StepState.WAITING -> R.drawable.ic_radio_off
            }
        )
        icon.imageTintList = ContextCompat.getColorStateList(this, if (state == StepState.WAITING) R.color.ngopi_border else R.color.ngopi_primary)
        tvTitle.setText(title)
        tvTitle.setTextColor(
            ContextCompat.getColor(
                this,
                when (state) {
                    StepState.DONE -> R.color.ngopi_ink
                    StepState.CURRENT -> R.color.ngopi_primary
                    StepState.WAITING -> R.color.ngopi_muted
                },
            )
        )
        tvTitle.setTypeface(null, if (state == StepState.WAITING) Typeface.NORMAL else Typeface.BOLD)
        tvTime.text = time
        tvTime.visibility = if (time.isNullOrBlank()) View.GONE else View.VISIBLE
        return row
    }

    private fun renderDone() {
        if (!useLayout(R.layout.activity_pesanan_selesai)) return
        findViewById<TextView>(R.id.tvDoneSubtitle).text = getString(
            R.string.done_subtitle,
            if (order.table > 0) getString(R.string.status_table, order.table) else getString(R.string.status_dine_in),
        )
        findViewById<TextView>(R.id.tvDoneTime).text = getString(R.string.done_time, progress.stepTimes["COMPLETED"] ?: lastUpdated)
        findViewById<TextView>(R.id.tvDoneTax).text = order.tax.toRupiah()
        findViewById<TextView>(R.id.tvDoneTotal).text = order.total.toRupiah()
        findViewById<View>(R.id.btnOrderAgain).setOnClickListener { backToHome(R.id.nav_menu) }
        findViewById<View>(R.id.btnSeeOrders).setOnClickListener { backToHome(R.id.nav_orders) }
    }

    // Kembali ke HomeActivity (di bawah layar ini) dan buka tab tertentu.
    private fun backToHome(navItemId: Int) {
        HomeActivity.pendingTab = navItemId
        finish()
    }

    private fun sendWhatsAppReceipt(
        order: PlacedOrder,
        userInitiated: Boolean,
        targetPhone: String? = null,
        targetName: String? = null,
    ) {
        val tvReceiptNote = findViewById<TextView?>(R.id.tvReceiptNote)
        val ivReceiptIcon = findViewById<ImageView?>(R.id.ivReceiptIcon)
        tvReceiptNote?.text = "Mengirim nota ke nomor WhatsApp akun Anda..."
        ivReceiptIcon?.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ngopi_muted))

        lifecycleScope.launch {
            val result = WhatsAppBotService.sendReceipt(order, targetPhone = targetPhone, targetName = targetName)
            if (result.isSuccess) {
                showReceiptSuccess()
                if (userInitiated) {
                    Toast.makeText(this@StatusPesananActivity, "Nota berhasil dikirim ke WhatsApp!", Toast.LENGTH_SHORT).show()
                }
            } else {
                val err = result.exceptionOrNull()?.message.orEmpty()
                findViewById<ImageView?>(R.id.ivReceiptIcon)?.imageTintList =
                    ColorStateList.valueOf(ContextCompat.getColor(this@StatusPesananActivity, R.color.ngopi_error))
                findViewById<TextView?>(R.id.tvReceiptNote)?.text = "Nota WhatsApp belum terkirim. Ketuk untuk coba kirim lagi."
                if (userInitiated) {
                    Toast.makeText(this@StatusPesananActivity, err.ifBlank { "Gagal mengirim nota" }, Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showReceiptSuccess() {
        findViewById<ImageView?>(R.id.ivReceiptIcon)?.imageTintList =
            ColorStateList.valueOf(ContextCompat.getColor(this, R.color.ngopi_primary))
        findViewById<TextView?>(R.id.tvReceiptNote)?.text = "Nota transaksi telah dikirim ke nomor WhatsApp di akun Anda."
    }

    override fun onDestroy() {
        super.onDestroy()
        OrderManager.selectedOrder = null
    }

    private enum class StepState { DONE, CURRENT, WAITING }

    private companion object {
        const val POLL_INTERVAL_MS = 5_000L
        val FINAL_STATUSES = setOf("COMPLETED", "CANCELLED")
        val STEP_TITLES = listOf(
            R.string.status_step_payment,
            R.string.status_step_order,
            R.string.status_step_brewing,
            R.string.status_step_ready,
            R.string.status_step_done,
        )

        fun now(): String = SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(Date())
    }
}
