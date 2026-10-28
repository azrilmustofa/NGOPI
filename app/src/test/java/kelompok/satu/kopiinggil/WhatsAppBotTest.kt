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
}
