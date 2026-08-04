# Agent Search Tools Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add arepos slim search endpoints and matching warchi-mcp tools so agents can find models/notations and in-model entities without downloading full list payloads.

**Architecture:** New `SearchService` + `SearchController` in arepos (`GET /api/v1/search/catalog`, `GET /api/v1/search/models/{modelId}`) return slim hit DTOs (no attrs). `warchi-mcp` adds thin `search_catalog` / `search_model` tools that call those endpoints. Existing list/get tools stay unchanged. Auth = same Cerbos + MCP allowlist as other reads.

**Tech Stack:** Kotlin, Spring Boot 3.5, Spring Data JPA, MockMvc/Testcontainers (arepos); Spring AI MCP annotations (warchi-mcp).

**Spec:** [`docs/superpowers/specs/2026-08-04-agent-search-tools-design.md`](../specs/2026-08-04-agent-search-tools-design.md)

**Branch:** continue `feat/warchi-mcp-api-keys` in both repos (or create `feat/agent-search-tools` in both if that branch is already merged — match existing local branch).

---

## File map

### arepos-server

| File | Role |
|------|------|
| `src/main/kotlin/ru/kavader/arepos/dto/search/SearchDtos.kt` | Request parsing helpers + slim response DTOs |
| `src/main/kotlin/ru/kavader/arepos/service/SearchService.kt` | Orchestrates queries, access checks, limit/kinds, hit assembly |
| `src/main/kotlin/ru/kavader/arepos/controller/SearchController.kt` | REST endpoints |
| `src/main/kotlin/ru/kavader/arepos/repository/NodesRepository.kt` | Add model-scoped name search |
| `src/main/kotlin/ru/kavader/arepos/repository/LinksRepository.kt` | Add model-scoped search by endpoint node names |
| `src/main/kotlin/ru/kavader/arepos/repository/DiagramsRepository.kt` | Add model-scoped name search (if missing convenience method) |
| `src/test/kotlin/ru/kavader/arepos/controller/SearchControllerTest.kt` | Integration tests |

**Note:** `Links` entity has **no `name` column**. Link hits always use `name: null`; matching is by source/target node names only (update design doc when implementing if still wrong).

### warchi-mcp

| File | Role |
|------|------|
| `src/main/kotlin/ru/kavader/warchimcp/tools/SearchTools.kt` | MCP tools |
| `docs/tools.md`, `docs/tools.ru.md` | Document new tools |
| `README.md`, `README.ru.md` | Mention search in tools blurb if present |

---

### Task 1: arepos — search DTOs

**Files:**
- Create: `arepos-server/src/main/kotlin/ru/kavader/arepos/dto/search/SearchDtos.kt`

- [ ] **Step 1: Add DTOs**

```kotlin
package ru.kavader.arepos.dto.search

import java.util.UUID

data class CatalogSearchResponse(
    val q: String,
    val limit: Int,
    val totalEstimate: Int,
    val hits: List<CatalogSearchHit>
)

data class CatalogSearchHit(
    val kind: String, // "model" | "notation"
    val id: UUID,
    val name: String,
    val version: String
)

data class ModelSearchResponse(
    val modelId: UUID,
    val q: String,
    val limit: Int,
    val totalEstimate: Int,
    val hits: List<ModelSearchHit>
)

data class ModelSearchHit(
    val kind: String, // "node" | "link" | "diagram"
    val id: UUID,
    val name: String?,
    val typeName: String? = null,
    val parentId: UUID? = null,
    val sourceId: UUID? = null,
    val targetId: UUID? = null,
    val sourceName: String? = null,
    val targetName: String? = null,
    val notationName: String? = null
)
```

- [ ] **Step 2: Commit**

```bash
cd /Users/nikolaygroznyh/Work/arepos-server
git add src/main/kotlin/ru/kavader/arepos/dto/search/SearchDtos.kt
git commit -m "$(cat <<'EOF'
feat(search): add slim search response DTOs

EOF
)"
```

