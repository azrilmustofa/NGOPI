package kelompok.satu.kopiinggil

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

// Kartu carousel di Menu. Menu yang sedang promo tampil sebagai "promo hari ini";
// kalau tidak ada promo, MenuFragment mengisi satu menu terlaris sebagai "Pilihan hari ini".
class PromoBannerAdapter(
    private val onClick: (DataBarang) -> Unit,
) : RecyclerView.Adapter<PromoBannerAdapter.Holder>() {

    private var items: List<DataBarang> = emptyList()

    fun submit(newItems: List<DataBarang>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getItemCount() = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_promo_banner, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val barang = items[position]
        val context = holder.itemView.context
        val promo = barang.promo?.takeIf { barang.sedangPromo }

        holder.title.text = barang.nama
        holder.photo.clipToOutline = true
        holder.photo.loadFoto(barang.photoUrl)
        holder.itemView.setOnClickListener { onClick(barang) }

        if (promo != null) {
            holder.label.text = promo.title.uppercase()
            holder.price.text = barang.harga.toRupiah()
            holder.normalPrice.visibility = View.VISIBLE
            holder.normalPrice.text = barang.hargaNormal.toRupiah()
            holder.normalPrice.paintFlags = holder.normalPrice.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
            holder.note.text = listOfNotNull(
                promo.description?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.menu_promo_save, (barang.hargaNormal - barang.harga).toRupiah()),
                promo.berlakuSampai(),
            ).joinToString(" · ")
            holder.badge.visibility = View.VISIBLE
            holder.badge.text = promo.label
        } else {
            holder.label.setText(R.string.menu_featured_label)
            holder.price.text = context.getString(R.string.menu_featured_price, barang.harga.toRupiah())
            holder.normalPrice.visibility = View.GONE
            holder.note.setText(R.string.menu_featured_best_seller)
            holder.badge.visibility = View.GONE
        }
    }

    class Holder(view: View) : RecyclerView.ViewHolder(view) {
        val label: TextView = view.findViewById(R.id.tvBannerLabel)
        val title: TextView = view.findViewById(R.id.tvBannerTitle)
        val price: TextView = view.findViewById(R.id.tvBannerPrice)
        val normalPrice: TextView = view.findViewById(R.id.tvBannerNormalPrice)
        val note: TextView = view.findViewById(R.id.tvBannerNote)
        val photo: ImageView = view.findViewById(R.id.ivBannerPhoto)
        val badge: TextView = view.findViewById(R.id.tvBannerBadge)
    }
}
