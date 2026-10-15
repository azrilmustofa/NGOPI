package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment

// Tab "Pesanan" tampil seperti halaman Keranjang di desain; tombol bawah lanjut ke checkout.
class PesananFragment : Fragment(R.layout.fragment_pesanan) {

    private lateinit var tvTableBadge: TextView
    private lateinit var llCartEmptyState: LinearLayout
    private lateinit var llActiveCartContainer: LinearLayout
    private lateinit var llCartItemsList: LinearLayout
    private lateinit var llCartBottomBar: View
    private lateinit var tvSubtotal: TextView
    private lateinit var tvBottomSubtotal: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvTableBadge = view.findViewById(R.id.tvPesananTableBadge)
        llCartEmptyState = view.findViewById(R.id.llCartEmptyState)
        llActiveCartContainer = view.findViewById(R.id.llActiveCartContainer)
        llCartItemsList = view.findViewById(R.id.llCartItemsList)
        llCartBottomBar = view.findViewById(R.id.llCartBottomBar)
        tvSubtotal = view.findViewById(R.id.tvSubtotal)
        tvBottomSubtotal = view.findViewById(R.id.tvBottomSubtotal)

        val goToMenu = View.OnClickListener { (activity as? HomeActivity)?.selectMenuTab() }
        view.findViewById<View>(R.id.btnGoToMenu).setOnClickListener(goToMenu)
        view.findViewById<View>(R.id.btnAddMore).setOnClickListener(goToMenu)

        view.findViewById<View>(R.id.btnCheckoutCart).setOnClickListener {
            if (CartManager.items.isNotEmpty()) {
                startActivity(Intent(requireContext(), CheckoutActivity::class.java))
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
        if (!hidden) renderPesanan()
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
        val isEmpty = cartItems.isEmpty()
        llCartEmptyState.visibility = if (isEmpty) View.VISIBLE else View.GONE
        llActiveCartContainer.visibility = if (isEmpty) View.GONE else View.VISIBLE
        llCartBottomBar.visibility = if (isEmpty) View.GONE else View.VISIBLE
        if (isEmpty) return

        llCartItemsList.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        cartItems.forEachIndexed { index, cartItem ->
            val itemView = inflater.inflate(R.layout.item_pesanan_cart, llCartItemsList, false)

            itemView.findViewById<TextView>(R.id.tvCartItemNama).text = cartItem.barang.nama
            itemView.findViewById<TextView>(R.id.tvCartItemHargaUnit).text = cartItem.ringkasan()
            itemView.findViewById<TextView>(R.id.tvCartItemTotalHarga).text =
                (cartItem.hargaSatuan * cartItem.quantity).toRupiah()
            itemView.findViewById<TextView>(R.id.tvCartItemQty).text = cartItem.quantity.toString()
            itemView.findViewById<View>(R.id.vCartItemDivider).visibility =
                if (index == cartItems.lastIndex) View.GONE else View.VISIBLE

            itemView.findViewById<ImageView>(R.id.btnCartItemMinus).setOnClickListener {
                CartManager.decreaseItem(cartItem)
                renderPesanan()
            }
            itemView.findViewById<ImageView>(R.id.btnCartItemPlus).setOnClickListener {
                CartManager.increaseItem(cartItem)
                renderPesanan()
            }

            llCartItemsList.addView(itemView)
        }

        val subtotal = CartManager.getTotalPrice().toRupiah()
        tvSubtotal.text = subtotal
        tvBottomSubtotal.text = subtotal
    }
}
