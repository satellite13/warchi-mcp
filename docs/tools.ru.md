# MCP Tools (v1)

English: [`tools.md`](tools.md)

Все tools возвращают JSON-строку-конверт:

```json
{ "ok": true, "data": { } }
```

или:

```json
{ "ok": false, "status": 403, "code": "missing_scope", "message": "...", "details": { } }
```

Известные `code`: `BATCH_SAVE_CONFLICT`, `DIAGRAM_CONFLICT`, `AMBIGUOUS_NOTATION_ELEMENT`, `LOCKED_BY_OTHER`, `model_not_allowed`, `missing_scope`, `arepos_error`.

## Tools чтения (`models:read`)

| Tool | Назначение | Основные аргументы |
|------|------------|--------------------|
| `search_catalog` | Компактный поиск моделей/нотаций по имени | `q`, `kinds?`, `limit?` |
| `search_model` | Компактный поиск узлов/связей/диаграмм в модели | `modelId`, `q`, `kinds?`, `limit?` |
| `search_notation` | Компактный поиск components/relations в нотации | `notationId`, `q`, `kinds?`, `limit?` |
| `list_models` | Список доступных моделей | `name?`, `page?`, `size?` |
| `get_model` | Метаданные модели | `modelId` |
| `list_diagrams` | Диаграммы модели | `modelId`, `page?`, `size?` |
| `get_diagram` | Диаграмма + attrs | `diagramId` |
| `list_nodes` | Узлы модели | `modelId`, `page?`, `size?` |
| `get_node` | Узел + attrs | `nodeId` |
| `list_links` | Связи модели | `modelId`, `page?`, `size?` |
| `get_link` | Связь + attrs | `linkId` |
| `list_notations` | Доступные нотации | `name?`, `page?`, `size?` |
| `get_notation_summary` | Нотация + components + relations (тяжёлый) | `notationId` |
| `list_wiki` | Список wiki для model/diagram/node/component | `modelId?`, `diagramId?`, `nodeId?`, `componentId?`, … |
| `get_wiki` | Прочитать markdown wiki по `fileId` | `fileId` |

### Советы для агентов

- Предпочитайте `search_catalog` → `search_notation` / `search_model` → `get_*` вместо массовых `list_*`.
- Для discovery компонентов/relations используйте `search_notation`, а не `get_notation_summary`.
- Hit’ы поиска без `attrs` и canvas; `get_*` — только для выбранных id.
- `limit` по умолчанию 20 (макс. 50). Связи ищутся по именам source/target.
- Wiki: `list_wiki` → `get_wiki`; create/update требуют file storage (MinIO) в arepos.

## Tools записи (`models:write`)

| Tool | Назначение | Основные аргументы |
|------|------------|--------------------|
| `create_node` | Создать узел (notation-aware) | `modelId`, `name`, `nodeTypeId?`, `notationId?`, `componentId?`/`componentName?`, … |
| `update_node` | Обновить узел | `nodeId`, … |
| `delete_node` | Удалить узел | `nodeId` |
| `create_link` | Создать связь (notation-aware) | `modelId`, `sourceId`, `targetId`, `linkTypeId?`, `notationId?`, `relationId?`/`relationName?`, … |
| `ensure_link` | Идемпотентный find-or-create связи | как `create_link` → `{link, created}` |
| `update_link` | Обновить связь | `linkId`, … |
| `delete_link` | Удалить связь | `linkId` |
| `create_diagram` | Создать диаграмму (пустой canvas) | `modelId`, `name`, `notationId`, `nodeId?`, `version?`, `attrs?` |
| `add_diagram_instances` | Merge/upsert instances на canvas | `diagramId`, `nodesJson?`, `edgesJson?`, `baseUpdatedAt?` |
| `update_diagram` | Обновить поля/attrs диаграммы | `diagramId`, … |
| `batch_save_model` | Атомарный batch-save (escape hatch) | `modelId`, `requestJson`, `force?` |
| `create_wiki` | Загрузить markdown + ref | `entityKind`, `entityId`, `content`, … |
| `update_wiki` | Заменить markdown | `fileId`, `content`, … |

### Happy-path ландшафт (~5 вызовов)

```
search_catalog / search_notation
create_node(..., notationId, componentName)   # ×N
ensure_link(..., notationId, relationName)    # ×N
create_diagram(...)
add_diagram_instances(..., edges по modelLinkId)
create_wiki(...)  # опционально
```

### Конфликты

- Canvas merge: `baseUpdatedAt` → `DIAGRAM_CONFLICT`
- Batch-save: `baseUpdatedAt` → `BATCH_SAVE_CONFLICT` (без silent overwrite, кроме `force=true`)
- Неоднозначное имя component/relation → `AMBIGUOUS_NOTATION_ELEMENT`
- Блокировки диаграмм → `LOCKED_BY_OTHER`

## Вне v1

CRUD нотаций, шаринг, бинарный upload UI, OEF import, admin endpoints, stdio transport, `layout_diagram`, graph neighbors, enforce relation-rules, `delete_diagram`.
