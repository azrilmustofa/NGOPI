package kelompok.satu.kopiinggil

import android.os.Bundle
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

// Pesanan diproses: tampil setelah pembayaran (saat ini langsung dianggap lunas).
class StatusPesananActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_status_pesanan)
        applyEdgeToEdge(R.id.main)

        val order = OrderManager.lastOrder
        if (order == null) {
            finish()
            return
        }

        findViewById<TextView>(R.id.tvOrderNumber).text = "ORDER ${order.number}"
        findViewById<TextView>(R.id.tvStatusTitle).text = when (order.status) {
            "PREPARING" -> "Sedang diracik"
            "READY" -> "Siap diantar"
            "COMPLETED" -> "Pesanan selesai"
            else -> "Pesanan diproses"
        }
        findViewById<TextView>(R.id.tvStatusSubtitle).text =
            "Meja %02d · Dibayar via %s".format(order.table, order.method.label)
        findViewById<TextView>(R.id.tvPaidTotal).text = order.total.toRupiah()
        findViewById<TextView>(R.id.tvOrderSummaryTotal).text = order.total.toRupiah()
        findViewById<TextView>(R.id.tvStepPaymentTime).text = "${order.paidAt} · Tervalidasi"
        findViewById<TextView>(R.id.tvStepOrderTime).text = "${order.paidAt} · Barista mulai bekerja"
        findViewById<TextView>(R.id.tvStepBrewTime).text = "Diperbarui ${order.paidAt}"
        findViewById<ViewGroup>(R.id.llOrderItems).bindOrderLines(order.items)

        findViewById<android.view.View>(R.id.btnBack).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.btnOrderAgain).setOnClickListener { finish() }
        findViewById<android.view.View>(R.id.btnCallCashier).setOnClickListener {
            Toast.makeText(this, R.string.invalid_call_cashier_todo, Toast.LENGTH_SHORT).show()
        }
    }
}
