package kelompok.satu.kopiinggil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhatsAppBotTest {

    @Test
    fun formatChatId_convertsStandardIndonesianNumbers() {
        assertEquals("6287736117128@c.us", WhatsAppBotService.formatChatId("087736117128"))
        assertEquals("6287736117128@c.us", WhatsAppBotService.formatChatId("+6287736117128"))
        assertEquals("6287736117128@c.us", WhatsAppBotService.formatChatId("6287736117128"))
        assertEquals("6287736117128@c.us", WhatsAppBotService.formatChatId("87736117128"))
        assertEquals("6287736117128@c.us", WhatsAppBotService.formatChatId("0877-3611-7128"))
        assertNull(WhatsAppBotService.formatChatId("12345"))
    }

    @Test
    fun buildReceiptText_containsEssentialDetails() {
        val dummyOrder = PlacedOrder(
            number = "#NG-TEST99",
            table = 3,
            items = listOf(
                CartItem(
                    barang = DataBarang(id = "1", nama = "Kopi Gula Aren", harga = 18000),
                    quantity = 2,
                    pilihan = listOf(
                        OpsiDipilih("opt1", "Gula", "Less Sugar", 0),
                        OpsiDipilih("opt2", "Susu", "Oat Milk", 5000),
                    ),
                    catatan = "Jangan terlalu manis",
                )
            ),
            subtotal = 46000,
            tax = 4600,
            total = 50600,
            method = MetodeBayar.QRIS,
            paidAt = "14.20",
            status = "PREPARING",
        )

        val receipt = WhatsAppBotService.buildReceiptText(dummyOrder, "Daffa")
        assertNotNull(receipt)
        assertTrue(receipt.contains("KOPI INGGIL"))
        assertTrue(receipt.contains("#NG-TEST99"))
        assertTrue(receipt.contains("Meja 03"))
        assertTrue(receipt.contains("Daffa"))
        assertTrue(receipt.contains("Kopi Gula Aren"))
        assertTrue(receipt.contains("Less Sugar"))
        assertTrue(receipt.contains("Jangan terlalu manis"))
        assertTrue(receipt.contains("QRIS (LUNAS)"))
        assertTrue(receipt.contains("Rp50.600"))
    }

    @Test
    fun buildCompletedReceiptText_containsEssentialDetails() {
        val itemsSummary = listOf(
            "• *2x Kopi Gula Aren* (Rp36.000)\n   _Pilihan: Less Sugar_\n   _Catatan: Jangan terlalu manis_"
        )
        val receipt = WhatsAppBotService.buildCompletedReceiptText(
            orderNumber = "NG-TEST99",
            tableLabel = "Meja 03",
            paidAtTime = "14.20",
            customerName = "Daffa",
            itemsSummary = itemsSummary,
            totalRp = 36000,
        )
        assertNotNull(receipt)
        assertTrue(receipt.contains("KOPI INGGIL"))
        assertTrue(receipt.contains("NG-TEST99"))
        assertTrue(receipt.contains("Meja 03"))
        assertTrue(receipt.contains("Daffa"))
        assertTrue(receipt.contains("Kopi Gula Aren"))
        assertTrue(receipt.contains("Status: *SELESAI*"))
        assertTrue(receipt.contains("Rp36.000"))
    }
    @Test
    fun customTemplate_keepsTransactionDetails() {
        val template = ReceiptTemplate("COMPLETED", "STRUK KAFE", "Kopi Inggil Cabang Utama", "Sampai jumpa kembali.")
        val receipt = WhatsAppBotService.buildCompletedReceiptText(
            "NG-TEST99", "Meja 03", "14.20", "Daffa", listOf("2x Kopi Gula Aren"), 36000, template,
        )
        assertTrue(receipt.startsWith(template.header))
        assertTrue(receipt.contains("*STRUK KAFE*"))
        assertTrue(receipt.endsWith(template.footer))
        assertTrue(receipt.contains("NG-TEST99"))
        assertTrue(receipt.contains("2x Kopi Gula Aren"))
        assertTrue(receipt.contains("Rp36.000"))
        assertTrue(receipt.contains("Status: *SELESAI*"))
    }
}
