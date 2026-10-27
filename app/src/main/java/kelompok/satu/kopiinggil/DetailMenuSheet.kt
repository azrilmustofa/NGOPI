package kelompok.satu.kopiinggil

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

// Popup detail menu yang naik dari bawah: foto, deskripsi, pilihan (dari database), catatan, jumlah, dan total.
class DetailMenuSheet(private val fragment: Fragment, private val barang: DataBarang) {
    private val context = fragment.requireContext()
    private val dialog = BottomSheetDialog(context)
    private val sheet = fragment.layoutInflater.inflate(R.layout.sheet_detail_menu, null)

    private val opsiContainer = sheet.findViewById<LinearLayout>(R.id.opsiContainer)
    private val tvTotal = sheet.findViewById<TextView>(R.id.tvTotal)
    private val tvQty = sheet.findViewById<TextView>(R.id.tvQty)
    private val btnTambah = sheet.findViewById<View>(R.id.btnTambah)

    private var grup: List<GrupOpsi> = emptyList()
    private val terpilih = mutableMapOf<String, MutableSet<String>>() // id grup -> id opsi
    private val refreshers = mutableListOf<() -> Unit>()
    private var qty = 1
    private var loadJob: Job? = null

    fun show() {
        sheet.findViewById<ImageView>(R.id.ivFoto).loadFoto(barang.photoUrl, isDetail = true)
        sheet.findViewById<TextView>(R.id.tvNama).text = barang.nama
        sheet.findViewById<TextView>(R.id.tvHarga).text = barang.harga.toRupiah()
        sheet.findViewById<TextView>(R.id.tvDeskripsi).apply {
            text = barang.deskripsi
            visibility = if (barang.deskripsi.isBlank()) View.GONE else View.VISIBLE
        }
        sheet.findViewById<View>(R.id.btnClose).setOnClickListener { dialog.dismiss() }

        sheet.findViewById<View>(R.id.btnMinus).setOnClickListener { ubahJumlah(-1) }
        sheet.findViewById<View>(R.id.btnPlus).setOnClickListener { ubahJumlah(1) }
        btnTambah.setOnClickListener { tambahKeKeranjang() }
        updateRingkasan()

        dialog.setContentView(sheet)
        val tinggi = (context.resources.displayMetrics.heightPixels * TINGGI_SHEET).toInt()
        sheet.layoutParams = sheet.layoutParams.apply { height = tinggi }
        // Latar bawaan sheet dibuat transparan agar sudut atas yang membulat terlihat.
        (sheet.parent as? View)?.setBackgroundColor(Color.TRANSPARENT)
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
        }
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog.setOnDismissListener { loadJob?.cancel() }
        dialog.show()

