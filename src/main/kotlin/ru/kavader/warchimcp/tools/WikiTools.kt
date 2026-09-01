package ru.kavader.warchimcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import ru.kavader.warchimcp.client.AreposApiClient

/**
 * Wiki pages are markdown files in arepos + document_refs + attrs.documentFileId on entities.
 */
@Component
class WikiTools(
    private val api: AreposApiClient,
    private val objectMapper: ObjectMapper
) {

    @McpTool(
        name = "list_wiki",
        description = "List wiki documents linked to a model/diagram/node/component (or other entity). " +
            "Returns slim items: fileId, label, entityType, entityId, entityName, parentName. Requires models:read."
    )
    fun listWiki(
        @McpToolParam(description = "Model UUID filter", required = false) modelId: String? = null,
        @McpToolParam(description = "Diagram UUID filter", required = false) diagramId: String? = null,
        @McpToolParam(description = "Node UUID filter", required = false) nodeId: String? = null,
        @McpToolParam(description = "Notation component UUID filter", required = false) componentId: String? = null,
        @McpToolParam(description = "Notation UUID filter", required = false) notationId: String? = null,
        @McpToolParam(description = "Node type UUID filter", required = false) nodeTypeId: String? = null,
        @McpToolParam(description = "Link type UUID filter", required = false) linkTypeId: String? = null
    ): String = ToolResult.run {
        api.getJson(
            "/api/v1/documents",
            mapOf(
                "modelId" to modelId?.takeIf { it.isNotBlank() },
                "diagramId" to diagramId?.takeIf { it.isNotBlank() },
                "nodeId" to nodeId?.takeIf { it.isNotBlank() },
                "componentId" to componentId?.takeIf { it.isNotBlank() },
                "notationId" to notationId?.takeIf { it.isNotBlank() },
                "nodeTypeId" to nodeTypeId?.takeIf { it.isNotBlank() },
                "linkTypeId" to linkTypeId?.takeIf { it.isNotBlank() }
            )
        )
    }

    @McpTool(
        name = "get_wiki",
        description = "Get wiki markdown content by fileId. Returns { fileId, content }. Requires models:read."
    )
    fun getWiki(
        @McpToolParam(description = "File UUID of the markdown wiki page", required = true) fileId: String
    ): String = ToolResult.run {
        val content = api.getRawText("/api/v1/files/$fileId")
        mapOf(
            "fileId" to fileId,
            "content" to content
        )
    }

    @McpTool(
        name = "create_wiki",
        description = "Create a markdown wiki page, register document_ref, and set attrs.documentFileId " +
            "on model/diagram/node/component when applicable. Requires models:write. " +
            "entityKind: model|diagram|node|component|notation|nodeType|linkType."
    )
    fun createWiki(
        @McpToolParam(
            description = "Entity kind: model, diagram, node, component, notation, nodeType, linkType",
            required = true
        ) entityKind: String,
        @McpToolParam(description = "Entity UUID", required = true) entityId: String,
        @McpToolParam(description = "Markdown content", required = true) content: String,
        @McpToolParam(description = "Filename (default derived from kind+id)", required = false)
        filename: String? = null,
        @McpToolParam(
            description = "Parent model UUID (required for diagram/node if not resolved from entity)",
            required = false
        ) modelId: String? = null,
        @McpToolParam(
            description = "Parent notation UUID (recommended for component)",
            required = false
        ) notationId: String? = null
    ): String = ToolResult.run {
        val kind = entityKind.trim().lowercase()
        val safeName = filename?.takeIf { it.isNotBlank() }
            ?: "${kind}-${entityId.take(8)}.md"

        val uploaded = api.postJson(
            "/api/v1/files/upload-markdown",
            mapOf(
                "content" to content,
                "filename" to safeName
            )
        )
        val fileId = uploaded.path("id").asText(null)
            ?: throw IllegalStateException("upload-markdown response missing id")

        val refBody = mutableMapOf<String, Any>("fileId" to fileId)
        when (kind) {
            "model" -> refBody["modelId"] = entityId
            "diagram" -> {
                refBody["diagramId"] = entityId
                val mid = modelId?.takeIf { it.isNotBlank() }
                    ?: api.getJson("/api/v1/diagrams/$entityId").path("modelId").asText(null)
                if (!mid.isNullOrBlank()) refBody["modelId"] = mid
            }
            "node" -> {
                refBody["nodeId"] = entityId
                val mid = modelId?.takeIf { it.isNotBlank() }
                    ?: api.getJson("/api/v1/nodes/$entityId").path("modelId").asText(null)
                if (!mid.isNullOrBlank()) refBody["modelId"] = mid
            }
            "component" -> {
                refBody["componentId"] = entityId
                val nid = notationId?.takeIf { it.isNotBlank() }
                    ?: api.getJson("/api/v1/components/$entityId").path("notationId").asText(null)
                if (!nid.isNullOrBlank()) refBody["notationId"] = nid
            }
            "notation" -> refBody["notationId"] = entityId
            "nodetype" -> refBody["nodeTypeId"] = entityId
            "linktype" -> refBody["linkTypeId"] = entityId
            else -> throw IllegalArgumentException(
                "Unsupported entityKind '$entityKind'. " +
                    "Use model|diagram|node|component|notation|nodeType|linkType"
            )
        }

        val ref = api.postJson("/api/v1/documents", refBody)
        val attrsUpdated = setDocumentFileId(kind, entityId, fileId)

        mapOf(
            "fileId" to fileId,
            "filename" to uploaded.path("filename").asText(safeName),
            "documentRef" to ref,
            "attrsDocumentFileIdSet" to attrsUpdated
        )
    }

    @McpTool(
        name = "update_wiki",
        description = "Update markdown content of an existing wiki file by fileId. Requires models:write."
    )
    fun updateWiki(
        @McpToolParam(description = "File UUID", required = true) fileId: String,
        @McpToolParam(description = "New markdown content", required = true) content: String,
        @McpToolParam(description = "Filename (optional)", required = false) filename: String? = null
    ): String = ToolResult.run {
        api.putJson(
            "/api/v1/files/$fileId/markdown",
            mapOf(
                "content" to content,
                "filename" to (filename?.takeIf { it.isNotBlank() } ?: "documentation.md")
            )
        )
    }

    private fun setDocumentFileId(kind: String, entityId: String, fileId: String): Boolean {
        val path = when (kind) {
            "model" -> "/api/v1/models/$entityId"
            "diagram" -> "/api/v1/diagrams/$entityId"
            "node" -> "/api/v1/nodes/$entityId"
            "component" -> "/api/v1/components/$entityId"
            else -> return false
        }
        val current = api.getJson(path)
        val attrs = mergeDocumentFileId(current.path("attrs").asText(null), fileId)
        api.putJson(path, mapOf("attrs" to attrs))
        return true
    }

    private fun mergeDocumentFileId(attrsRaw: String?, fileId: String): String {
        val node = try {
            if (attrsRaw.isNullOrBlank()) {
                objectMapper.createObjectNode()
            } else {
                val parsed = objectMapper.readTree(attrsRaw)
                if (parsed is ObjectNode) parsed.deepCopy() else objectMapper.createObjectNode()
            }
        } catch (_: Exception) {
            objectMapper.createObjectNode()
        }
        node.put("documentFileId", fileId)
        return objectMapper.writeValueAsString(node)
    }
}
