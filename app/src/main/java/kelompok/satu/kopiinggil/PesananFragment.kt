package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PesananFragment : Fragment(R.layout.fragment_pesanan) {

    private lateinit var tvTableBadge: TextView
    private lateinit var llPesananEmptyState: LinearLayout
    private lateinit var cardActiveOrder: LinearLayout
    private lateinit var tvOrderNumber: TextView
    private lateinit var tvOrderStatusBadge: TextView
    private lateinit var tvOrderSubtitle: TextView
    private lateinit var llPesananItemsList: ViewGroup
    private lateinit var tvPesananTotal: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTableBadge = view.findViewById(R.id.tvPesananTableBadge)
        llPesananEmptyState = view.findViewById(R.id.llPesananEmptyState)
        cardActiveOrder = view.findViewById(R.id.cardActiveOrder)
        tvOrderNumber = view.findViewById(R.id.tvOrderNumber)
        tvOrderStatusBadge = view.findViewById(R.id.tvOrderStatusBadge)
        tvOrderSubtitle = view.findViewById(R.id.tvOrderSubtitle)
        llPesananItemsList = view.findViewById(R.id.llPesananItemsList)
        tvPesananTotal = view.findViewById(R.id.tvPesananTotal)

        view.findViewById<View>(R.id.btnPesananGoToMenu).setOnClickListener {
            (activity as? HomeActivity)?.selectMenuTab()
        }

        view.findViewById<View>(R.id.btnDetailStatus).setOnClickListener {
            startActivity(Intent(requireContext(), StatusPesananActivity::class.java))
        }

        loadPesanan()
    }

    override fun onResume() {
        super.onResume()
        loadPesanan()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) loadPesanan()
    }

    private fun loadPesanan() {
        renderPesanan()
        viewLifecycleOwner.lifecycleScope.launch {
            OrderManager.fetchLatestOrder()
            renderPesanan()
        }
    }

    private fun renderPesanan() {
        if (!isAdded) return

        val table = CartManager.tableNumber
        tvTableBadge.text = if (table > 0) {
            getString(R.string.menu_table_badge, table)
        } else {
            getString(R.string.table_placeholder)
        }

        val order = OrderManager.lastOrder
        if (order != null) {
            llPesananEmptyState.visibility = View.GONE
            cardActiveOrder.visibility = View.VISIBLE

            tvOrderNumber.text = "ORDER ${order.number}"
            tvOrderStatusBadge.text = order.statusLabel
            tvOrderSubtitle.text = "Dibayar pukul ${order.paidAt} · ${order.method.label}"
            llPesananItemsList.bindOrderLines(order.items)
            tvPesananTotal.text = order.total.toRupiah()
        } else {
            llPesananEmptyState.visibility = View.VISIBLE
            cardActiveOrder.visibility = View.GONE
        }
    }
}
