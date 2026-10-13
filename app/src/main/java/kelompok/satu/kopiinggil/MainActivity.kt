package kelompok.satu.kopiinggil

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.Window
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

class MainActivity : AppCompatActivity() {
    private lateinit var tvTableNumber: TextView
    private var loadingDialog: Dialog? = null
    private var tableNumber = 0

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val content = result.contents ?: return@registerForActivityResult
        val table = parseTableNumber(content)
        showLoading()
        findViewById<View>(R.id.main).postDelayed({
            hideLoading()
            if (isFinishing) return@postDelayed
            if (table == null) {
                if (!supportFragmentManager.isStateSaved) {
                    InvalidQrFragment().show(supportFragmentManager, InvalidQrFragment.TAG)
                }
            } else {
                tvTableNumber.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
                tableNumber = table
                tvTableNumber.text = String.format("%02d", table)
                findViewById<View>(R.id.ivTableVerified).visibility = View.VISIBLE
                findViewById<View>(R.id.btnOpenMenu).apply {
                    isEnabled = true
                    alpha = 1f
                }
            }
        }, LOADING_MS)
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

        supportFragmentManager.setFragmentResultListener(InvalidQrFragment.RESULT_KEY, this) { _, _ -> startScan() }

        tvTableNumber = findViewById(R.id.tvTableNumber)
        findViewById<View>(R.id.btnScan).setOnClickListener { startScan() }
        findViewById<View>(R.id.btnOpenMenu).apply {
            setOnClickListener {
                startActivity(Intent(this@MainActivity, HomeActivity::class.java).putExtra(HomeActivity.EXTRA_TABLE, tableNumber))
            }
            if (SKIP_QR_VALIDATION) {
                isEnabled = true
                alpha = 1f
            }
        }
    }

    private fun showLoading() {
        if (loadingDialog?.isShowing == true) return
        val progress = ProgressBar(this).apply {
            indeterminateTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.ngopi_primary))
        }
        loadingDialog = Dialog(this).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            setContentView(FrameLayout(context).apply {
                setPadding(48, 48, 48, 48)
                addView(progress)
            })
            setCancelable(false)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            show()
        }
    }

    private fun hideLoading() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    override fun onDestroy() {
        hideLoading()
        super.onDestroy()
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
        const val LOADING_MS = 1500L

        // Sementara true agar "Lihat menu" bisa dibuka tanpa scan QR. Set false untuk mengaktifkan validasi lagi.
        const val SKIP_QR_VALIDATION = true
    }
}