        loadOpsi()
    }

    private fun loadOpsi() {
        loadJob = fragment.viewLifecycleOwner.lifecycleScope.launch {
            try {
                grup = supabase.from("menu_item_option_group")
                    .select(
                        Columns.raw(
                            "display_order, option_group(id, name, is_required, min_selection, max_selection, " +
                                "menu_option(id, name, price_delta_rp, is_available, display_order))",
                        ),
                    ) {
                        filter { eq("menu_item_id", barang.id) }
                        order("display_order", Order.ASCENDING)
                    }
                    .decodeList<MenuItemGrupOpsi>()
                    .map { it.grup.copy(opsi = it.grup.opsi.filter { o -> o.isAvailable }.sortedBy { o -> o.urutan }) }
                    .filter { it.opsi.isNotEmpty() }
                renderGrup()
            } catch (e: Exception) {
                Toast.makeText(context, R.string.detail_load_failed, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun renderGrup() {
        opsiContainer.removeAllViews()
        refreshers.clear()
        terpilih.clear()
        grup.forEach { g ->
            val pilihan = terpilih.getOrPut(g.id) { mutableSetOf() }
            // Grup wajib dengan satu pilihan langsung memilih opsi pertama.
            if (g.maksPilih <= 1 && g.minPilih >= 1) pilihan.add(g.opsi.first().id)

            val view = fragment.layoutInflater.inflate(R.layout.item_opsi_group, opsiContainer, false)
            view.findViewById<TextView>(R.id.tvGroupTitle).text = g.name
            view.findViewById<TextView>(R.id.tvGroupRule).text = aturan(g)
            val choices = view.findViewById<LinearLayout>(R.id.choiceContainer)
            g.opsi.forEach { choices.addView(buatPilihan(g, it, choices)) }
            opsiContainer.addView(view)
            if (g !== grup.last()) {
                opsiContainer.addView(garisPemisah())
            }
        }
        refreshers.forEach { it() }
        updateRingkasan()
    }

    private fun aturan(g: GrupOpsi): String {
        val status = context.getString(if (g.wajib) R.string.detail_required else R.string.detail_optional)
        return if (g.maksPilih <= 1) {
            context.getString(R.string.detail_rule_single, status)
        } else {
            context.getString(R.string.detail_rule_multi, g.maksPilih, status)
        }
    }

    private fun buatPilihan(g: GrupOpsi, opsi: OpsiMenu, parent: LinearLayout): View {
        val row = fragment.layoutInflater.inflate(R.layout.item_opsi_choice, parent, false)
        val ikon = row.findViewById<ImageView>(R.id.ivChoice)
        val tunggal = g.maksPilih <= 1
        row.findViewById<TextView>(R.id.tvChoiceLabel).text = opsi.name
        row.findViewById<TextView>(R.id.tvChoicePrice).text =
            if (opsi.selisihHarga == 0) {
                context.getString(R.string.detail_price_included)
            } else {
                context.getString(R.string.detail_price_extra, opsi.selisihHarga.toRupiah())
            }

        val refresh = {
            val aktif = opsi.id in terpilih.getValue(g.id)
            ikon.setImageResource(
                when {
                    tunggal && aktif -> R.drawable.ic_radio_on
                    tunggal -> R.drawable.ic_radio_off
                    aktif -> R.drawable.ic_check_box_on
                    else -> R.drawable.ic_check_box_off
                },
            )
            val warna = if (aktif) R.color.ngopi_primary else R.color.ngopi_muted
            ikon.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, warna))
        }
        refreshers.add(refresh)

        row.setOnClickListener {
            val pilihan = terpilih.getValue(g.id)
            when {
                tunggal -> {
                    // Pilihan opsional boleh dibatalkan dengan menekan lagi; yang wajib selalu terisi satu.
                    if (opsi.id in pilihan && g.minPilih < 1) pilihan.clear() else {
                        pilihan.clear()
                        pilihan.add(opsi.id)
                    }
                }
                opsi.id in pilihan -> pilihan.remove(opsi.id)
                pilihan.size >= g.maksPilih ->
                    Toast.makeText(context, context.getString(R.string.detail_max_choice, g.maksPilih), Toast.LENGTH_SHORT).show()
                else -> pilihan.add(opsi.id)
            }
            refreshers.forEach { it() }
            updateRingkasan()
        }
        return row
    }

    private fun garisPemisah() = View(context).apply {
        setBackgroundColor(ContextCompat.getColor(context, R.color.ngopi_border))
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 1)
    }

    private fun opsiDipilih(): List<OpsiDipilih> =
        grup.flatMap { g ->
            g.opsi.filter { it.id in terpilih[g.id].orEmpty() }
                .map { OpsiDipilih(it.id, g.name, it.name, it.selisihHarga) }
        }

    private fun hargaOpsi() = opsiDipilih().sumOf { it.selisihHarga }

    private fun lengkap() = grup.all { terpilih[it.id].orEmpty().size >= it.minPilih }

    private fun ubahJumlah(delta: Int) {
        qty = (qty + delta).coerceIn(1, MAKS_JUMLAH)
        updateRingkasan()
    }

    private fun updateRingkasan() {
        tvQty.text = qty.toString()
        tvTotal.text = ((barang.harga + hargaOpsi()) * qty).toRupiah()
        val siap = lengkap()
        btnTambah.isEnabled = siap
        btnTambah.alpha = if (siap) 1f else 0.5f
    }

    private fun tambahKeKeranjang() {
        if (!lengkap()) return
        val catatan = sheet.findViewById<EditText>(R.id.etCatatan).text?.toString().orEmpty().trim()
        CartManager.addItem(barang, qty, opsiDipilih(), catatan)
        (fragment.activity as? HomeActivity)?.updateCartBadge()
        Toast.makeText(context, context.getString(R.string.menu_added, barang.nama), Toast.LENGTH_SHORT).show()
        dialog.dismiss()
    }

    private companion object {
        const val TINGGI_SHEET = 0.92f
        const val MAKS_JUMLAH = 99
    }
}
