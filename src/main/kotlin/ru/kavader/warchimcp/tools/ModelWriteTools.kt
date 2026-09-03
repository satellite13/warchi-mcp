package ru.kavader.warchimcp.tools

import com.fasterxml.jackson.databind.ObjectMapper
import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import ru.kavader.warchimcp.client.AreposApiClient

@Component
class ModelWriteTools(
    private val api: AreposApiClient,
    private val mapper: ObjectMapper
) {
    @McpTool(
        name = "create_node",
        description = "Create a node in a model. Prefer notationId+componentName (or componentId) " +
            "so the server sets nodeTypeId and attrs.notationComponents. " +
            "nodeTypeId is required only when no component binding is provided."
    )
    fun createNode(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(description = "Node name", required = true) name: String,
        @McpToolParam(description = "Node type UUID (optional when component binding is set)", required = false)
        nodeTypeId: String? = null,
        @McpToolParam(description = "Parent node UUID", required = false) parentNodeId: String? = null,
        @McpToolParam(description = "JSON attrs string", required = false) attrs: String? = null,
        @McpToolParam(description = "Notation UUID for component binding", required = false) notationId: String? = null,
        @McpToolParam(description = "Notation component UUID", required = false) componentId: String? = null,
        @McpToolParam(description = "Notation component name (requires notationId)", required = false)
        componentName: String? = null
    ): String = ToolResult.run {
        api.postJson(
            "/api/v1/nodes",
            mapOf(
                "modelId" to modelId,
                "name" to name,
                "nodeTypeId" to nodeTypeId,
                "parentNodeId" to parentNodeId,
                "attrs" to attrs,
                "notationId" to notationId,
                "componentId" to componentId,
                "componentName" to componentName
            ).filterValues { it != null }
        )
    }

    @McpTool(
        name = "ensure_node",
        description = "Idempotent find-or-create node by modelId+parentNodeId+name " +
            "(case-insensitive). Returns {node, created}. Notation binding " +
            "(notationId+componentName/componentId) applies on create only; hit does not mutate. " +
            "Multiple matches → 409 AMBIGUOUS_NODE. No DB unique constraint — dual concurrent ensure may race."
    )
    fun ensureNode(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(description = "Node name", required = true) name: String,
        @McpToolParam(description = "Node type UUID (optional when component binding is set)", required = false)
        nodeTypeId: String? = null,
        @McpToolParam(description = "Parent node UUID", required = false) parentNodeId: String? = null,
        @McpToolParam(description = "JSON attrs string", required = false) attrs: String? = null,
        @McpToolParam(description = "Notation UUID for component binding", required = false) notationId: String? = null,
        @McpToolParam(description = "Notation component UUID", required = false) componentId: String? = null,
        @McpToolParam(description = "Notation component name (requires notationId)", required = false)
        componentName: String? = null
    ): String = ToolResult.run {
        api.postJson(
            "/api/v1/nodes/ensure",
            mapOf(
                "modelId" to modelId,
                "name" to name,
                "nodeTypeId" to nodeTypeId,
                "parentNodeId" to parentNodeId,
                "attrs" to attrs,
                "notationId" to notationId,
                "componentId" to componentId,
                "componentName" to componentName
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
    ): String = ToolResult.run {
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
    ): String = ToolResult.run {
        api.deleteJson("/api/v1/nodes/$nodeId")
        mapOf("deleted" to true, "nodeId" to nodeId)
    }

    @McpTool(
        name = "create_link",
        description = "Create a link between nodes. Prefer notationId+relationName (or relationId) " +
            "so the server sets linkTypeId and attrs.notationRelations. " +
            "linkTypeId is required only when no relation binding is provided."
    )
    fun createLink(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(description = "Source node UUID", required = true) sourceId: String,
        @McpToolParam(description = "Target node UUID", required = true) targetId: String,
        @McpToolParam(description = "Link type UUID (optional when relation binding is set)", required = false)
        linkTypeId: String? = null,
        @McpToolParam(description = "JSON attrs string", required = false) attrs: String? = null,
        @McpToolParam(description = "Notation UUID for relation binding", required = false) notationId: String? = null,
        @McpToolParam(description = "Notation relation UUID", required = false) relationId: String? = null,
        @McpToolParam(description = "Notation relation name (requires notationId)", required = false)
        relationName: String? = null
    ): String = ToolResult.run {
        api.postJson(
            "/api/v1/links",
            mapOf(
                "modelId" to modelId,
                "sourceId" to sourceId,
                "targetId" to targetId,
                "linkTypeId" to linkTypeId,
                "attrs" to attrs,
                "notationId" to notationId,
                "relationId" to relationId,
                "relationName" to relationName
            ).filterValues { it != null }
        )
    }

    @McpTool(
        name = "ensure_link",
        description = "Idempotent find-or-create link by modelId+sourceId+targetId+linkType " +
            "(after notation relation resolve). Returns {link, created}. " +
            "Direction-strict; concurrent dual-create may still race (no DB unique constraint)."
    )
    fun ensureLink(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(description = "Source node UUID", required = true) sourceId: String,
        @McpToolParam(description = "Target node UUID", required = true) targetId: String,
        @McpToolParam(description = "Link type UUID (optional when relation binding is set)", required = false)
        linkTypeId: String? = null,
        @McpToolParam(description = "JSON attrs string", required = false) attrs: String? = null,
        @McpToolParam(description = "Notation UUID for relation binding", required = false) notationId: String? = null,
        @McpToolParam(description = "Notation relation UUID", required = false) relationId: String? = null,
        @McpToolParam(description = "Notation relation name (requires notationId)", required = false)
        relationName: String? = null
    ): String = ToolResult.run {
        api.postJson(
            "/api/v1/links/ensure",
            mapOf(
                "modelId" to modelId,
                "sourceId" to sourceId,
                "targetId" to targetId,
                "linkTypeId" to linkTypeId,
                "attrs" to attrs,
                "notationId" to notationId,
                "relationId" to relationId,
                "relationName" to relationName
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
    ): String = ToolResult.run {
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
    ): String = ToolResult.run {
        api.deleteJson("/api/v1/links/$linkId")
        mapOf("deleted" to true, "linkId" to linkId)
    }

    @McpTool(
        name = "create_diagram",
        description = "Create a diagram in a model. Defaults version=1.0.0 and empty instances canvas. " +
            "Then use add_diagram_instances to place nodes/edges."
    )
    fun createDiagram(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(description = "Diagram name", required = true) name: String,
        @McpToolParam(description = "Notation UUID", required = true) notationId: String,
        @McpToolParam(description = "Bound model node UUID", required = false) nodeId: String? = null,
        @McpToolParam(description = "Diagram version (default 1.0.0)", required = false) version: String? = null,
        @McpToolParam(description = "JSON attrs string (default empty instances)", required = false) attrs: String? = null
    ): String = ToolResult.run {
        api.postJson(
            "/api/v1/diagrams",
            mapOf(
                "modelId" to modelId,
                "name" to name,
                "notationId" to notationId,
                "nodeId" to nodeId,
                "version" to (version?.takeIf { it.isNotBlank() } ?: "1.0.0"),
                "attrs" to (attrs ?: """{"instances":{"nodes":[],"edges":[]}}""")
            ).filterValues { it != null }
        )
    }

    @McpTool(
        name = "ensure_diagram",
        description = "Idempotent find-or-create diagram by modelId+name → latest non-deleted version. " +
            "Returns {diagram, created}. On create defaults version=1.0.0 and empty instances canvas. " +
            "On hit does not update fields. No DB unique constraint — dual concurrent ensure may race. " +
            "Then use add_diagram_instances to place nodes/edges."
    )
    fun ensureDiagram(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(description = "Diagram name", required = true) name: String,
        @McpToolParam(description = "Notation UUID", required = true) notationId: String,
        @McpToolParam(description = "Bound model node UUID", required = false) nodeId: String? = null,
        @McpToolParam(description = "Diagram version (default 1.0.0 on create)", required = false) version: String? = null,
        @McpToolParam(description = "JSON attrs string (default empty instances on create)", required = false)
        attrs: String? = null
    ): String = ToolResult.run {
        api.postJson(
            "/api/v1/diagrams/ensure",
            mapOf(
                "modelId" to modelId,
                "name" to name,
                "notationId" to notationId,
                "nodeId" to nodeId,
                "version" to (version?.takeIf { it.isNotBlank() } ?: "1.0.0"),
                "attrs" to (attrs ?: """{"instances":{"nodes":[],"edges":[]}}""")
            ).filterValues { it != null }
        )
    }

    @McpTool(
        name = "add_diagram_instances",
        description = "Merge/upsert diagram canvas instances. Nodes keyed by modelNodeId; edges by modelLinkId " +
            "with auto-resolve of source/target instance ids. Pass nodesJson/edgesJson as JSON arrays. " +
            "Optional baseUpdatedAt for optimistic concurrency (409 DIAGRAM_CONFLICT)."
    )
    fun addDiagramInstances(
        @McpToolParam(description = "Diagram UUID", required = true) diagramId: String,
        @McpToolParam(
            description = "JSON array of {modelNodeId,x,y,width?,height?,id?}",
            required = false
        ) nodesJson: String? = null,
        @McpToolParam(
            description = "JSON array of {modelLinkId,sourceInstanceId?,targetInstanceId?,id?}",
            required = false
        ) edgesJson: String? = null,
        @McpToolParam(description = "Optimistic concurrency base updatedAt (ISO-8601)", required = false)
        baseUpdatedAt: String? = null
    ): String = ToolResult.run {
        val body = mapper.createObjectNode()
        body.set<com.fasterxml.jackson.databind.JsonNode>(
            "nodes",
            if (nodesJson.isNullOrBlank()) mapper.createArrayNode() else mapper.readTree(nodesJson)
        )
        body.set<com.fasterxml.jackson.databind.JsonNode>(
            "edges",
            if (edgesJson.isNullOrBlank()) mapper.createArrayNode() else mapper.readTree(edgesJson)
        )
        if (!baseUpdatedAt.isNullOrBlank()) {
            body.put("baseUpdatedAt", baseUpdatedAt)
        }
        api.postJson("/api/v1/diagrams/$diagramId/instances:merge", mapper.treeToValue(body, Map::class.java))
    }

    @McpTool(
        name = "update_diagram",
        description = "Update diagram fields (name/version/notation/node/attrs). " +
            "For incremental canvas edits prefer add_diagram_instances. " +
            "For conflict-aware bulk edits prefer batch_save_model. " +
            "Returns 409 CONFLICT when the diagram is not the latest version by name " +
            "or the name+version combination already exists."
    )
    fun updateDiagram(
        @McpToolParam(description = "Diagram UUID", required = true) diagramId: String,
        @McpToolParam(description = "Diagram name", required = false) name: String? = null,
        @McpToolParam(description = "Diagram version", required = false) version: String? = null,
        @McpToolParam(description = "Notation UUID", required = false) notationId: String? = null,
        @McpToolParam(description = "Bound model node UUID", required = false) nodeId: String? = null,
        @McpToolParam(description = "JSON attrs string (diagram content)", required = false) attrs: String? = null
    ): String = ToolResult.run {
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
            "Pass JSON matching arepos BatchSaveRequest. On 409 returns BATCH_SAVE_CONFLICT details. " +
            "Escape hatch for complex atomic cases; prefer create_diagram + add_diagram_instances for simple canvas."
    )
    fun batchSaveModel(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(
            description = "BatchSaveRequest JSON object (force, nodes, links, diagrams)",
            required = true
        ) requestJson: String,
        @McpToolParam(description = "Force overwrite on conflict", required = false) force: Boolean? = null
    ): String = ToolResult.run {
        val tree = mapper.readTree(requestJson)
        if (force != null && tree is com.fasterxml.jackson.databind.node.ObjectNode) {
            tree.put("force", force)
        }
        api.postJson("/api/v1/models/$modelId/batch-save", mapper.treeToValue(tree, Map::class.java))
    }
}
