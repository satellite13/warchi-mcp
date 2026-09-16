package ru.kavader.warchimcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import ru.kavader.warchimcp.client.AreposApiClient

/**
 * Notation catalog helpers.
 *
 * wArchi custom property schemas live in the `attrs.customProperties` JSON of notation
 * components and node types (attrs are replaced wholesale on PUT), so "ensure a property
 * exists" is a read-merge-write: GET current attrs, append the missing definitions
 * (matched by name, existing ones untouched), PUT the full attrs back.
 */
@Component
class NotationTools(
    private val api: AreposApiClient,
    private val mapper: ObjectMapper
) {
    @McpTool(
        name = "ensure_custom_properties",
        description = "Ensure customProperties are defined on a notation component " +
            "(create-if-missing, matched by name; existing definitions are left untouched) and mirror " +
            "the same properties onto the component's node type (override with nodeTypeId). " +
            "componentId may be omitted when nodeTypeId is given — then the properties are ensured on " +
            "that node type only (no notation component), which is how folder node types like " +
            "Directory (outside any notation) get their property schema. " +
            "Idempotent — safe to retry. Requires notation edit permission of the API key owner. " +
            "Returns {component: {...} | null, nodeType: {id, name, changed, added, existing}}. " +
            "propertiesJson is a JSON array: [{\"name\":\"owner\",\"type\":\"string\",\"maxLength\":40}, " +
            "{\"name\":\"severity\",\"type\":\"enum\",\"enumValues\":[\"Low\",\"Critical\"]}, ...]. " +
            "type is string|number|boolean|enum; unknown fields (defaultValue, interactive, ...) are copied."
    )
    fun ensureCustomProperties(
        @McpToolParam(
            description = "Notation component UUID (optional when nodeTypeId is provided)",
            required = false
        ) componentId: String? = null,
        @McpToolParam(
            description = "Node type UUID to write the properties into (default: the component's own node type). " +
                "Required when componentId is omitted",
            required = false
        ) nodeTypeId: String? = null,
        @McpToolParam(
            description = "JSON array of property definitions, e.g. " +
                "[{\"name\":\"owner\",\"type\":\"string\",\"required\":false,\"maxLength\":40}]",
            required = true
        ) propertiesJson: String
    ): String = ToolResult.run {
        val definitions = CustomProperties.parseDefinitions(propertiesJson, mapper)

        val explicitNodeType = nodeTypeId?.takeIf { it.isNotBlank() }
        require(!componentId.isNullOrBlank() || explicitNodeType != null) {
            "either componentId or nodeTypeId must be provided"
        }

        // Fetch the component once; it feeds both the component merge and the default node type.
        val componentPayload = componentId?.takeIf { it.isNotBlank() }
            ?.let { api.getJson("/api/v1/components/$it") }
        val ntyId = explicitNodeType
            ?: componentPayload?.path("nodeTypeId")?.asText(null)
            ?: throw IllegalStateException(
                "component $componentId has no node type and nodeTypeId was not provided"
            )

        val componentEntry: Map<String, Any?>? = componentPayload?.let { component ->
            val cid = component.path("id").asText(componentId)
            val componentMerge = CustomProperties.merge(
                component.path("attrs").asText(null), definitions, mapper
            )
            if (componentMerge.changed) {
                api.putJson("/api/v1/components/$cid", mapOf("attrs" to componentMerge.attrs))
            }
            resultEntry(component.path("id").asText(cid), component.path("name").asText(cid), componentMerge)
        }
        val nodeType = api.getJson("/api/v1/node-types/$ntyId")
        val nodeTypeMerge = CustomProperties.merge(
            nodeType.path("attrs").asText(null), definitions, mapper
        )
        if (nodeTypeMerge.changed) {
            api.putJson("/api/v1/node-types/$ntyId", mapOf("attrs" to nodeTypeMerge.attrs))
        }

        mapOf(
            "component" to componentEntry,
            "nodeType" to resultEntry(
                nodeType.path("id").asText(ntyId), nodeType.path("name").asText(ntyId), nodeTypeMerge
            )
        )
    }

    private fun resultEntry(id: String, name: String, merge: CustomProperties.MergeResult): Map<String, Any?> =
        mapOf(
            "id" to id,
            "name" to name,
            "changed" to merge.changed,
            "added" to merge.added,
            "existing" to merge.existing
        )

    @McpTool(
        name = "get_node_type_default_directory",
        description = "Get the defaultDirectoryPath from a node type's attrs. " +
            "Returns {nodeTypeId, name, defaultDirectoryPath}. " +
            "defaultDirectoryPath is null if not set."
    )
    fun getNodeTypeDefaultDirectory(
        @McpToolParam(description = "Node type UUID", required = true) nodeTypeId: String
    ): String = ToolResult.run {
        val nodeType = api.getJson("/api/v1/node-types/$nodeTypeId")
        val attrs = nodeType.path("attrs").asText(null)
        val defaultDir = attrs?.let { a ->
            try {
                mapper.readTree(a).path("defaultDirectoryPath").asText(null)
            } catch (_: Exception) {
                null
            }
        }
        mapOf(
            "nodeTypeId" to nodeType.path("id").asText(nodeTypeId),
            "name" to nodeType.path("name").asText(""),
            "defaultDirectoryPath" to defaultDir
        )
    }

    @McpTool(
        name = "set_node_type_default_directory",
        description = "Set or clear the defaultDirectoryPath on a node type's attrs. " +
            "Idempotent — safe to retry. Requires notation/node-type edit permission. " +
            "Pass an empty string or null to clear. " +
            "Returns {nodeTypeId, name, defaultDirectoryPath, changed}."
    )
    fun setNodeTypeDefaultDirectory(
        @McpToolParam(description = "Node type UUID", required = true) nodeTypeId: String,
        @McpToolParam(
            description = "Directory path (e.g. '/Application', '/Technology'). Pass empty string or omit to clear.",
            required = false
        ) defaultDirectoryPath: String? = null
    ): String = ToolResult.run {
        val nodeType = api.getJson("/api/v1/node-types/$nodeTypeId")
        val attrsRaw = nodeType.path("attrs").asText(null)
        val root: com.fasterxml.jackson.databind.node.ObjectNode = when {
            attrsRaw.isNullOrBlank() -> mapper.createObjectNode()
            else -> {
                val parsed = mapper.readTree(attrsRaw)
                require(parsed is com.fasterxml.jackson.databind.node.ObjectNode) {
                    "current attrs is not a JSON object"
                }
                parsed
            }
        }

        val currentDir = root.path("defaultDirectoryPath").asText(null)
        val targetDir = defaultDirectoryPath?.takeIf { it.isNotBlank() }

        val changed = currentDir != targetDir
        if (changed) {
            if (targetDir != null) {
                root.put("defaultDirectoryPath", targetDir)
            } else {
                (root as com.fasterxml.jackson.databind.node.ObjectNode).remove("defaultDirectoryPath")
            }
            api.putJson("/api/v1/node-types/$nodeTypeId", mapOf("attrs" to mapper.writeValueAsString(root)))
        }

        mapOf(
            "nodeTypeId" to nodeType.path("id").asText(nodeTypeId),
            "name" to nodeType.path("name").asText(""),
            "defaultDirectoryPath" to (if (changed) targetDir else currentDir),
            "changed" to changed
        )
    }
}
