package kelompok.satu.kopiinggil

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

class PesananFragment : Fragment(R.layout.fragment_pesanan) {

    private lateinit var tvTableBadge: TextView
    private lateinit var llCartEmptyState: LinearLayout
    private lateinit var llActiveCartContainer: LinearLayout
    private lateinit var llCartItemsList: LinearLayout
    private lateinit var tvCartTotalItems: TextView
    private lateinit var tvCartTotalPrice: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTableBadge = view.findViewById(R.id.tvPesananTableBadge)
        llCartEmptyState = view.findViewById(R.id.llCartEmptyState)
        llActiveCartContainer = view.findViewById(R.id.llActiveCartContainer)
        llCartItemsList = view.findViewById(R.id.llCartItemsList)
        tvCartTotalItems = view.findViewById(R.id.tvCartTotalItems)
        tvCartTotalPrice = view.findViewById(R.id.tvCartTotalPrice)

        view.findViewById<View>(R.id.btnGoToMenu).setOnClickListener {
            (activity as? HomeActivity)?.selectMenuTab()
        }

        view.findViewById<View>(R.id.btnClearCart).setOnClickListener {
            CartManager.clear()
            renderPesanan()
            Toast.makeText(requireContext(), "Pesanan dikosongkan", Toast.LENGTH_SHORT).show()
        }

        view.findViewById<View>(R.id.btnCheckoutCart).setOnClickListener {
            if (CartManager.items.isNotEmpty()) {
                Toast.makeText(
                    requireContext(),
                    "Memproses pesanan (${CartManager.getTotalItemCount()} item - ${CartManager.getTotalPrice().toRupiah()})",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }

        renderPesanan()
    }

    override fun onResume() {
        super.onResume()
        renderPesanan()
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
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

        val cartItems = CartManager.items
        if (cartItems.isEmpty()) {
            llCartEmptyState.visibility = View.VISIBLE
            llActiveCartContainer.visibility = View.GONE
        } else {
            llCartEmptyState.visibility = View.GONE
            llActiveCartContainer.visibility = View.VISIBLE

            llCartItemsList.removeAllViews()
            val inflater = LayoutInflater.from(requireContext())

            for (cartItem in cartItems) {
                val itemView = inflater.inflate(R.layout.item_pesanan_cart, llCartItemsList, false)

                val tvNama = itemView.findViewById<TextView>(R.id.tvCartItemNama)
                val tvHargaUnit = itemView.findViewById<TextView>(R.id.tvCartItemHargaUnit)
                val tvTotalHarga = itemView.findViewById<TextView>(R.id.tvCartItemTotalHarga)
                val tvQty = itemView.findViewById<TextView>(R.id.tvCartItemQty)
                val btnMinus = itemView.findViewById<ImageView>(R.id.btnCartItemMinus)
                val btnPlus = itemView.findViewById<ImageView>(R.id.btnCartItemPlus)

                tvNama.text = cartItem.barang.nama
                tvHargaUnit.text = "${cartItem.barang.harga.toRupiah()} / porsi"
                tvTotalHarga.text = (cartItem.barang.harga * cartItem.quantity).toRupiah()
                tvQty.text = cartItem.quantity.toString()

                btnMinus.setOnClickListener {
                    CartManager.decreaseItem(cartItem.barang)
                    renderPesanan()
                }

                btnPlus.setOnClickListener {
                    CartManager.addItem(cartItem.barang)
                    renderPesanan()
                }

                llCartItemsList.addView(itemView)
            }

            tvCartTotalItems.text = "Total ${CartManager.getTotalItemCount()} Item"
            tvCartTotalPrice.text = CartManager.getTotalPrice().toRupiah()
        }
    }
}
