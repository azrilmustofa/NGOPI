package kelompok.satu.kopiinggil

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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
private data class KasirOrderDto(
    val id: String = "",
    @SerialName("public_id") val publicId: String = "",
    @SerialName("table_id") val tableId: String = "",
    val status: String = "",
    @SerialName("total_rp") val totalRp: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

class KasirRingkasanFragment : Fragment(R.layout.fragment_kasir_ringkasan) {

    private lateinit var tvDateSync: TextView
    private lateinit var tvCountBantuan: TextView
    private lateinit var tvCountBayar: TextView
    private lateinit var llAttentionContainer: LinearLayout
    private lateinit var tvEmpty: TextView

    private var allOrders = listOf<KasirOrderDto>()
    private var tableMap = mapOf<String, CafeTable>()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        tvDateSync = view.findViewById(R.id.tvKasirDateSync)
        tvCountBantuan = view.findViewById(R.id.tvCountPerluBantuan)
        tvCountBayar = view.findViewById(R.id.tvCountBayarTertunda)
        llAttentionContainer = view.findViewById(R.id.llKasirAttentionContainer)
        tvEmpty = view.findViewById(R.id.tvKasirAttentionEmpty)

        updateDateHeader()

        tvDateSync.setOnClickListener {
            Toast.makeText(requireContext(), "Menyinkronkan data kasir...", Toast.LENGTH_SHORT).show()
            fetchKasirData(isSilent = false)
        }

        view.findViewById<View>(R.id.cardMetricBayar).setOnClickListener {
            (activity as? KasirHomeActivity)?.selectTransaksiTab()
        }

        view.findViewById<View>(R.id.cardMetricBantuan).setOnClickListener {
            Toast.makeText(requireContext(), "Menampilkan antrean bantuan meja", Toast.LENGTH_SHORT).show()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                requireContext().pollEvery(POLL_INTERVAL_MS) {
                    if (isAdded && !isHidden) {
                        fetchKasirData(isSilent = true)
                    }
                    true
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateDateHeader()
        fetchKasirData(isSilent = true)
    }

    private fun updateDateHeader() {
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.forLanguageTag("id-ID"))
        val todayStr = dateFormat.format(Date())
        tvDateSync.text = "Hari ini, $todayStr · Sinkron otomatis"
    }

    private fun fetchKasirData(isSilent: Boolean = false) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val tables: List<CafeTable> = try {
                    supabase.postgrest["cafe_table"].select().decodeList()
                } catch (_: Exception) {
                    emptyList()
                }
                tableMap = tables.associateBy { it.id }

                val orders: List<KasirOrderDto> = try {
                    supabase.postgrest["customer_order"].select {
                        order("created_at", Order.DESCENDING)
                    }.decodeList()
                } catch (_: Exception) {
                    emptyList()
                }

                allOrders = orders
                renderDashboard()
            } catch (e: Exception) {
                if (!isSilent && isAdded) {
                    Toast.makeText(requireContext(), "Gagal memperbarui: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun renderDashboard() {
        if (!isAdded) return

        // 1. Hitung pesanan bayar tertunda (AWAITING_PAYMENT)
        val pendingPaymentOrders = allOrders.filter {
            it.status.equals("AWAITING_PAYMENT", ignoreCase = true)
        }

        tvCountBayar.text = String.format(Locale.getDefault(), "%02d", pendingPaymentOrders.size)
        // Sementara metric bantuan menghitung permintaan perhatian (bayar tertunda / bantuan)
        tvCountBantuan.text = String.format(Locale.getDefault(), "%02d", pendingPaymentOrders.size)

        // 2. Tampilkan item yang butuh perhatian (attention container)
        llAttentionContainer.removeAllViews()

        if (pendingPaymentOrders.isEmpty()) {
            tvEmpty.visibility = View.VISIBLE
        } else {
            tvEmpty.visibility = View.GONE
            val inflater = LayoutInflater.from(requireContext())

            for (order in pendingPaymentOrders) {
                val row = inflater.inflate(R.layout.item_kasir_attention, llAttentionContainer, false)

                val tvName = row.findViewById<TextView>(R.id.tvAttentionName)
                val tvKind = row.findViewById<TextView>(R.id.tvAttentionKindBadge)
                val tvSub = row.findViewById<TextView>(R.id.tvAttentionSub)

                val tableObj = tableMap[order.tableId]
                val tableLabel = tableObj?.label ?: if (order.tableId.isNotEmpty()) order.tableId else "Meja --"

                tvName.text = "$tableLabel · Menunggu Pembayaran"
                tvKind.text = "BAYAR TERTUNDA"
                val timeStr = formatTime(order.createdAt)
                tvSub.text = "#${order.publicId.ifBlank { order.id.take(8) }} · $timeStr · ${order.totalRp.toRupiah()}"

                row.setOnClickListener {
                    val intent = Intent(requireContext(), KasirTransaksiVerifikasiActivity::class.java).apply {
                        putExtra(KasirTransaksiVerifikasiActivity.EXTRA_ORDER_ID, order.id)
                        putExtra(KasirTransaksiVerifikasiActivity.EXTRA_PUBLIC_ID, order.publicId)
                        putExtra(KasirTransaksiVerifikasiActivity.EXTRA_TABLE_LABEL, tableLabel)
                        putExtra(KasirTransaksiVerifikasiActivity.EXTRA_STATUS, order.status)
                        putExtra(KasirTransaksiVerifikasiActivity.EXTRA_TOTAL_RP, order.totalRp)
                        putExtra(KasirTransaksiVerifikasiActivity.EXTRA_CREATED_AT, order.createdAt)
                    }
                    startActivity(intent)
                }

                llAttentionContainer.addView(row)
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
        private const val POLL_INTERVAL_MS = 5000L
    }
}
