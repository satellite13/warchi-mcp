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

Known `code` values: `BATCH_SAVE_CONFLICT`, `LOCKED_BY_OTHER`, `model_not_allowed`, `missing_scope`, `arepos_error`.

## Read tools (`models:read`)

| Tool | Purpose | Main args |
|------|---------|-----------|
| `search_catalog` | Slim search models/notations by name | `q`, `kinds?`, `limit?` |
| `search_model` | Slim search nodes/links/diagrams in a model | `modelId`, `q`, `kinds?`, `limit?` |
| `list_models` | List accessible models | `name?`, `page?`, `size?` |
| `get_model` | Model metadata | `modelId` |
| `list_diagrams` | Diagrams of a model | `modelId`, `page?`, `size?` |
| `get_diagram` | Diagram + attrs | `diagramId` |
| `list_nodes` | Nodes of a model | `modelId`, `page?`, `size?` |
| `get_node` | Node + attrs | `nodeId` |
| `list_links` | Links of a model | `modelId`, `page?`, `size?` |
| `get_link` | Link + attrs | `linkId` |
| `list_notations` | Accessible notations | `name?`, `page?`, `size?` |
| `get_notation_summary` | Notation + components + relations | `notationId` |
| `list_wiki` | List wiki docs for model/diagram/node/component | `modelId?`, `diagramId?`, `nodeId?`, `componentId?`, … |
| `get_wiki` | Read wiki markdown by `fileId` | `fileId` |

### Agent tips

- Prefer `search_catalog` → `search_model` → `get_*` over bulk `list_*` when looking up by name.
- Search hits omit `attrs` and diagram canvas; call `get_*` only for selected ids.
- Default `limit` is 20 (max 50). Links match by source/target node names (links have no name field).
- Wiki: `list_wiki` → `get_wiki` for content; create/update needs MinIO/file storage enabled on arepos.

## Write tools (`models:write`)

| Tool | Purpose | Main args |
|------|---------|-----------|
| `create_node` | Create node | `modelId`, `name`, `nodeTypeId`, `parentNodeId?`, `attrs?` |
| `update_node` | Update node | `nodeId`, `name?`, `nodeTypeId?`, `parentNodeId?`, `attrs?` |
| `delete_node` | Delete node | `nodeId` |
| `create_link` | Create link | `modelId`, `sourceId`, `targetId`, `linkTypeId`, `attrs?` |
| `update_link` | Update link | `linkId`, `sourceId?`, `targetId?`, `linkTypeId?`, `attrs?` |
| `delete_link` | Delete link | `linkId` |
| `update_diagram` | Update diagram fields/attrs | `diagramId`, `name?`, `version?`, `notationId?`, `nodeId?`, `attrs?` |
| `batch_save_model` | Atomic batch save | `modelId`, `requestJson` (BatchSaveRequest), `force?` |
| `create_wiki` | Upload markdown, register ref, set `attrs.documentFileId` | `entityKind`, `entityId`, `content`, `filename?`, `modelId?`, `notationId?` |
| `update_wiki` | Replace markdown content | `fileId`, `content`, `filename?` |

### Conflict handling

- Prefer `batch_save_model` with `baseUpdatedAt` for collaborative edits
- On HTTP 409 / conflict payload, tools return `code: BATCH_SAVE_CONFLICT` and details — **no silent overwrite** unless `force=true` is explicitly requested
- Diagram edit locks surface as `LOCKED_BY_OTHER` when arepos reports that reason

## Out of scope (v1)

Notation CRUD, resource shares, binary file upload UI, OEF import, admin endpoints, stdio transport.
