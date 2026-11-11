package kelompok.satu.kopiinggil

import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

class BantuanActivity : AppCompatActivity() {
    private lateinit var cardActive: View
    private lateinit var requestTitle: TextView
    private lateinit var requestBadge: TextView
    private lateinit var requestDetail: TextView
    private lateinit var requestTime: TextView
    private lateinit var callButton: AppCompatButton
    private var sending = false

    private val tableId: String
        get() = intent.getStringExtra(EXTRA_TABLE_ID).orEmpty().ifBlank { CartManager.tableId }
    private val orderPublicId: String?
        get() = intent.getStringExtra(EXTRA_ORDER_PUBLIC_ID)?.takeIf { it.isNotBlank() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_bantuan)
        applyEdgeToEdge(R.id.main)

        cardActive = findViewById(R.id.cardActiveRequest)
        requestTitle = findViewById(R.id.tvHelpRequestTitle)
        requestBadge = findViewById(R.id.tvHelpRequestBadge)
        requestDetail = findViewById(R.id.tvHelpRequestDetail)
        requestTime = findViewById(R.id.tvHelpRequestTime)
        callButton = findViewById(R.id.btnCallCashierDirect)
        cardActive.visibility = View.GONE

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnHelpPayment).setOnClickListener { send("PAYMENT") }
        findViewById<View>(R.id.btnHelpOrder).setOnClickListener { send("ORDER") }
        findViewById<View>(R.id.btnHelpReceipt).setOnClickListener { send("RECEIPT") }
        callButton.setOnClickListener { send("OTHER") }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                pollEvery(POLL_INTERVAL_MS) {
                    refresh()
                    true
                }
            }
        }
    }

    private fun send(topic: String) {
        if (sending) return
        if (tableId.isBlank()) {
            Toast.makeText(this, "Scan QR meja terlebih dahulu agar Kasir mengetahui lokasimu", Toast.LENGTH_LONG).show()
            return
        }
        sending = true
        callButton.isEnabled = false
        lifecycleScope.launch {
            try {
                val request = requestSupport(topic, tableId, orderPublicId)
                render(request)
                val message = if (request.merged) {
                    "Permintaanmu sudah aktif dan tidak dikirim ganda"
                } else {
                    "Kasir berhasil dipanggil ke ${request.tableLabel}"
                }
                Toast.makeText(this@BantuanActivity, message, Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(this@BantuanActivity, e.toUserMessage(this@BantuanActivity, R.string.err_help_failed), Toast.LENGTH_LONG).show()
            } finally {
                sending = false
                callButton.isEnabled = true
            }
        }
    }

    private suspend fun refresh() {
        if (tableId.isBlank() || sending) return
        try {
            val request = loadMySupportRequest(tableId)
            if (request == null) cardActive.visibility = View.GONE else render(request)
        } catch (_: Exception) {
            // Status terakhir tetap ditampilkan saat jaringan putus sebentar.
        }
    }

    private fun render(request: SupportRequest) {
        cardActive.visibility = View.VISIBLE
        requestTitle.text = request.topicTitle()
        requestBadge.text = if (request.status == "ACKNOWLEDGED") "DITERIMA" else "MENUNGGU"
        requestDetail.text = if (request.status == "ACKNOWLEDGED") {
            "Kasir sudah menerima permintaan dan akan menuju ${request.tableLabel}."
        } else {
            "Kasir telah dipanggil ke ${request.tableLabel}. Harap tunggu sebentar."
        }
        val order = request.publicId?.let { " · #${it.trimStart('#')}" }.orEmpty()
        requestTime.text = "Dikirim pukul ${formatTime(request.lastRequestedAt)}$order"
    }

    private fun formatTime(iso: String): String = runCatching {
        OffsetDateTime.parse(iso).atZoneSameInstant(ZoneId.of("Asia/Jakarta"))
            .format(DateTimeFormatter.ofPattern("HH.mm", Locale.forLanguageTag("id-ID")))
    }.getOrDefault("--.--")

    companion object {
        const val EXTRA_TABLE_ID = "extra_help_table_id"
        const val EXTRA_ORDER_PUBLIC_ID = "extra_help_order_public_id"
        private const val POLL_INTERVAL_MS = 5_000L
    }
}
