package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.RadioButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

// Langkah 3 dari 3. Belum terintegrasi payment gateway: tekan Bayar langsung dianggap lunas.
class MetodePembayaranActivity : AppCompatActivity() {

    private var selected = MetodeBayar.QRIS

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_metode_pembayaran)
        applyEdgeToEdge(R.id.main)

        if (CartManager.items.isEmpty()) {
            finish()
            return
        }

        val total = CartManager.getTotalPrice() + OrderManager.taxOf(CartManager.getTotalPrice())
        findViewById<TextView>(R.id.tvPaymentSubtitle).text =
            "Total ${total.toRupiah()} · Meja %02d".format(CartManager.tableNumber)
        findViewById<TextView>(R.id.btnPay).text = "Bayar ${total.toRupiah()}"

        val radios = mapOf(
            MetodeBayar.QRIS to findViewById<RadioButton>(R.id.rbQris),
            MetodeBayar.VIRTUAL_ACCOUNT to findViewById<RadioButton>(R.id.rbVa),
            MetodeBayar.EWALLET to findViewById<RadioButton>(R.id.rbEwallet),
        )
        val rows = mapOf(
            MetodeBayar.QRIS to findViewById<View>(R.id.rowQris),
            MetodeBayar.VIRTUAL_ACCOUNT to findViewById<View>(R.id.rowVa),
            MetodeBayar.EWALLET to findViewById<View>(R.id.rowEwallet),
        )
        fun select(method: MetodeBayar) {
            selected = method
            radios.forEach { (m, rb) -> rb.isChecked = m == method }
        }
        rows.forEach { (m, row) -> row.setOnClickListener { select(m) } }
        radios.forEach { (m, rb) -> rb.setOnClickListener { select(m) } }
        select(selected)

        findViewById<View>(R.id.btnCancel).setOnClickListener { finish() }
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<View>(R.id.btnPay).setOnClickListener {
            OrderManager.payAndPlace(selected)
            // Checkout ikut ditutup supaya Back dari status mendarat di Home.
            setResult(RESULT_OK)
            startActivity(Intent(this, StatusPesananActivity::class.java))
            finish()
        }
    }
}
