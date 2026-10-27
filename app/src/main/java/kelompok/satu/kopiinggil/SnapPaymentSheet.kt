package kelompok.satu.kopiinggil

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.view.KeyEvent
import android.view.View
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// Pop-up pembayaran Midtrans (Snap) di atas layar Checkout, tanpa pindah halaman.
// Status lunas ditentukan server: selama pop-up terbuka, app memeriksa status tiap beberapa detik dan menutup
// pop-up sendiri begitu lunas atau gagal. onResult: PAID, FAILED, atau PENDING (pop-up ditutup pengguna).
class SnapPaymentSheet(
    private val activity: AppCompatActivity,
    private val url: String,
    private val onResult: (PaymentState) -> Unit,
) {
    private var pollJob: Job? = null
    private var result: PaymentState? = null

    @SuppressLint("SetJavaScriptEnabled")
    fun show() {
        val dialog = BottomSheetDialog(activity)
        val sheet = activity.layoutInflater.inflate(R.layout.sheet_snap_payment, null)
        val progress = sheet.findViewById<ProgressBar>(R.id.progressSnap)
        val web = sheet.findViewById<WebView>(R.id.webSnap)

        web.settings.javaScriptEnabled = true
        web.settings.domStorageEnabled = true
        // Tombol "Download QR" di halaman Midtrans: simpan ke Galeri supaya bisa dibuka dari aplikasi e-wallet.
        web.enableDownloads(activity)
        web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                view?.keepBlobsForDownload()
            }

            // Aplikasi bank / dompet digital (skema selain http) dibuka lewat intent, bukan di WebView.
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                if (uri.scheme == "http" || uri.scheme == "https") return false
                try {
                    activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri.toString())))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(activity, R.string.snap_app_missing, Toast.LENGTH_SHORT).show()
                }
                return true
            }
        }
        sheet.findViewById<View>(R.id.btnSnapClose).setOnClickListener { dialog.dismiss() }

        dialog.setContentView(sheet)
        // Latar bawaan sheet dibuat transparan agar sudut atas yang membulat terlihat.
        (sheet.parent as? View)?.setBackgroundColor(android.graphics.Color.TRANSPARENT)
        sheet.layoutParams = sheet.layoutParams.apply {
            height = (activity.resources.displayMetrics.heightPixels * SHEET_HEIGHT_RATIO).toInt()
        }
        dialog.behavior.apply {
            state = BottomSheetBehavior.STATE_EXPANDED
            skipCollapsed = true
            // Geser ke bawah dimatikan supaya tidak bentrok dengan scroll halaman pembayaran; tutup lewat tombol.
            isDraggable = false
        }
        // Tombol Back: mundur di halaman pembayaran dulu, baru menutup pop-up.
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (web.canGoBack()) web.goBack() else dialog.dismiss()
                true
            } else false
        }
        dialog.setOnDismissListener {
            pollJob?.cancel()
            web.destroy()
            onResult(result ?: PaymentState.PENDING)
        }

        web.loadUrl(url)
        dialog.show()
        startPolling(dialog)
    }

    private fun startPolling(dialog: BottomSheetDialog) {
        pollJob = activity.lifecycleScope.launch {
            val expiresAt = OrderManager.pendingPayment?.expiresAtMillis ?: return@launch
            while (System.currentTimeMillis() < expiresAt + EXPIRY_GRACE_MS) {
                delay(POLL_INTERVAL_MS)
                val state = try {
                    OrderManager.checkPayment()
                } catch (_: Exception) {
                    PaymentState.PENDING // jaringan putus sebentar: coba lagi di putaran berikut
                }
                if (state != PaymentState.PENDING) {
                    result = state
                    dialog.dismiss()
                    return@launch
                }
            }
            result = PaymentState.FAILED
            dialog.dismiss()
        }
    }

    private companion object {
        const val SHEET_HEIGHT_RATIO = 0.92f
        const val POLL_INTERVAL_MS = 3_000L
        const val EXPIRY_GRACE_MS = 30_000L
    }
}