---

### Task 2: arepos — repository queries

**Files:**
- Modify: `arepos-server/src/main/kotlin/ru/kavader/arepos/repository/NodesRepository.kt`
- Modify: `arepos-server/src/main/kotlin/ru/kavader/arepos/repository/LinksRepository.kt`
- Modify: `arepos-server/src/main/kotlin/ru/kavader/arepos/repository/DiagramsRepository.kt`

- [ ] **Step 1: Add node search by model + name**

Append to `NodesRepository`:

```kotlin
@Query(
    """
    SELECT n FROM Nodes n
    JOIN FETCH n.nodeType
    LEFT JOIN FETCH n.parentNode
    WHERE n.model.id = :modelId
      AND LOWER(n.name) LIKE LOWER(CONCAT('%', :q, '%'))
    ORDER BY n.name ASC, n.id ASC
    """
)
fun searchByModelIdAndName(
    @Param("modelId") modelId: UUID,
    @Param("q") q: String,
    pageable: Pageable
): List<Nodes>
```

Use `Pageable.ofSize(limit)` from the service (no separate count query required if service caps fetch at `limit` and sets `totalEstimate` to `hits.size` when `< limit`, or runs a cheap count — prefer `Page<Nodes>` if easier):

Alternative (preferred for `totalEstimate`):

```kotlin
@Query(
    """
    SELECT n FROM Nodes n
    WHERE n.model.id = :modelId
      AND LOWER(n.name) LIKE LOWER(CONCAT('%', :q, '%'))
    """
)
fun searchByModelIdAndName(
    @Param("modelId") modelId: UUID,
    @Param("q") q: String,
    pageable: Pageable
): Page<Nodes>
```

Then map with `nodeType.name` / `parentNode?.id` (ensure types initialized — join fetch in `@EntityGraph` or access inside transaction).

- [ ] **Step 2: Add link search by endpoint names**

Append to `LinksRepository`:

```kotlin
@Query(
    """
    SELECT l FROM Links l
    JOIN FETCH l.linkType
    JOIN FETCH l.source
    JOIN FETCH l.target
    WHERE l.model.id = :modelId
      AND (
        LOWER(l.source.name) LIKE LOWER(CONCAT('%', :q, '%'))
        OR LOWER(l.target.name) LIKE LOWER(CONCAT('%', :q, '%'))
      )
    ORDER BY l.source.name ASC, l.target.name ASC, l.id ASC
    """
)
fun searchByModelIdAndEndpointNames(
    @Param("modelId") modelId: UUID,
    @Param("q") q: String,
    pageable: Pageable
): List<Links>
```

(Or `Page<Links>` with matching countQuery without FETCH.)

- [ ] **Step 3: Add diagram search by model + name**

If `DiagramsRepository.findByFilters` already covers this with empty-optional filters, the service may call:

```kotlin
diagramsRepository.findByFilters(
    ownerId = null,
    modelId = modelId,
    nodeId = null,
    notationId = null,
    name = q,
    pageable = PageRequest.of(0, limit)
)
```

Only add a dedicated method if `findByFilters` requires non-blank name awkwardly — it already uses `LIKE %name%`. Prefer reusing it.

- [ ] **Step 4: Commit**

```bash
cd /Users/nikolaygroznyh/Work/arepos-server
git add src/main/kotlin/ru/kavader/arepos/repository/NodesRepository.kt \
  src/main/kotlin/ru/kavader/arepos/repository/LinksRepository.kt \
  src/main/kotlin/ru/kavader/arepos/repository/DiagramsRepository.kt
git commit -m "$(cat <<'EOF'
feat(search): add repository queries for model-scoped search

EOF
)"
```

---

### Task 3: arepos — SearchService

**Files:**
- Create: `arepos-server/src/main/kotlin/ru/kavader/arepos/service/SearchService.kt`

