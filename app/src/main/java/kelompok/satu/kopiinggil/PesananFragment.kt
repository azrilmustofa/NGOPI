package kelompok.satu.kopiinggil

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class PesananFragment : Fragment(R.layout.fragment_pesanan) {

    private lateinit var tvTableBadge: TextView
    private lateinit var llPesananEmptyState: LinearLayout
    private lateinit var llOrdersList: LinearLayout

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTableBadge = view.findViewById(R.id.tvPesananTableBadge)
        llPesananEmptyState = view.findViewById(R.id.llPesananEmptyState)
        llOrdersList = view.findViewById(R.id.llOrdersList)

        view.findViewById<View>(R.id.btnPesananGoToMenu).setOnClickListener {
            (activity as? HomeActivity)?.selectMenuTab()
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
            OrderManager.fetchOrders()
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

        val orders = OrderManager.orderHistory
        if (orders.isNotEmpty()) {
            llPesananEmptyState.visibility = View.GONE
            llOrdersList.visibility = View.VISIBLE
            llOrdersList.removeAllViews()

            val inflater = LayoutInflater.from(requireContext())
            orders.forEach { order ->
                val card = inflater.inflate(R.layout.item_pesanan_card, llOrdersList, false)
                val tvOrderNumber = card.findViewById<TextView>(R.id.tvOrderNumber)
                val tvOrderStatusBadge = card.findViewById<TextView>(R.id.tvOrderStatusBadge)
                val tvOrderSubtitle = card.findViewById<TextView>(R.id.tvOrderSubtitle)
                val llPesananItemsList = card.findViewById<ViewGroup>(R.id.llPesananItemsList)
                val tvPesananTotal = card.findViewById<TextView>(R.id.tvPesananTotal)
                val btnDetailStatus = card.findViewById<View>(R.id.btnDetailStatus)

                tvOrderNumber.text = "ORDER ${order.number}"
                tvOrderStatusBadge.text = order.statusLabel

                if (order.status.equals("COMPLETED", true)) {
                    tvOrderStatusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ngopi_border))
                    tvOrderStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.ngopi_muted))
                } else {
                    tvOrderStatusBadge.backgroundTintList = null
                    tvOrderStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.ngopi_primary))
                }

                val tableInfo = if (order.table > 0) "Meja ${order.table} · " else ""
                tvOrderSubtitle.text = "${tableInfo}Dibayar pukul ${order.paidAt} · ${order.method.label}"
                llPesananItemsList.bindOrderLines(order.items)
                tvPesananTotal.text = order.total.toRupiah()

                btnDetailStatus.setOnClickListener {
                    OrderManager.selectedOrder = order
                    startActivity(Intent(requireContext(), StatusPesananActivity::class.java))
                }

                llOrdersList.addView(card)
            }
        } else {
            llPesananEmptyState.visibility = View.VISIBLE
            llOrdersList.visibility = View.GONE
            llOrdersList.removeAllViews()
        }
    }
}
