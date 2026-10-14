package kelompok.satu.kopiinggil

import org.junit.Test

import org.junit.Assert.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
class ExampleUnitTest {
    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }

    @Test
    fun supabaseConfig_isConfigured() {
        assertEquals("https://uvaeklvwkxibwcrhofku.supabase.co", SUPABASE_URL)
        assertTrue(SUPABASE_KEY.startsWith("sb_publishable_"))
    }

    @Test
    fun dataBarang_decodesFromJson() {
        val sampleJson = """
            {"id":"MENU-01","category_id":"CAT-01","name":"Es Kopi Susu","description":"Espresso pilihan, susu segar, dan gula aren.","photo_url":"https://example.com/photo.jpg","allergy_info":"Susu","base_price_rp":24000,"is_available":true,"is_active":true,"created_at":"2026-10-14T10:15:31.62927+00:00"}
        """.trimIndent()
        val item = kotlinx.serialization.json.Json.decodeFromString<DataBarang>(sampleJson)
        assertEquals("MENU-01", item.id)
        assertEquals("Es Kopi Susu", item.nama)
        assertEquals(24000, item.harga)
        assertEquals(KategoriBarang.KOPI, item.kategori)
    }
}