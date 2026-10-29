package kelompok.satu.kopiinggil

import android.app.DatePickerDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
private data class HistoryOrderDto(
    val id: String = "",
    @SerialName("public_id") val publicId: String = "",
    @SerialName("table_id") val tableId: String = "",
    val status: String = "",
    @SerialName("total_rp") val totalRp: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("paid_at") val paidAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
private data class HistoryOrderItemDto(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("name_snapshot") val nameSnapshot: String = "",
    val quantity: Int = 1,
    val note: String? = null,
)

class StaffRiwayatFragment : Fragment(R.layout.fragment_staff_riwayat) {

    private lateinit var tvRangeBadge: TextView
    private lateinit var tvSyncText: TextView
    private lateinit var tvCountDone: TextView
    private lateinit var tvAvgTime: TextView
    private lateinit var tvCountCancelled: TextView

    private lateinit var chipToday: TextView
    private lateinit var chipYesterday: TextView
    private lateinit var chipWeek: TextView
    private lateinit var chipDate: TextView

    private lateinit var llHistoryContainer: LinearLayout
    private lateinit var tvEmpty: TextView

    private enum class FilterPeriod {
        TODAY, YESTERDAY, WEEK, DATE
    }

    private var currentFilter = FilterPeriod.TODAY
    private var selectedCustomDate: Date? = null

    private data class HistoryItemData(
        val dto: HistoryOrderDto,
        val tableLabel: String,
        val itemsSummary: String,
        val durationMinutes: Long,
        val finishedDate: Date,
    )

    private var allHistory = listOf<HistoryItemData>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvRangeBadge = view.findViewById(R.id.tvHistoryRangeBadge)
        tvSyncText = view.findViewById(R.id.tvHistorySyncText)
        tvCountDone = view.findViewById(R.id.tvHistoryCountDone)
        tvAvgTime = view.findViewById(R.id.tvHistoryAvgTime)
        tvCountCancelled = view.findViewById(R.id.tvHistoryCountCancelled)

        chipToday = view.findViewById(R.id.chipHistoryToday)
        chipYesterday = view.findViewById(R.id.chipHistoryYesterday)
        chipWeek = view.findViewById(R.id.chipHistoryWeek)
        chipDate = view.findViewById(R.id.chipHistoryDate)

        llHistoryContainer = view.findViewById(R.id.llStaffHistoryContainer)
        tvEmpty = view.findViewById(R.id.tvHistoryEmpty)

        setupChips()

        tvSyncText.setOnClickListener {
            Toast.makeText(requireContext(), "Memperbarui riwayat...", Toast.LENGTH_SHORT).show()
            fetchHistoryFromSupabase(isSilent = false)
        }

        fetchHistoryFromSupabase(isSilent = true)
    }

    override fun onResume() {
        super.onResume()
        fetchHistoryFromSupabase(isSilent = true)
    }

    override fun onHiddenChanged(hidden: Boolean) {
        super.onHiddenChanged(hidden)
        if (!hidden) {
            fetchHistoryFromSupabase(isSilent = true)
        }
    }

    private fun setupChips() {
        chipToday.setOnClickListener {
            currentFilter = FilterPeriod.TODAY
            updateChipUi()
            render()
        }

        chipYesterday.setOnClickListener {
            currentFilter = FilterPeriod.YESTERDAY
            updateChipUi()
            render()
        }

        chipWeek.setOnClickListener {
            currentFilter = FilterPeriod.WEEK
            updateChipUi()
            render()
        }

        chipDate.setOnClickListener {
            showDatePicker()
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        selectedCustomDate?.let { cal.time = it }

        DatePickerDialog(
            requireContext(),
            { _, year, month, dayOfMonth ->
                val pickedCal = Calendar.getInstance().apply {
                    set(Calendar.YEAR, year)
                    set(Calendar.MONTH, month)
                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                selectedCustomDate = pickedCal.time
                currentFilter = FilterPeriod.DATE
                updateChipUi()
                render()
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH)
        ).show()
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

        chipToday.setActive(currentFilter == FilterPeriod.TODAY)
        chipYesterday.setActive(currentFilter == FilterPeriod.YESTERDAY)
        chipWeek.setActive(currentFilter == FilterPeriod.WEEK)
        chipDate.setActive(currentFilter == FilterPeriod.DATE)

        if (currentFilter == FilterPeriod.DATE && selectedCustomDate != null) {
            val dateLabel = SimpleDateFormat("dd MMM", Locale.forLanguageTag("id-ID")).format(selectedCustomDate!!)
            chipDate.text = dateLabel
            tvRangeBadge.text = dateLabel.uppercase()
        } else {
            chipDate.text = getString(R.string.staff_history_filter_date)
            tvRangeBadge.text = when (currentFilter) {
                FilterPeriod.TODAY -> getString(R.string.staff_history_today_badge)
                FilterPeriod.YESTERDAY -> "KEMARIN"
                FilterPeriod.WEEK -> "7 HARI"
                FilterPeriod.DATE -> "TANGGAL"
            }
        }
    }

    private fun fetchHistoryFromSupabase(isSilent: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. Ambil cafe_table
                val tables: List<CafeTable> = try {
                    supabase.postgrest["cafe_table"].select().decodeList()
                } catch (_: Exception) {
                    emptyList()
                }
                val tableMap = tables.associateBy { it.id }

                // 2. Ambil customer_order yang statusnya COMPLETED atau CANCELLED
                val orders: List<HistoryOrderDto> = try {
                    supabase.postgrest["customer_order"].select {
                        filter {
                            isIn("status", listOf("COMPLETED", "CANCELLED"))
                        }
                        order("updated_at", Order.DESCENDING)
                    }.decodeList()
                } catch (_: Exception) {
                    emptyList()
                }

                val orderIds = orders.map { it.id }

                // 3. Ambil item untuk order yang ditemukan
                val itemsMap: Map<String, List<HistoryOrderItemDto>> = try {
                    if (orderIds.isNotEmpty()) {
                        supabase.postgrest["order_item"].select {
                            filter { isIn("order_id", orderIds) }
                        }.decodeList<HistoryOrderItemDto>().groupBy { it.orderId }
                    } else emptyMap()
                } catch (_: Exception) {
                    emptyMap()
                }

                allHistory = orders.map { orderDto ->
                    val tableObj = tableMap[orderDto.tableId]
                    val tableLabel = tableObj?.label ?: if (orderDto.tableId.isNotEmpty()) orderDto.tableId else "Meja --"
                    val items = itemsMap[orderDto.id].orEmpty()
                    val summary = if (items.isNotEmpty()) {
                        "${items.sumOf { it.quantity }} item · " + items.joinToString(", ") { "${it.quantity}× ${it.nameSnapshot}" }
                    } else {
                        "Belum ada rincian item"
                    }

                    val duration = calculateDurationMinutes(
                        orderDto.paidAt ?: orderDto.createdAt,
                        orderDto.updatedAt ?: orderDto.paidAt ?: orderDto.createdAt
                    )

                    val finishedDate = parseIsoDate(orderDto.updatedAt ?: orderDto.paidAt ?: orderDto.createdAt) ?: Date()

                    HistoryItemData(
                        dto = orderDto,
                        tableLabel = tableLabel,
                        itemsSummary = summary,
                        durationMinutes = duration,
                        finishedDate = finishedDate
                    )
                }

                render()

                val time = SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(Date())
                tvSyncText.text = getString(R.string.staff_history_sync, time)
            } catch (e: Exception) {
                if (!isSilent) {
                    Toast.makeText(requireContext(), e.toUserMessage(requireContext(), R.string.err_queue_load_failed), Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun render() {
        val filtered = allHistory.filter { item ->
            when (currentFilter) {
                FilterPeriod.TODAY -> isSameDay(item.finishedDate, Date())
                FilterPeriod.YESTERDAY -> {
                    val cal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
                    isSameDay(item.finishedDate, cal.time)
                }
                FilterPeriod.WEEK -> {
                    val cutoff = System.currentTimeMillis() - (7 * 24 * 60 * 60 * 1000L)
                    item.finishedDate.time >= cutoff
                }
                FilterPeriod.DATE -> {
                    selectedCustomDate?.let { isSameDay(item.finishedDate, it) } ?: true
                }
            }
        }

        // Hitung Metrik
        val doneOrders = filtered.filter { it.dto.status.equals("COMPLETED", ignoreCase = true) }
        val cancelledOrders = filtered.filter { it.dto.status.equals("CANCELLED", ignoreCase = true) }

        tvCountDone.text = String.format(Locale.getDefault(), "%02d", doneOrders.size)
        tvCountCancelled.text = String.format(Locale.getDefault(), "%02d", cancelledOrders.size)

        if (doneOrders.isNotEmpty()) {
            val avg = (doneOrders.sumOf { it.durationMinutes } / doneOrders.size).coerceAtLeast(1)
            tvAvgTime.text = "$avg mnt"
        } else {
            tvAvgTime.text = "- mnt"
        }

        // Render List
        llHistoryContainer.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        if (filtered.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
            llHistoryContainer.visibility = View.GONE
            return
        }

        tvEmpty.visibility = View.GONE
        llHistoryContainer.visibility = View.VISIBLE

        filtered.forEach { item ->
            val card = inflater.inflate(R.layout.item_staff_riwayat, llHistoryContainer, false)
            val tvOrderNumber = card.findViewById<TextView>(R.id.tvHistoryOrderNumber)
            val tvMeta = card.findViewById<TextView>(R.id.tvHistoryMeta)
            val tvBadge = card.findViewById<TextView>(R.id.tvHistoryStatusBadge)
            val tvItems = card.findViewById<TextView>(R.id.tvHistoryItems)

            tvOrderNumber.text = "#${item.dto.publicId.ifEmpty { item.dto.id.take(8) }}"
            tvItems.text = item.itemsSummary

            val timeStr = formatTime(item.dto.updatedAt ?: item.dto.paidAt ?: item.dto.createdAt)

            if (item.dto.status.equals("COMPLETED", ignoreCase = true)) {
                tvBadge.text = getString(R.string.staff_history_status_done)
                tvBadge.setBackgroundResource(R.drawable.bg_badge_done)
                tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.ngopi_primary))
                tvMeta.text = getString(R.string.staff_history_meta_done, item.tableLabel, timeStr, item.durationMinutes)
            } else {
                tvBadge.text = getString(R.string.staff_history_status_cancelled)
                tvBadge.setBackgroundResource(R.drawable.bg_badge_new)
                tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.ngopi_error))
                tvMeta.text = getString(R.string.staff_history_meta_cancelled, item.tableLabel, timeStr)
            }

            card.setOnClickListener {
                val intent = Intent(requireContext(), StaffDetailProduksiActivity::class.java).apply {
                    putExtra(StaffDetailProduksiActivity.EXTRA_ORDER_ID, item.dto.id)
                    putExtra(StaffDetailProduksiActivity.EXTRA_PUBLIC_ID, item.dto.publicId)
                    putExtra(StaffDetailProduksiActivity.EXTRA_TABLE_LABEL, item.tableLabel)
                    putExtra(StaffDetailProduksiActivity.EXTRA_STATUS, item.dto.status)
                    putExtra(StaffDetailProduksiActivity.EXTRA_CREATED_AT, item.dto.createdAt)
                }
                startActivity(intent)
            }

            llHistoryContainer.addView(card)
        }
    }

    private fun parseIsoDate(isoString: String?): Date? {
        if (isoString.isNullOrBlank()) return null
        return try {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val clean = isoString.substringBefore(".").substringBefore("+").substringBefore("Z")
            parser.parse(clean)
        } catch (_: Exception) {
            null
        }
    }

    private fun calculateDurationMinutes(startIso: String?, endIso: String?): Long {
        val start = parseIsoDate(startIso) ?: return 1L
        val end = parseIsoDate(endIso) ?: return 1L
        val diff = (end.time - start.time) / 60_000L
        return diff.coerceAtLeast(1L)
    }

    private fun isSameDay(d1: Date, d2: Date): Boolean {
        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return fmt.format(d1) == fmt.format(d2)
    }

    private fun formatTime(isoString: String?): String {
        val date = parseIsoDate(isoString) ?: return SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).format(Date())
        val formatter = SimpleDateFormat("HH.mm", Locale.forLanguageTag("id-ID")).apply {
            timeZone = TimeZone.getDefault()
        }
        return formatter.format(date)
    }
}
