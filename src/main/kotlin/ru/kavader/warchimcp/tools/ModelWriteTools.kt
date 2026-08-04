package ru.kavader.warchimcp.tools

import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import ru.kavader.warchimcp.client.AreposApiClient

@Component
class ModelWriteTools(
    private val api: AreposApiClient
) {

    @McpTool(name = "create_node", description = "Create a node in a model")
    fun createNode(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(description = "Node name", required = true) name: String,
        @McpToolParam(description = "Node type UUID", required = true) nodeTypeId: String,
        @McpToolParam(description = "Parent node UUID", required = false) parentNodeId: String? = null,
        @McpToolParam(description = "JSON attrs string", required = false) attrs: String? = null
    ): String = runTool {
        api.postJson(
            "/api/v1/nodes",
            mapOf(
                "modelId" to modelId,
                "name" to name,
                "nodeTypeId" to nodeTypeId,
                "parentNodeId" to parentNodeId,
                "attrs" to attrs
            ).filterValues { it != null }
        )
    }

    @McpTool(name = "update_node", description = "Update an existing node")
    fun updateNode(
        @McpToolParam(description = "Node UUID", required = true) nodeId: String,
        @McpToolParam(description = "New name", required = false) name: String? = null,
        @McpToolParam(description = "Node type UUID", required = false) nodeTypeId: String? = null,
        @McpToolParam(description = "Parent node UUID", required = false) parentNodeId: String? = null,
        @McpToolParam(description = "JSON attrs string", required = false) attrs: String? = null
    ): String = runTool {
        api.putJson(
            "/api/v1/nodes/$nodeId",
            mapOf(
                "name" to name,
                "nodeTypeId" to nodeTypeId,
                "parentNodeId" to parentNodeId,
                "attrs" to attrs
            ).filterValues { it != null }
        )
    }

    @McpTool(name = "delete_node", description = "Delete a node by id")
    fun deleteNode(
        @McpToolParam(description = "Node UUID", required = true) nodeId: String
    ): String = runTool {
        api.deleteJson("/api/v1/nodes/$nodeId")
        mapOf("deleted" to true, "nodeId" to nodeId)
    }

    @McpTool(name = "create_link", description = "Create a link between nodes")
    fun createLink(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(description = "Source node UUID", required = true) sourceId: String,
        @McpToolParam(description = "Target node UUID", required = true) targetId: String,
        @McpToolParam(description = "Link type UUID", required = true) linkTypeId: String,
        @McpToolParam(description = "JSON attrs string", required = false) attrs: String? = null
    ): String = runTool {
        api.postJson(
            "/api/v1/links",
            mapOf(
                "modelId" to modelId,
                "sourceId" to sourceId,
                "targetId" to targetId,
                "linkTypeId" to linkTypeId,
                "attrs" to attrs
            ).filterValues { it != null }
        )
    }

    @McpTool(name = "update_link", description = "Update an existing link")
    fun updateLink(
        @McpToolParam(description = "Link UUID", required = true) linkId: String,
        @McpToolParam(description = "Source node UUID", required = false) sourceId: String? = null,
        @McpToolParam(description = "Target node UUID", required = false) targetId: String? = null,
        @McpToolParam(description = "Link type UUID", required = false) linkTypeId: String? = null,
        @McpToolParam(description = "JSON attrs string", required = false) attrs: String? = null
    ): String = runTool {
        api.putJson(
            "/api/v1/links/$linkId",
            mapOf(
                "sourceId" to sourceId,
                "targetId" to targetId,
                "linkTypeId" to linkTypeId,
                "attrs" to attrs
            ).filterValues { it != null }
        )
    }

    @McpTool(name = "delete_link", description = "Delete a link by id")
    fun deleteLink(
        @McpToolParam(description = "Link UUID", required = true) linkId: String
    ): String = runTool {
        api.deleteJson("/api/v1/links/$linkId")
        mapOf("deleted" to true, "linkId" to linkId)
    }

    @McpTool(
        name = "update_diagram",
        description = "Update diagram fields (name/version/notation/node/attrs). " +
            "For conflict-aware bulk edits prefer batch-save via attrs updates with baseUpdatedAt. " +
            "Returns structured errors for LOCKED_BY_OTHER / BATCH_SAVE_CONFLICT when applicable."
    )
    fun updateDiagram(
        @McpToolParam(description = "Diagram UUID", required = true) diagramId: String,
        @McpToolParam(description = "Diagram name", required = false) name: String? = null,
        @McpToolParam(description = "Diagram version", required = false) version: String? = null,
        @McpToolParam(description = "Notation UUID", required = false) notationId: String? = null,
        @McpToolParam(description = "Bound model node UUID", required = false) nodeId: String? = null,
        @McpToolParam(description = "JSON attrs string (diagram content)", required = false) attrs: String? = null
    ): String = runTool {
        api.putJson(
            "/api/v1/diagrams/$diagramId",
            mapOf(
                "name" to name,
                "version" to version,
                "notationId" to notationId,
                "nodeId" to nodeId,
                "attrs" to attrs
            ).filterValues { it != null }
        )
    }

    @McpTool(
        name = "batch_save_model",
        description = "Atomic batch save for nodes/links/diagrams of a model. " +
            "Pass JSON matching arepos BatchSaveRequest. On 409 returns BATCH_SAVE_CONFLICT details."
    )
    fun batchSaveModel(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(
            description = "BatchSaveRequest JSON object (force, nodes, links, diagrams)",
            required = true
        ) requestJson: String,
        @McpToolParam(description = "Force overwrite on conflict", required = false) force: Boolean? = null
    ): String = runTool {
        val mapper = com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules()
        val tree = mapper.readTree(requestJson)
        if (force != null && tree is com.fasterxml.jackson.databind.node.ObjectNode) {
            tree.put("force", force)
        }
        api.postJson("/api/v1/models/$modelId/batch-save", mapper.treeToValue(tree, Map::class.java))
    }

    private fun runTool(block: () -> Any?): String =
        try {
            ToolResult.ok(block())
        } catch (ex: Exception) {
            ToolResult.error(ex)
        }
}
