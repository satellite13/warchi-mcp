package ru.kavader.warchimcp.tools

import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import ru.kavader.warchimcp.client.AreposApiClient

@Component
class ModelReadTools(
    private val api: AreposApiClient
) {

    @McpTool(name = "list_models", description = "List architectural models accessible to the API key owner")
    fun listModels(
        @McpToolParam(description = "Optional name filter", required = false) name: String? = null,
        @McpToolParam(description = "Page number (0-based)", required = false) page: Int? = 0,
        @McpToolParam(description = "Page size", required = false) size: Int? = 50
    ): String = ToolResult.run {
        api.getJson(
            "/api/v1/models",
            mapOf(
                "name" to name?.takeIf { it.isNotBlank() },
                "page" to (page ?: 0),
                "size" to (size ?: 50)
            )
        )
    }

    @McpTool(name = "get_model", description = "Get model metadata by id")
    fun getModel(
        @McpToolParam(description = "Model UUID", required = true) modelId: String
    ): String = ToolResult.run {
        api.getJson("/api/v1/models/$modelId")
    }

    @McpTool(name = "list_diagrams", description = "List diagrams for a model")
    fun listDiagrams(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(description = "Page number (0-based)", required = false) page: Int? = 0,
        @McpToolParam(description = "Page size", required = false) size: Int? = 50
    ): String = ToolResult.run {
        api.getJson(
            "/api/v1/diagrams",
            mapOf(
                "modelId" to modelId,
                "page" to (page ?: 0),
                "size" to (size ?: 50)
            )
        )
    }

    @McpTool(name = "get_diagram", description = "Get diagram metadata and attrs by id")
    fun getDiagram(
        @McpToolParam(description = "Diagram UUID", required = true) diagramId: String
    ): String = ToolResult.run {
        api.getJson("/api/v1/diagrams/$diagramId")
    }

    @McpTool(name = "list_nodes", description = "List nodes of a model")
    fun listNodes(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(description = "Page number (0-based)", required = false) page: Int? = 0,
        @McpToolParam(description = "Page size", required = false) size: Int? = 100
    ): String = ToolResult.run {
        api.getJson(
            "/api/v1/nodes",
            mapOf(
                "modelId" to modelId,
                "page" to (page ?: 0),
                "size" to (size ?: 100)
            )
        )
    }

    @McpTool(name = "get_node", description = "Get node by id including attrs")
    fun getNode(
        @McpToolParam(description = "Node UUID", required = true) nodeId: String
    ): String = ToolResult.run {
        api.getJson("/api/v1/nodes/$nodeId")
    }

    @McpTool(name = "list_links", description = "List links of a model")
    fun listLinks(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(description = "Page number (0-based)", required = false) page: Int? = 0,
        @McpToolParam(description = "Page size", required = false) size: Int? = 100
    ): String = ToolResult.run {
        api.getJson(
            "/api/v1/links",
            mapOf(
                "modelId" to modelId,
                "page" to (page ?: 0),
                "size" to (size ?: 100)
            )
        )
    }

    @McpTool(name = "get_link", description = "Get link by id including attrs")
    fun getLink(
        @McpToolParam(description = "Link UUID", required = true) linkId: String
    ): String = ToolResult.run {
        api.getJson("/api/v1/links/$linkId")
    }

    @McpTool(name = "list_notations", description = "List notations accessible to the user")
    fun listNotations(
        @McpToolParam(description = "Optional name filter", required = false) name: String? = null,
        @McpToolParam(description = "Page number (0-based)", required = false) page: Int? = 0,
        @McpToolParam(description = "Page size", required = false) size: Int? = 50
    ): String = ToolResult.run {
        api.getJson(
            "/api/v1/notations",
            mapOf(
                "name" to name?.takeIf { it.isNotBlank() },
                "page" to (page ?: 0),
                "size" to (size ?: 50)
            )
        )
    }

    @McpTool(
        name = "get_notation_summary",
        description = "Get notation metadata plus components and relations summaries"
    )
    fun getNotationSummary(
        @McpToolParam(description = "Notation UUID", required = true) notationId: String
    ): String = ToolResult.run {
        val notation = api.getJson("/api/v1/notations/$notationId")
        val components = api.getJson(
            "/api/v1/components",
            mapOf("notationId" to notationId, "size" to 500)
        )
        val relations = api.getJson(
            "/api/v1/relations",
            mapOf("notationId" to notationId, "size" to 500)
        )
        mapOf(
            "notation" to notation,
            "components" to components,
            "relations" to relations
        )
    }
}
