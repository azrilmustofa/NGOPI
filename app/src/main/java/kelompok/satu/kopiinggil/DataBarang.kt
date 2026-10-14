package kelompok.satu.kopiinggil

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.NumberFormat
import java.util.Locale

enum class KategoriBarang(val catId: String, val label: String) {
    SEMUA("", "Semua"),
    KOPI("CAT-01", "Kopi"),
    NON_KOPI("CAT-02", "Non-kopi"),
    MAKANAN("CAT-03", "Makanan");

    companion object {
        fun fromCategoryId(id: String): KategoriBarang = when (id) {
            KOPI.catId -> KOPI
            NON_KOPI.catId -> NON_KOPI
            MAKANAN.catId -> MAKANAN
            else -> SEMUA
        }
    }
}

@Serializable
data class DataBarang(
    val id: String = "",
    @SerialName("category_id")
    val categoryId: String = "",
    @SerialName("name")
    val nama: String = "",
    @SerialName("description")
    val deskripsi: String = "",
    @SerialName("base_price_rp")
    val harga: Int = 0,
    @SerialName("photo_url")
    val photoUrl: String? = null,
    @SerialName("allergy_info")
    val allergyInfo: String? = null,
    @SerialName("is_available")
    val isAvailable: Boolean = true,
    @SerialName("is_active")
    val isActive: Boolean = true,
    @SerialName("created_at")
    val createdAt: String? = null,
) {
    val kategori: KategoriBarang
        get() = KategoriBarang.fromCategoryId(categoryId)
}

fun Int.toRupiah(): String = "Rp" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID")).format(this)
