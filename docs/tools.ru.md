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

Известные `code`: `BATCH_SAVE_CONFLICT`, `LOCKED_BY_OTHER`, `model_not_allowed`, `missing_scope`, `arepos_error`.

## Tools чтения (`models:read`)

| Tool | Назначение | Основные аргументы |
|------|------------|--------------------|
| `search_catalog` | Компактный поиск моделей/нотаций по имени | `q`, `kinds?`, `limit?` |
| `search_model` | Компактный поиск узлов/связей/диаграмм в модели | `modelId`, `q`, `kinds?`, `limit?` |
| `list_models` | Список доступных моделей | `name?`, `page?`, `size?` |
| `get_model` | Метаданные модели | `modelId` |
| `list_diagrams` | Диаграммы модели | `modelId`, `page?`, `size?` |
| `get_diagram` | Диаграмма + attrs | `diagramId` |
| `list_nodes` | Узлы модели | `modelId`, `page?`, `size?` |
| `get_node` | Узел + attrs | `nodeId` |
| `list_links` | Связи модели | `modelId`, `page?`, `size?` |
| `get_link` | Связь + attrs | `linkId` |
| `list_notations` | Доступные нотации | `name?`, `page?`, `size?` |
| `get_notation_summary` | Нотация + components + relations | `notationId` |

### Советы для агентов

- Предпочитайте `search_catalog` → `search_model` → `get_*` вместо массовых `list_*` при поиске по имени.
- Hit’ы поиска без `attrs` и canvas диаграммы; `get_*` — только для выбранных id.
- `limit` по умолчанию 20 (макс. 50). Связи ищутся по именам source/target (у link нет поля name).

## Tools записи (`models:write`)

| Tool | Назначение | Основные аргументы |
|------|------------|--------------------|
| `create_node` | Создать узел | `modelId`, `name`, `nodeTypeId`, `parentNodeId?`, `attrs?` |
| `update_node` | Обновить узел | `nodeId`, `name?`, `nodeTypeId?`, `parentNodeId?`, `attrs?` |
| `delete_node` | Удалить узел | `nodeId` |
| `create_link` | Создать связь | `modelId`, `sourceId`, `targetId`, `linkTypeId`, `attrs?` |
| `update_link` | Обновить связь | `linkId`, `sourceId?`, `targetId?`, `linkTypeId?`, `attrs?` |
| `delete_link` | Удалить связь | `linkId` |
| `update_diagram` | Обновить поля/attrs диаграммы | `diagramId`, `name?`, `version?`, `notationId?`, `nodeId?`, `attrs?` |
| `batch_save_model` | Атомарный batch-save | `modelId`, `requestJson` (BatchSaveRequest), `force?` |

### Конфликты

- Для совместного редактирования предпочтителен `batch_save_model` с `baseUpdatedAt`
- При HTTP 409 / conflict tools возвращают `code: BATCH_SAVE_CONFLICT` и details — **без silent overwrite**, если явно не передан `force=true`
- Блокировки редактирования диаграмм отдаются как `LOCKED_BY_OTHER`, если так сообщает arepos

## Вне v1

CRUD нотаций, шаринг, файлы/MinIO, OEF import, admin endpoints, stdio transport.
