package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doOnTextChanged
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// Langkah 2 dari 3: ringkasan pesanan + data pemesan, lalu lanjut ke metode pembayaran.
class CheckoutActivity : AppCompatActivity() {

    private val payment = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode == RESULT_OK) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)
        applyEdgeToEdge(R.id.main)

        if (CartManager.items.isEmpty()) {
            finish()
            return
        }

        val subtotal = CartManager.getTotalPrice()
        val tax = OrderManager.taxOf(subtotal)
        val total = subtotal + tax

        findViewById<TextView>(R.id.tvCheckoutSubtitle).text = "Meja %02d · Dine-in".format(CartManager.tableNumber)
        findViewById<android.view.ViewGroup>(R.id.llCheckoutItems).bindOrderLines(CartManager.items)
        findViewById<TextView>(R.id.tvCheckoutSubtotal).text = subtotal.toRupiah()
        findViewById<TextView>(R.id.tvCheckoutTax).text = tax.toRupiah()
        findViewById<TextView>(R.id.tvCheckoutTotal).text = total.toRupiah()
        findViewById<TextView>(R.id.tvBottomTotal).text = total.toRupiah()

        val tilName = findViewById<TextInputLayout>(R.id.tilCustomerName)
        val tilPhone = findViewById<TextInputLayout>(R.id.tilCustomerPhone)
        val etName = findViewById<TextInputEditText>(R.id.etCustomerName)
        val etPhone = findViewById<TextInputEditText>(R.id.etCustomerPhone)
        val cbConsentWa = findViewById<MaterialCheckBox>(R.id.cbConsentWa)

        etName.doOnTextChanged { _, _, _, _ -> tilName.error = null }
        etPhone.doOnTextChanged { _, _, _, _ -> tilPhone.error = null }

        populateCustomerInfo(etName, etPhone)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.btnProceedPayment).setOnClickListener {
            val name = etName.text?.toString().orEmpty().trim()
            val phone = etPhone.text?.toString().orEmpty().trim()

            var valid = true
            if (name.isEmpty()) {
                tilName.error = getString(R.string.register_error_name)
                valid = false
            }
            if (phone.isEmpty()) {
                tilPhone.error = getString(R.string.register_error_phone)
                valid = false
            }
            if (!cbConsentWa.isChecked) {
                Toast.makeText(this, "Setujui penerimaan pesan WhatsApp untuk melanjutkan", Toast.LENGTH_SHORT).show()
                valid = false
            }

            if (!valid) return@setOnClickListener

            // Sinkronkan pembaruan nama / nomor telepon ke profil akun Supabase di latar belakang
            lifecycleScope.launch {
                try {
                    val fullPhone = if (phone.startsWith("+")) phone else "+62$phone"
                    supabase.auth.updateUser {
                        data = buildJsonObject {
                            put("full_name", name)
                            put("phone", fullPhone)
                        }
                    }
                } catch (_: Exception) {
                    // Abaikan kegagalan update profil agar alur pembayaran pelanggan tidak terhambat
                }
            }

            payment.launch(Intent(this, MetodePembayaranActivity::class.java))
        }
    }

    private fun populateCustomerInfo(etName: TextInputEditText, etPhone: TextInputEditText) {
        fun applyData(name: String?, rawPhone: String?, email: String?) {
            if (etName.text.isNullOrBlank()) {
                if (!name.isNullOrBlank()) {
                    etName.setText(name)
                } else if (!email.isNullOrBlank()) {
                    etName.setText(email.substringBefore("@"))
                }
            }
            if (etPhone.text.isNullOrBlank() && !rawPhone.isNullOrBlank()) {
                val clean = rawPhone
                    .replace(" ", "")
                    .replace("-", "")
                    .removePrefix("+62")
                    .removePrefix("62")
                    .removePrefix("0")
                etPhone.setText(clean)
            }
        }

        // 1. Tampilkan langsung dari cache sesi login aktif
        val user = supabase.auth.currentUserOrNull()
        val metadata = user?.userMetadata
        val initialName = metadata?.get("full_name")?.jsonPrimitive?.content
        val initialPhone = metadata?.get("phone")?.jsonPrimitive?.content
        applyData(initialName, initialPhone, user?.email)

        // 2. Refresh dari server Supabase untuk memastikan data profil paling mutakhir
        lifecycleScope.launch {
            try {
                val refreshedUser = supabase.auth.retrieveUserForCurrentSession(updateSession = true)
                val refreshedMeta = refreshedUser.userMetadata
                val latestName = refreshedMeta?.get("full_name")?.jsonPrimitive?.content
                val latestPhone = refreshedMeta?.get("phone")?.jsonPrimitive?.content
                applyData(latestName, latestPhone, refreshedUser.email)
            } catch (_: Exception) {
                // Gunakan cache yang sudah terisi
            }
        }
    }
}
