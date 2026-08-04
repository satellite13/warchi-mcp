# MCP ensure_node / ensure_diagram Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add idempotent `ensure_node` and `ensure_diagram` (arepos + thin MCP) so agent retries do not duplicate nodes/diagrams.

**Architecture:** Mirror `LinkEnsureService` / `POST /links/ensure`. New arepos ensure services + controller endpoints; MCP tools pass-through; docs update.

**Tech Stack:** Kotlin/Spring Boot (arepos), Spring AI MCP (warchi-mcp), MockMvc/TestContainers tests.

**Spec:** [2026-08-04-mcp-ensure-node-diagram-design.md](../specs/2026-08-04-mcp-ensure-node-diagram-design.md)

**Branches:** `feat/mcp-ensure-node-diagram` in arepos-server and warchi-mcp.

---

## File map

### arepos-server

| File | Role |
|------|------|
| `dto/model/EnsureNodeResponse` / `EnsureDiagramResponse` (in DiagramInstancesDtos.kt or new EnsureDtos.kt) | Response envelopes |
| `dto/model/AmbiguousNodeException` | 409 with candidates |
| `GlobalExceptionHandler.kt` | Handle `AMBIGUOUS_NODE` |
| `NodesRepository.kt` | Query by model + parent + name ignore case |
| `service/NodeEnsureService.kt` | Find-or-create node |
| `service/DiagramEnsureService.kt` | Find-or-create diagram (latest by name) |
| `NodesController.kt` | `POST /ensure` |
| `DiagramsController.kt` | `POST /ensure` |
| `McpEnsureNodeDiagramControllerTest.kt` | Integration tests |

### warchi-mcp

| File | Role |
|------|------|
| `ModelWriteTools.kt` | `ensure_node`, `ensure_diagram` |
| `ToolResult.kt` | Classify `AMBIGUOUS_NODE` |
| `docs/tools.md`, `docs/tools.ru.md`, `AGENTS.md` | Docs |

---

### Task 1: arepos — failing tests for ensure_node / ensure_diagram

**Files:**
- Create: `arepos-server/src/test/kotlin/ru/kavader/arepos/controller/McpEnsureNodeDiagramControllerTest.kt`

- [ ] **Step 1: Write integration tests** covering:
  - ensure_node creates then second call `created=false` same id
  - same name under different parents → two nodes / ensure respects parent
  - two nodes same parent+name → 409 `AMBIGUOUS_NODE`
  - ensure_diagram creates with empty instances; second call `created=false`
  - two diagram versions same name → returns latest

- [ ] **Step 2: Run tests — expect fail (404/no mapping)**

```bash
cd /Users/nikolaygroznyh/Work/arepos-server
./gradlew test --tests "ru.kavader.arepos.controller.McpEnsureNodeDiagramControllerTest"
```

Expected: FAIL (endpoint missing)

- [ ] **Step 3: Commit tests**

```bash
git add src/test/kotlin/ru/kavader/arepos/controller/McpEnsureNodeDiagramControllerTest.kt
git commit -m "test: add failing coverage for ensure_node and ensure_diagram"
```

---

### Task 2: arepos — NodeEnsureService + endpoint

**Files:**
- Modify: `NodesRepository.kt` — add finder
- Create: `NodeEnsureService.kt`
- Modify: `NodesController.kt` — `POST /ensure`
- Modify: DTOs + `GlobalExceptionHandler.kt`

- [ ] **Step 1: Repository method**

```kotlin
fun findByModel_IdAndParentNode_IdAndNameIgnoreCase(
    modelId: UUID,
    parentNodeId: UUID,
    name: String
): List<Nodes>

@Query("""
  SELECT n FROM Nodes n
  WHERE n.model.id = :modelId
    AND n.parentNode IS NULL
    AND LOWER(n.name) = LOWER(:name)
""")
fun findRootByModelIdAndNameIgnoreCase(
    @Param("modelId") modelId: UUID,
    @Param("name") name: String
): List<Nodes>
```

- [ ] **Step 2: Implement `NodeEnsureService.ensure`**
  - require edit model
  - resolve matches by parent null vs id
  - 0 → create via notation binding path (reuse NodesController create logic or extract shared create helper from controller into service — prefer extract create into service used by both create and ensure, same as links)
  - 1 → `{node, created:false}`
  - >1 → `AmbiguousNodeException`

- [ ] **Step 3: Wire `POST /api/v1/nodes/ensure`**

- [ ] **Step 4: Exception handler for `AMBIGUOUS_NODE`**

- [ ] **Step 5: Run node-related tests — pass**

```bash
./gradlew test --tests "ru.kavader.arepos.controller.McpEnsureNodeDiagramControllerTest"
```

- [ ] **Step 6: Commit**

```bash
git commit -m "feat: add POST /nodes/ensure find-or-create"
```

---

### Task 3: arepos — DiagramEnsureService + endpoint

**Files:**
- Create: `DiagramEnsureService.kt`
- Modify: `DiagramsController.kt`

- [ ] **Step 1: Implement ensure**
  - `findByModelIdAndNameAndDeletedFalse`
  - if non-empty: pick max with `diagramLifecycleService.compareDiagramVersions`
  - else: create with version default `1.0.0`, attrs default `{"instances":{"nodes":[],"edges":[]}}` if null; reuse createDiagram auth/checks

- [ ] **Step 2: `POST /api/v1/diagrams/ensure`**

- [ ] **Step 3: Run all ensure tests — pass**

- [ ] **Step 4: Commit**

```bash
git commit -m "feat: add POST /diagrams/ensure find-or-create latest by name"
```

---

### Task 4: warchi-mcp tools + docs

**Files:**
- Modify: `ModelWriteTools.kt`, `ToolResult.kt`, `ToolResultTest.kt`
- Modify: `docs/tools.md`, `docs/tools.ru.md`, `AGENTS.md`

- [ ] **Step 1: Add `ensure_node` / `ensure_diagram` tools** (mirror create_* args)

- [ ] **Step 2: Classify `AMBIGUOUS_NODE` in ToolResult + unit test**

- [ ] **Step 3: Update docs / agent recipe to prefer ensure_***

- [ ] **Step 4: Run MCP tests**

```bash
cd /Users/nikolaygroznyh/Work/warchi-mcp && ./gradlew test
```

- [ ] **Step 5: Commit**

```bash
git commit -m "feat: add ensure_node and ensure_diagram MCP tools"
```

---

### Task 5: Local k8s deploy (optional, if requested)

```bash
cd /Users/nikolaygroznyh/Work/arepos-server && SKIP_CONFIRM=true ./scripts/deploy.sh
cd /Users/nikolaygroznyh/Work/warchi-mcp && SKIP_CONFIRM=true IMAGE_TAG="0.1.0-$(git rev-parse --short HEAD)" ./scripts/deploy.sh
```

---

## Self-review

1. Spec coverage: ensure_node, ensure_diagram, AMBIGUOUS_NODE, latest diagram, MCP wrappers, docs — covered.
2. No placeholders.
3. Matches ensure_link patterns already in repo.
