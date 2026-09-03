package ru.kavader.warchimcp.tools

import com.fasterxml.jackson.databind.JsonNode
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
            "entityKind: model|diagram|node|component|notation|nodeType|linkType. " +
            "Prefer ensure_wiki for retries."
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
        createWikiInternal(entityKind, entityId, content, filename, modelId, notationId)
    }

    @McpTool(
        name = "ensure_wiki",
        description = "Idempotent ensure wiki markdown for an entity. " +
            "If attrs.documentFileId exists (or a single document_ref is found), updates that file; " +
            "otherwise creates via create_wiki. Returns {fileId, created, updated, ...}. " +
            "Prefer over create_wiki for retries. Requires models:write. " +
            "entityKind: model|diagram|node|component|notation|nodeType|linkType. " +
            "Multiple document_refs without documentFileId → error AMBIGUOUS_WIKI."
    )
    fun ensureWiki(
        @McpToolParam(
            description = "Entity kind: model, diagram, node, component, notation, nodeType, linkType",
            required = true
        ) entityKind: String,
        @McpToolParam(description = "Entity UUID", required = true) entityId: String,
        @McpToolParam(description = "Desired markdown content", required = true) content: String,
        @McpToolParam(description = "Filename (used on create; optional on update)", required = false)
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
        validateKind(kind, entityKind)

        val existingFileId = resolveExistingFileId(kind, entityId, modelId, notationId)
        if (existingFileId != null) {
            val updated = api.putJson(
                "/api/v1/files/$existingFileId/markdown",
                mapOf(
                    "content" to content,
                    "filename" to (filename?.takeIf { it.isNotBlank() } ?: "documentation.md")
                )
            )
            // Keep attrs.documentFileId in sync for kinds that support it.
            val attrsUpdated = setDocumentFileId(kind, entityId, existingFileId)
            return@run mapOf(
                "fileId" to existingFileId,
                "created" to false,
                "updated" to true,
                "updateResult" to updated,
                "attrsDocumentFileIdSet" to attrsUpdated
            )
        }

        val created = createWikiInternal(entityKind, entityId, content, filename, modelId, notationId)
        created + mapOf("created" to true, "updated" to false)
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

    private fun createWikiInternal(
        entityKind: String,
        entityId: String,
        content: String,
        filename: String?,
        modelId: String?,
        notationId: String?
    ): Map<String, Any?> {
        val kind = entityKind.trim().lowercase()
        validateKind(kind, entityKind)
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
        }

        val ref = api.postJson("/api/v1/documents", refBody)
        val attrsUpdated = setDocumentFileId(kind, entityId, fileId)

        return mapOf(
            "fileId" to fileId,
            "filename" to uploaded.path("filename").asText(safeName),
            "documentRef" to ref,
            "attrsDocumentFileIdSet" to attrsUpdated
        )
    }

    private fun resolveExistingFileId(
        kind: String,
        entityId: String,
        modelId: String?,
        notationId: String?
    ): String? {
        entityPath(kind, entityId)?.let { path ->
            val entity = api.getJson(path)
            readDocumentFileId(entity.path("attrs"))?.let { return it }
        }

        val query = documentQuery(kind, entityId, modelId, notationId)
        val listed = api.getJson("/api/v1/documents", query)
        val fileIds = extractDocumentFileIds(listed)
        return when {
            fileIds.isEmpty() -> null
            fileIds.size == 1 -> fileIds.first()
            else -> throw IllegalStateException(
                "AMBIGUOUS_WIKI: multiple document refs for $kind/$entityId " +
                    "(candidates=${fileIds.joinToString()}). " +
                    "Pass a specific fileId to update_wiki or set attrs.documentFileId."
            )
        }
    }

    private fun documentQuery(
        kind: String,
        entityId: String,
        modelId: String?,
        notationId: String?
    ): Map<String, Any?> = when (kind) {
        "model" -> mapOf("modelId" to entityId)
        "diagram" -> mapOf(
            "diagramId" to entityId,
            "modelId" to modelId?.takeIf { it.isNotBlank() }
        )
        "node" -> mapOf(
            "nodeId" to entityId,
            "modelId" to modelId?.takeIf { it.isNotBlank() }
        )
        "component" -> mapOf(
            "componentId" to entityId,
            "notationId" to notationId?.takeIf { it.isNotBlank() }
        )
        "notation" -> mapOf("notationId" to entityId)
        "nodetype" -> mapOf("nodeTypeId" to entityId)
        "linktype" -> mapOf("linkTypeId" to entityId)
        else -> emptyMap()
    }

    private fun extractDocumentFileIds(listed: JsonNode): List<String> {
        val items = when {
            listed.isArray -> listed
            listed.has("items") && listed.path("items").isArray -> listed.path("items")
            listed.has("content") && listed.path("content").isArray -> listed.path("content")
            listed.has("data") && listed.path("data").isArray -> listed.path("data")
            else -> return emptyList()
        }
        return items.mapNotNull { item ->
            sequenceOf("fileId", "id", "file_id")
                .mapNotNull { key -> item.path(key).asText(null)?.takeIf { it.isNotBlank() } }
                .firstOrNull()
        }.distinct()
    }

    private fun entityPath(kind: String, entityId: String): String? = when (kind) {
        "model" -> "/api/v1/models/$entityId"
        "diagram" -> "/api/v1/diagrams/$entityId"
        "node" -> "/api/v1/nodes/$entityId"
        "component" -> "/api/v1/components/$entityId"
        else -> null
    }

    private fun setDocumentFileId(kind: String, entityId: String, fileId: String): Boolean {
        val path = entityPath(kind, entityId) ?: return false
        val current = api.getJson(path)
        val attrs = mergeDocumentFileId(current.path("attrs"), fileId)
        api.putJson(path, mapOf("attrs" to attrs))
        return true
    }

    internal fun mergeDocumentFileId(attrsNode: JsonNode, fileId: String): String {
        val node: ObjectNode = try {
            when {
                attrsNode.isObject -> attrsNode.deepCopy()
                attrsNode.isTextual && attrsNode.asText().isNotBlank() -> {
                    val parsed = objectMapper.readTree(attrsNode.asText())
                    if (parsed is ObjectNode) parsed else objectMapper.createObjectNode()
                }
                else -> objectMapper.createObjectNode()
            }
        } catch (_: Exception) {
            objectMapper.createObjectNode()
        }
        node.put("documentFileId", fileId)
        return objectMapper.writeValueAsString(node)
    }

    /** Backward-compatible helper used by older string-based call sites/tests. */
    internal fun mergeDocumentFileId(attrsRaw: String?, fileId: String): String {
        val node = if (attrsRaw.isNullOrBlank()) {
            objectMapper.nullNode()
        } else {
            objectMapper.valueToTree<JsonNode>(attrsRaw)
        }
        // valueToTree on String yields a textual node — mergeDocumentFileId(JsonNode) handles it.
        return mergeDocumentFileId(node, fileId)
    }

    private fun readDocumentFileId(attrsNode: JsonNode): String? {
        if (attrsNode.isMissingNode || attrsNode.isNull) return null
        return try {
            val obj = when {
                attrsNode.isObject -> attrsNode
                attrsNode.isTextual && attrsNode.asText().isNotBlank() ->
                    objectMapper.readTree(attrsNode.asText())
                else -> return null
            }
            obj.path("documentFileId").asText(null)?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun validateKind(kind: String, original: String) {
        val allowed = setOf(
            "model", "diagram", "node", "component", "notation", "nodetype", "linktype"
        )
        if (kind !in allowed) {
            throw IllegalArgumentException(
                "Unsupported entityKind '$original'. " +
                    "Use model|diagram|node|component|notation|nodeType|linkType"
            )
        }
    }
}
