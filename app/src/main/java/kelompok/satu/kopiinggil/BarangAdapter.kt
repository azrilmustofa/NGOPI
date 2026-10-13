package kelompok.satu.kopiinggil

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView

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
        return view
    }

    private class Holder(view: View) {
        val foto: ImageView = view.findViewById(R.id.ivBarang)
        val nama: TextView = view.findViewById(R.id.tvNamaBarang)
        val deskripsi: TextView = view.findViewById(R.id.tvDeskripsiBarang)
        val harga: TextView = view.findViewById(R.id.tvHargaBarang)
        val tambah: ImageView = view.findViewById(R.id.btnTambah)
    }
}
