package kelompok.satu.kopiinggil

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
    // Total porsi yang pernah dipesan, diisi dari view menu_terlaris (bukan kolom menu_item).
    @Transient
    val terjual: Int = 0,
    // Diisi denganPromo(): harga di atas sudah harga promo, hargaNormal = harga sebelum diskon.
    @Transient
    val hargaNormal: Int = 0,
    @Transient
    val promo: Promo? = null,
) {
    val sedangPromo: Boolean
        get() = hargaNormal > harga

    val kategori: KategoriBarang
        get() = KategoriBarang.fromCategoryId(categoryId)
}

// Satu baris view menu_terlaris: jumlah porsi yang dipesan per menu (jumlah quantity di order_item).
@Serializable
data class MenuTerlaris(
    @SerialName("menu_item_id")
    val menuItemId: String,
    @SerialName("total_dipesan")
    val totalDipesan: Int = 0,
)

enum class UrutBarang(val labelRes: Int, val descRes: Int) {
    TERLARIS(R.string.menu_sort_popular, R.string.menu_sort_popular_desc),
    TERMURAH(R.string.menu_sort_cheapest, R.string.menu_sort_cheapest_desc),
    TERMAHAL(R.string.menu_sort_priciest, R.string.menu_sort_priciest_desc),
}

fun Int.toRupiah(): String = "Rp" + NumberFormat.getIntegerInstance(Locale.forLanguageTag("id-ID")).format(this)
