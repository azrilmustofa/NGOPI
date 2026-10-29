package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.checkbox.MaterialCheckBox
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.user.UserInfo
import kotlinx.coroutines.launch
import kotlinx.serialization.json.jsonPrimitive
import java.util.UUID

// Konfirmasi pesanan: ringkasan + data pemesan (dari akun, tidak diedit di sini).
// Saat dibuka, keranjang dicek ke server (quote-cart): total resmi, dan pop-up kalau harga berubah / menu habis.
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

    // Sedang menunggu quote-cart: tombol bayar ditahan supaya pelanggan membayar total yang sudah dicek.
    private var checkingPrice = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_checkout)
        applyEdgeToEdge(R.id.main)

        if (CartManager.items.isEmpty()) {
            finish()
            return
        }

        findViewById<TextView>(R.id.tvCheckoutSubtitle).text = "Meja %02d · Dine-in".format(CartManager.tableNumber)
        renderCart()

        tvName = findViewById(R.id.tvCustomerName)
        tvPhone = findViewById(R.id.tvCustomerPhone)
        btnPay = findViewById(R.id.btnProceedPayment)
        bannerPending = findViewById(R.id.bannerPending)
        val cbConsentWa = findViewById<MaterialCheckBox>(R.id.cbConsentWa)
        val cbConsentPromo = findViewById<MaterialCheckBox>(R.id.cbConsentPromo)

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
            // Nomor dari akun yang terlalu pendek/panjang akan ditolak server; beri tahu lebih awal.
            if (customerPhone.length !in 8..13) {
                Toast.makeText(this, R.string.checkout_phone_invalid, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            startPayment(consentPromo = cbConsentPromo.isChecked)
        }

        renderPending()
        // Transaksi yang sedang berjalan sudah punya harga tetap; cek harga hanya untuk pesanan baru.
        if (OrderManager.pendingPayment == null) refreshQuote()
    }

    // Daftar item + perkiraan total dari app; diganti angka server setelah quote-cart selesai.
    private fun renderCart() {
        val subtotal = CartManager.getTotalPrice()
        val tax = OrderManager.taxOf(subtotal)
        findViewById<ViewGroup>(R.id.llCheckoutItems).bindOrderLines(CartManager.items)
        renderTotals(subtotal, tax, subtotal + tax)
    }

    private fun renderTotals(subtotal: Int, taxAndService: Int, total: Int) {
        findViewById<TextView>(R.id.tvCheckoutSubtotal).text = subtotal.toRupiah()
        findViewById<TextView>(R.id.tvCheckoutTax).text = taxAndService.toRupiah()
        findViewById<TextView>(R.id.tvCheckoutTotal).text = total.toRupiah()
        findViewById<TextView>(R.id.tvBottomTotal).text = total.toRupiah()
    }

    private fun renderQuote(quote: CartQuote) {
        renderTotals(quote.subtotal, quote.tax + quote.service, quote.total)
        findViewById<TextView>(R.id.tvCheckoutTaxLabel).text =
            if (quote.service > 0) getString(R.string.checkout_tax_service_label, quote.taxPercentage.percent(), quote.servicePercentage.percent())
            else getString(R.string.checkout_tax_label, quote.taxPercentage.percent())
    }

    // Cek harga & stok ke server. Kalau server tidak bisa dihubungi, tetap boleh bayar:
    // create-payment menghitung ulang semuanya dan menolak menu yang habis.
    private fun refreshQuote() {
        checkingPrice = true
        btnPay.isEnabled = false
        btnPay.setText(R.string.checkout_btn_checking)
        lifecycleScope.launch {
            try {
                val quote = OrderManager.quoteCart()
                if (quote.hasChanges) showChanges(quote) else renderQuote(quote)
            } catch (e: Exception) {
                android.util.Log.w("Checkout", "Cek harga gagal, pakai perkiraan app", e)
            } finally {
                checkingPrice = false
                btnPay.isEnabled = true
                renderPending()
            }
        }
    }

    // Pop-up "Ada perubahan di keranjang": perbarui ke harga server, atau kembali ke Keranjang.
    private fun showChanges(quote: CartQuote) {
        val dialog = BottomSheetDialog(this)
        val sheet = layoutInflater.inflate(R.layout.sheet_cart_changes, null)
        val list = sheet.findViewById<LinearLayout>(R.id.llCartChanges)

        quote.lines.filter { it.isChanged }.forEach { line ->
            val item = CartManager.items.getOrNull(line.index) ?: return@forEach
            val row = layoutInflater.inflate(R.layout.item_cart_change, list, false)
            val label = row.findViewById<TextView>(R.id.tvChangeLabel)
            val qty = if (item.quantity > 1) "${item.quantity}× " else ""
            row.findViewById<TextView>(R.id.tvChangeName).text =
                (listOf(qty + item.barang.nama) + item.opsi).joinToString(" · ")
            if (line.isUnavailable) {
                label.setText(R.string.cart_changes_label_unavailable)
                label.setTextColor(getColor(R.color.ngopi_error))
                row.findViewById<View>(R.id.llChangePrices).visibility = View.GONE
                row.findViewById<TextView>(R.id.tvChangeReason).apply {
                    visibility = View.VISIBLE
                    text = getString(R.string.cart_changes_will_remove, line.reason ?: getString(R.string.cart_changes_unavailable))
                }
            } else {
                label.setText(R.string.cart_changes_label_price)
                row.findViewById<TextView>(R.id.tvChangeOldPrice).text = (item.hargaSatuan * item.quantity).toRupiah()
                row.findViewById<TextView>(R.id.tvChangeNewPrice).text = (line.unitPrice * item.quantity).toRupiah()
            }
            list.addView(row)
        }
        sheet.findViewById<TextView>(R.id.tvCartChangesTotal).text = quote.total.toRupiah()

        sheet.findViewById<View>(R.id.btnCartChangesApply).setOnClickListener {
            dialog.dismiss()
            if (!CartManager.applyQuote(quote)) {
                // Keranjang berubah sejak dicek: cek ulang.
                refreshQuote()
                return@setOnClickListener
            }
            if (CartManager.items.isEmpty()) {
                Toast.makeText(this, R.string.cart_changes_all_gone, Toast.LENGTH_LONG).show()
                finish()
                return@setOnClickListener
            }
            findViewById<ViewGroup>(R.id.llCheckoutItems).bindOrderLines(CartManager.items)
            renderQuote(quote)
            Toast.makeText(this, R.string.cart_changes_updated, Toast.LENGTH_SHORT).show()
        }
        sheet.findViewById<View>(R.id.btnCartChangesBack).setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialog.setContentView(sheet)
        // Harus memilih salah satu: tidak boleh lanjut bayar dengan harga lama.
        dialog.setCancelable(false)
        dialog.show()
    }

    // Membuat transaksi (atau memakai yang masih berjalan) lalu membuka pop-up Midtrans.
    private fun startPayment(consentPromo: Boolean) {
        if (OrderManager.pendingPayment != null) {
            continuePayment(retry = false)
            return
        }
        btnPay.isEnabled = false
        lifecycleScope.launch {
            try {
                openSheet(OrderManager.createPayment(checkoutKey, consentPromo).redirectUrl)
            } catch (e: Exception) {
                android.util.Log.e("Checkout", "Gagal membuat pembayaran", e)
                Toast.makeText(this@CheckoutActivity, e.toUserMessage(this@CheckoutActivity, R.string.err_payment_create_failed), Toast.LENGTH_LONG).show()
                // Server sudah menjawab gagal: pesanan dengan kunci ini (kalau sempat dibuat) sudah dibatalkan server,
                // jadi percobaan berikutnya pakai kunci baru. Kunci lama hanya dipakai lagi kalau jaringan putus
                // (server mungkin masih memproses) atau 409 (permintaan lain dengan kunci ini sedang berjalan).
                val status = (e as? PaymentException)?.httpStatus
                if (status != null && status != 409) checkoutKey = UUID.randomUUID().toString()
                // Bisa jadi menu habis/harga berubah tepat sebelum bayar: cek ulang supaya pop-up perubahan muncul.
                refreshQuote()
            } finally {
                if (!checkingPrice) btnPay.isEnabled = true
            }
        }
    }

    // Pesanan sudah dibuat tapi belum lunas: buka lagi transaksi yang masih berlaku, atau (retry / sudah
    // kedaluwarsa) minta transaksi baru untuk pesanan yang sama lewat retry-payment.
    private fun continuePayment(retry: Boolean) {
        btnPay.isEnabled = false
        lifecycleScope.launch {
            try {
                val result = if (retry) OrderManager.retryPayment() else OrderManager.resumePayment()
                when (result) {
                    is PaymentResume.Open -> openSheet(result.redirectUrl)
                    PaymentResume.Paid -> openStatus()
                    is PaymentResume.Closed -> {
                        // Pesanan lama ditutup server; tekan bayar lagi = pesanan baru (keranjang masih utuh).
                        checkoutKey = UUID.randomUUID().toString()
                        refreshQuote() // pesanan baru memakai harga terbaru
                        AlertDialog.Builder(this@CheckoutActivity)
                            .setTitle(R.string.pay_closed_title)
                            .setMessage(result.message ?: getString(R.string.pay_closed_message))
                            .setPositiveButton(android.R.string.ok, null)
                            .show()
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("Checkout", "Gagal melanjutkan pembayaran", e)
                Toast.makeText(this@CheckoutActivity, e.toUserMessage(this@CheckoutActivity, R.string.err_payment_create_failed), Toast.LENGTH_LONG).show()
            } finally {
                btnPay.isEnabled = true
            }
        }
    }

    private fun openStatus() {
        startActivity(Intent(this, StatusPesananActivity::class.java).apply {
            putExtra("EXTRA_CUSTOMER_PHONE", customerPhone)
            putExtra("EXTRA_CUSTOMER_NAME", customerName)
        })
        finish()
    }

    private fun openSheet(url: String) {
        btnPay.isEnabled = false
        SnapPaymentSheet(this, url) { state ->
            btnPay.isEnabled = true
            when (state) {
                PaymentState.PAID -> openStatus()
                // Pesanan tetap tersimpan; coba lagi = transaksi baru untuk pesanan yang sama.
                PaymentState.FAILED -> {
                    renderPending()
                    AlertDialog.Builder(this)
                        .setTitle(R.string.pay_failed_title)
                        .setMessage(R.string.pay_failed_message)
                        .setPositiveButton(R.string.pay_failed_retry) { _, _ -> continuePayment(retry = true) }
                        .setNegativeButton(R.string.pay_failed_later, null)
                        .show()
                }
                // Pop-up ditutup sebelum selesai: transaksi tetap berjalan, bisa dilanjutkan.
                PaymentState.PENDING -> renderPending()
            }
        }.show()
    }

    // Ada transaksi yang belum selesai: tampilkan penanda dan ubah tombol jadi "Lanjutkan pembayaran".
    private fun renderPending() {
        if (checkingPrice) return
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

    // 10.0 -> "10", 2.5 -> "2,5"
    private fun Double.percent(): String =
        if (this % 1.0 == 0.0) toInt().toString() else toString().replace('.', ',')
}
