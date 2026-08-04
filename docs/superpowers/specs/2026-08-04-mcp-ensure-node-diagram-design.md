# MCP ensure_node / ensure_diagram — Design

Date: 2026-08-04  
Repos: arepos-server + warchi-mcp  
Branch: `feat/mcp-ensure-node-diagram`

## Goal

Idempotent find-or-create for model nodes and diagrams so agent retries do not duplicate entities.

Happy-path after this slice:

`search_notation` → `ensure_node` → `ensure_link` → `ensure_diagram` → `add_diagram_instances` → `create_wiki`

## Decisions (locked)

- Scope: **only** `ensure_node` + `ensure_diagram` (no layout, no composite landscape tool).
- Logic in **arepos**; MCP thin wrappers (same pattern as `ensure_link`).
- `ensure_node` match: `(modelId, parentNodeId|null, name)` case-insensitive. Notation binding used on create only; hit does not mutate attrs/type.
- `ensure_diagram` match: `(modelId, name)` → **latest** non-deleted version. Create defaults: `version=1.0.0`, empty instances attrs.
- No new DB unique constraints in v1; document race caveat.
- Ambiguous nodes (>1 match) → `409 AMBIGUOUS_NODE` with candidates.

## arepos API

### `POST /api/v1/nodes/ensure`

Body: `NodeRequest` (incl. optional `notationId` / `componentId` / `componentName`).

Response:

```json
{ "node": { /* NodeResponse */ }, "created": true }
```

### `POST /api/v1/diagrams/ensure`

Body: `modelId`, `name`, `notationId`, optional `nodeId`, `version`, `attrs`.

Response:

```json
{ "diagram": { /* DiagramResponse */ }, "created": false }
```

On hit: return latest by `DiagramLifecycleService.compareDiagramVersions`; do not update fields.

## MCP tools

| Tool | Endpoint |
|------|----------|
| `ensure_node` | `POST /nodes/ensure` |
| `ensure_diagram` | `POST /diagrams/ensure` |

Propagate `AMBIGUOUS_NODE` in `ToolResult` codes.

## Non-goals

`layout_diagram`, `upsert_diagram_from_spec`, `delete_diagram`, `ensure_wiki`, fuzzy relation names, `search_model` parentId filter.

## Tests

- ensure_node: create then idempotent; different parents allow same name; ambiguous → 409
- ensure_diagram: create empty canvas then idempotent; multi-version same name → latest
