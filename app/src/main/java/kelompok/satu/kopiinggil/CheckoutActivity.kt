package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity

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

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.btnProceedPayment).setOnClickListener {
            payment.launch(Intent(this, MetodePembayaranActivity::class.java))
        }
    }
}
