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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

// Tab Lihat menu: header, carousel promo, pencarian, kategori, dan daftar barang (ListView).
class MenuFragment : Fragment(R.layout.fragment_menu) {
    private lateinit var adapter: BarangAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var lvBarang: ListView
    private lateinit var rvPromo: RecyclerView
    private lateinit var llPromoDots: LinearLayout
    private val promoAdapter = PromoBannerAdapter { barang -> DetailMenuSheet(this, barang).show() }
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

        // Tombol + membuka popup yang sama dengan mengetuk menu, supaya pilihan wajib (mis. susu) selalu terisi.
        adapter = BarangAdapter(requireContext()) { barang -> DetailMenuSheet(this, barang).show() }
        tvEmpty = view.findViewById(R.id.tvEmpty)
        lvBarang = view.findViewById(R.id.lvBarang)
        lvBarang.adapter = adapter
        lvBarang.setOnItemClickListener { _, _, position, _ ->
            val barang = adapter.getItem(position)
            if (barang.isAvailable) DetailMenuSheet(this, barang).show()
            else Toast.makeText(requireContext(), getString(R.string.menu_sold_out_toast, barang.nama), Toast.LENGTH_SHORT).show()
        }

        rvPromo = view.findViewById(R.id.rvPromo)
        llPromoDots = view.findViewById(R.id.llPromoDots)
        setupPromoCarousel()

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
                    semuaBarang = data.filter { it.isActive }
                        .map { it.copy(terjual = terlaris[it.id] ?: 0) }
                        .denganPromo(loadPromos())
                    render()
                    renderPromo()
                }
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.toUserMessage(requireContext(), R.string.err_menu_load_failed), Toast.LENGTH_LONG).show()
            }
        }
    }

    // Promo yang sedang berlaku (RLS hanya mengembalikan yang aktif). Gagal / tabel belum ada = tanpa promo.
    private suspend fun loadPromos(): List<Promo> = try {
        supabase.from("promo").select().decodeList<Promo>()
    } catch (e: Exception) {
        android.util.Log.w("MenuFragment", "Promo tidak bisa dibaca", e)
        emptyList()
    }

    private fun setupPromoCarousel() {
        rvPromo.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        rvPromo.adapter = promoAdapter
        if (rvPromo.onFlingListener == null) PagerSnapHelper().attachToRecyclerView(rvPromo)
        rvPromo.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) renderPromoDots()
            }
        })
    }

    // Semua menu yang sedang promo (prioritas tertinggi dulu); kalau tidak ada, satu menu terlaris.
    private fun renderPromo() {
        val tersedia = semuaBarang.filter { it.isAvailable }
        val banner = tersedia.filter { it.sedangPromo }
            .sortedWith(compareByDescending<DataBarang> { it.promo?.priority ?: 0 }.thenByDescending { it.hargaNormal - it.harga })
            .ifEmpty { listOfNotNull(tersedia.maxByOrNull { it.terjual }) }
        promoAdapter.submit(banner)
        rvPromo.visibility = if (banner.isEmpty()) View.GONE else View.VISIBLE
        renderPromoDots()
    }

    private fun renderPromoDots() {
        val count = promoAdapter.itemCount
        llPromoDots.visibility = if (count > 1) View.VISIBLE else View.GONE
        if (count <= 1) return
        val current = (rvPromo.layoutManager as LinearLayoutManager).findFirstCompletelyVisibleItemPosition().coerceAtLeast(0)
        val density = resources.displayMetrics.density
        llPromoDots.removeAllViews()
        repeat(count) { i ->
            val active = i == current
            llPromoDots.addView(View(requireContext()).apply {
                setBackgroundResource(R.drawable.bg_badge)
                backgroundTintList = ContextCompat.getColorStateList(requireContext(), if (active) R.color.ngopi_primary else R.color.ngopi_border)
                layoutParams = LinearLayout.LayoutParams(((if (active) 18 else 6) * density).toInt(), (6 * density).toInt()).apply {
                    marginStart = (3 * density).toInt()
                    marginEnd = (3 * density).toInt()
                }
            })
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
                maxLines = 1
                setPadding((4 * dp).toInt(), 0, (4 * dp).toInt(), 0)
                setOnClickListener {
                    kategori = item
                    render()
                }
            }
            container.addView(
                chip,
                LinearLayout.LayoutParams(0, (33 * dp).toInt(), 1f).apply {
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
        }.sortedBy { !it.isAvailable } // menu habis selalu di bawah
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
