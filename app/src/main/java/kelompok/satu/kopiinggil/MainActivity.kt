package kelompok.satu.kopiinggil

import android.app.Dialog
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.widget.Toast
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
import androidx.lifecycle.lifecycleScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

class MainActivity : AppCompatActivity() {
    private lateinit var tvTableNumber: TextView
    private var loadingDialog: Dialog? = null
    private var tableNumber = 0
    private var tableId = ""

    private fun openHome() {
        startActivity(
            Intent(this, HomeActivity::class.java)
                .putExtra(HomeActivity.EXTRA_TABLE, tableNumber)
                .putExtra(HomeActivity.EXTRA_TABLE_ID, tableId),
        )
    }

    private val scanLauncher = registerForActivityResult(ScanContract()) { result ->
        val content = result.contents ?: return@registerForActivityResult
        showLoading()
        lifecycleScope.launch {
            // Gagal memeriksa (koneksi/server) berbeda dengan QR tidak berlaku: jangan tampilkan layar QR tidak berlaku.
            val table = try {
                validateTableWithSupabase(content)
            } catch (e: Exception) {
                hideLoading()
                Toast.makeText(this@MainActivity, e.toUserMessage(this@MainActivity, R.string.err_table_check_failed), Toast.LENGTH_LONG).show()
                return@launch
            }
            hideLoading()
            if (isFinishing) return@launch
            if (table == null || !table.isActive) {
                if (!supportFragmentManager.isStateSaved) {
                    InvalidQrFragment.newInstance(content)
                        .show(supportFragmentManager, InvalidQrFragment.TAG)
                }
            } else {
                tableNumber = table.tableNumber
                tableId = table.id
                tvTableNumber.setTextSize(TypedValue.COMPLEX_UNIT_SP, 52f)
                tvTableNumber.text = String.format(java.util.Locale.getDefault(), "%02d", tableNumber)
                findViewById<View>(R.id.ivTableVerified).visibility = View.VISIBLE
                findViewById<View>(R.id.btnOpenMenu).apply {
                    isEnabled = true
                    alpha = 1f
                }
                // Otomatis berpindah ke HomeActivity (MenuFragment) dengan membawa nomor meja yang terverifikasi
                openHome()
            }
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
        initializePushNotifications()

        supportFragmentManager.setFragmentResultListener(InvalidQrFragment.RESULT_KEY, this) { _, _ -> startScan() }

        tvTableNumber = findViewById(R.id.tvTableNumber)
        findViewById<View>(R.id.btnScan).setOnClickListener { startScan() }
        findViewById<View>(R.id.btnOpenMenu).apply {
            setOnClickListener { openHome() }
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

    // null = QR tidak cocok dengan meja mana pun (layar QR tidak berlaku). Error koneksi/server dilempar ke pemanggil.
    private suspend fun validateTableWithSupabase(content: String): CafeTable? {
        return supabase.postgrest.rpc(
            "resolve_table_qr",
            buildJsonObject { put("p_code", content.trim()) },
        ).decodeList<CafeTable>().singleOrNull()
    }

    private companion object {
        // Set false untuk mengaktifkan validasi QR melalui Supabase
        const val SKIP_QR_VALIDATION = false
    }
}
