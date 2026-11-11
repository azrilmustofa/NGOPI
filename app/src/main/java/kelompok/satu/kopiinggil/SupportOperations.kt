package kelompok.satu.kopiinggil

import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

@Serializable
data class SupportRequest(
    val id: String,
    @SerialName("order_id") val orderId: String? = null,
    @SerialName("public_id") val publicId: String? = null,
    @SerialName("table_id") val tableId: String,
    @SerialName("table_label") val tableLabel: String,
    val topic: String,
    val status: String,
    @SerialName("repeat_count") val repeatCount: Int = 1,
    @SerialName("created_at") val createdAt: String,
    @SerialName("last_requested_at") val lastRequestedAt: String,
    @SerialName("acknowledged_at") val acknowledgedAt: String? = null,
    @SerialName("resolved_at") val resolvedAt: String? = null,
    @SerialName("payment_status") val paymentStatus: String? = null,
    @SerialName("order_created_at") val orderCreatedAt: String? = null,
    @SerialName("total_rp") val totalRp: Int? = null,
    val merged: Boolean = false,
    @SerialName("cooldown_seconds") val cooldownSeconds: Int = 0,
)

fun SupportRequest.topicTitle(): String = when (topic) {
    "QR" -> "Masalah QR meja"
    "PAYMENT" -> "Bantuan pembayaran"
    "ORDER" -> "Pesanan atau alergi"
    "RECEIPT" -> "Nomor WhatsApp atau struk"
    else -> "Bantuan di meja"
}

suspend fun requestSupport(topic: String, tableId: String, orderPublicId: String? = null): SupportRequest =
    supabase.postgrest.rpc("request_support", buildJsonObject {
        put("p_topic", topic)
        put("p_table_id", tableId)
        if (orderPublicId == null) put("p_order_public_id", JsonNull)
        else put("p_order_public_id", orderPublicId.trimStart('#'))
    }).decodeAs()

suspend fun requestInvalidQrSupport(qrCode: String): SupportRequest =
    supabase.postgrest.rpc("request_invalid_qr_support", buildJsonObject {
        put("p_code", qrCode.trim().take(500))
    }).decodeAs()

suspend fun loadMySupportRequest(tableId: String): SupportRequest? =
    supabase.postgrest.rpc("get_my_support_request", buildJsonObject {
        put("p_table_id", tableId)
    }).decodeAs()

suspend fun loadActiveSupportRequests(): List<SupportRequest> =
    supabase.postgrest.rpc("list_active_support_requests").decodeAs()

suspend fun loadSupportRequest(requestId: String): SupportRequest =
    supabase.postgrest.rpc("get_support_request", buildJsonObject {
        put("p_request_id", requestId)
    }).decodeAs()

suspend fun updateSupportRequest(requestId: String, expectedStatus: String, status: String): SupportRequest =
    supabase.postgrest.rpc("update_support_request", buildJsonObject {
        put("p_request_id", requestId)
        put("p_expected_status", expectedStatus)
        put("p_status", status)
    }).decodeAs()

@Serializable
data class RefundResult(
    val id: String,
    @SerialName("order_id") val orderId: String,
    @SerialName("order_status") val orderStatus: String,
    @SerialName("refund_status") val refundStatus: String,
    @SerialName("amount_rp") val amountRp: Int,
    @SerialName("provider_reference") val providerReference: String? = null,
    @SerialName("attempt_count") val attemptCount: Int = 0,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("refunded_at") val refundedAt: String? = null,
)

suspend fun loadOrderRefund(orderId: String): RefundResult? =
    supabase.postgrest.rpc("get_order_refund", buildJsonObject {
        put("p_order_id", orderId)
    }).decodeAs()
