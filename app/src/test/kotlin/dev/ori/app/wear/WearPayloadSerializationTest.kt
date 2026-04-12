package dev.ori.app.wear

import dev.ori.domain.model.WearPaths
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * WearPayloads are plain data classes serialized via android.os.Bundle / DataMap on the
 * device side, which cannot be exercised in a JVM unit test. Instead, we lock down the
 * [WearPaths] contract: every path constant must be unique, non-blank, and namespaced
 * under "/oridev/" so the watch and phone never accidentally cross paths.
 */
class WearPayloadSerializationTest {

    private val allPaths = listOf(
        WearPaths.CONNECTIONS_STATUS,
        WearPaths.TRANSFERS_ACTIVE,
        WearPaths.SNIPPETS_WATCH,
        WearPaths.COMMAND_EXECUTE,
        WearPaths.COMMAND_RESPONSE,
        WearPaths.PANIC_DISCONNECT_ALL,
        WearPaths.CONNECT_REQUEST,
        WearPaths.DISCONNECT_REQUEST,
        WearPaths.TWO_FA_REQUEST,
        WearPaths.TWO_FA_RESPONSE,
    )

    @Test
    fun `all WearPaths constants are non-blank`() {
        for (path in allPaths) {
            assertFalse(path.isBlank(), "Path should not be blank")
        }
    }

    @Test
    fun `all WearPaths constants are unique`() {
        assertEquals(allPaths.size, allPaths.toSet().size, "Wear paths must be unique")
    }

    @Test
    fun `all WearPaths constants are namespaced under oridev`() {
        for (path in allPaths) {
            assertTrue(path.startsWith("/oridev/"), "Path '$path' must start with /oridev/")
        }
    }

    @Test
    fun `two factor request and response paths are distinct`() {
        assertFalse(
            WearPaths.TWO_FA_REQUEST == WearPaths.TWO_FA_RESPONSE,
            "2FA request/response paths must be distinct to avoid loops",
        )
    }
}
