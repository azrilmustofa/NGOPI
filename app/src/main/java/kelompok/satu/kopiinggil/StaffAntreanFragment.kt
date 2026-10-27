package kelompok.satu.kopiinggil

import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
private data class StaffOrderDto(
    val id: String = "",
    @SerialName("public_id") val publicId: String = "",
    @SerialName("table_id") val tableId: String = "",
    val status: String = "",
    @SerialName("total_rp") val totalRp: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class StaffOrderItemDto(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("name_snapshot") val nameSnapshot: String = "",
    val quantity: Int = 1,
    val note: String? = null,
)

class StaffAntreanFragment : Fragment(R.layout.fragment_staff_antrean) {
    private lateinit var tvCountBaru: TextView
    private lateinit var tvCountDibuat: TextView
    private lateinit var tvCountSiap: TextView
    private lateinit var tvSyncText: TextView
    private lateinit var llQueueContainer: LinearLayout

    private lateinit var chipSemua: TextView
    private lateinit var chipBaru: TextView
    private lateinit var chipDibuat: TextView
    private lateinit var chipSiap: TextView

    private var selectedFilter = "SEMUA"
    private var allOrders = listOf<StaffOrderData>()

    private data class StaffOrderData(
        val dto: StaffOrderDto,
        val tableLabel: String,
        val itemsSummary: String,
    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvCountBaru = view.findViewById(R.id.tvCountBaru)
        tvCountDibuat = view.findViewById(R.id.tvCountDibuat)
        tvCountSiap = view.findViewById(R.id.tvCountSiap)
        tvSyncText = view.findViewById(R.id.tvStaffSyncText)
        llQueueContainer = view.findViewById(R.id.llStaffQueueContainer)

        chipSemua = view.findViewById(R.id.chipFilterSemua)
        chipBaru = view.findViewById(R.id.chipFilterBaru)
        chipDibuat = view.findViewById(R.id.chipFilterDibuat)
        chipSiap = view.findViewById(R.id.chipFilterSiap)

        setupFilterChips()
        fetchOrdersFromSupabase()
    }

    private fun setupFilterChips() {
        val chips = listOf(
            chipSemua to "SEMUA",
            chipBaru to "BARU",
            chipDibuat to "DIBUAT",
            chipSiap to "SIAP",
        )

        chips.forEach { (chip, filterKey) ->
            chip.setOnClickListener {
                selectedFilter = filterKey
                updateChipUi()
                renderList()
            }
        }
    }

    private fun updateChipUi() {
        val activeBg = R.drawable.bg_chip_active
        val inactiveBg = R.drawable.bg_chip
        val activeColor = ContextCompat.getColor(requireContext(), R.color.white)
        val inactiveColor = ContextCompat.getColor(requireContext(), R.color.ngopi_ink)

        fun TextView.setActive(active: Boolean) {
            setBackgroundResource(if (active) activeBg else inactiveBg)
            setTextColor(if (active) activeColor else inactiveColor)
        }

        chipSemua.setActive(selectedFilter == "SEMUA")
        chipBaru.setActive(selectedFilter == "BARU")
        chipDibuat.setActive(selectedFilter == "DIBUAT")
        chipSiap.setActive(selectedFilter == "SIAP")
    }

    private fun fetchOrdersFromSupabase() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // Ambil daftar meja untuk pemetaan label
                val tables: List<CafeTable> = try {
                    supabase.postgrest["cafe_table"].select().decodeList()
                } catch (_: Exception) {
                    emptyList()
                }
                val tableMap = tables.associateBy { it.id }

                // Ambil daftar pesanan dari customer_order
                val orders: List<StaffOrderDto> = try {
                    supabase.postgrest["customer_order"].select().decodeList()
                } catch (_: Exception) {
                    emptyList()
                }

                // Ambil item pesanan dari order_item
                val itemsMap: Map<String, List<StaffOrderItemDto>> = try {
                    val allItems: List<StaffOrderItemDto> = supabase.postgrest["order_item"].select().decodeList()
                    allItems.groupBy { it.orderId }
                } catch (_: Exception) {
                    emptyMap()
                }

                allOrders = orders.map { orderDto ->
                    val tableObj = tableMap[orderDto.tableId]
                    val tableLabel = tableObj?.label ?: if (orderDto.tableId.isNotEmpty()) orderDto.tableId else "Meja --"
                    val items = itemsMap[orderDto.id].orEmpty()
                    val summary = if (items.isNotEmpty()) {
                        "${items.sumOf { it.quantity }} item · " + items.joinToString(", ") { "${it.quantity}× ${it.nameSnapshot}" }
                    } else {
                        "Belum ada rincian item"
                    }
                    StaffOrderData(orderDto, tableLabel, summary)
                }

                updateMetrics()
                renderList()

                val time = SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(Date())
                tvSyncText.text = getString(R.string.app_name) + " · Sinkron " + time
            } catch (e: Exception) {
                Toast.makeText(requireContext(), "Gagal memuat antrean: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateMetrics() {
        val baruCount = allOrders.count { it.dto.status.equals("PAID", true) || it.dto.status.equals("BARU", true) }
        val dibuatCount = allOrders.count { it.dto.status.equals("BREWING", true) || it.dto.status.equals("DIBUAT", true) }
        val siapCount = allOrders.count { it.dto.status.equals("READY", true) || it.dto.status.equals("SIAP", true) }

        tvCountBaru.text = String.format(Locale.getDefault(), "%02d", baruCount)
        tvCountDibuat.text = String.format(Locale.getDefault(), "%02d", dibuatCount)
        tvCountSiap.text = String.format(Locale.getDefault(), "%02d", siapCount)
    }

    private fun renderList() {
        llQueueContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        val filtered = allOrders.filter { data ->
            when (selectedFilter) {
                "BARU" -> data.dto.status.equals("PAID", true) || data.dto.status.equals("BARU", true)
                "DIBUAT" -> data.dto.status.equals("BREWING", true) || data.dto.status.equals("DIBUAT", true)
                "SIAP" -> data.dto.status.equals("READY", true) || data.dto.status.equals("SIAP", true)
                else -> true
            }
        }

        if (filtered.isEmpty()) {
            val emptyTv = TextView(requireContext()).apply {
                text = "Belum ada antrean pesanan"
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.ngopi_muted))
                setPadding(0, 32, 0, 32)
                gravity = android.view.Gravity.CENTER
            }
            llQueueContainer.addView(emptyTv)
            return
        }

        filtered.forEach { data ->
            val row = inflater.inflate(R.layout.item_staff_antrean, llQueueContainer, false)
            val tvOrderNumber = row.findViewById<TextView>(R.id.tvOrderNumber)
            val tvOrderMeta = row.findViewById<TextView>(R.id.tvOrderMeta)
            val tvOrderStatusBadge = row.findViewById<TextView>(R.id.tvOrderStatusBadge)
            val tvOrderItemsSummary = row.findViewById<TextView>(R.id.tvOrderItemsSummary)

            tvOrderNumber.text = "#${data.dto.publicId.ifEmpty { data.dto.id.take(8) }}"
            tvOrderMeta.text = "${data.tableLabel} · ${formatTime(data.dto.createdAt)}"
            tvOrderItemsSummary.text = data.itemsSummary

            when {
                data.dto.status.equals("PAID", true) || data.dto.status.equals("BARU", true) -> {
                    tvOrderStatusBadge.text = "BARU"
                    tvOrderStatusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ngopi_primary))
                    tvOrderStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                }
                data.dto.status.equals("BREWING", true) || data.dto.status.equals("DIBUAT", true) -> {
                    tvOrderStatusBadge.text = "DIBUAT"
                    tvOrderStatusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ngopi_accent))
                    tvOrderStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.ngopi_ink))
                }
                data.dto.status.equals("READY", true) || data.dto.status.equals("SIAP", true) -> {
                    tvOrderStatusBadge.text = "SIAP"
                    tvOrderStatusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ngopi_primary_light))
                    tvOrderStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                }
                else -> {
                    tvOrderStatusBadge.text = data.dto.status.uppercase()
                    tvOrderStatusBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ngopi_border))
                    tvOrderStatusBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.ngopi_muted))
                }
            }

            row.setOnClickListener {
                Toast.makeText(requireContext(), "Pesanan #${data.dto.publicId} di ${data.tableLabel}", Toast.LENGTH_SHORT).show()
            }

            llQueueContainer.addView(row)
        }
    }

    private fun formatTime(isoString: String?): String {
        if (isoString.isNullOrBlank()) return SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(Date())
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val clean = isoString.substringBefore(".").substringBefore("+").substringBefore("Z")
            val date = parser.parse(clean)
            val formatter = SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).apply {
                timeZone = TimeZone.getDefault()
            }
            formatter.format(date ?: Date())
        } catch (_: Exception) {
            SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(Date())
        }
    }
}
