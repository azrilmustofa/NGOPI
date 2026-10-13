package kelompok.satu.kopiinggil

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.bottomnavigation.BottomNavigationView

class MenuActivity : AppCompatActivity() {
    private lateinit var adapter: BarangAdapter
    private lateinit var tvEmpty: TextView
    private lateinit var lvBarang: ListView
    private val chips = mutableMapOf<KategoriBarang, TextView>()
    private var kategori = KategoriBarang.SEMUA
    private var query = ""

    // Data sementara, nanti diganti dari database.
    private val semuaBarang = listOf(
        DataBarang("Es Kopi Susu", "Espresso, susu, gula aren", 24000, KategoriBarang.KOPI),
        DataBarang("Cappuccino", "Espresso, susu steamed", 28000, KategoriBarang.KOPI),
        DataBarang("Americano", "Espresso, air", 20000, KategoriBarang.KOPI),
        DataBarang("Matcha Latte", "Matcha, susu", 29000, KategoriBarang.NON_KOPI),
        DataBarang("Coklat Panas", "Coklat, susu steamed", 25000, KategoriBarang.NON_KOPI),
        DataBarang("Croissant", "Mentega, dipanggang hangat", 22000, KategoriBarang.MAKANAN),
        DataBarang("Roti Bakar Coklat", "Roti tebal, coklat, keju", 20000, KategoriBarang.MAKANAN),
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_menu)
        applyEdgeToEdge(R.id.main)

        val table = intent.getIntExtra(EXTRA_TABLE, 0)
        findViewById<TextView>(R.id.tvTableBadge).text =
            if (table > 0) getString(R.string.menu_table_badge, table) else getString(R.string.table_placeholder)

        adapter = BarangAdapter(this) { barang ->
            Toast.makeText(this, getString(R.string.menu_added, barang.nama), Toast.LENGTH_SHORT).show()
        }
        tvEmpty = findViewById(R.id.tvEmpty)
        lvBarang = findViewById(R.id.lvBarang)
        lvBarang.adapter = adapter

        buildChips(findViewById(R.id.categoryContainer))
        findViewById<EditText>(R.id.etSearch).doAfterTextChanged {
            query = it?.toString().orEmpty().trim()
            render()
        }
        render()

        findViewById<BottomNavigationView>(R.id.bottomNav).apply {
            selectedItemId = R.id.nav_menu
            setOnItemSelectedListener { item ->
                // TODO: buka layar Pesanan dan Bantuan setelah tersedia
                item.itemId == R.id.nav_menu
            }
        }
    }

    private fun buildChips(container: LinearLayout) {
        val dp = resources.displayMetrics.density
        KategoriBarang.entries.forEach { item ->
            val chip = TextView(this).apply {
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
            chip.setTextColor(ContextCompat.getColor(this, if (active) R.color.ngopi_surface else R.color.ngopi_ink))
        }
        val hasil = semuaBarang.filter {
            (kategori == KategoriBarang.SEMUA || it.kategori == kategori) &&
                (query.isEmpty() || it.nama.contains(query, true) || it.deskripsi.contains(query, true))
        }.take(MAX_POPULER)
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
        const val EXTRA_TABLE = "extra_table"
        private const val MAX_POPULER = 2
    }
}
