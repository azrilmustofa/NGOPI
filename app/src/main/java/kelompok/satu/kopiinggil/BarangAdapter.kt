package kelompok.satu.kopiinggil

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.dispose
import coil.load

class BarangAdapter(
    context: Context,
    private val onTambah: (DataBarang) -> Unit,
) : BaseAdapter() {
    private val inflater = LayoutInflater.from(context)
    private var items: List<DataBarang> = emptyList()

    fun submit(newItems: List<DataBarang>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun getCount() = items.size
    override fun getItem(position: Int) = items[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_data_barang, parent, false).also {
            it.tag = Holder(it)
        }
        val holder = view.tag as Holder
        val barang = items[position]
        holder.nama.text = barang.nama
        holder.deskripsi.text = barang.deskripsi
        holder.harga.text = barang.harga.toRupiah()
        holder.tambah.setOnClickListener { onTambah(barang) }
        bindFoto(holder.foto, barang.photoUrl)
        return view
    }

    // Foto dari photo_url; jika kosong atau gagal dimuat, tampil ikon kopi seperti placeholder.
    private fun bindFoto(foto: ImageView, url: String?) {
        foto.dispose()
        showPlaceholder(foto)
        if (url.isNullOrBlank()) return
        foto.load(url) {
            crossfade(true)
            listener(onSuccess = { _, _ ->
                foto.setPadding(0, 0, 0, 0)
                foto.imageTintList = null
            })
        }
    }

    private fun showPlaceholder(foto: ImageView) {
        val pad = (PLACEHOLDER_PADDING_DP * foto.resources.displayMetrics.density).toInt()
        foto.setPadding(pad, pad, pad, pad)
        foto.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(foto.context, R.color.ngopi_primary))
        foto.setImageResource(R.drawable.ic_coffee)
    }

    private companion object {
        const val PLACEHOLDER_PADDING_DP = 20
    }

    private class Holder(view: View) {
        val foto: ImageView = view.findViewById(R.id.ivBarang)
        val nama: TextView = view.findViewById(R.id.tvNamaBarang)
        val deskripsi: TextView = view.findViewById(R.id.tvDeskripsiBarang)
        val harga: TextView = view.findViewById(R.id.tvHargaBarang)
        val tambah: ImageView = view.findViewById(R.id.btnTambah)
    }
}
