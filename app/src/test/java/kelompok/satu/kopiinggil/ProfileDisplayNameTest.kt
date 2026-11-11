package kelompok.satu.kopiinggil

import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileDisplayNameTest {
    @Test
    fun customerNameTakesPriority() {
        val metadata = buildJsonObject {
            put("full_name", "Nama pelanggan")
            put("name", "Nama lama")
        }
        assertEquals("Nama pelanggan", profileDisplayName(metadata, "customer@example.com"))
    }

    @Test
    fun staffCreatedByAdminUsesItsNameMetadata() {
        val metadata = buildJsonObject {
            put("full_name", JsonNull)
            put("name", "Arya Barista")
        }
        assertEquals("Arya Barista", profileDisplayName(metadata, "barista@example.com"))
    }

    @Test
    fun missingOrBlankNamesFallBackToTheLoginEmail() {
        assertEquals("kasir1", profileDisplayName(null, "kasir1@example.com"))
        val metadata = buildJsonObject { put("full_name", " "); put("name", "") }
        assertEquals("kasir1", profileDisplayName(metadata, "kasir1@example.com"))
    }
}
