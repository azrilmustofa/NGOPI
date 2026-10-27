package kelompok.satu.kopiinggil

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StaffUser(
    @SerialName("uid")
    val uid: String = "",
    @SerialName("name")
    val name: String = "",
    @SerialName("role")
    val role: String = "",
    @SerialName("is_active")
    val isActive: Boolean = true,
)
