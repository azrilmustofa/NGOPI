package kelompok.satu.kopiinggil

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OrderReceiptTest {
    private val receipt = OrderReceipt(
        orderId = "order-picked", publicId = "NG-PICKED", tableLabel = "Meja 07", status = "READY",
        totalRp = 25300, subtotalRp = 22000, taxRp = 2200, serviceChargeRp = 1100,
        paidAt = "2026-11-09T17:30:00+00:00", customerName = "Pelanggan terpilih", phoneE164 = "+6281234567890",
        items = listOf(ReceiptLine("Kopi harga snapshot", 2, 22000, "Tanpa es", listOf("Oat milk"))),
    )

    @Test
    fun receiptUsesSelectedOrderSnapshotsAndPaymentTimeInWib() {
        val text = receipt.message(null, "Permintaan pelanggan")
        for (expected in listOf("NG-PICKED", "Meja 07", "Pelanggan terpilih", "2x Kopi harga snapshot",
            "Tanpa es", "Oat milk", "Rp25.300", "10/11/2026 00:30 WIB", "Status pesanan: READY")) {
            assertTrue(expected, text.contains(expected))
        }
        assertFalse(text.contains("NG-2839"))
        assertEquals("Belum tercatat", receiptTime(null))
        assertTrue(receipt.copy(status = "COMPLETED").message(null, "Ulang").contains("Status pesanan: COMPLETED"))
        val template = ReceiptTemplate("PAID", "Judul dari admin", "Header dari admin", "Penutup dari admin")
        val templated = receipt.message(template, "Ulang")
        assertTrue(templated.contains("Judul dari admin"))
        assertTrue(templated.contains("Header dari admin"))
        assertTrue(templated.endsWith("Penutup dari admin"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun receiptCannotClaimAnUnpaidOrderIsPaid() {
        receipt.copy(status = "AWAITING_PAYMENT").message(null, "Ulang")
    }

    @Test
    fun editedNumbersHaveTheSameValidationForWahaAndManualWhatsapp() {
        assertEquals("6281234567890@c.us", WhatsAppBotService.formatChatId("0812-3456-7890"))
        assertEquals(null, WhatsAppBotService.formatChatId("12345"))
        assertEquals(null, WhatsAppBotService.formatChatId("081234567890123456789"))
    }
}
