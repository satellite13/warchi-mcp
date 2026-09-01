package ru.kavader.warchimcp.tools

import org.springaicommunity.mcp.annotation.McpTool
import org.springaicommunity.mcp.annotation.McpToolParam
import org.springframework.stereotype.Component
import ru.kavader.warchimcp.client.AreposApiClient

@Component
class SearchTools(
    private val api: AreposApiClient
) {

    @McpTool(
        name = "search_catalog",
        description = "Search models and notations by name. Returns slim hits (id, name, version) without attrs. Use this to resolve modelId before search_model or get_*. Prefer over list_models when looking up by name."
    )
    fun searchCatalog(
        @McpToolParam(description = "Substring to match against name (case-insensitive)", required = true)
        q: String,
        @McpToolParam(description = "Comma-separated kinds: models,notations (default both)", required = false)
        kinds: String? = null,
        @McpToolParam(description = "Max hits (default 20, max 50)", required = false)
        limit: Int? = null
    ): String = ToolResult.run {
        api.getJson(
            "/api/v1/search/catalog",
            mapOf(
                "q" to q,
                "kinds" to kinds?.takeIf { it.isNotBlank() },
                "limit" to limit
            )
        )
    }

    @McpTool(
        name = "search_model",
        description = "Search nodes, links, and diagrams inside one model by name (links match source/target node names). Returns slim hits without attrs/canvas. Use before list_nodes/list_links. Then call get_* only for selected ids."
    )
    fun searchModel(
        @McpToolParam(description = "Model UUID", required = true) modelId: String,
        @McpToolParam(description = "Substring to match (case-insensitive)", required = true) q: String,
        @McpToolParam(description = "Comma-separated kinds: nodes,links,diagrams (default all)", required = false)
        kinds: String? = null,
        @McpToolParam(description = "Max hits (default 20, max 50)", required = false)
        limit: Int? = null
    ): String = ToolResult.run {
        api.getJson(
            "/api/v1/search/models/$modelId",
            mapOf(
                "q" to q,
                "kinds" to kinds?.takeIf { it.isNotBlank() },
                "limit" to limit
            )
        )
    }

    @McpTool(
        name = "search_notation",
        description = "Slim search of components/relations inside a notation by name. " +
            "Returns {kind,id,name,version,nodeTypeId|linkTypeId}. Prefer over get_notation_summary for discovery."
    )
    fun searchNotation(
        @McpToolParam(description = "Notation UUID", required = true) notationId: String,
        @McpToolParam(description = "Substring to match against component/relation name", required = true) q: String,
        @McpToolParam(description = "Comma-separated kinds: components,relations (default both)", required = false)
        kinds: String? = null,
        @McpToolParam(description = "Max hits (default 20, max 50)", required = false)
        limit: Int? = null
    ): String = ToolResult.run {
        api.getJson(
            "/api/v1/search/notations/$notationId",
            mapOf(
                "q" to q,
                "kinds" to kinds?.takeIf { it.isNotBlank() },
                "limit" to limit
            )
        )
    }
}
