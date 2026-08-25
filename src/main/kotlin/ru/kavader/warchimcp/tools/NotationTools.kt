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
    private val api: AreposApiClient
) {
    private val mapper = ObjectMapper().findAndRegisterModules()

    @McpTool(
        name = "ensure_custom_properties",
        description = "Ensure customProperties are defined on a notation component " +
            "(create-if-missing, matched by name; existing definitions are left untouched) and mirror " +
            "the same properties onto the component's node type (override with nodeTypeId). " +
            "Idempotent — safe to retry. Requires notation edit permission of the API key owner. " +
            "Returns {component: {id, name, changed, added, existing}, nodeType: {...}}. " +
            "propertiesJson is a JSON array: [{\"name\":\"owner\",\"type\":\"string\",\"maxLength\":40}, " +
            "{\"name\":\"severity\",\"type\":\"enum\",\"enumValues\":[\"Low\",\"Critical\"]}, ...]. " +
            "type is string|number|boolean|enum; unknown fields (defaultValue, interactive, ...) are copied."
    )
    fun ensureCustomProperties(
        @McpToolParam(description = "Notation component UUID", required = true) componentId: String,
        @McpToolParam(
            description = "Node type UUID to mirror the properties into (default: the component's own node type)",
            required = false
        ) nodeTypeId: String? = null,
        @McpToolParam(
            description = "JSON array of property definitions, e.g. " +
                "[{\"name\":\"owner\",\"type\":\"string\",\"required\":false,\"maxLength\":40}]",
            required = true
        ) propertiesJson: String
    ): String = runTool {
        val definitions = CustomProperties.parseDefinitions(propertiesJson, mapper)

        val component = api.getJson("/api/v1/components/$componentId")
        val componentMerge = CustomProperties.merge(
            component.path("attrs").asText(null), definitions, mapper
        )
        if (componentMerge.changed) {
            api.putJson("/api/v1/components/$componentId", mapOf("attrs" to componentMerge.attrs))
        }

        val ntyId = nodeTypeId?.takeIf { it.isNotBlank() }
            ?: component.path("nodeTypeId").asText(null)
            ?: throw IllegalStateException(
                "component $componentId has no node type and nodeTypeId was not provided"
            )
        val nodeType = api.getJson("/api/v1/node-types/$ntyId")
        val nodeTypeMerge = CustomProperties.merge(
            nodeType.path("attrs").asText(null), definitions, mapper
        )
        if (nodeTypeMerge.changed) {
            api.putJson("/api/v1/node-types/$ntyId", mapOf("attrs" to nodeTypeMerge.attrs))
        }

        mapOf(
            "component" to resultEntry(
                component.path("id").asText(componentId), component.path("name").asText(componentId), componentMerge
            ),
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

    private fun runTool(block: () -> Any?): String =
        try {
            ToolResult.ok(block())
        } catch (ex: Exception) {
            ToolResult.error(ex)
        }
}
