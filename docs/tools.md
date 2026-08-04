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

### Conflict handling

- Prefer `batch_save_model` with `baseUpdatedAt` for collaborative edits
- On HTTP 409 / conflict payload, tools return `code: BATCH_SAVE_CONFLICT` and details — **no silent overwrite** unless `force=true` is explicitly requested
- Diagram edit locks surface as `LOCKED_BY_OTHER` when arepos reports that reason

## Out of scope (v1)

Notation CRUD, resource shares, files/MinIO, OEF import, admin endpoints, stdio transport.
