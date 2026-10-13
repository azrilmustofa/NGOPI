package kelompok.satu.kopiinggil

import java.text.NumberFormat
import java.util.Locale

enum class KategoriBarang(val label: String) {
    SEMUA("Semua"),
    KOPI("Kopi"),
    NON_KOPI("Non-kopi"),
    MAKANAN("Makanan"),
}

data class DataBarang(
    val nama: String,
    val deskripsi: String,
    val harga: Int,
    val kategori: KategoriBarang,
)

fun Int.toRupiah(): String = "Rp" + NumberFormat.getIntegerInstance(Locale("id", "ID")).format(this)
