package kelompok.satu.kopiinggil

data class AssistedCustomer(
    val tableId: String,
    val name: String,
    val phoneDigits: String,
)

object AssistedOrderSession {
    private var customer: AssistedCustomer? = null

    fun start(tableId: String, name: String, phoneDigits: String) {
        customer = AssistedCustomer(tableId, name.trim(), phoneDigits)
    }

    fun forTable(tableId: String): AssistedCustomer? =
        customer?.takeIf { it.tableId == tableId }

    fun clear() {
        customer = null
    }
}
