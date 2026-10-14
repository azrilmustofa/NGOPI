package kelompok.satu.kopiinggil

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

// Tab Lihat menu: header, pencarian, kategori, dan daftar barang (ListView).
class MenuFragment : Fragment(R.layout.fragment_menu) {
    private lateinit var adapter: BarangAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var lvBarang: ListView
    private val chips = mutableMapOf<KategoriBarang, TextView>()
    private var kategori = KategoriBarang.SEMUA
    private var query = ""
    private var urutan = UrutBarang.TERLARIS

    private var semuaBarang: List<DataBarang> = emptyList()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val table = requireArguments().getInt(ARG_TABLE, 0)
        view.findViewById<TextView>(R.id.tvTableBadge).text =
            if (table > 0) getString(R.string.menu_table_badge, table) else getString(R.string.table_placeholder)

        adapter = BarangAdapter(requireContext()) { barang ->
            Toast.makeText(requireContext(), getString(R.string.menu_added, barang.nama), Toast.LENGTH_SHORT).show()
        }
        tvEmpty = view.findViewById(R.id.tvEmpty)
        lvBarang = view.findViewById(R.id.lvBarang)
        lvBarang.adapter = adapter

        chips.clear()
        buildChips(view.findViewById(R.id.categoryContainer))
        view.findViewById<EditText>(R.id.etSearch).apply {
            setText(query)
            doAfterTextChanged {
                query = it?.toString().orEmpty().trim()
                render()
            }
        }
        view.findViewById<View>(R.id.btnSort).setOnClickListener { showSortMenu() }
        render()
        loadMenuFromSupabase()
    }

    private fun loadMenuFromSupabase() {
        lifecycleScope.launch {
            try {
                val data = supabase.from("menu_item").select().decodeList<DataBarang>()
                if (data.isNotEmpty()) {
                    val terlaris = loadTerlaris()
                    semuaBarang = data.filter { it.isActive }.map { it.copy(terjual = terlaris[it.id] ?: 0) }
                    render()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Jumlah dipesan per menu. Jika view belum dibuat / gagal dibaca, semua dianggap 0 (urutan mengikuti database).
    private suspend fun loadTerlaris(): Map<String, Int> = try {
        supabase.from("menu_terlaris").select().decodeList<MenuTerlaris>().associate { it.menuItemId to it.totalDipesan }
    } catch (e: Exception) {
        emptyMap()
    }

    private fun showSortMenu() {
        val dialog = BottomSheetDialog(requireContext())
        val sheet = layoutInflater.inflate(R.layout.sheet_sort, null)
        val options = sheet.findViewById<LinearLayout>(R.id.sortOptions)
        UrutBarang.entries.forEach { item ->
            val row = layoutInflater.inflate(R.layout.item_sort_option, options, false)
            val active = item == urutan
            row.findViewById<TextView>(R.id.tvSortLabel).apply {
                setText(item.labelRes)
                if (active) setTextColor(ContextCompat.getColor(context, R.color.ngopi_primary))
            }
            row.findViewById<TextView>(R.id.tvSortDesc).setText(item.descRes)
            row.findViewById<View>(R.id.ivSortCheck).visibility = if (active) View.VISIBLE else View.INVISIBLE
            if (active) row.setBackgroundResource(R.drawable.bg_sort_option_active)
            row.setOnClickListener {
                urutan = item
                render()
                dialog.dismiss()
            }
            options.addView(row)
        }
        dialog.setContentView(sheet)
        // Latar bawaan sheet dibuat transparan agar sudut atas yang membulat terlihat.
        (sheet.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        dialog.show()
    }

    private fun buildChips(container: LinearLayout) {
        val dp = resources.displayMetrics.density
        KategoriBarang.entries.forEach { item ->
            val chip = TextView(requireContext()).apply {
                text = item.label
                textSize = 12f
                setTypeface(typeface, android.graphics.Typeface.BOLD)
                gravity = android.view.Gravity.CENTER
                setPadding((14 * dp).toInt(), 0, (14 * dp).toInt(), 0)
                setOnClickListener {
                    kategori = item
                    render()
                }
            }
            container.addView(
                chip,
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, (33 * dp).toInt()).apply {
                    marginEnd = (8 * dp).toInt()
                },
            )
            chips[item] = chip
        }
    }

    private fun render() {
        chips.forEach { (item, chip) ->
            val active = item == kategori
            chip.setBackgroundResource(if (active) R.drawable.bg_chip_active else R.drawable.bg_chip)
            chip.setTextColor(
                ContextCompat.getColor(requireContext(), if (active) R.color.ngopi_surface else R.color.ngopi_ink),
            )
        }
        val hasil = semuaBarang.filter {
            (kategori == KategoriBarang.SEMUA || it.kategori == kategori) &&
                (query.isEmpty() || it.nama.contains(query, true) || it.deskripsi.contains(query, true))
        }.let { list ->
            when (urutan) {
                UrutBarang.TERLARIS -> list.sortedByDescending { it.terjual }
                UrutBarang.TERMURAH -> list.sortedBy { it.harga }
                UrutBarang.TERMAHAL -> list.sortedByDescending { it.harga }
            }
        }
        adapter.submit(hasil)
        lvBarang.fitHeightToContent()
        tvEmpty.visibility = if (hasil.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun ListView.fitHeightToContent() {
        val adapter = adapter ?: return
        var total = 0
        val width = View.MeasureSpec.makeMeasureSpec(resources.displayMetrics.widthPixels, View.MeasureSpec.EXACTLY)
        for (i in 0 until adapter.count) {
            val row = adapter.getView(i, null, this)
            row.measure(width, View.MeasureSpec.UNSPECIFIED)
            total += row.measuredHeight
        }
        layoutParams = layoutParams.apply { height = total }
        requestLayout()
    }

    companion object {
        private const val ARG_TABLE = "table"

        fun newInstance(table: Int) = MenuFragment().apply {
            arguments = Bundle().apply { putInt(ARG_TABLE, table) }
        }
    }
}
