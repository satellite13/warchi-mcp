# Design: Agent-oriented search APIs (arepos + warchi-mcp)

Date: 2026-08-04  
Status: draft (awaiting review)  
Repos: arepos-server, warchi-mcp

## Problem

MCP v1 exposes list/get tools that return raw arepos JSON. Agents often:

1. Cannot efficiently find a model by partial name (or must page large catalogs).
2. Must `list_nodes` / `list_links` / `list_diagrams` for an entire model and scan locally.
3. Pay tokens for full `attrs`, canvas JSON, and unrelated fields.

`list_models` already has an optional `name` filter, but in-model lists do not expose useful search in MCP, and response payloads are not agent-slim.

## Goals

- Let agents find models/notations and entities inside a model with **small, structured hits**.
- Keep **full detail** behind existing `get_*` tools (attrs, diagram canvas, etc.).
- Enforce the same auth as today: user JWT / MCP `mcp_access` + scopes + model allowlist + Cerbos.
- Prefer **server-side filtering** in arepos; MCP stays a thin wrapper.

## Non-goals (v1)

- Full-text / RAG over `attrs`, descriptions, or diagram canvas.
- Graph traversal / “neighbors of node” queries (later).
- Changing write tools or batch-save.
- Vector search, embeddings, ranking models beyond simple substring match.
- Breaking changes to existing list/get endpoints (they stay as-is for UI/clients).

## Decisions (locked)

| Topic | Choice |
|-------|--------|
| Where to implement | **arepos first**, MCP thin tools |
| Search coverage | Catalog (models + notations) **and** in-model (nodes, links, diagrams) |
| Attrs search | **No** in v1 |
| MCP surface | Two tools: `search_catalog`, `search_model` |
| Response shape | Slim hit DTOs, no `attrs` |

## Approach

Dedicated search endpoints in arepos with slim DTOs; MCP maps 1:1.

```text
Agent
  -> MCP search_catalog / search_model
    -> arepos GET /api/v1/search/...
      -> repositories (ILIKE / containing, scoped by access)
  -> MCP get_* only for selected ids
```

## Arepos API

### Auth & access

- Same as other `/api/v1/*` reads: authenticated principal (cookie session, Bearer user JWT, or MCP JWT).
- Requires ability to **view** the resource (Cerbos + ownership/shares).
- MCP keys: need `models:read`; catalog/model hits must respect **model allowlist** when present (same as `ResourceAccessService` today).
- Notations: same rules as `GET /notations` (including model-editor notation access if applicable — keep consistent with existing list semantics for the caller).

### `GET /api/v1/search/catalog`

Query params:

| Param | Type | Default | Notes |
|-------|------|---------|-------|
| `q` | string | required (min 1 after trim) | Case-insensitive substring on **name** |
| `kinds` | csv | `models,notations` | Subset of `models`, `notations` |
| `limit` | int | `20` | Hard cap `50` |

Response:

```json
{
  "q": "lema",
  "limit": 20,
  "totalEstimate": 3,
  "hits": [
    {
      "kind": "model",
      "id": "...",
      "name": "LemanaPro",
      "version": "1.0.0"
    },
    {
      "kind": "notation",
      "id": "...",
      "name": "ArchiMate",
      "version": "3.2.0"
    }
  ]
}
```

Rules:

- Search only non-deleted entities (same as normal list).
- Models: match `name` containing `q` (ignore case); return matching versions the caller can view (do not collapse to one row per name unless we already do that elsewhere — **return each version as a separate hit** so agents can pick `modelId`).
- Notations: same by `name` (+ `version` on hit).
- Order: name asc, then version desc (semver if cheap; else string desc).
- `totalEstimate`: number of hits before limit truncation (or exact count if cheap); if expensive, `hits.length` + `truncated: true` is acceptable — prefer exact count when using SQL `count` with same predicate.
- Empty `q` after trim → `400` with clear message.

### `GET /api/v1/search/models/{modelId}`

Path: `modelId` required. Caller must be able to view the model (and pass allowlist).

Query params:

| Param | Type | Default | Notes |
|-------|------|---------|-------|
| `q` | string | required | Substring on entity **name** |
| `kinds` | csv | `nodes,links,diagrams` | Subset of those three |
| `limit` | int | `20` | Hard cap `50` |