- [ ] **Step 1: Implement service**

```kotlin
package ru.kavader.arepos.service

import org.springframework.data.domain.PageRequest
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import ru.kavader.arepos.dto.search.*
import ru.kavader.arepos.repository.*
import ru.kavader.arepos.security.ResourceAccessService
import java.util.UUID

@Service
class SearchService(
    private val modelsRepository: ModelsRepository,
    private val notationsRepository: NotationsRepository,
    private val nodesRepository: NodesRepository,
    private val linksRepository: LinksRepository,
    private val diagramsRepository: DiagramsRepository,
    private val accessService: ResourceAccessService
) {
    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_LIMIT = 50
        val CATALOG_KINDS = setOf("models", "notations")
        val MODEL_KINDS = setOf("nodes", "links", "diagrams")
    }

    @Transactional(readOnly = true)
    fun searchCatalog(qRaw: String?, kindsRaw: String?, limitRaw: Int?): CatalogSearchResponse {
        val q = normalizeQuery(qRaw)
        val limit = normalizeLimit(limitRaw)
        val kinds = parseKinds(kindsRaw, CATALOG_KINDS)
        val pageable = PageRequest.of(0, limit)

        val hits = mutableListOf<CatalogSearchHit>()
        var total = 0

        if ("models" in kinds) {
            val page = modelsRepository.findByNameContainingIgnoreCase(q, pageable)
            val viewable = page.content.filter { accessService.canViewModel(it) }
            // Note: filter-after-fetch can underfill page; acceptable for v1 agent search.
            // Better: fetch larger page then filter — keep simple: filter page.content, totalEstimate = viewable.size if incomplete
            total += page.totalElements.toInt() // upper bound before ACL; see note below
            hits += viewable.map {
                CatalogSearchHit("model", it.id!!, it.name, it.version)
            }
        }
        if ("notations" in kinds) {
            val page = notationsRepository.findByNameContainingIgnoreCase(q, pageable)
            val viewable = page.content.filter { accessService.canViewNotation(it) }
            total += page.totalElements.toInt()
            hits += viewable.map {
                CatalogSearchHit("notation", it.id!!, it.name, it.version)
            }
        }

        val ordered = hits.sortedWith(compareBy({ it.kind }, { it.name.lowercase() }, { it.version }))
        val limited = ordered.take(limit)
        return CatalogSearchResponse(q, limit, totalEstimate = minOf(total, ordered.size).let {
            // Prefer: ordered.size if we loaded all candidates into hits without per-kind limit stacking issues.
            // v1: totalEstimate = ordered.size before take, or limited.size + truncated flag.
            ordered.size
        }, hits = limited)
    }

    @Transactional(readOnly = true)
    fun searchModel(modelId: UUID, qRaw: String?, kindsRaw: String?, limitRaw: Int?): ModelSearchResponse {
        val model = modelsRepository.findById(modelId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Model $modelId not found")
        }
        accessService.requireCanViewModel(model)

        val q = normalizeQuery(qRaw)
        val limit = normalizeLimit(limitRaw)
        val kinds = parseKinds(kindsRaw, MODEL_KINDS)
        val pageable = PageRequest.of(0, limit)

        val hits = mutableListOf<ModelSearchHit>()

        if ("nodes" in kinds) {
            val page = nodesRepository.searchByModelIdAndName(modelId, q, pageable)
            hits += page.content.map { n ->
                ModelSearchHit(
                    kind = "node",
                    id = n.id!!,
                    name = n.name,
                    typeName = n.nodeType.name,
                    parentId = n.parentNode?.id
                )
            }
        }
        if ("links" in kinds) {
            val links = linksRepository.searchByModelIdAndEndpointNames(modelId, q, pageable)
            // if Page: links.content
            val list = if (links is org.springframework.data.domain.Page<*>) {
                @Suppress("UNCHECKED_CAST")
                (links as org.springframework.data.domain.Page<ru.kavader.arepos.model.Links>).content
            } else {
                links
            }
            hits += list.map { l ->
                ModelSearchHit(
                    kind = "link",
                    id = l.id!!,
                    name = null,
                    typeName = l.linkType.name,
                    sourceId = l.source.id,
                    targetId = l.target.id,
                    sourceName = l.source.name,
                    targetName = l.target.name
                )
            }
        }
        if ("diagrams" in kinds) {
            val page = diagramsRepository.findByFilters(null, modelId, null, null, q, pageable)
            hits += page.content.map { d ->
                ModelSearchHit(
                    kind = "diagram",
                    id = d.id!!,
                    name = d.name,
                    notationName = d.notation?.name
                )
            }
        }

        // Stable kind order: nodes, links, diagrams (already appended in that order), then take limit
        val limited = hits.take(limit)
        return ModelSearchResponse(
            modelId = modelId,
            q = q,
            limit = limit,
            totalEstimate = hits.size,
            hits = limited
        )
    }

    private fun normalizeQuery(qRaw: String?): String {
        val q = qRaw?.trim().orEmpty()
        if (q.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Query parameter 'q' must be non-empty")
        }
        return q
    }

    private fun normalizeLimit(limitRaw: Int?): Int {
        val limit = limitRaw ?: DEFAULT_LIMIT
        if (limit < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be >= 1")
        }
        return minOf(limit, MAX_LIMIT)
    }

    private fun parseKinds(kindsRaw: String?, allowed: Set<String>): Set<String> {
        if (kindsRaw.isNullOrBlank()) return allowed
        val parsed = kindsRaw.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toSet()
        val unknown = parsed - allowed
        if (unknown.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Unknown kinds: ${unknown.joinToString()}. Allowed: ${allowed.joinToString()}"
            )
        }
        if (parsed.isEmpty()) return allowed
        return parsed
    }
}
```

