package kelompok.satu.kopiinggil

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// Satu pilihan (mis. "Oat milk") dari tabel menu_option.
@Serializable
data class OpsiMenu(
    val id: String,
    val name: String,
    @SerialName("price_delta_rp")
    val selisihHarga: Int = 0,
    @SerialName("is_available")
    val isAvailable: Boolean = true,
    @SerialName("display_order")
    val urutan: Int = 0,
)

// Kelompok pilihan (mis. "Pilihan susu") dari tabel option_group beserta pilihannya.
@Serializable
data class GrupOpsi(
    val id: String,
    val name: String,
    @SerialName("is_required")
    val wajib: Boolean = false,
    @SerialName("min_selection")
    val minPilih: Int = 0,
    @SerialName("max_selection")
    val maksPilih: Int = 1,
    @SerialName("menu_option")
    val opsi: List<OpsiMenu> = emptyList(),
)

// Baris tabel penghubung menu_item_option_group dengan grup yang ikut di-embed.
@Serializable
data class MenuItemGrupOpsi(
    @SerialName("display_order")
    val urutan: Int = 0,
    @SerialName("option_group")
    val grup: GrupOpsi,
)
