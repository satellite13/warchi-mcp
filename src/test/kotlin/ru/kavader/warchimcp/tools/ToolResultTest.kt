package ru.kavader.warchimcp.tools

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import ru.kavader.warchimcp.client.AreposClientException

class ToolResultTest {

    @Test
    fun `ok wraps payload`() {
        val json = ToolResult.ok(mapOf("id" to "123"))
        assertTrue(json.contains("\"ok\":true"))
        assertTrue(json.contains("123"))
    }

    @Test
    fun `error classifies batch save conflict`() {
        val json = ToolResult.error(
            AreposClientException(
                status = 409,
                message = "Conflict",
                body = """{"error":"BATCH_SAVE_CONFLICT","conflicts":[]}"""
            )
        )
        assertTrue(json.contains("\"ok\":false"))
        assertTrue(json.contains("BATCH_SAVE_CONFLICT"))
    }

    @Test
    fun `error classifies locked by other`() {
        val json = ToolResult.error(
            AreposClientException(
                status = 200,
                message = "LOCKED_BY_OTHER",
                body = """{"reason":"LOCKED_BY_OTHER"}"""
            )
        )
        assertTrue(json.contains("LOCKED_BY_OTHER"))
    }
}