**Implementation cleanup for the engineer:** Do not leave the `links is Page` hack — pick `Page` **or** `List` in the repository and write clean Kotlin. For catalog ACL underfill, document in code comment that v1 filters the first page; optionally fetch `PageRequest.of(0, limit * 3)` before filter if tests show underfill. Prefer `accessService.canViewModels(page.content)` batch API if available.

Fix `totalEstimate` to: number of hits assembled **before** `.take(limit)` (or sum of page.totalElements when not ACL-filtering mid-page). Spec: exact when cheap.

- [ ] **Step 2: Commit**

```bash
cd /Users/nikolaygroznyh/Work/arepos-server
git add src/main/kotlin/ru/kavader/arepos/service/SearchService.kt
git commit -m "$(cat <<'EOF'
feat(search): add SearchService for catalog and in-model search

EOF
)"
```

---

### Task 4: arepos — SearchController

**Files:**
- Create: `arepos-server/src/main/kotlin/ru/kavader/arepos/controller/SearchController.kt`

- [ ] **Step 1: Add controller**

```kotlin
package ru.kavader.arepos.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import ru.kavader.arepos.dto.search.CatalogSearchResponse
import ru.kavader.arepos.dto.search.ModelSearchResponse
import ru.kavader.arepos.service.SearchService
import java.util.UUID

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "Slim search endpoints for agents and MCP")
class SearchController(
    private val searchService: SearchService
) {

    @GetMapping("/catalog")
    @Operation(summary = "Search models and notations by name (slim hits)")
    fun searchCatalog(
        @RequestParam q: String,
        @RequestParam(required = false) kinds: String?,
        @RequestParam(required = false) limit: Int?
    ): CatalogSearchResponse = searchService.searchCatalog(q, kinds, limit)

    @GetMapping("/models/{modelId}")
    @Operation(summary = "Search nodes, links, diagrams inside a model (slim hits)")
    fun searchModel(
        @PathVariable modelId: UUID,
        @RequestParam q: String,
        @RequestParam(required = false) kinds: String?,
        @RequestParam(required = false) limit: Int?
    ): ModelSearchResponse = searchService.searchModel(modelId, q, kinds, limit)
}
```

