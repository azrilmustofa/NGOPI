package kelompok.satu.kopiinggil

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class KasirPembatalanRefundActivity : AppCompatActivity() {

    private var orderId: String = ""
    private var publicId: String = ""
    private var tableLabel: String = ""
    private var currentStatus: String = ""
    private var totalRp: Int = 0

    private lateinit var tvEyebrow: TextView
    private lateinit var tvSubtitle: TextView
    private lateinit var etReason: EditText
    private lateinit var tvRefundStatus: TextView
    private lateinit var tvActor: TextView
    private lateinit var btnConfirmCancel: AppCompatButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kasir_pembatalan_refund)
        applyEdgeToEdge(R.id.main)

        orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        publicId = intent.getStringExtra(EXTRA_PUBLIC_ID).orEmpty()
        tableLabel = intent.getStringExtra(EXTRA_TABLE_LABEL).orEmpty().ifBlank { "Meja --" }
        currentStatus = intent.getStringExtra(EXTRA_STATUS).orEmpty().ifBlank { "PAID" }
        totalRp = intent.getIntExtra(EXTRA_TOTAL_RP, 0)

        tvEyebrow = findViewById(R.id.tvCancelEyebrow)
        tvSubtitle = findViewById(R.id.tvCancelSubtitle)
        etReason = findViewById(R.id.etCancelReason)
        tvRefundStatus = findViewById(R.id.tvCancelRefundStatus)
        tvActor = findViewById(R.id.tvCancelActor)
        btnConfirmCancel = findViewById(R.id.btnConfirmCancel)

        val dispId = publicId.ifBlank { orderId.take(8) }
        tvEyebrow.text = "ORDER #$dispId"
        tvSubtitle.text = "Order $currentStatus · $tableLabel · ${totalRp.toRupiah()}"

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnAbortCancel).setOnClickListener { finish() }

        btnConfirmCancel.setOnClickListener {
            val reason = etReason.text.toString().trim()
            if (reason.isBlank()) {
                etReason.error = "Alasan pembatalan wajib diisi"
                etReason.requestFocus()
                Toast.makeText(this, "Silakan isi alasan pembatalan", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            showConfirmationDialog(reason)
        }

        loadActorName()
    }

    private fun loadActorName() {
        val user = supabase.auth.currentUserOrNull()
        if (user == null) {
            tvActor.text = "Kasir yang login"
            return
        }

        lifecycleScope.launch {
            try {
                val staffList = supabase.postgrest["staff_user"].select {
                    filter { eq("uid", user.id) }
                }.decodeList<StaffUser>()

                if (staffList.isNotEmpty()) {
                    val staff = staffList.first()
                    tvActor.text = "${staff.name} (${staff.role})"
                } else {
                    val emailName = user.email?.substringBefore("@") ?: "Kasir"
                    tvActor.text = "$emailName (Kasir)"
                }
            } catch (_: Exception) {
                tvActor.text = "Kasir"
            }
        }
    }

    private fun showConfirmationDialog(reason: String) {
        val dispId = publicId.ifBlank { orderId.take(8) }
        AlertDialog.Builder(this)
            .setTitle("Konfirmasi Pembatalan")
            .setMessage("Apakah Anda yakin ingin membatalkan pesanan #$dispId ($tableLabel) sebesar ${totalRp.toRupiah()}?\n\nAlasan: \"$reason\"\n\nTindakan ini akan menghentikan alur pesanan dan dicatat dalam riwayat audit.")
            .setPositiveButton("Ya, Batalkan Pesanan") { _, _ ->
                processCancellation(reason)
            }
            .setNegativeButton("Kembali", null)
            .show()
    }

    private fun processCancellation(reason: String) {
        btnConfirmCancel.isEnabled = false
        Toast.makeText(this, "Memproses pembatalan pesanan...", Toast.LENGTH_SHORT).show()

        lifecycleScope.launch {
            try {
                val nowIso = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }.format(Date())

                // 1. Update customer_order status ke CANCELLED
                supabase.postgrest["customer_order"].update(
                    buildJsonObject {
                        put("status", "CANCELLED")
                        put("updated_at", nowIso)
                    }
                ) {
                    filter { eq("id", orderId) }
                }

                // 2. Insert riwayat pembatalan ke order_status_history
                try {
                    val currentUser = supabase.auth.currentUserOrNull()
                    supabase.postgrest["order_status_history"].insert(
                        buildJsonObject {
                            put("order_id", orderId)
                            if (currentUser != null) put("actor_uid", currentUser.id)
                            put("actor_type", "KASIR")
                            put("from_status", currentStatus)
                            put("to_status", "CANCELLED")
                            put("note", "Pembatalan oleh kasir: $reason")
                        }
                    )
                } catch (_: Exception) {}

                tvRefundStatus.text = "Dibatalkan oleh Kasir"
                Toast.makeText(this@KasirPembatalanRefundActivity, "Pesanan #${publicId.ifBlank { orderId.take(8) }} berhasil dibatalkan", Toast.LENGTH_LONG).show()

                setResult(RESULT_OK)
                finish()

            } catch (e: Exception) {
                btnConfirmCancel.isEnabled = true
                Toast.makeText(this@KasirPembatalanRefundActivity, "Gagal membatalkan: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    companion object {
        const val EXTRA_ORDER_ID = "extra_cancel_order_id"
        const val EXTRA_PUBLIC_ID = "extra_cancel_public_id"
        const val EXTRA_TABLE_LABEL = "extra_cancel_table_label"
        const val EXTRA_STATUS = "extra_cancel_status"
        const val EXTRA_TOTAL_RP = "extra_cancel_total_rp"
    }
}
