# Landscape recipe (happy-path)

Russian: [`landscape-recipe.ru.md`](landscape-recipe.ru.md)

Step-by-step scenario for creating or extending an architecture landscape via MCP tools.
A short overview also lives in [`tools.md`](tools.md) (Happy-path section).

All tools return a JSON envelope `{ "ok": true|false, ... }` — see the tools catalogue.

## Goal

1. Nodes and links in the **model** (semantic graph)
2. A **diagram** with canvas instances
3. Optional **wiki** description

Prefer **`ensure_*`** over **`create_*`** (`create_node` / `create_link` / `create_diagram`) — ensure calls are idempotent and safe on retries; bare `create_*` always inserts and can duplicate on retry.
`batch_save_model` remains the escape hatch for atomic multi-entity edits.

## Prerequisites

| Need | Why |
|------|-----|
| API key `warchi_ak_…` | Client → MCP (`Authorization` / `X-Api-Key`) |
| Scope `models:read` | Discovery (`search_*`, `get_*`, `list_wiki`) |
| Scope `models:write` | `ensure_*`, `add_diagram_instances`, `ensure_wiki`, … |
| Access to the target model | `mode=all` or a grant on `modelId` |
| File storage (MinIO) on arepos | Wiki create/update only |

Auth details: [`auth.md`](auth.md).

## Flow

```text
0. Discovery
   search_catalog  →  modelId, notationId
   search_notation →  componentName / relationName  (or ids)
   search_model?   →  existing nodes/links/diagrams

1. ensure_node  ×N   (notationId + componentName|componentId)
2. ensure_link  ×M   (notationId + relationName|relationId)
3. ensure_diagram
4. add_diagram_instances  (nodes by modelNodeId, edges by modelLinkId)
5. ensure_wiki?  (optional)
```

Rough size: ~5 call **kinds**; actual call count grows with N nodes and M links.

## Phase 0 — Discovery

### 0.1 Model and notation

```
search_catalog(q="<name>", kinds="models,notations", limit=20)
```

Keep `modelId` and `notationId`. Skip if ids are already known.

Default `kinds` is both (`models,notations`). Default `limit` is 20 (max 50).

### 0.2 Notation element types

```
search_notation(notationId, q="<component>", kinds="components")
search_notation(notationId, q="<relation>", kinds="relations")
```

Hits are slim: `{kind,id,name,version,nodeTypeId|linkTypeId}` — no full attrs.

Do **not** use `get_notation_summary` for discovery (heavy response).

Ambiguous name → `409` / `AMBIGUOUS_NOTATION_ELEMENT` with `candidates`:
narrow `q` or pass `componentId` / `relationId` instead of a name.

### 0.3 Existing model elements (optional)

```
search_model(modelId, q="<name>", kinds="nodes,links,diagrams")
```

Links match by source/target node names (links have no name field).
Hits omit attrs and canvas — call `get_*` only for selected ids.

## Phase 1 — Semantic graph

### 1.1 Nodes

```
ensure_node(
  modelId,
  name,
  notationId,
  componentName   // or componentId; nodeTypeId only when no binding
  [, parentNodeId]
)
```

Response: `{ node, created }` (`created: true|false`).

| Rule | Detail |
|------|--------|
| Match key | `modelId + parentNodeId + name` (case-insensitive) |
| Notation binding | Applies **on create only**; a hit does not mutate the node |
| Ambiguity | Multiple matches → `409` / `AMBIGUOUS_NODE` + `candidates` |
| Races | No DB unique constraint — concurrent dual-ensure may create duplicates |

Keep `node.id` for links and canvas.

### 1.2 Links

After nodes:

```
ensure_link(
  modelId,
  sourceId,
  targetId,
  notationId,
  relationName   // or relationId; linkTypeId only when no binding
)
```

Response: `{ link, created }`.

| Rule | Detail |
|------|--------|
| Match key | `modelId + sourceId + targetId + linkTypeId` (direction-strict) |
| Type resolve | `notationId` + `relationName`/`relationId` → `linkTypeId` + `attrs.notationRelations` |
| Races | Same as nodes — concurrent dual-create may race |

Keep `link.id` for `edgesJson`.

## Phase 2 — Diagram and canvas

### 2.1 Diagram

```
ensure_diagram(modelId, name, notationId [, nodeId] [, version] [, attrs])
```

Response: `{ diagram, created }`.

| Rule | Detail |
|------|--------|
| Match key | `modelId + name` → latest non-deleted version |
| Create defaults | `version=1.0.0`, empty canvas `{"instances":{"nodes":[],"edges":[]}}` |
| Hit | Fields are **not** updated |
| Races | Dual concurrent ensure may race |

Keep `diagram.id` and `diagram.updatedAt` (for optimistic concurrency).

### 2.2 Canvas instances

