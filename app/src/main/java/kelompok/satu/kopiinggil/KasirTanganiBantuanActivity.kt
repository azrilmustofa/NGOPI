package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.OffsetDateTime

class KasirTanganiBantuanActivity : AppCompatActivity() {
    private lateinit var request: SupportRequest
    private lateinit var primaryButton: AppCompatButton
    private lateinit var secondaryButton: AppCompatButton
    private val requestId get() = intent.getStringExtra(EXTRA_REQUEST_ID).orEmpty()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kasir_tangani_bantuan)
        applyEdgeToEdge(R.id.main)

        primaryButton = findViewById(R.id.btnHelpActionPrimary)
        secondaryButton = findViewById(R.id.btnHelpActionSecondary)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        primaryButton.setOnClickListener { advanceStatus() }
        secondaryButton.setOnClickListener { openOrderOrClose() }
        loadRequest()
    }

    override fun onResume() {
        super.onResume()
        if (requestId.isNotBlank() && ::primaryButton.isInitialized) loadRequest()
    }

    private fun loadRequest() {
        if (requestId.isBlank()) {
            finish()
            return
        }
        primaryButton.isEnabled = false
        lifecycleScope.launch {
            try {
                request = loadSupportRequest(requestId)
                render()
            } catch (e: Exception) {
                Toast.makeText(this@KasirTanganiBantuanActivity, "Permintaan bantuan tidak dapat dimuat: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                finish()
            }
        }
    }

    private fun render() {
        findViewById<TextView>(R.id.tvHelpEyebrow).text = "PERMINTAAN #${request.id.take(8).uppercase()}"
        findViewById<TextView>(R.id.tvHelpTitle).text = "${request.tableLabel} minta Kasir"
        val order = request.publicId?.let { "Order #${it.trimStart('#')} · " }.orEmpty()
        findViewById<TextView>(R.id.tvHelpSubtitle).text = "$order Status ${request.status}"
        findViewById<TextView>(R.id.tvHelpTopic).text = request.topicTitle()
        findViewById<TextView>(R.id.tvHelpDesc).text = when (request.topic) {
            "PAYMENT" -> "Pelanggan ingin memastikan status pembayaran. Datangi meja dan periksa order bersama pelanggan."
            "ORDER" -> "Pelanggan membutuhkan bantuan terkait pesanan atau informasi alergi."
            "RECEIPT" -> "Pelanggan membutuhkan bantuan nomor WhatsApp atau struk transaksi."
            else -> "Pelanggan meminta Kasir datang ke meja."
        }
        findViewById<TextView>(R.id.tvHelpTable).text = request.tableLabel
        findViewById<TextView>(R.id.tvHelpAge).text = requestAge(request.lastRequestedAt)
        findViewById<TextView>(R.id.tvHelpPaymentBadge).text = request.paymentStatus ?: "BELUM ADA ORDER"
        primaryButton.isEnabled = request.status != "RESOLVED"
        primaryButton.text = when (request.status) {
            "OPEN" -> "Terima permintaan"
            "ACKNOWLEDGED" -> "Tandai bantuan selesai"
            else -> "Bantuan selesai"
        }
        secondaryButton.text = if (request.orderId == null) "Kembali ke daftar bantuan" else "Lihat order"
    }

    private fun advanceStatus() {
        val next = when (request.status) {
            "OPEN" -> "ACKNOWLEDGED"
            "ACKNOWLEDGED" -> "RESOLVED"
            else -> return
        }
        primaryButton.isEnabled = false
        lifecycleScope.launch {
            try {
                request = updateSupportRequest(request.id, request.status, next)
                render()
                if (next == "RESOLVED") setResult(RESULT_OK)
                Toast.makeText(
                    this@KasirTanganiBantuanActivity,
                    if (next == "RESOLVED") "Bantuan ditandai selesai" else "Permintaan diterima",
                    Toast.LENGTH_SHORT,
                ).show()
            } catch (e: Exception) {
                primaryButton.isEnabled = true
                Toast.makeText(this@KasirTanganiBantuanActivity, "Status gagal diperbarui: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                loadRequest()
            }
        }
    }

    private fun openOrderOrClose() {
        val orderId = request.orderId ?: run {
            finish()
            return
        }
        startActivity(Intent(this, KasirTransaksiVerifikasiActivity::class.java).apply {
            putExtra(KasirTransaksiVerifikasiActivity.EXTRA_ORDER_ID, orderId)
            putExtra(KasirTransaksiVerifikasiActivity.EXTRA_PUBLIC_ID, request.publicId)
            putExtra(KasirTransaksiVerifikasiActivity.EXTRA_TABLE_LABEL, request.tableLabel)
            putExtra(KasirTransaksiVerifikasiActivity.EXTRA_STATUS, request.paymentStatus)
            putExtra(KasirTransaksiVerifikasiActivity.EXTRA_TOTAL_RP, request.totalRp ?: 0)
            putExtra(KasirTransaksiVerifikasiActivity.EXTRA_CREATED_AT, request.orderCreatedAt)
        })
    }

    private fun requestAge(iso: String): String = runCatching {
        val minutes = Duration.between(OffsetDateTime.parse(iso), OffsetDateTime.now()).toMinutes().coerceAtLeast(0)
        when {
            minutes < 1 -> "Baru saja"
            minutes < 60 -> "$minutes menit"
            else -> "${minutes / 60} jam"
        }
    }.getOrDefault("--")

    companion object {
        const val EXTRA_REQUEST_ID = "extra_support_request_id"
    }
}