Ensure MCP scope filter treats these as read paths (same `/api/v1/**` + `models:read` — verify `McpScopeFilter` does not block GET search; if it uses path prefixes, add `/api/v1/search/**` to read allowlist if needed).

- [ ] **Step 2: Check McpScopeFilter**

Open `McpScopeFilter.kt`. If it only allows known prefixes, add:

- `/api/v1/search/` → requires `models:read`

- [ ] **Step 3: Commit**

```bash
cd /Users/nikolaygroznyh/Work/arepos-server
git add src/main/kotlin/ru/kavader/arepos/controller/SearchController.kt \
  src/main/kotlin/ru/kavader/arepos/security/McpScopeFilter.kt
git commit -m "$(cat <<'EOF'
feat(search): expose catalog and model search REST endpoints

EOF
)"
```

---

### Task 5: arepos — SearchControllerTest (TDD-friendly)

**Files:**
- Create: `arepos-server/src/test/kotlin/ru/kavader/arepos/controller/SearchControllerTest.kt`

- [ ] **Step 1: Write failing integration tests**

Follow `NodesControllerTest` / `ControllerIntegrationTest` patterns (`@SpringBootTest`, `@AutoConfigureMockMvc`, `.withAuth(userId)`).

Cover at least:

1. `GET /api/v1/search/catalog?q=...` returns model hit with `kind=model`, no attrs field.
2. Empty `q` → 400.
3. `limit=100` capped to 50 (or returns ≤50 hits).
4. `GET /api/v1/search/models/{id}?q=...` finds node by partial name.
5. Same endpoint finds link when source/target name matches.
6. Other user’s model → 403.
7. Unknown `kinds` → 400.

Skeleton:

```kotlin
@SpringBootTest
@AutoConfigureMockMvc
class SearchControllerTest : ControllerIntegrationTest() {

    @Autowired lateinit var mockMvc: MockMvc
    // repos...

    @Test
    fun `catalog search returns slim model hits`() {
        // persist owner + model name "LemanaPro"
        mockMvc.perform(get("/api/v1/search/catalog").param("q", "lema").withAuth(owner.id!!))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.hits[0].kind").value("model"))
            .andExpect(jsonPath("$.hits[0].name").value("LemanaPro"))
            .andExpect(jsonPath("$.hits[0].attrs").doesNotExist())
    }

    @Test
    fun `rejects blank q`() {
        mockMvc.perform(get("/api/v1/search/catalog").param("q", "  ").withAuth(owner.id!!))
            .andExpect(status().isBadRequest)
    }

    // ... model search node/link, 403, kinds validation
}
```

- [ ] **Step 2: Run tests (expect fail before service wired; pass after Tasks 3–4)**

```bash
cd /Users/nikolaygroznyh/Work/arepos-server
./gradlew test --tests "ru.kavader.arepos.controller.SearchControllerTest"
```

Expected: PASS when Tasks 1–4 done.

- [ ] **Step 3: Commit**

```bash
git add src/test/kotlin/ru/kavader/arepos/controller/SearchControllerTest.kt
git commit -m "$(cat <<'EOF'
test(search): cover catalog and in-model search endpoints

EOF
)"
```

---

### Task 6: Fix design note — links have no name

**Files:**
- Modify: `warchi-mcp/docs/superpowers/specs/2026-08-04-agent-search-tools-design.md`

- [ ] **Step 1: Update matching rules**

Replace link matching bullet with: links match when source or target **node name** contains `q`; hit `name` is always `null`.

- [ ] **Step 2: Commit in warchi-mcp**