```
add_diagram_instances(
  diagramId,
  nodesJson='[{"modelNodeId":"<uuid>","x":100,"y":200,"width":120,"height":60}, ...]',
  edgesJson='[{"modelLinkId":"<uuid>"}, ...]',
  baseUpdatedAt="<ISO-8601>"   // recommended
)
```

| Rule | Detail |
|------|--------|
| Node merge | By `modelNodeId` (upsert position / size) |
| Edge merge | By `modelLinkId`; source/target instance ids auto-resolve |
| Deletion | Does not remove instances absent from the request |
| Conflict | Stale `baseUpdatedAt` → `409` / `DIAGRAM_CONFLICT` |

On `DIAGRAM_CONFLICT`: `get_diagram` → fresh `updatedAt` → retry merge.

v1 has no `layout_diagram` — set coordinates manually (grid / agent heuristic).

Prefer `add_diagram_instances` for incremental canvas edits over full replace via `update_diagram`.

## Phase 3 — Wiki (optional)

Prefer **`ensure_wiki`** over `create_wiki` for retries (idempotent).

```
ensure_wiki(
  entityKind="diagram",   // model|diagram|node|component|notation|nodeType|linkType
  entityId,
  content,
  [, modelId] [, notationId] [, filename]
)
```

Behavior:
- If `attrs.documentFileId` is set (or exactly one document ref exists) → `update_wiki` that file → `{fileId, created:false, updated:true}`
- If none exists → same as `create_wiki` → `{fileId, created:true, updated:false}`
- If multiple refs and no `documentFileId` → `AMBIGUOUS_WIKI`

Requires `models:write` and file storage on arepos.
Pass `modelId` for diagram/node when needed; `notationId` for component.

Manual update path (still valid):

```
list_wiki(diagramId=...) → get_wiki(fileId=...) → update_wiki(fileId, content)
```

## Error handling

| Code / status | When | Action |
|---------------|------|--------|
| `AMBIGUOUS_NOTATION_ELEMENT` | Several components/relations share a name | Narrow search or pass an id |
| `AMBIGUOUS_NODE` | Several nodes match model+parent+name | Pick id from `candidates` or use a unique name |
| `AMBIGUOUS_WIKI` | Several document refs and no `documentFileId` | Set `documentFileId` or call `update_wiki` with an explicit `fileId` |
| `DIAGRAM_CONFLICT` | Stale `baseUpdatedAt` | Re-read diagram and retry merge |
| `BATCH_SAVE_CONFLICT` | Conflict in batch-save | No silent overwrite unless explicit `force=true` |
| `409` on `update_diagram` | Not latest by name, or duplicate name+version | Prefer `ensure_diagram` + `add_diagram_instances` |
| `missing_scope` / `model_not_allowed` | Missing rights | New API key with required scopes/grants |
| `arepos_error` | Other arepos failure | Inspect `status` / `message` / `details` |

**Retries:** repeat the same `ensure_*` calls safely. Do not switch to `create_*` without a reason.

Diagram edit locks (`/api/v1/diagram-locks/*`, `LOCKED_BY_OTHER`) are **not** called by MCP tools; a UI editor may change the same diagram concurrently.

## When to leave the happy-path

| Situation | Tool |
|-----------|------|
| Atomic multi-entity change | `batch_save_model` |
| Need customProperties definitions on a notation component | `ensure_custom_properties` **before** bulk `ensure_node` (requires notation edit permission) |
| Change attrs / name of an existing node or link | `update_node` / `update_link` |
| Delete a node or link | `delete_node` / `delete_link` |
| Full replace of diagram attrs | `update_diagram` (watch for 409) |

## Example: 3 nodes and 2 links

| # | Tool | Intent |
|---|------|--------|
| 1 | `search_catalog` | Resolve model and notation |
| 2 | `search_notation` | Component (e.g. Application Component) |
| 3–5 | `ensure_node` ×3 | Nodes A, B, C with `componentName` |
| 6 | `search_notation` | Relation (e.g. Serving) |
| 7–8 | `ensure_link` ×2 | A→B, B→C |
| 9 | `ensure_diagram` | Landscape diagram name |
| 10 | `add_diagram_instances` | 3 nodes + 2 edges + `baseUpdatedAt` |
| 11 | `ensure_wiki` | Diagram description (optional) |

## Completion checklist

- [ ] Nodes created/found; `node.id` values kept
- [ ] Links between intended source/target; `link.id` values kept
- [ ] Diagram exists (`ensure_diagram`)
- [ ] Canvas has the intended instances (`add_diagram_instances`)
- [ ] No unresolved `AMBIGUOUS_*` / `DIAGRAM_CONFLICT`
- [ ] Wiki created or updated (if needed)

## Out of this recipe (v1)

Do not plan on: notation CRUD (except `ensure_custom_properties`), shares, OEF import, admin tools, stdio, `layout_diagram`, graph neighbors, relation-rules enforce on create, `delete_diagram`.

Full tools catalogue: [`tools.md`](tools.md).
