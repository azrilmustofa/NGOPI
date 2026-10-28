package kelompok.satu.kopiinggil

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import androidx.core.content.ContextCompat
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.roundToInt

// Satu baris tabel promo (supabase/promo.sql). RLS hanya mengembalikan promo yang sedang berlaku.
@Serializable
data class Promo(
    val id: String,
    @SerialName("menu_item_id") val menuItemId: String,
    val title: String,
    val description: String? = null,
    @SerialName("discount_type") val discountType: String,
    @SerialName("discount_value") val discountValue: Int,
    @SerialName("ends_at") val endsAt: String,
    val priority: Int = 0,
) {
    // Sama dengan loadDiscounts di quote-cart/create-payment: potongan dari harga dasar menu, maksimal harga itu.
    fun potonganUntuk(harga: Int): Int = minOf(
        harga,
        if (discountType == "PERCENT") (harga * discountValue / 100.0).roundToInt() else discountValue,
    )

    val label: String
        get() = if (discountType == "PERCENT") "-$discountValue%" else "-${discountValue.toRupiah()}"

    // "s.d. 22.00" kalau berakhir hari ini, "s.d. 31 Okt" kalau lebih lama.
    fun berlakuSampai(): String? = try {
        val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        val date = parser.parse(endsAt.substringBefore(".").substringBefore("+").removeSuffix("Z")) ?: null
        date?.let {
            val pattern = if (android.text.format.DateUtils.isToday(it.time - 1)) "HH.mm" else "d MMM"
            "s.d. " + SimpleDateFormat(pattern, Locale.forLanguageTag("id-ID")).format(it)
        }
    } catch (_: Exception) {
        null
    }
}

// Pasang promo terbaik ke tiap menu: harga jadi harga promo, harga asli disimpan di hargaNormal.
fun List<DataBarang>.denganPromo(promos: List<Promo>): List<DataBarang> {
    val perMenu = promos.groupBy { it.menuItemId }
    return map { barang ->
        val best = perMenu[barang.id]?.maxByOrNull { it.potonganUntuk(barang.harga) } ?: return@map barang
        val potongan = best.potonganUntuk(barang.harga)
        if (potongan <= 0) barang
        else barang.copy(harga = barang.harga - potongan, hargaNormal = barang.harga, promo = best)
    }
}

// "Rp19.200  Rp24.000" dengan harga normal dicoret, atau harga biasa kalau tidak promo.
fun DataBarang.hargaTampil(context: Context): CharSequence {
    if (!sedangPromo) return harga.toRupiah()
    val text = SpannableStringBuilder(harga.toRupiah()).append("  ")
    val start = text.length
    text.append(hargaNormal.toRupiah())
    listOf(
        StrikethroughSpan(),
        RelativeSizeSpan(0.85f),
        StyleSpan(Typeface.NORMAL),
        ForegroundColorSpan(ContextCompat.getColor(context, R.color.ngopi_muted)),
    ).forEach { text.setSpan(it, start, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
    return text
}
