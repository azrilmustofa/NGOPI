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
}