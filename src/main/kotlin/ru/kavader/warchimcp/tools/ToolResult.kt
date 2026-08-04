package ru.kavader.warchimcp.tools

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import ru.kavader.warchimcp.client.AreposClientException

object ToolResult {
    private val mapper = ObjectMapper().findAndRegisterModules()

    fun ok(payload: Any?): String {
        val root = mapper.createObjectNode()
        root.put("ok", true)
        when (payload) {
            null -> root.putNull("data")
            is JsonNode -> root.set<JsonNode>("data", payload)
            is String -> root.put("data", payload)
            else -> root.set<JsonNode>("data", mapper.valueToTree(payload))
        }
        return mapper.writeValueAsString(root)
    }

    fun error(ex: Exception): String {
        val root = mapper.createObjectNode()
        root.put("ok", false)
        if (ex is AreposClientException) {
            root.put("status", ex.status)
            root.put("message", ex.message)
            classify(ex, root)
            if (!ex.body.isNullOrBlank()) {
                try {
                    root.set<JsonNode>("details", mapper.readTree(ex.body))
                } catch (_: Exception) {
                    root.put("details", ex.body.take(1000))
                }
            }
        } else {
            root.put("message", ex.message ?: ex::class.java.simpleName)
        }
        return mapper.writeValueAsString(root)
    }

    private fun classify(ex: AreposClientException, root: ObjectNode) {
        val body = ex.body.orEmpty()
        val message = ex.message
        when {
            body.contains("AMBIGUOUS_NOTATION_ELEMENT") || message.contains("AMBIGUOUS_NOTATION_ELEMENT") ->
                root.put("code", "AMBIGUOUS_NOTATION_ELEMENT")
            body.contains("DIAGRAM_CONFLICT") || message.contains("DIAGRAM_CONFLICT") ->
                root.put("code", "DIAGRAM_CONFLICT")
            body.contains("BATCH_SAVE_CONFLICT") ||
                (ex.status == 409 && message.contains("conflict", true) && !body.contains("DIAGRAM_CONFLICT")) ->
                root.put("code", "BATCH_SAVE_CONFLICT")
            body.contains("LOCKED_BY_OTHER") || message.contains("LOCKED_BY_OTHER") ->
                root.put("code", "LOCKED_BY_OTHER")
            message == "model_not_allowed" || body.contains("model_not_allowed") ->
                root.put("code", "model_not_allowed")
            body.contains("missing_scope") || message.contains("missing_scope") ->
                root.put("code", "missing_scope")
            else -> root.put("code", "arepos_error")
        }
    }
}
