package kelompok.satu.kopiinggil

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
private data class KasirTxOrderDto(
    val id: String = "",
    @SerialName("public_id") val publicId: String = "",
    @SerialName("table_id") val tableId: String = "",
    val status: String = "",
    @SerialName("total_rp") val totalRp: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
private data class KasirTxOrderItemDto(
    val id: String = "",
    @SerialName("order_id") val orderId: String = "",
    @SerialName("name_snapshot") val nameSnapshot: String = "",
    val quantity: Int = 1,
)

class KasirTransaksiFragment : Fragment(R.layout.fragment_kasir_transaksi) {

    private lateinit var tvSyncStatus: TextView
    private lateinit var chipSemua: TextView
    private lateinit var chipTertunda: TextView
    private lateinit var chipLunas: TextView
    private lateinit var chipBatal: TextView
    private lateinit var llContainer: LinearLayout
    private lateinit var tvEmpty: TextView

    private var selectedFilter = "SEMUA"

    private data class TxDisplayData(
        val dto: KasirTxOrderDto,
        val tableLabel: String,
        val itemsSummary: String,
    )

    private var allTransactions = listOf<TxDisplayData>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvSyncStatus = view.findViewById(R.id.tvTxSyncStatus)
        chipSemua = view.findViewById(R.id.chipTxSemua)
        chipTertunda = view.findViewById(R.id.chipTxTertunda)
        chipLunas = view.findViewById(R.id.chipTxLunas)
        chipBatal = view.findViewById(R.id.chipTxBatal)
        llContainer = view.findViewById(R.id.llTxListContainer)
        tvEmpty = view.findViewById(R.id.tvTxListEmpty)

        setupChips()

        tvSyncStatus.setOnClickListener {
            Toast.makeText(requireContext(), "Menyinkronkan transaksi...", Toast.LENGTH_SHORT).show()
            fetchTransactions(isSilent = false)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                requireContext().pollEvery(POLL_INTERVAL_MS) {
                    if (isAdded && !isHidden) {
                        fetchTransactions(isSilent = true)
                    }
                    true
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        fetchTransactions(isSilent = true)
    }

    fun setFilter(filterKey: String) {
        selectedFilter = filterKey
        if (::chipSemua.isInitialized) {
            updateChipUi()
            renderList()
        }
    }

    private fun setupChips() {
        val chips = listOf(
            chipSemua to "SEMUA",
            chipTertunda to "TERTUNDA",
            chipLunas to "LUNAS",
            chipBatal to "BATAL",
        )

        chips.forEach { (chip, key) ->
            chip.setOnClickListener {
                selectedFilter = key
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

        fun TextView.applyState(isActive: Boolean) {
            setBackgroundResource(if (isActive) activeBg else inactiveBg)
            setTextColor(if (isActive) activeColor else inactiveColor)
        }

        chipSemua.applyState(selectedFilter == "SEMUA")
        chipTertunda.applyState(selectedFilter == "TERTUNDA")
        chipLunas.applyState(selectedFilter == "LUNAS")
        chipBatal.applyState(selectedFilter == "BATAL")
    }

    private fun fetchTransactions(isSilent: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. Ambil cafe_table
                val tables: List<CafeTable> = try {
                    supabase.postgrest["cafe_table"].select().decodeList()
                } catch (_: Exception) {
                    emptyList()
                }
                val tableMap = tables.associateBy { it.id }

                // 2. Ambil customer_order
                val orders: List<KasirTxOrderDto> = try {
                    supabase.postgrest["customer_order"].select {
                        order("created_at", Order.DESCENDING)
                    }.decodeList()
                } catch (_: Exception) {
                    emptyList()
                }

                val sortedOrders = orders.sortedByDescending { it.createdAt.orEmpty() }
                val orderIds = sortedOrders.map { it.id }

                // 3. Ambil order_item
                val itemsMap: Map<String, List<KasirTxOrderItemDto>> = try {
                    if (orderIds.isNotEmpty()) {
                        supabase.postgrest["order_item"].select {
                            filter { isIn("order_id", orderIds) }
                        }.decodeList<KasirTxOrderItemDto>().groupBy { it.orderId }
                    } else emptyMap()
                } catch (_: Exception) {
                    emptyMap()
                }

                allTransactions = sortedOrders.map { orderDto ->
                    val tableObj = tableMap[orderDto.tableId]
                    val tableLabel = tableObj?.label ?: if (orderDto.tableId.isNotEmpty()) orderDto.tableId else "Meja --"
                    val items = itemsMap[orderDto.id].orEmpty()
                    val summary = if (items.isNotEmpty()) {
                        "${items.sumOf { it.quantity }} item · " + items.joinToString(", ") { "${it.quantity}× ${it.nameSnapshot}" }
                    } else {
                        "Belum ada rincian item"
                    }
                    TxDisplayData(orderDto, tableLabel, summary)
                }

                renderList()
            } catch (e: Exception) {
                if (!isSilent && isAdded) {
                    Toast.makeText(requireContext(), "Gagal memuat transaksi: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderList() {
        if (!isAdded) return

        val filtered = allTransactions.filter { data ->
            val status = data.dto.status.uppercase()
            when (selectedFilter) {
                "TERTUNDA" -> status in listOf("AWAITING_PAYMENT", "PENDING", "")
                "LUNAS" -> status in listOf("PAID", "ACCEPTED", "PREPARING", "READY", "COMPLETED")
                "BATAL" -> status == "CANCELLED"
                else -> true
            }
        }

        llContainer.removeAllViews()

        if (filtered.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
            val inflater = LayoutInflater.from(requireContext())

            for (data in filtered) {
                val row = inflater.inflate(R.layout.item_kasir_transaksi, llContainer, false)

                val tvNumber = row.findViewById<TextView>(R.id.tvTxOrderNumber)
                val tvMeta = row.findViewById<TextView>(R.id.tvTxOrderMeta)
                val tvBadge = row.findViewById<TextView>(R.id.tvTxStatusBadge)
                val tvItems = row.findViewById<TextView>(R.id.tvTxItemsSummary)
                val tvTotal = row.findViewById<TextView>(R.id.tvTxTotalPrice)

                val dispPublicId = data.dto.publicId.ifBlank { data.dto.id.take(8) }
                tvNumber.text = "#$dispPublicId"
                tvMeta.text = "${data.tableLabel} · ${formatTime(data.dto.createdAt)} WIB"
                tvItems.text = data.itemsSummary
                tvTotal.text = data.dto.totalRp.toRupiah()

                val status = data.dto.status.uppercase()
                when (status) {
                    "PAID", "ACCEPTED", "PREPARING", "READY", "COMPLETED" -> {
                        tvBadge.text = if (status == "COMPLETED") "SELESAI" else "LUNAS"
                        tvBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ngopi_primary))
                        tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    }
                    "CANCELLED" -> {
                        tvBadge.text = "BATAL"
                        tvBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ngopi_error))
                        tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                    }
                    else -> {
                        tvBadge.text = "TERTUNDA"
                        tvBadge.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.ngopi_surface))
                        tvBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.ngopi_primary))
                    }
                }

                row.setOnClickListener {
                    val intent = Intent(requireContext(), KasirTransaksiVerifikasiActivity::class.java).apply {
                        putExtra(KasirTransaksiVerifikasiActivity.EXTRA_ORDER_ID, data.dto.id)
                        putExtra(KasirTransaksiVerifikasiActivity.EXTRA_PUBLIC_ID, data.dto.publicId)
                        putExtra(KasirTransaksiVerifikasiActivity.EXTRA_TABLE_LABEL, data.tableLabel)
                        putExtra(KasirTransaksiVerifikasiActivity.EXTRA_STATUS, data.dto.status)
                        putExtra(KasirTransaksiVerifikasiActivity.EXTRA_TOTAL_RP, data.dto.totalRp)
                        putExtra(KasirTransaksiVerifikasiActivity.EXTRA_CREATED_AT, data.dto.createdAt)
                    }
                    startActivity(intent)
                }

                llContainer.addView(row)
            }
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

    companion object {
        private const val POLL_INTERVAL_MS = 4000L
    }
}