```bash
cd /Users/nikolaygroznyh/Work/warchi-mcp
git add docs/superpowers/specs/2026-08-04-agent-search-tools-design.md
git commit -m "$(cat <<'EOF'
docs(search): clarify link search matches endpoint node names

EOF
)"
```

---

### Task 7: warchi-mcp — SearchTools

**Files:**
- Create: `warchi-mcp/src/main/kotlin/ru/kavader/warchimcp/tools/SearchTools.kt`

- [ ] **Step 1: Add tools component**

Mirror `ModelReadTools` style:

```kotlin
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
    ): String = runTool {
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
    ): String = runTool {
        api.getJson(
            "/api/v1/search/models/$modelId",
            mapOf(
                "q" to q,
                "kinds" to kinds?.takeIf { it.isNotBlank() },
                "limit" to limit
            )
        )
    }

    private fun runTool(block: () -> Any?): String =
        try {
            ToolResult.ok(block())
        } catch (ex: Exception) {
            ToolResult.error(ex)
        }
}
```

Spring will pick up `@Component` automatically (same package scan as other tools).

- [ ] **Step 2: Compile**

```bash
cd /Users/nikolaygroznyh/Work/warchi-mcp
./gradlew test
```

Expected: PASS (no new unit tests required if ToolResult already covered; optional smoke test later).

- [ ] **Step 3: Commit**

```bash
git add src/main/kotlin/ru/kavader/warchimcp/tools/SearchTools.kt
git commit -m "$(cat <<'EOF'
feat(mcp): add search_catalog and search_model tools

EOF
)"
```

---

### Task 8: Docs

**Files:**
- Modify: `warchi-mcp/docs/tools.md`
- Modify: `warchi-mcp/docs/tools.ru.md`
- Modify: `warchi-mcp/README.md` / `README.ru.md` (short mention under tools if there is a list)

- [ ] **Step 1: Document tools**

Add rows under read tools:

| `search_catalog` | Slim search models/notations by name | `q`, `kinds?`, `limit?` |
| `search_model` | Slim search nodes/links/diagrams in model | `modelId`, `q`, `kinds?`, `limit?` |

Add a short “Agent tips” subsection: prefer search → get; do not list entire model for name lookup.

- [ ] **Step 2: Commit**

```bash
cd /Users/nikolaygroznyh/Work/warchi-mcp
git add docs/tools.md docs/tools.ru.md README.md README.ru.md
git commit -m "$(cat <<'EOF'
docs(mcp): document agent search tools

EOF
)"
```

---

### Task 9: Deploy & manual verify (local OrbStack)

- [ ] **Step 1: Redeploy arepos-server then warchi-mcp**

```bash
cd /Users/nikolaygroznyh/Work/arepos-server
SKIP_CONFIRM=true NAMESPACE=arch ./scripts/deploy.sh

cd /Users/nikolaygroznyh/Work/warchi-mcp
SKIP_CONFIRM=true NAMESPACE=arch ./scripts/deploy.sh
```

- [ ] **Step 2: Smoke via curl (with user JWT or through MCP)**

```bash
# after login / with token
curl -sS "http://arepos-server.arch.svc.cluster.local:8080/api/v1/search/catalog?q=lema" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

- [ ] **Step 3: In Cursor MCP**, call `search_catalog` then `search_model`; confirm small payloads.

---

## Execution notes

- Prefer implementing Tasks 5 tests early (after DTOs) if doing strict TDD; otherwise Tasks 1→5 in order is fine if tests are written against the finished API in the same PR.
- Do not expand to attrs FTS in this plan.
- Do not change existing `list_*` response shapes.
- Commits only when the user (or executing agent with commit permission) asks — if running autonomously under subagent-driven-development, follow that skill’s commit cadence; otherwise pause before commit if user forbids commits.

## Done when

- `SearchControllerTest` green in arepos
- `./gradlew test` green in warchi-mcp
- Docs list `search_catalog` / `search_model`
- Manual Cursor call returns slim hits without attrs
