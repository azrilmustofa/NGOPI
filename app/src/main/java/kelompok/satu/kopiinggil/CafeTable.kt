package kelompok.satu.kopiinggil

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CafeTable(
    @SerialName("id")
    val id: String = "",
    @SerialName("label")
    val label: String = "",
    @SerialName("is_active")
    val isActive: Boolean = true,
) {
    // Mengambil nomor meja sebagai integer dari label (misal "Meja 07" -> 7) atau id (misal "TBL-07" -> 7)
    val tableNumber: Int
        get() {
            val digits = Regex("\\d+").find(label)?.value ?: Regex("\\d+").find(id)?.value
            return digits?.toIntOrNull() ?: 0
        }
}
