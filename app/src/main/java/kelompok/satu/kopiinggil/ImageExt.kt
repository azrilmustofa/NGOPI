package kelompok.satu.kopiinggil

import android.content.res.ColorStateList
import android.widget.ImageView
import androidx.core.content.ContextCompat
import coil.dispose
import coil.load

// Muat foto dari url; jika kosong atau gagal dimuat, tampil ikon kopi sebagai placeholder.
fun ImageView.loadFoto(url: String?, placeholderPaddingDp: Int) {
    dispose()
    showPlaceholder(placeholderPaddingDp)
    if (url.isNullOrBlank()) return
    load(url) {
        crossfade(true)
        listener(onSuccess = { _, _ ->
            setPadding(0, 0, 0, 0)
            imageTintList = null
        })
    }
}

private fun ImageView.showPlaceholder(paddingDp: Int) {
    val pad = (paddingDp * resources.displayMetrics.density).toInt()
    setPadding(pad, pad, pad, pad)
    imageTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.ngopi_primary))
    setImageResource(R.drawable.ic_coffee)
}
