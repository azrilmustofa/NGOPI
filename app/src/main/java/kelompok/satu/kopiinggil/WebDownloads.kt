package kelompok.satu.kopiinggil

import android.annotation.SuppressLint
import android.app.Activity
import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Base64
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.URLUtil
import android.webkit.WebView
import android.widget.Toast

// WebView bawaan tidak menangani unduhan. Ini membuat tombol "Download QR" di halaman pembayaran berfungsi:
// gambar disimpan ke Galeri (Pictures/NGOPI) supaya bisa dibuka dari aplikasi e-wallet di HP yang sama.
// Tekan lama gambar (mis. QR) juga didukung, seperti di browser.
@SuppressLint("JavascriptInterface")
fun WebView.enableDownloads(activity: Activity) {
    val saver = DownloadSaver(activity)

    // Tekan lama gambar: alamat http(s) disalin (bisa ditempel ke simulator Midtrans),
    // gambar data/blob disimpan ke Galeri. Selain gambar, tekan lama tetap memilih teks seperti biasa.
    setOnLongClickListener {
        val hit = hitTestResult
        val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE ||
            hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
        val src = hit.extra
        android.util.Log.d("WebDownloads", "tekan lama type=${hit.type} src=${src?.substringBefore(',')?.take(120)}")
        when {
            !isImage || src.isNullOrBlank() -> false
            src.startsWith("http") -> {
                (activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                    .setPrimaryClip(ClipData.newPlainText("Alamat gambar", src))
                Toast.makeText(activity, R.string.image_url_copied, Toast.LENGTH_LONG).show()
                true
            }
            src.startsWith("data:") -> {
                saver.saveDataUrl(src)
                true
            }
            src.startsWith("blob:") -> {
                evaluateJavascript(readBlobJs(src), null)
                true
            }
            else -> false
        }
    }
    addJavascriptInterface(saver, "NgopiDownload")
    setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
        android.util.Log.d("WebDownloads", "unduh ${url.substringBefore(',').take(80)} mime=$mimeType")
        when {
            url.startsWith("data:") -> saver.saveDataUrl(url)
            // blob: hanya bisa dibaca dari dalam halaman, jadi diubah jadi data URL lewat JavaScript.
            url.startsWith("blob:") -> evaluateJavascript(readBlobJs(url), null)
            else -> saver.enqueue(url, userAgent, contentDisposition, mimeType)
        }
    }
}

// Dipanggil setiap halaman selesai dimuat. Halaman Midtrans langsung menghapus blob QR setelah memicu unduhan,
// jadi blob disimpan di halaman sejak dibuat dan penghapusannya ditunda supaya masih bisa dibaca app.
fun WebView.keepBlobsForDownload() {
    evaluateJavascript(
        "(function(){if(window.__ngopiBlobs)return;window.__ngopiBlobs={};" +
            "var c=URL.createObjectURL.bind(URL),r=URL.revokeObjectURL.bind(URL);" +
            "URL.createObjectURL=function(o){var u=c(o);try{if(o instanceof Blob)window.__ngopiBlobs[u]=o}catch(e){}return u};" +
            "URL.revokeObjectURL=function(u){setTimeout(function(){r(u);delete window.__ngopiBlobs[u]},60000)};})()",
        null,
    )
}

// Membaca blob (dari simpanan di atas, atau langsung kalau masih ada) lalu mengirimnya ke app sebagai data URL.
private fun readBlobJs(url: String): String {
    val safe = url.replace("\\", "").replace("'", "")
    return "(function(u){function send(b){var f=new FileReader();" +
        "f.onloadend=function(){NgopiDownload.saveDataUrl(f.result)};f.onerror=function(){NgopiDownload.fail('reader')};" +
        "f.readAsDataURL(b)}" +
        "var k=window.__ngopiBlobs&&window.__ngopiBlobs[u];if(k){send(k);return}" +
        "var x=new XMLHttpRequest();x.open('GET',u,true);x.responseType='blob';" +
        "x.onload=function(){send(x.response)};x.onerror=function(){NgopiDownload.fail('blob sudah dihapus halaman')};" +
        "x.send();})('$safe')"
}

private class DownloadSaver(private val activity: Activity) {

    // Dipanggil dari JavaScript (thread lain), jadi dialihkan ke UI thread.
    @JavascriptInterface
    fun saveDataUrl(dataUrl: String) {
        activity.runOnUiThread { save(dataUrl) }
    }

    @JavascriptInterface
    fun fail(reason: String) {
        android.util.Log.e("WebDownloads", "Gagal membaca unduhan: $reason")
        activity.runOnUiThread { Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show() }
    }

    private fun save(dataUrl: String) {
        try {
            val header = dataUrl.substringBefore(',')
            val mime = header.removePrefix("data:").substringBefore(';').ifBlank { "image/png" }
            val bytes = if (header.contains(";base64")) {
                Base64.decode(dataUrl.substringAfter(','), Base64.DEFAULT)
            } else {
                Uri.decode(dataUrl.substringAfter(',')).toByteArray()
            }
            val isImage = mime.startsWith("image/")
            val ext = when (mime) {
                "image/jpeg" -> "jpg"
                "image/svg+xml" -> "svg"
                else -> mime.substringAfter('/').ifBlank { "png" }
            }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, "ngopi-${System.currentTimeMillis()}.$ext")
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (isImage) "Pictures/NGOPI" else "Download/NGOPI")
            }
            val collection = if (isImage) MediaStore.Images.Media.EXTERNAL_CONTENT_URI else MediaStore.Downloads.EXTERNAL_CONTENT_URI
            val resolver = activity.contentResolver
            val uri = resolver.insert(collection, values) ?: error("Gagal membuat file")
            resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Gagal menulis file")
            Toast.makeText(
                activity,
                if (isImage) R.string.download_saved_image else R.string.download_saved_file,
                Toast.LENGTH_LONG,
            ).show()
        } catch (e: Exception) {
            android.util.Log.e("WebDownloads", "Gagal menyimpan unduhan", e)
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }

    // Unduhan berupa URL biasa diserahkan ke DownloadManager (muncul di notifikasi).
    fun enqueue(url: String, userAgent: String?, contentDisposition: String?, mimeType: String?) {
        try {
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            val dir = if (mimeType?.startsWith("image/") == true) Environment.DIRECTORY_PICTURES else Environment.DIRECTORY_DOWNLOADS
            val request = DownloadManager.Request(Uri.parse(url))
                .setMimeType(mimeType)
                .addRequestHeader("User-Agent", userAgent)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(dir, "NGOPI/$fileName")
            CookieManager.getInstance().getCookie(url)?.let { request.addRequestHeader("Cookie", it) }
            (activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
            Toast.makeText(activity, R.string.download_started, Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            android.util.Log.e("WebDownloads", "Gagal memulai unduhan", e)
            Toast.makeText(activity, R.string.download_failed, Toast.LENGTH_SHORT).show()
        }
    }
}
