package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatButton
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch

class KasirBantuPesananActivity : AppCompatActivity() {

    private lateinit var tvSelectedTable: TextView
    private lateinit var btnOpenMenu: AppCompatButton
    private lateinit var etCustomerName: TextInputEditText
    private lateinit var etCustomerPhone: TextInputEditText
    private var tables = listOf<CafeTable>()
    private var selectedTable: CafeTable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_kasir_bantu_pesanan)
        applyEdgeToEdge(R.id.main)

        tvSelectedTable = findViewById(R.id.tvSelectedTableValue)
        btnOpenMenu = findViewById(R.id.btnOpenMenuAssisted)
        etCustomerName = findViewById(R.id.etAssistedCustomerName)
        etCustomerPhone = findViewById(R.id.etAssistedCustomerPhone)

        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<View>(R.id.cardSelectTable).setOnClickListener {
            showTablePickerDialog()
        }

        btnOpenMenu.setOnClickListener {
            val table = selectedTable
            if (table == null) {
                Toast.makeText(this, "Silakan pilih meja terlebih dahulu", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val customerName = etCustomerName.text?.toString()?.trim().orEmpty()
            val phoneDigits = etCustomerPhone.text?.toString().orEmpty()
                .filter(Char::isDigit).removePrefix("62").trimStart('0')
            if (customerName.length !in 2..100) {
                etCustomerName.error = "Isi nama pelanggan"
                etCustomerName.requestFocus()
                return@setOnClickListener
            }
            if (phoneDigits.length !in 8..13) {
                etCustomerPhone.error = "Nomor WhatsApp belum valid"
                etCustomerPhone.requestFocus()
                return@setOnClickListener
            }

            AssistedOrderSession.start(table.id, customerName, phoneDigits)

            val intent = Intent(this, HomeActivity::class.java).apply {
                putExtra(HomeActivity.EXTRA_TABLE, table.tableNumber)
                putExtra(HomeActivity.EXTRA_TABLE_ID, table.id)
                putExtra(HomeActivity.EXTRA_ASSISTED_ORDER, true)
            }
            startActivity(intent)
        }

        loadActiveTables()
    }

    private fun loadActiveTables() {
        lifecycleScope.launch {
            try {
                tables = supabase.postgrest["cafe_table"].select().decodeList<CafeTable>()
                    .filter { it.isActive }
                    .sortedBy { it.tableNumber }

                if (tables.isNotEmpty()) {
                    selectedTable = tables.first()
                    tvSelectedTable.text = selectedTable?.label ?: "Meja 01"
                } else {
                    tvSelectedTable.text = "Tidak ada meja aktif"
                }
            } catch (e: Exception) {
                tvSelectedTable.text = "Pilih Meja"
            }
        }
    }

    private fun showTablePickerDialog() {
        if (tables.isEmpty()) {
            Toast.makeText(this, "Memuat daftar meja...", Toast.LENGTH_SHORT).show()
            loadActiveTables()
            return
        }

        val tableLabels = tables.map { it.label }.toTypedArray()
        val currentIdx = tables.indexOfFirst { it.id == selectedTable?.id }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("Pilih Meja Pelanggan")
            .setSingleChoiceItems(tableLabels, currentIdx) { dialog, which ->
                selectedTable = tables[which]
                tvSelectedTable.text = selectedTable?.label
                dialog.dismiss()
            }
            .setNegativeButton("Batal", null)
            .show()
    }
}
