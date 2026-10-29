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
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
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
private data class StrukCustomerDto(
    @SerialName("order_id") val orderId: String = "",
    val name: String = "",
    @SerialName("phone_e164") val phoneE164: String = "",
)

class KasirStrukWaActivity : AppCompatActivity() {

    private var orderId: String = ""
    private var publicId: String = ""
    private var customerPhone: String = ""
    private var customerName: String = ""
    private var tableLabel: String = ""
    private var totalRp: Int = 0

    private lateinit var tvEyebrow: TextView
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
        publicId = intent.getStringExtra(EXTRA_PUBLIC_ID).orEmpty()
        customerPhone = intent.getStringExtra(EXTRA_PHONE).orEmpty()
        customerName = intent.getStringExtra(EXTRA_CUSTOMER_NAME).orEmpty()
        tableLabel = intent.getStringExtra(EXTRA_TABLE_LABEL).orEmpty().ifBlank { "Meja --" }
        totalRp = intent.getIntExtra(EXTRA_TOTAL_RP, 0)

        tvEyebrow = findViewById(R.id.tvReceiptEyebrow)
        tvLastAttempt = findViewById(R.id.tvReceiptLastAttempt)
        tvProviderStatus = findViewById(R.id.tvReceiptProviderStatus)
        tvRecipientPhone = findViewById(R.id.tvReceiptRecipientPhone)
        etReason = findViewById(R.id.etReceiptResendReason)
        btnResend = findViewById(R.id.btnResendReceipt)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnBackToOrderReceipt).setOnClickListener { finish() }

        val dispId = publicId.ifBlank { orderId.take(8) }
        tvEyebrow.text = "ORDER #$dispId"
        tvLastAttempt.text = SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(Date())
        tvRecipientPhone.text = customerPhone.ifBlank { "Belum ada nomor" }

        // Kasir dapat tap nomor telepon untuk mengedit jika nomor pelanggan berubah
        tvRecipientPhone.setOnClickListener {
            showEditPhoneDialog()
        }

        btnResend.setOnClickListener {
            val phone = customerPhone.trim()
            if (phone.isBlank()) {
                showEditPhoneDialog()
                return@setOnClickListener
            }
            sendReceiptWa(phone)
        }

        loadCustomerInfo()
    }

    private fun loadCustomerInfo() {
        if (customerPhone.isNotBlank() && customerName.isNotBlank()) return

        lifecycleScope.launch {
            try {
                val customer = supabase.postgrest["order_customer"].select {
                    filter { eq("order_id", orderId) }
                }.decodeList<StrukCustomerDto>().firstOrNull()

                if (customer != null) {
                    customerName = customer.name
                    if (customer.phoneE164.isNotBlank()) {
                        customerPhone = customer.phoneE164
                        tvRecipientPhone.text = customerPhone
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun showEditPhoneDialog() {
        val input = EditText(this).apply {
            hint = "Contoh: 08123456789"
            setText(customerPhone)
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Ubah Nomor WhatsApp")
            .setView(input)
            .setPositiveButton("Simpan") { _, _ ->
                val newPhone = input.text.toString().trim()
                if (newPhone.isNotBlank()) {
                    customerPhone = newPhone
                    tvRecipientPhone.text = customerPhone
                }
            }
            .setNegativeButton("Batal", null)
            .show()
    }

    private fun sendReceiptWa(phone: String) {
        btnResend.isEnabled = false
        Toast.makeText(this, "Mengirim struk WhatsApp...", Toast.LENGTH_SHORT).show()

        val reason = etReason.text.toString().trim().ifBlank { "Kirim ulang oleh kasir" }

        lifecycleScope.launch {
            val receiptMessage = buildString {
                appendLine("☕ *KOPI INGGIL*")
                appendLine("Jl. Raya Cafe No. 1, Yogyakarta")
                appendLine("----------------------------------------")
                appendLine("*NOTA TRANSAKSI PEMESANAN*")
                appendLine("No. Pesanan : *#${publicId.ifBlank { orderId.take(8) }}*")
                appendLine("Meja        : *$tableLabel*")
                appendLine("Nama        : ${customerName.ifBlank { "Pelanggan" }}")
                appendLine("Waktu       : ${SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("id-ID")).format(Date())} WIB")
                appendLine("Total       : ${totalRp.toRupiah()}")
                appendLine("Status      : LUNAS")
                appendLine("----------------------------------------")
                appendLine("Keterangan  : $reason")
                appendLine("Terima kasih telah memesan di Kopi Inggil! 🙏")
            }

            val result = WhatsAppBotService.sendTextMessage(phone, receiptMessage, publicId)

            btnResend.isEnabled = true
            if (result.isSuccess) {
                tvProviderStatus.text = "SENT"
                tvProviderStatus.setTextColor(getColor(R.color.ngopi_primary))
                Toast.makeText(this@KasirStrukWaActivity, "Struk berhasil dikirim ke WhatsApp!", Toast.LENGTH_LONG).show()

                // Catat ke audit riwayat
                try {
                    val currentUser = supabase.auth.currentUserOrNull()
                    supabase.postgrest["order_status_history"].insert(
                        buildJsonObject {
                            put("order_id", orderId)
                            if (currentUser != null) put("actor_uid", currentUser.id)
                            put("actor_type", "KASIR")
                            put("note", "Kirim ulang struk WA: $reason")
                        }
                    )
                } catch (_: Exception) {}

            } else {
                tvProviderStatus.text = "FAILED"
                val err = result.exceptionOrNull()?.message ?: "Gagal terhubung"
                showFallbackDirectWaDialog(phone, receiptMessage, err)
            }
        }
    }

    private fun showFallbackDirectWaDialog(phone: String, message: String, errorMsg: String) {
        val cleanPhone = phone.filter { it.isDigit() }.let {
            if (it.startsWith("0")) "62" + it.substring(1) else it
        }

        AlertDialog.Builder(this)
            .setTitle("Server WAHA Tidak Terjangkau")
            .setMessage("Bot otomatis belum aktif ($errorMsg).\n\nApakah Anda ingin membuka aplikasi WhatsApp langsung untuk mengirim struk ini?")
            .setPositiveButton("Buka WhatsApp Langsung") { _, _ ->
                try {
                    val uri = Uri.parse("https://api.whatsapp.com/send?phone=$cleanPhone&text=${Uri.encode(message)}")
                    startActivity(Intent(Intent.ACTION_VIEW, uri))
                } catch (e: Exception) {
                    Toast.makeText(this, "Tidak dapat membuka WhatsApp", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Tutup", null)
            .show()
    }

    companion object {
        const val EXTRA_ORDER_ID = "extra_wa_order_id"
        const val EXTRA_PUBLIC_ID = "extra_wa_public_id"
        const val EXTRA_PHONE = "extra_wa_phone"
        const val EXTRA_CUSTOMER_NAME = "extra_wa_customer_name"
        const val EXTRA_TABLE_LABEL = "extra_wa_table_label"
        const val EXTRA_TOTAL_RP = "extra_wa_total_rp"
    }
}
