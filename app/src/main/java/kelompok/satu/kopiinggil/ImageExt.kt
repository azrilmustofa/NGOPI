package kelompok.satu.kopiinggil

import android.widget.ImageView
import coil.dispose
import coil.load

// Muat foto dari url secara konsisten dengan ukuran dan scaleType tetap.
fun ImageView.loadFoto(url: String?, isDetail: Boolean = false) {
    dispose()
    setPadding(0, 0, 0, 0)
    imageTintList = null
    scaleType = ImageView.ScaleType.CENTER_CROP

    val placeholderRes = if (isDetail) R.drawable.drawable_detail_placeholder else R.drawable.drawable_menu_placeholder

    if (url.isNullOrBlank()) {
        setImageResource(placeholderRes)
        return
    }

    load(url) {
        crossfade(true)
        placeholder(placeholderRes)
        error(placeholderRes)
    }
}
