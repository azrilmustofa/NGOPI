package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.checkbox.MaterialCheckBox
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

// Konfirmasi pesanan: ringkasan + data pemesan (dari akun, tidak diedit di sini).
// Tombol bayar membuka pop-up Midtrans (SnapPaymentSheet) di layar ini; lunas ditentukan server.
class CheckoutActivity : AppCompatActivity() {

    private lateinit var tvName: TextView
    private lateinit var tvPhone: TextView
    private lateinit var btnPay: TextView
    private lateinit var bannerPending: View

    // Data akun yang dipakai untuk struk & bantuan.
    private var customerName = ""
    private var customerPhone = ""

    // Satu kunci per percobaan: tap ganda atau membuka lagi pop-up memakai transaksi yang sama.
    // Diganti baru kalau transaksi gagal/kedaluwarsa, karena transaksi lamanya tidak bisa dipakai lagi.
    private var checkoutKey = UUID.randomUUID().toString()

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
        findViewById<ViewGroup>(R.id.llCheckoutItems).bindOrderLines(CartManager.items)
        findViewById<TextView>(R.id.tvCheckoutSubtotal).text = subtotal.toRupiah()
        findViewById<TextView>(R.id.tvCheckoutTax).text = tax.toRupiah()
        findViewById<TextView>(R.id.tvCheckoutTotal).text = total.toRupiah()
        findViewById<TextView>(R.id.tvBottomTotal).text = total.toRupiah()

        tvName = findViewById(R.id.tvCustomerName)
        tvPhone = findViewById(R.id.tvCustomerPhone)
        btnPay = findViewById(R.id.btnProceedPayment)
        bannerPending = findViewById(R.id.bannerPending)
        val cbConsentWa = findViewById<MaterialCheckBox>(R.id.cbConsentWa)

        // 1. Tampil langsung dari sesi login, 2. lalu segarkan dari server supaya data profil terbaru.
        bindAccount(supabase.auth.currentUserOrNull())
        lifecycleScope.launch {
            try {
                bindAccount(supabase.auth.retrieveUserForCurrentSession(updateSession = true))
            } catch (_: Exception) {
                // Pakai data sesi yang sudah tampil.
            }
        }

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        btnPay.setOnClickListener {
            if (customerName.isBlank() || customerPhone.isBlank()) {
                Toast.makeText(this, R.string.checkout_profile_incomplete, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            if (!cbConsentWa.isChecked) {
                Toast.makeText(this, R.string.checkout_consent_required, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startPayment()
        }

        renderPending()
    }

    // Membuat transaksi (atau memakai yang masih berjalan) lalu membuka pop-up Midtrans.
    private fun startPayment() {
        val existing = OrderManager.pendingPayment
        if (existing != null) {
            openSheet(existing.redirectUrl)
            return
        }
        btnPay.isEnabled = false
        lifecycleScope.launch {
            try {
                openSheet(OrderManager.createPayment(checkoutKey).redirectUrl)
            } catch (e: Exception) {
                android.util.Log.e("Checkout", "Gagal membuat pembayaran", e)
                Toast.makeText(this@CheckoutActivity, e.toUserMessage(this@CheckoutActivity, R.string.err_payment_create_failed), Toast.LENGTH_LONG).show()
            } finally {
                btnPay.isEnabled = true
            }
        }
    }

    private fun openSheet(url: String) {
        btnPay.isEnabled = false
        SnapPaymentSheet(this, url) { state ->
            btnPay.isEnabled = true
            when (state) {
                PaymentState.PAID -> {
                    startActivity(Intent(this, StatusPesananActivity::class.java).apply {
                        putExtra("EXTRA_CUSTOMER_PHONE", customerPhone)
                        putExtra("EXTRA_CUSTOMER_NAME", customerName)
                    })
                    finish()
                }
                PaymentState.FAILED -> {
                    OrderManager.discardPending()
                    checkoutKey = UUID.randomUUID().toString()
                    renderPending()
                    AlertDialog.Builder(this)
                        .setTitle(R.string.pay_failed_title)
                        .setMessage(R.string.pay_failed_message)
                        .setPositiveButton(R.string.pay_failed_retry, null)
                        .show()
                }
                // Pop-up ditutup sebelum selesai: transaksi tetap berjalan, bisa dilanjutkan.
                PaymentState.PENDING -> renderPending()
            }
        }.show()
    }

    // Ada transaksi yang belum selesai: tampilkan penanda dan ubah tombol jadi "Lanjutkan pembayaran".
    private fun renderPending() {
        val pending = OrderManager.pendingPayment != null
        bannerPending.visibility = if (pending) View.VISIBLE else View.GONE
        btnPay.setText(if (pending) R.string.checkout_btn_continue else R.string.checkout_btn_pay)
    }

    private fun bindAccount(user: UserInfo?) {
        val metadata = user?.userMetadata
        customerName = metadata?.get("full_name")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
            ?: user?.email.orEmpty().substringBefore("@")
        // Sama seperti Profil: hanya angka, tanpa awalan 62/0 (prefix +62 sudah tampil di UI).
        customerPhone = metadata?.get("phone")?.jsonPrimitive?.content.orEmpty()
            .filter(Char::isDigit).removePrefix("62").trimStart('0')

        tvName.text = customerName
        tvPhone.text = customerPhone.ifEmpty { getString(R.string.checkout_phone_empty) }
    }
}