Response:

```json
{
  "modelId": "...",
  "q": "crm",
  "limit": 20,
  "totalEstimate": 12,
  "hits": [
    {
      "kind": "node",
      "id": "...",
      "name": "CRM System",
      "typeName": "Application Component",
      "parentId": "..."
    },
    {
      "kind": "link",
      "id": "...",
      "name": null,
      "typeName": "Serving",
      "sourceId": "...",
      "targetId": "...",
      "sourceName": "CRM System",
      "targetName": "Billing DB"
    },
    {
      "kind": "diagram",
      "id": "...",
      "name": "CRM context",
      "notationName": "ArchiMate"
    }
  ]
}
```

Matching rules:

- **nodes**: `name` ILIKE `%q%` within model.
- **diagrams**: `name` ILIKE `%q%` within model.
- **links**: `Links` has no `name` column; match if source or target node **name** contains `q`. Hit `name` is always `null`; agents use `sourceName` / `targetName` / `typeName`. Still no attrs search.
- Interleave or group by kind: return **grouped by kind in stable order** `nodes`, then `links`, then `diagrams`, each group name-sorted, then apply global `limit` across the combined list (document this). Simpler alternative: per-kind soft budgets (`limit` split evenly) — **choose global limit after concat in kind order** for predictable agent UX.
- Link hits without a name: `name: null` is fine; agents use `sourceName`/`targetName`/`typeName`.

### Implementation notes (arepos)

- New controller e.g. `SearchController` under `/api/v1/search`.
- Prefer repository query methods / JPQL with `ContainingIgnoreCase` and model scope; avoid loading full entities into memory when possible.
- Map to slim response records (no JSON `attrs` columns in SELECT list if feasible).
- Tests: controller/repository tests for access deny, allowlist, empty q, limit cap, link match via endpoint names.

## MCP tools

### `search_catalog`

- Scope: `models:read`
- Args: `q` (required), `kinds?` (default models+notations), `limit?` (default 20)
- Calls: `GET /api/v1/search/catalog`
- Returns: existing ToolResult envelope with arepos JSON as `data` (already slim)

### `search_model`

- Scope: `models:read`
- Args: `modelId` (required), `q` (required), `kinds?`, `limit?`
- Calls: `GET /api/v1/search/models/{modelId}`
- Returns: ToolResult envelope

### Tool descriptions (agent guidance)

Descriptions must tell the model:

1. Use `search_catalog` to resolve `modelId` / notation ids.
2. Use `search_model` before bulk `list_*`.
3. Use `get_*` only for entities that need attrs or full payload.
4. Keep `limit` small (default 20).

### Compatibility

- Keep existing `list_*` / `get_*` tools unchanged.
- Optionally later: add `name` filter passthrough on `list_nodes` etc. — **not required** if search tools cover the agent path.

## Agent workflow (target)

```text
search_catalog(q="Lemana")
  -> pick modelId
search_model(modelId, q="billing")
  -> pick node/link/diagram ids
get_node / get_link / get_diagram as needed
create_* / update_* / batch_save_model for writes
```

## Docs & changelog

- Update `warchi-mcp/docs/tools.md` + `tools.ru.md`
- Short note in arepos `docs/` or API collaboration doc if there is a natural place
- README example can mention search tools in the tools section

## Rollout

1. arepos: migration not required (read-only queries); ship endpoints + tests; deploy.
2. warchi-mcp: add tools + docs; deploy.
3. Manual check from Cursor against local OrbStack.

## Open points (resolved defaults)

| Point | Default |
|-------|---------|
| Min `q` length | 1 (trimmed) |
| Default / max limit | 20 / 50 |
| Model versions in catalog | One hit per version row |
| Link matching | source/target node names only (no link name field) |
| Hit ordering in model search | kinds order nodes → links → diagrams, then name |

## Success criteria

- Agent can locate a model and a node by partial name without downloading full node/link lists.
- Typical `search_*` response stays small (tens of hits, no attrs/canvas).
- Unauthorized / allowlisted-out models return the same denial semantics as other reads (403 / empty as today — **prefer consistent 403 when modelId known but forbidden**).
