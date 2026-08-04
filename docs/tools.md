# MCP Tools (v1)

Russian: [`tools.ru.md`](tools.ru.md)

All tools return a JSON string envelope:

```json
{ "ok": true, "data": { } }
```

or:

```json
{ "ok": false, "status": 403, "code": "missing_scope", "message": "...", "details": { } }
```

Known `code` values: `BATCH_SAVE_CONFLICT`, `DIAGRAM_CONFLICT`, `AMBIGUOUS_NOTATION_ELEMENT`, `AMBIGUOUS_NODE`, `LOCKED_BY_OTHER`, `model_not_allowed`, `missing_scope`, `arepos_error`.

## Read tools (`models:read`)

| Tool | Purpose | Main args |
|------|---------|-----------|
| `search_catalog` | Slim search models/notations by name | `q`, `kinds?`, `limit?` |
| `search_model` | Slim search nodes/links/diagrams in a model | `modelId`, `q`, `kinds?`, `limit?` |
| `search_notation` | Slim search components/relations in a notation | `notationId`, `q`, `kinds?`, `limit?` |
| `list_models` | List accessible models | `name?`, `page?`, `size?` |
| `get_model` | Model metadata | `modelId` |
| `list_diagrams` | Diagrams of a model | `modelId`, `page?`, `size?` |
| `get_diagram` | Diagram + attrs | `diagramId` |
| `list_nodes` | Nodes of a model | `modelId`, `page?`, `size?` |
| `get_node` | Node + attrs | `nodeId` |
| `list_links` | Links of a model | `modelId`, `page?`, `size?` |
| `get_link` | Link + attrs | `linkId` |
| `list_notations` | Accessible notations | `name?`, `page?`, `size?` |
| `get_notation_summary` | Notation + components + relations (heavy) | `notationId` |
| `list_wiki` | List wiki docs for model/diagram/node/component | `modelId?`, `diagramId?`, `nodeId?`, `componentId?`, … |
| `get_wiki` | Read wiki markdown by `fileId` | `fileId` |

### Agent tips

- Prefer `search_catalog` → `search_notation` / `search_model` → `get_*` over bulk `list_*` when looking up by name.
- Prefer `search_notation` over `get_notation_summary` for discovery (Archimate has dozens of components).
- Search hits omit `attrs` and diagram canvas; call `get_*` only for selected ids.
- Default `limit` is 20 (max 50). Links match by source/target node names (links have no name field).
- Wiki: `list_wiki` → `get_wiki` for content; create/update needs MinIO/file storage enabled on arepos.

## Write tools (`models:write`)

| Tool | Purpose | Main args |
|------|---------|-----------|
| `create_node` | Create node (notation-aware) | `modelId`, `name`, `nodeTypeId?`, `notationId?`, `componentId?`/`componentName?`, `parentNodeId?`, `attrs?` |
| `ensure_node` | Idempotent find-or-create node | same as `create_node` → `{node, created}` |
| `update_node` | Update node | `nodeId`, `name?`, `nodeTypeId?`, `parentNodeId?`, `attrs?` |
| `delete_node` | Delete node | `nodeId` |
| `create_link` | Create link (notation-aware) | `modelId`, `sourceId`, `targetId`, `linkTypeId?`, `notationId?`, `relationId?`/`relationName?`, `attrs?` |
| `ensure_link` | Idempotent find-or-create link | same as `create_link` → `{link, created}` |
| `update_link` | Update link | `linkId`, `sourceId?`, `targetId?`, `linkTypeId?`, `attrs?` |
| `delete_link` | Delete link | `linkId` |
| `create_diagram` | Create diagram (empty canvas by default) | `modelId`, `name`, `notationId`, `nodeId?`, `version?`, `attrs?` |
| `ensure_diagram` | Idempotent find-or-create diagram (latest by name) | same as `create_diagram` → `{diagram, created}` |
| `add_diagram_instances` | Merge/upsert canvas instances | `diagramId`, `nodesJson?`, `edgesJson?`, `baseUpdatedAt?` |
| `update_diagram` | Update diagram fields/attrs | `diagramId`, `name?`, `version?`, `notationId?`, `nodeId?`, `attrs?` |
| `batch_save_model` | Atomic batch save (escape hatch) | `modelId`, `requestJson` (BatchSaveRequest), `force?` |
| `create_wiki` | Upload markdown, register ref, set `attrs.documentFileId` | `entityKind`, `entityId`, `content`, `filename?`, `modelId?`, `notationId?` |
| `update_wiki` | Replace markdown content | `fileId`, `content`, `filename?` |

### Happy-path landscape recipe (~5 calls)

Prefer `ensure_node` / `ensure_diagram` / `ensure_link` over `create_*` for retries (idempotent).

```
search_catalog / search_notation
ensure_node(modelId, name, notationId, componentName)   # ×N
ensure_link(modelId, sourceId, targetId, notationId, relationName)  # ×N
ensure_diagram(modelId, name, notationId)
add_diagram_instances(diagramId, nodesJson, edgesJson)  # edges by modelLinkId only
create_wiki(...)  # optional
```

- With `notationId` + `componentName` / `relationName`, arepos resolves type ids and writes `notationComponents` / `notationRelations`.
- Ambiguous component/relation name → `409` / `AMBIGUOUS_NOTATION_ELEMENT` with `candidates`.
- Ambiguous node (multiple matches for model+parent+name) → `409` / `AMBIGUOUS_NODE` with `candidates`.
- `add_diagram_instances` merges by `modelNodeId` / `modelLinkId`; does not delete untouched instances.
- `ensure_node` match key: `modelId + parentNodeId + name` (case-insensitive). Notation binding on create only.
- `ensure_diagram` match key: `modelId + name` → latest non-deleted version. Create defaults empty canvas.
- `ensure_link` match key: `modelId + sourceId + targetId + linkTypeId` (direction-strict). No DB unique constraint — dual concurrent ensure may race.

### Conflict handling

- Prefer `add_diagram_instances` with `baseUpdatedAt` for canvas merges → `DIAGRAM_CONFLICT` on stale base
- Prefer `batch_save_model` with `baseUpdatedAt` for collaborative multi-entity edits → `BATCH_SAVE_CONFLICT`
- **No silent overwrite** unless `force=true` is explicitly requested on batch-save
- Diagram edit locks surface as `LOCKED_BY_OTHER` when arepos reports that reason

## Out of scope (v1)

Notation CRUD, resource shares, binary file upload UI, OEF import, admin endpoints, stdio transport, `layout_diagram`, graph neighbors, relation-rules enforce on create, `delete_diagram`.
