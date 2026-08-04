# MCP Diagram Convenience Tools — Design

Date: 2026-08-04  
Repos: arepos-server + warchi-mcp  
Branch: `feat/mcp-diagram-convenience`

## Goal

Agent happy-path without hand-building UUID/attrs/canvas JSON:

`search_notation` → `create_node(componentName)` → `ensure_link(relationName)` → `create_diagram` → `add_diagram_instances` → `create_wiki`

## Decisions

- Convenience logic lives in **arepos-server**; MCP is thin wrappers.
- Extend existing `create_node` / `create_link` (optional notation binding), not separate smart tools.
- Canvas tool is **merge/upsert** (`add_diagram_instances`), not full replace.
- Scope includes `search_notation` + `ensure_link`; excludes `layout_diagram`, `delete_diagram`, graph neighbors, relation-rules enforce.

## arepos API

| Endpoint | Behavior |
|----------|----------|
| `POST /nodes` | Optional `notationId` + `componentId`/`componentName`; resolves `nodeTypeId`, merges `attrs.notationComponents` |
| `POST /links` | Optional `notationId` + `relationId`/`relationName`; resolves `linkTypeId`, merges `attrs.notationRelations` |
| `POST /links/ensure` | Find-or-create by `(modelId, sourceId, targetId, linkTypeId)` → `{link, created}` |
| `POST /diagrams/{id}/instances:merge` | Upsert nodes by `modelNodeId`, edges by `modelLinkId` with instance resolve; optional `baseUpdatedAt` |
| `GET /search/notations/{id}` | Slim component/relation hits |
| `POST /diagrams` | Already existed; MCP `create_diagram` wraps it |

Error codes: `AMBIGUOUS_NOTATION_ELEMENT`, `DIAGRAM_CONFLICT`.

## MCP tools

`create_diagram`, extended `create_node`/`create_link`, `add_diagram_instances`, `search_notation`, `ensure_link`.

## Non-goals

ELK/grid layout, delete diagram, neighbors API, DB unique constraint on links, UI changes in wArchi.
