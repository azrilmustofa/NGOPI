package kelompok.satu.kopiinggil

import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {
    private lateinit var tvTableNumber: TextView

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val content = result.contents ?: return@registerForActivityResult
        val table = parseTableNumber(content)
        if (table == null) {
            findViewById<View>(R.id.main).postDelayed({
                if (!isFinishing && !supportFragmentManager.isStateSaved) {
                    InvalidQrSheet().show(supportFragmentManager, InvalidQrSheet.TAG)
                }
            }, SHEET_DELAY_MS)
        } else {
            tvTableNumber.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
            tvTableNumber.text = String.format("%02d", table)
            findViewById<View>(R.id.ivTableVerified).visibility = View.VISIBLE
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        supportFragmentManager.setFragmentResultListener(InvalidQrSheet.RESULT_KEY, this) { _, _ -> startScan() }

        tvTableNumber = findViewById(R.id.tvTableNumber)
        findViewById<View>(R.id.btnScan).setOnClickListener { startScan() }
    }

    private fun startScan() {
        scanLauncher.launch(
            ScanOptions()
                .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt(getString(R.string.scan_prompt))
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }

    // Menerima isi QR seperti "7", "07", "Meja 7", "meja-07", atau URL berisi ?table=7 / ?meja=7.
    private fun parseTableNumber(content: String): Int? {
        val text = content.trim()
        val labelled = Regex("(?i)(?:table|meja|no)[^0-9]{0,3}(\\d{1,3})").find(text)
        val digits = labelled?.groupValues?.get(1) ?: text.takeIf { it.matches(Regex("\\d{1,3}")) }
        return digits?.toIntOrNull()?.takeIf { it > 0 }
    }

    private companion object {
        const val SHEET_DELAY_MS = 350L
    }
}
