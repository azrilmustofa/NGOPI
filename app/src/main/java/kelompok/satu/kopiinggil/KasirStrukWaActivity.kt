package kelompok.satu.kopiinggil

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant

class KasirStrukWaActivity : AppCompatActivity() {
    private lateinit var orderId: String
    private var receipt: OrderReceipt? = null
    private var editedPhone: String? = null
    private lateinit var tvLastAttempt: TextView
    private lateinit var tvProviderStatus: TextView
    private lateinit var tvRecipientPhone: TextView
    private lateinit var etReason: EditText
    private lateinit var btnResend: AppCompatButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kasir_struk_wa)
        applyEdgeToEdge(R.id.main)
        orderId = intent.getStringExtra(EXTRA_ORDER_ID).orEmpty()
        if (orderId.isBlank()) {
            Toast.makeText(this, "Pilih pesanan lunas dari daftar transaksi", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        editedPhone = savedInstanceState?.getString("edited_phone")
        tvLastAttempt = findViewById(R.id.tvReceiptLastAttempt)
        tvProviderStatus = findViewById(R.id.tvReceiptProviderStatus)
        tvRecipientPhone = findViewById(R.id.tvReceiptRecipientPhone)
        etReason = findViewById(R.id.etReceiptResendReason)
        btnResend = findViewById(R.id.btnResendReceipt)
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnBackToOrderReceipt).setOnClickListener { finish() }
        tvRecipientPhone.setOnClickListener { showEditPhoneDialog() }
        btnResend.setOnClickListener {
            if (receipt == null) loadReceipt() else sendReceiptWa()
        }
        loadReceipt()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("edited_phone", editedPhone)
        super.onSaveInstanceState(outState)
    }

    private fun loadReceipt() {
        btnResend.isEnabled = false
        btnResend.text = "Memuat pesanan..."
        tvRecipientPhone.isEnabled = false
        lifecycleScope.launch {
            try {
                val data = loadOrderReceipt(orderId)
                receipt = data
                findViewById<TextView>(R.id.tvReceiptEyebrow).text = "ORDER #${data.publicId.trimStart('#')} · ${data.tableLabel}"
                tvRecipientPhone.text = editedPhone ?: data.phoneE164
                tvLastAttempt.text = receiptTime(data.lastAttemptAt)
                showStatus(data.lastAttemptStatus ?: "Belum ada pengiriman ulang")
                btnResend.text = "Kirim ulang struk"
                tvRecipientPhone.isEnabled = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                receipt = null
                showStatus("Gagal memuat pesanan")
                btnResend.text = "Coba muat kembali"
                Toast.makeText(this@KasirStrukWaActivity, e.toUserMessage(this@KasirStrukWaActivity, R.string.err_receipt_load_failed), Toast.LENGTH_LONG).show()
            } finally {
                btnResend.isEnabled = true
            }
        }
    }

    private fun showStatus(status: String) {
        tvProviderStatus.text = status
        tvProviderStatus.setTextColor(getColor(if (status == "SENT") R.color.ngopi_primary else R.color.ngopi_ink))
        findViewById<TextView>(R.id.tvReceiptDeliveryBadge).text = when (status) {
            "SENT" -> "DITERIMA PROVIDER"
            "FAILED" -> "PENGIRIMAN GAGAL"
            "OPENED" -> "WHATSAPP DIBUKA"
            else -> "KIRIM ULANG STRUK"
        }
    }

    private fun showEditPhoneDialog() {
        val input = EditText(this).apply {
            hint = "Contoh: 08123456789"
            contentDescription = "Nomor WhatsApp penerima"
            setText(editedPhone ?: receipt?.phoneE164.orEmpty())
            inputType = android.text.InputType.TYPE_CLASS_PHONE
        }
        val dialog = AlertDialog.Builder(this)
            .setTitle("Ubah nomor untuk pengiriman ini")
            .setView(input)
            .setPositiveButton("Simpan", null)
            .setNegativeButton("Batal", null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val phone = WhatsAppBotService.formatChatId(input.text.toString())?.substringBefore('@')
                if (phone == null) {
                    input.error = "Masukkan nomor WhatsApp Indonesia yang valid"
                    input.requestFocus()
                } else {
                    editedPhone = "+$phone"
                    tvRecipientPhone.text = editedPhone
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    private fun sendReceiptWa() {
        val reason = etReason.text.toString().trim().ifBlank { "Kirim ulang oleh kasir" }
        if (reason.length > 500) {
            etReason.error = "Alasan maksimal 500 karakter"
            etReason.requestFocus()
            return
        }
        btnResend.isEnabled = false
        btnResend.text = "Mengirim struk..."
        tvRecipientPhone.isEnabled = false
        etReason.isEnabled = false
        lifecycleScope.launch {
            try {
                // Ambil ulang agar pesanan yang baru dibatalkan tidak dikirim sebagai lunas.
                val data = loadOrderReceipt(orderId)
                receipt = data
                val phone = WhatsAppBotService.formatChatId(editedPhone ?: data.phoneE164)?.substringBefore('@')
                if (phone == null) {
                    Toast.makeText(this@KasirStrukWaActivity, "Nomor WhatsApp tidak valid. Periksa bersama pelanggan.", Toast.LENGTH_LONG).show()
                    showEditPhoneDialog()
                    return@launch
                }
                val templateId = if (data.status == "COMPLETED") "COMPLETED" else "PAID"
                val message = data.message(WhatsAppBotService.loadReceiptTemplate(templateId), reason)
                val result = withContext(NonCancellable) {
                    val sent = WhatsAppBotService.sendTextMessage(phone, message, data.publicId)
                    recordAttempt(if (sent.isSuccess) "SENT" else "FAILED", phone, reason)
                    sent
                }
                val status = if (result.isSuccess) "SENT" else "FAILED"
                showStatus(status)
                tvLastAttempt.text = receiptTime(Instant.now().toString())
                if (result.isSuccess) {
                    Toast.makeText(this@KasirStrukWaActivity, "Struk diterima provider WhatsApp", Toast.LENGTH_LONG).show()
                } else {
                    showFallbackDirectWaDialog(phone, message, reason)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                receipt = null
                showStatus("Tidak dapat mengirim struk")
                Toast.makeText(this@KasirStrukWaActivity, e.toUserMessage(this@KasirStrukWaActivity, R.string.err_receipt_send_failed), Toast.LENGTH_LONG).show()
            } finally {
                btnResend.isEnabled = true
                btnResend.text = if (receipt == null) "Coba muat kembali" else "Kirim ulang struk"
                tvRecipientPhone.isEnabled = true
                etReason.isEnabled = true
            }
        }
    }

    private suspend fun recordAttempt(status: String, phone: String, reason: String) {
        // shortcut: WAHA dan audit terpisah; gunakan outbox server saat pengiriman dipindah dari laptop.
        try {
            supabase.postgrest.rpc("record_receipt_resend", buildJsonObject {
                put("p_order_id", orderId)
                put("p_result", status)
                put("p_phone", phone)
                put("p_reason", reason)
            })
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            Toast.makeText(this, "Hasil pengiriman $status belum tercatat di server. Jangan langsung mengirim ulang bila struk sudah diterima.", Toast.LENGTH_LONG).show()
        }
    }

    private fun showFallbackDirectWaDialog(phone: String, message: String, reason: String) {
        AlertDialog.Builder(this)
            .setTitle("Struk belum berhasil dikirim")
            .setMessage("Periksa WAHA dan koneksi. Anda juga dapat membuka WhatsApp untuk mengirim struk secara manual.")
            .setPositiveButton("Buka WhatsApp") { _, _ ->
                try {
                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$phone&text=${Uri.encode(message)}")
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                    showStatus("OPENED")
                    lifecycleScope.launch { recordAttempt("OPENED", phone, reason) }
                } catch (_: Exception) {
                    Toast.makeText(this, "Tidak dapat membuka WhatsApp", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    companion object {
        const val EXTRA_ORDER_ID = "extra_wa_order_id"
    }
}
