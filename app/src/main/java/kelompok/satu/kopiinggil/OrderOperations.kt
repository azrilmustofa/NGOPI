package kelompok.satu.kopiinggil

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

val PRODUCTION_STATUSES = listOf("PAID", "ACCEPTED", "PREPARING", "READY")
val RECEIPT_STATUSES = PRODUCTION_STATUSES + "COMPLETED"

@Serializable
data class OrderTransition(
    val status: String,
    @SerialName("updated_at") val updatedAt: String,
)

suspend fun transitionOrder(orderId: String, expectedStatus: String, status: String, note: String): OrderTransition =
    supabase.postgrest.rpc("transition_order", buildJsonObject {
        put("p_order_id", orderId)
        put("p_expected_status", expectedStatus)
        put("p_status", status)
        put("p_note", note)
    }).decodeAs<OrderTransition>()
