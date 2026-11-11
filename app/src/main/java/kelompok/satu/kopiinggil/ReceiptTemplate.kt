package kelompok.satu.kopiinggil

import kotlinx.serialization.Serializable

@Serializable
data class ReceiptTemplate(
    val id: String,
    val title: String,
    val header: String,
    val footer: String,
)
