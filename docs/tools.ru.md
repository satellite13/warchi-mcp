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

Известные `code`: `BATCH_SAVE_CONFLICT`, `DIAGRAM_CONFLICT`, `AMBIGUOUS_NOTATION_ELEMENT`, `AMBIGUOUS_NODE`, `AMBIGUOUS_WIKI`, `model_not_allowed`, `missing_scope`, `arepos_error`.

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
| `list_wiki` | Список wiki для model/diagram/node/component (fileId, label, entityType, entityId) | `modelId?`, `diagramId?`, `nodeId?`, `componentId?`, … |
| `get_wiki` | Прочитать markdown wiki по `fileId` | `fileId` |

### Советы для агентов

- Овервелы списков: `list_models` → `{items, total, page, size}`; `list_diagrams` / `list_nodes` / `list_links` / `list_notations` → `{content, page: {number, size, totalElements, totalPages}}`.
- Предпочитайте `search_catalog` → `search_notation` / `search_model` → `get_*` вместо массовых `list_*`.
- Для discovery компонентов/relations используйте `search_notation`, а не `get_notation_summary`.
- Hit’ы поиска без `attrs` и canvas; `get_*` — только для выбранных id.
- `limit` по умолчанию 20 (макс. 50). Связи ищутся по именам source/target.
- Wiki: `list_wiki` → `get_wiki`; create/update требуют file storage (MinIO) в arepos.

## Tools записи (`models:write`)

| Tool | Назначение | Основные аргументы |
|------|------------|--------------------|
| `create_node` | Создать узел (notation-aware) | `modelId`, `name`, `nodeTypeId?`, `notationId?`, `componentId?`/`componentName?`, … |
| `ensure_node` | Идемпотентный find-or-create узла | как `create_node` → `{node, created}` |
| `update_node` | Обновить узел | `nodeId`, … |
| `delete_node` | Удалить узел | `nodeId` |
| `create_link` | Создать связь (notation-aware) | `modelId`, `sourceId`, `targetId`, `linkTypeId?`, `notationId?`, `relationId?`/`relationName?`, … |
| `ensure_link` | Идемпотентный find-or-create связи | как `create_link` → `{link, created}` |
| `update_link` | Обновить связь | `linkId`, … |
| `delete_link` | Удалить связь | `linkId` |
| `create_diagram` | Создать диаграмму (пустой canvas) | `modelId`, `name`, `notationId`, `nodeId?`, `version?`, `attrs?` |
| `ensure_diagram` | Идемпотентный find-or-create диаграммы (latest по имени) | как `create_diagram` → `{diagram, created}` |
| `add_diagram_instances` | Merge/upsert instances на canvas | `diagramId`, `nodesJson?`, `edgesJson?`, `baseUpdatedAt?` |
| `update_diagram` | Обновить поля/attrs диаграммы | `diagramId`, … |
| `batch_save_model` | Атомарный batch-save (escape hatch) | `modelId`, `requestJson`, `force?` |
| `create_wiki` | Загрузить markdown + ref | `entityKind`, `entityId`, `content`, … |
| `ensure_wiki` | Идемпотентный ensure wiki (update если есть `documentFileId`/единственный ref, иначе create) → `{fileId, created, updated}` | как `create_wiki` |
| `update_wiki` | Заменить markdown | `fileId`, `content`, … |
| `ensure_custom_properties` | Создаёт отсутствующие customProperties нотационного компонента (add-if-missing по имени, существующие не трогаются) + зеркалирует на node type компонента; нужна права на редактирование нотации | `componentId`, `propertiesJson`, `nodeTypeId?` |

### Happy-path ландшафт (~5 типов вызовов)

Полный пошаговый рецепт: [`landscape-recipe.ru.md`](landscape-recipe.ru.md).

Предпочитайте `ensure_node` / `ensure_diagram` / `ensure_link` вместо `create_*` при ретраях (идемпотентно).

```
search_catalog / search_notation
ensure_node(..., notationId, componentName)   # ×N
ensure_link(..., notationId, relationName)    # ×N
ensure_diagram(...)
add_diagram_instances(..., edges по modelLinkId)
ensure_wiki(...)  # опционально (предпочтительнее create_wiki при ретраях)
```

- `ensure_node`: ключ `modelId + parentNodeId + name` (case-insensitive); notation binding только при create.
- `ensure_diagram`: ключ `modelId + name` → latest non-deleted; create — пустой canvas.
- `ensure_link`: ключ `modelId + sourceId + targetId + linkTypeId` (direction-strict).
- `ensure_custom_properties` идемпотентен: читает текущие `attrs`, дописывает только определения с отсутствующим `name` (существующие определения не мутирует) и PUT'ит весь `attrs` (arepos заменяет `attrs` целиком). Тот же merge применяется к node type компонента — wArchi показывает значения свойств в двух скоупах (`node.attrs.typeProperties` / `node.attrs.componentProperties`). Поля определения: `name` (required, ключ матчинга), `type` (string|number|boolean|enum, по умолчанию string), `required?`, `system?`, `regex?`, `min?`, `max?`, `maxLength?`, `enumValues?` (non-empty для enum), `id?` (генерируется, если не задан), `defaultValue?`, `interactive?`/`interactiveKind?`/`interactiveIcon?`.

### Конфликты

- Canvas merge: `baseUpdatedAt` → `DIAGRAM_CONFLICT`
- Batch-save: `baseUpdatedAt` → `BATCH_SAVE_CONFLICT` (без silent overwrite, кроме `force=true`)
- Неоднозначное имя component/relation → `AMBIGUOUS_NOTATION_ELEMENT`
- Неоднозначный узел (несколько совпадений model+parent+name) → `AMBIGUOUS_NODE`
- `update_diagram` → `409 CONFLICT`, если диаграмма не последней версии по имени или дубль name+version
- Блокировки диаграмм (`/api/v1/diagram-locks/*`, advisory, `LOCKED_BY_OTHER`) инструментами **не** вызываются; в UI диаграмму могут менять параллельно

## Вне v1

CRUD нотаций (единственное исключение — `ensure_custom_properties`), шаринг, бинарный upload UI, OEF import, admin endpoints, stdio transport, `layout_diagram`, graph neighbors, enforce relation-rules, `delete_diagram`.
